package com.bettercontent.betterworldmanagement.feasibility;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.logging.LogUtils;
import com.mojang.datafixers.util.Either;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Comparator;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRegistry;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.gametest.framework.GameTestTicker;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.gametest.ForgeGameTestHooks;
import org.slf4j.Logger;

/** Non-shipping bridge to the native GameTest framework on an actual dedicated fixture server. */
public final class NativeFixtureRunner {
    private static final Logger LOG = LogUtils.getLogger();
    private static final TicketType<UUID> PREPARATION_TICKET = TicketType.create("axioms_native_preparation", Comparator.<UUID>naturalOrder());
    private static boolean installed;
    private static Supplier<List<Class<?>>> classes;
    private static MinecraftServer server;
    private static int startupTicks;
    private static boolean autoAttempted;
    private static Campaign campaign;
    private static List<Class<?>> selectedClasses = List.of();
    private static Throwable registrationError;
    private static final Map<Class<?>, List<TestFunction>> registeredFunctions = new LinkedHashMap<>();

    private NativeFixtureRunner() {}

    public static synchronized void install(Supplier<List<Class<?>>> selectedClasses) {
        if (!Boolean.getBoolean("world_axioms.nativeFixtures") || installed) return;
        classes = java.util.Objects.requireNonNull(selectedClasses);
        installed = true;
        MinecraftForge.EVENT_BUS.addListener(NativeFixtureRunner::started);
        MinecraftForge.EVENT_BUS.addListener(NativeFixtureRunner::tick);
        MinecraftForge.EVENT_BUS.addListener(NativeFixtureRunner::commands);
        MinecraftForge.EVENT_BUS.addListener(NativeFixtureRunner::stopped);
    }

    private static void started(ServerStartedEvent event) {
        if (!(event.getServer() instanceof DedicatedServer) || !event.getServer().isDedicatedServer()) return;
        server = event.getServer(); startupTicks = 0; autoAttempted = false; campaign = null;
        LOG.warn("Native fixture runner enabled on an actual dedicated server; disposable worlds only. No production flags changed.");
        registrationError = null;
        registeredFunctions.clear();
        try {
            selectedClasses = List.copyOf(classes.get());
            Set<String> names = new HashSet<>();
            for (Class<?> type : selectedClasses) {
                String name = type.getSimpleName();
                if (!names.add(name)) throw new IllegalArgumentException("Duplicate native fixture class name: " + name);
                // PrefixGameTestTemplate(false) removes the CLASS PREFIX FROM TEST NAMES too in pinned Forge.
                // Native getTestFunctionsForClassName cannot recover those functions. Preserve actual public
                // registration provenance by identity, without inspecting members or manufacturing functions.
                if (GameTestRegistry.isTestClass(name))
                    throw new IllegalStateException("Native fixture class was already registered; provenance is ambiguous: " + name);
                List<TestFunction> actual = registerFunctions(type);
                if (actual.isEmpty()) throw new IllegalStateException("No actual native functions registered for " + name);
                registeredFunctions.put(type, actual);
                LOG.info("Native public registration {} -> {}", type.getName(), actual.stream().map(TestFunction::getTestName).toList());
            }
        } catch (Throwable error) {
            registrationError = error;
            LOG.error("Native fixture class registration failed; any requested campaign will be FAIL", error);
        }
    }

    static List<TestFunction> registerFunctions(Class<?> type) {
        Set<TestFunction> before = Collections.newSetFromMap(new IdentityHashMap<>());
        before.addAll(GameTestRegistry.getAllTestFunctions());
        GameTestRegistry.register(type); // Native registry alone performs annotation/member inspection.
        return GameTestRegistry.getAllTestFunctions().stream().filter(function -> !before.contains(function)).toList();
    }

    private static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("axioms_native").requires(source -> source.hasPermission(4))
                .then(Commands.literal("run").executes(context -> {
                    MinecraftServer target = context.getSource().getServer();
                    if (target != server || !(target instanceof DedicatedServer) || !target.isDedicatedServer()) {
                        context.getSource().sendFailure(Component.literal("An actual started dedicated fixture server is required."));
                        return 0;
                    }
                    if (startupTicks < 20 || (campaign != null && !campaign.finished)) {
                        context.getSource().sendFailure(Component.literal("Wait 20 startup ticks and for the current campaign to finish."));
                        return 0;
                    }
                    if (pendingSuccession(target)) {
                        context.getSource().sendFailure(Component.literal("Native fixtures refused: successor/reset request exists."));
                        return 0;
                    }
                    autoAttempted = true;
                    begin(target);
                    context.getSource().sendSuccess(() -> Component.literal("Native fixture campaign: "
                            + (campaign == null ? "initialization failed; see server log" : campaign.id)), false);
                    return campaign != null ? 1 : 0;
                })));
    }

    private static boolean pendingSuccession(MinecraftServer target) {
        Path control = target.getServerDirectory().toPath().toAbsolutePath().normalize()
                .resolve(".better_world_management/control");
        return Files.exists(control.resolve("successor-request-v5.tsv"))
                || Files.exists(control.resolve("reset-request-v5.tsv"))
                || Files.exists(control.resolve("world-axiom-reset-v1.json"));
    }

    private static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer() != server) return;
        startupTicks++;
        if (!autoAttempted && startupTicks >= 20 && Boolean.parseBoolean(System.getProperty("world_axioms.autoFixtures", "true"))) {
            autoAttempted = true;
            if (pendingSuccession(server)) LOG.warn("Native auto fixtures skipped: successor/reset request exists; world left untouched.");
            else begin(server);
        }
        Campaign current = campaign;
        if (current == null || current.finished) return;
        if (current.preparing) {
            try { prepareTick(current); }
            catch (Throwable error) {
                current.fatal = error;
                LOG.error("Native fixture asynchronous preparation failed: " + current.id, error);
                failInitialization(current);
            }
            return;
        }
        try {
            // MinecraftServer already ticks the native ticker when Forge enables GameTests. Never double-tick it.
            if (!ForgeGameTestHooks.isGametestEnabled()) GameTestTicker.SINGLETON.tick();
            if (current.infos.stream().allMatch(GameTestInfo::isDone)) finish(current);
        } catch (Throwable error) {
            current.fatal = error;
            LOG.error("Native fixture framework tick failed for campaign " + current.id, error);
            finish(current);
        }
    }

    private static void begin(MinecraftServer target) {
        campaign = null;
        if (pendingSuccession(target)) {
            LOG.warn("Native fixture start refused: successor/reset request appeared; world left untouched.");
            return;
        }
        Campaign next;
        try {
            Path base = target.getServerDirectory().toPath().toAbsolutePath().normalize().resolve("axiom-native-results");
            Files.createDirectories(base);
            next = new Campaign(target, UUID.randomUUID(), base);
            Files.createDirectory(next.directory);
        } catch (IOException error) {
            LOG.error("Cannot allocate a fresh native fixture evidence directory; no tests started", error);
            return;
        }
        campaign = next;
        try {
            for (Class<?> type : selectedClasses) next.classNames.add(type.getName());
            if (registrationError != null) throw new IllegalStateException("Native class registration failed", registrationError);
            List<Class<?>> selected = selectedClasses;
            if (selected.isEmpty()) throw new IllegalStateException("No native fixture classes selected");
            Set<String> classNames = new HashSet<>();
            Map<String, TestFunction> functions = new LinkedHashMap<>();
            for (Class<?> type : selected) {
                String name = type.getSimpleName();
                if (!classNames.add(name)) throw new IllegalArgumentException("Duplicate native fixture class name: " + name);
                Collection<TestFunction> registered = registeredFunctions.getOrDefault(type, List.of());
                if (registered.isEmpty()) throw new IllegalStateException("No native functions registered for " + name);
                for (TestFunction function : registered) {
                    if (functions.putIfAbsent(function.getTestName(), function) != null)
                        throw new IllegalStateException("Duplicate native test name: " + function.getTestName());
                }
            }
            String only = System.getProperty("world_axioms.test", "");
            if (!only.isBlank()) {
                TestFunction selectedFunction = functions.get(only);
                if (selectedFunction == null) throw new IllegalArgumentException("Unknown exact native test: " + only);
                functions.clear();
                functions.put(only, selectedFunction);
            }
            next.functions.addAll(functions.values());
            if (next.functions.stream().noneMatch(TestFunction::isRequired))
                throw new IllegalStateException("No required native fixture cases selected; cannot qualify PASS");
            // Fresh remote origins avoid spawn and prior campaign structures; the native framework owns region tickets.
            int x = 1_000_000 + Math.floorMod(next.id.getMostSignificantBits(), 125_000) * 16;
            int z = 1_000_000 + Math.floorMod(next.id.getLeastSignificantBits(), 125_000) * 16;
            next.origin = new BlockPos(x, 128, z);
            var level = target.overworld();
            if (level.getSharedSpawnPos().distSqr(next.origin) < 8192.0 * 8192.0
                    || !level.getWorldBorder().isWithinBounds(next.origin.offset(-4096, 0, -4096))
                    || !level.getWorldBorder().isWithinBounds(next.origin.offset(4096, 0, 4096)))
                throw new IllegalStateException("No safe isolated fixture origin within this world border");
            for (var player : level.players()) if (player.blockPosition().distSqr(next.origin) < 8192.0 * 8192.0)
                throw new IllegalStateException("A player is near the proposed native fixture region");
            next.preparationChunks.addAll(preparationRegion(next.origin));
            next.preparing = true;
            next.preparationStarted = System.nanoTime();
            next.preparationThread = Thread.currentThread();
            next.requestExecutor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(25), task -> {
                Thread thread = new Thread(task, "axioms-native-request-" + next.id);
                thread.setDaemon(true);
                return thread;
            });
            write(next, "PREPARING"); // No native cases/clocks/body execution before all 25 real FULL chunks.
        } catch (Throwable error) {
            next.fatal = error;
            LOG.error("Native fixture campaign initialization failed: " + next.id, error);
            failInitialization(next);
        }
    }

    static List<ChunkPos> preparationRegion(BlockPos origin) {
        ChunkPos center = new ChunkPos(origin);
        List<ChunkPos> positions = new ArrayList<>();
        // Exactly the pinned native StructureUtils.forceLoadChunks first-unit region: -1..3.
        for (int dx = -1; dx <= 3; dx++) for (int dz = -1; dz <= 3; dz++)
            positions.add(new ChunkPos(center.x + dx, center.z + dz));
        return List.copyOf(positions);
    }

    private static void prepareTick(Campaign current) throws IOException {
        if (pendingSuccession(current.server)) throw new IllegalStateException("Succession request appeared during preparation");
        current.preparationTicks++;
        var source = current.server.overworld().getChunkSource();
        // Tickets are established at least one ordinary server tick before their false-create request.
        // The public API's OFF-thread branch marshals native scheduling to its main-thread executor;
        // calling the same public API directly here would managedBlock and defeat asynchronous preparation.
        if (current.preparationFutures.size() < current.preparationTickets.size()) {
            ChunkPos pos = current.preparationChunks.get(current.preparationFutures.size());
            CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>> future =
                    CompletableFuture.supplyAsync(() -> {
                        if (Thread.currentThread() == current.preparationThread)
                            throw new IllegalStateException("Chunk future request must use the supported off-thread API branch");
                        return source.getChunkFuture(pos.x, pos.z, ChunkStatus.FULL, false);
                    }, current.requestExecutor).thenCompose(value -> value);
            current.preparationFutures.put(pos, future);
            current.preparationStatus.put(pos, "REQUESTED");
        }
        if (current.preparationTickets.size() < 25) {
            ChunkPos pos = current.preparationChunks.get(current.preparationTickets.size());
            source.addRegionTicket(PREPARATION_TICKET, pos, 0, current.id);
            current.preparationTickets.add(pos);
            current.preparationStatus.put(pos, "TICKETED");
        }
        int completed = 0;
        for (var entry : current.preparationFutures.entrySet()) {
            ChunkPos pos = entry.getKey();
            var future = entry.getValue();
            if (!future.isDone()) { current.preparationStatus.put(pos, "PENDING"); continue; }
            // Joining only a proven DONE future cannot wait for generation. Exceptional completion fails closed.
            if (future.isCompletedExceptionally() || future.isCancelled()) current.preparationStatus.put(pos, "FAILED");
            var result = future.join();
            if (result.left().isEmpty()) {
                current.preparationStatus.put(pos, "FAILED");
                throw new IllegalStateException("Native FULL request failed: " + pos + " " + result);
            }
            ChunkAccess chunk = result.left().orElseThrow();
            var now = source.getChunkNow(pos.x, pos.z);
            if (chunk.getStatus() != ChunkStatus.FULL || !chunk.getPos().equals(pos)
                    || now == null || now.getStatus() != ChunkStatus.FULL || !now.getPos().equals(pos)) {
                current.preparationStatus.put(pos, "FAILED");
                throw new IllegalStateException("Completed request lacks actual resident FULL chunk: " + pos);
            }
            current.preparationStatus.put(pos, "DONE"); completed++;
        }
        current.preparationCompleted = completed;
        if (current.preparationFutures.size() == 25 && completed == 25) {
            for (var player : current.server.overworld().players())
                if (player.blockPosition().distSqr(current.origin) < 8192.0 * 8192.0)
                    throw new IllegalStateException("Player approached prepared native fixture region");
            startNative(current);
        } else if (current.preparationTicks == 1 || current.preparationTicks % 20 == 0) write(current, "PREPARING");
    }

    private static void startNative(Campaign current) throws IOException {
        var level = current.server.overworld();
        current.preparing = false;
        current.preparationFinished = System.nanoTime();
        write(current, "RUNNING"); // Native case timeouts remain owned by runTests, not the preparation clock.
        current.infos.addAll(GameTestRunner.runTests(current.functions, current.origin, Rotation.NONE, level, GameTestTicker.SINGLETON, 4));
        if (current.infos.size() != current.functions.size()) throw new IllegalStateException("Native runner returned an unexpected case count");
        for (ChunkPos pos : current.preparationChunks)
            if (!level.getForcedChunks().contains(pos.toLong()))
                throw new IllegalStateException("Native framework did not take ownership of prepared region: " + pos);
        current.frameworkOwnsPreparation = true;
        releasePreparation(current); // Remove only OUR custom UUID tickets; never alter native forced flags.
        GameTestListener listener = new GameTestListener() {
            @Override public void testStructureLoaded(GameTestInfo info) { record(current, "structure_loaded", info); }
            @Override public void testPassed(GameTestInfo info) { record(current, "passed", info); }
            @Override public void testFailed(GameTestInfo info) { record(current, "failed", info); }
        };
        for (GameTestInfo info : current.infos) info.addListener(listener);
        write(current, "RUNNING");
        LOG.info("Native fixture campaign {} started {} actual cases after {} FULL chunks / {} preparation ticks; results {}",
                current.id, current.infos.size(), current.preparationCompleted, current.preparationTicks, current.directory);
    }

    private static void releasePreparation(Campaign current) {
        var source = current.server.overworld().getChunkSource();
        for (ChunkPos pos : current.preparationTickets) source.removeRegionTicket(PREPARATION_TICKET, pos, 0, current.id);
        current.preparationTickets.clear();
        if (current.requestExecutor != null) current.requestExecutor.shutdownNow();
    }

    private static void failInitialization(Campaign current) {
        current.finished = true;
        current.worldSaved = false;
        try {
            // Publish the real failure BEFORE any potentially blocking world save/normal shutdown.
            // Native03 proved a synchronous END-tick flush can watchdog before publishing a manifest.
            write(current, "FAIL");
            LOG.error("Native fixture initialization FAIL published {}; world_saved=false", current.directory);
            releasePreparation(current);
            if (Boolean.getBoolean("world_axioms.stopAfterFixtures")) current.server.halt(false);
        } catch (IOException error) {
            releasePreparation(current);
            LOG.error("Cannot persist native initialization FAIL; server will NOT auto-stop " + current.id, error);
        }
    }

    private static void record(Campaign current, String event, GameTestInfo info) {
        JsonObject row = new JsonObject(); row.addProperty("event", event); row.addProperty("name", info.getTestName());
        row.addProperty("at", Instant.now().toString()); row.addProperty("required", info.isRequired());
        row.addProperty("completed", info.isDone());
        if (info.getError() != null) {
            row.addProperty("error", stack(info.getError()));
            LOG.error("Native case failed: " + info.getTestName(), info.getError());
        } else LOG.info("Native case {}: {}", event, info.getTestName());
        current.events.add(row);
    }

    private static void finish(Campaign current) {
        if (current.finished) return;
        current.finished = true;
        boolean saved = false;
        try { saved = current.server.saveEverything(false, true, true); }
        catch (Throwable error) { current.saveError = error; LOG.error("Native fixture final normal world save failed", error); }
        current.worldSaved = saved;
        if (!saved && current.saveError == null) current.saveError = new IllegalStateException("Normal world save did not report success");
        boolean complete = current.functions.size() == current.infos.size() && !current.functions.isEmpty()
                && current.infos.stream().allMatch(GameTestInfo::isDone);
        boolean pass = complete && current.fatal == null && current.saveError == null
                && current.infos.stream().anyMatch(GameTestInfo::isRequired)
                && current.infos.stream().filter(GameTestInfo::isRequired).allMatch(GameTestInfo::hasSucceeded);
        try {
            write(current, pass ? "PASS" : "FAIL");
            LOG.info("Native fixture campaign {} final status {}; evidence {}", current.id, pass ? "PASS" : "FAIL", current.directory);
            // Results are forced and atomically published and normal world save has succeeded before any optional stop.
            if (saved && Boolean.getBoolean("world_axioms.stopAfterFixtures")) current.server.halt(false);
        } catch (IOException error) {
            LOG.error("Native results could not be flushed; server will NOT auto-stop. Campaign " + current.id, error);
        }
    }

    private static void write(Campaign current, String status) throws IOException {
        JsonObject result = new JsonObject(); result.addProperty("schema", "world_axioms_native_fixtures_v1");
        result.addProperty("campaign", current.id.toString()); result.addProperty("status", status);
        result.addProperty("started_at", current.startedAt); result.addProperty("updated_at", Instant.now().toString());
        result.addProperty("actual_dedicated", current.server instanceof DedicatedServer && current.server.isDedicatedServer());
        result.addProperty("forge_gametest_enabled", ForgeGameTestHooks.isGametestEnabled());
        result.addProperty("world_saved", current.worldSaved);
        if (current.origin != null) {
            JsonObject origin = new JsonObject(); origin.addProperty("x", current.origin.getX());
            origin.addProperty("y", current.origin.getY()); origin.addProperty("z", current.origin.getZ()); result.add("origin", origin);
        }
        JsonArray selected = new JsonArray(); current.classNames.forEach(name -> selected.add(new JsonPrimitive(name))); result.add("classes", selected);
        JsonObject provenance = new JsonObject();
        registeredFunctions.forEach((type, functions) -> {
            JsonArray names = new JsonArray(); functions.forEach(function -> names.add(function.getTestName()));
            provenance.add(type.getName(), names);
        });
        result.add("registered_functions_by_class", provenance);
        JsonObject preparation = new JsonObject();
        preparation.addProperty("limit", 25); preparation.addProperty("requested", current.preparationFutures.size());
        preparation.addProperty("completed", current.preparationStatus.values().stream().filter("DONE"::equals).count()); preparation.addProperty("ticks", current.preparationTicks);
        preparation.addProperty("owned_tickets", current.preparationTickets.size());
        preparation.addProperty("framework_owns_region", current.frameworkOwnsPreparation);
        preparation.addProperty("wall_ms", current.preparationStarted == 0 ? 0 :
                ((current.preparationFinished == 0 ? System.nanoTime() : current.preparationFinished) - current.preparationStarted) / 1_000_000);
        JsonArray requests = new JsonArray(); current.preparationStatus.forEach((pos, chunkState) -> {
            JsonObject row = new JsonObject(); row.addProperty("x", pos.x); row.addProperty("z", pos.z); row.addProperty("status", chunkState); requests.add(row);
        });
        preparation.add("requests", requests); result.add("preparation", preparation);
        JsonArray cases = new JsonArray(); int required = 0, completed = 0, failed = 0;
        for (TestFunction function : current.functions) {
            GameTestInfo info = current.infos.stream().filter(test -> test.getTestName().equals(function.getTestName())).findFirst().orElse(null);
            JsonObject row = new JsonObject(); row.addProperty("name", function.getTestName()); row.addProperty("required", function.isRequired());
            row.addProperty("completed", info != null && info.isDone()); row.addProperty("passed", info != null && info.hasSucceeded());
            row.addProperty("failed", info != null && info.hasFailed());
            if (function.isRequired()) required++;
            if (info != null) {
                if (info.isDone()) completed++; if (info.hasFailed()) failed++;
                row.addProperty("runtime_ms", info.getRunTime()); row.addProperty("timeout_ticks", info.getTimeoutTicks());
                if (info.getError() != null) row.addProperty("error", stack(info.getError()));
            }
            cases.add(row);
        }
        result.addProperty("expected", current.functions.size()); result.addProperty("required", required);
        result.addProperty("completed", completed); result.addProperty("failed", failed); result.add("cases", cases);
        JsonArray events = new JsonArray(); current.events.forEach(events::add); result.add("events", events);
        if (current.fatal != null) result.addProperty("framework_error", stack(current.fatal));
        if (current.saveError != null) result.addProperty("save_error", stack(current.saveError));
        byte[] data = new GsonBuilder().setPrettyPrinting().create().toJson(result).getBytes(StandardCharsets.UTF_8);
        Path temporary = current.directory.resolve("results-" + UUID.randomUUID() + ".partial.json");
        try (FileChannel output = FileChannel.open(temporary, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            ByteBuffer buffer = ByteBuffer.wrap(data); while (buffer.hasRemaining()) output.write(buffer); output.force(true);
        }
        Files.move(temporary, current.directory.resolve("results.json"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        try (FileChannel directory = FileChannel.open(current.directory, StandardOpenOption.READ)) { directory.force(true); }
    }

    private static String stack(Throwable error) {
        StringWriter buffer = new StringWriter(); error.printStackTrace(new PrintWriter(buffer)); return buffer.toString();
    }
    private static void stopped(ServerStoppedEvent event) {
        if (event.getServer() != server) return;
        if (campaign != null) releasePreparation(campaign);
        if (campaign != null && !campaign.finished) {
            campaign.fatal = new IllegalStateException("Server stopped before all native fixtures completed");
            campaign.finished = true;
            try { write(campaign, "FAIL"); } catch (IOException error) { LOG.error("Cannot persist interrupted native fixture campaign", error); }
        }
        server = null; campaign = null; selectedClasses = List.of(); registrationError = null; registeredFunctions.clear();
    }
    private static final class Campaign {
        final MinecraftServer server; final UUID id; final Path directory; final String startedAt = Instant.now().toString();
        final List<String> classNames = new ArrayList<>(); final List<TestFunction> functions = new ArrayList<>();
        final List<GameTestInfo> infos = new ArrayList<>(); final List<JsonObject> events = new ArrayList<>();
        BlockPos origin; Throwable fatal, saveError; boolean finished, worldSaved;
        final List<ChunkPos> preparationChunks = new ArrayList<>(), preparationTickets = new ArrayList<>();
        final Map<ChunkPos, CompletableFuture<Either<ChunkAccess, ChunkHolder.ChunkLoadingFailure>>> preparationFutures = new LinkedHashMap<>();
        final Map<ChunkPos, String> preparationStatus = new LinkedHashMap<>();
        boolean preparing, frameworkOwnsPreparation; int preparationTicks, preparationCompleted;
        long preparationStarted, preparationFinished; Thread preparationThread; ThreadPoolExecutor requestExecutor;
        Campaign(MinecraftServer server, UUID id, Path base) { this.server = server; this.id = id; this.directory = base.resolve(id.toString()); }
    }
}
