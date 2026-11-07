package com.bettercontent.betterworldmanagement;

import com.mojang.serialization.Lifecycle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.server.level.progress.StoringChunkProgressListener;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.NbtIo;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.server.RegistryLayer;
import net.minecraft.world.level.WorldDataConfiguration;
import com.bettercontent.betterworldmanagement.mixin.MinecraftTickInvoker;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** Client-only fresh-world transaction. Candidate saves never receive a connection. */
@OnlyIn(Dist.CLIENT)
public final class SingleplayerInitialSpawn {
    private record Creation(CreateWorldScreen screen, PrimaryLevelData.SpecialWorldProperty property,
                            LayeredRegistryAccess<RegistryLayer> registries, Lifecycle lifecycle) {}
    private static final SecureRandom RANDOM = new SecureRandom();
    private static Session session;
    private static Runnable nextAction;
    private static final class Session {
        final Minecraft client;
        final Creation creation;
        final String target;
        final String transaction = UUID.randomUUID().toString();
        final boolean random;
        long started;
        final long loadingStarted = System.nanoTime();
        long lastLoadingFrame;
        long lastFailureCheck;
        boolean observedFailure;
        LevelLoadingScreen earlyScreen;
        final Path saves;
        final Path archived;
        final LevelStorageSource stageSource;
        final Set<Long> usedSeeds = new HashSet<>();
        String stage;
        String outcome;
        String phaseReason = "Preparing the first closed candidate";
        int number;
        boolean waiting;
        boolean retrying;
        WorldCreationContext awaitingContext;
        Session(Minecraft client, Creation creation, String target, String rawSeedInput) {
            this.client = client;
            this.creation = creation;
            this.target = target;
            this.random = rawSeedInput.isBlank();
            this.saves = client.getLevelSource().getBaseDir();
            this.archived = client.gameDirectory.toPath().resolve(".better_world_management/initial-spawn/").resolve(transaction);
            try {
                Path root = archived.resolve("staging");
                Files.createDirectories(root, PosixFilePermissions.asFileAttribute(
                        PosixFilePermissions.fromString("rwx------")));
                if (!Files.getFileStore(root).equals(Files.getFileStore(saves)))
                    throw new IOException("Single-player stage and saves must be on one filesystem for atomic publication");
                this.stageSource = LevelStorageSource.createDefault(root);
                Path intent = archived.resolve("creation-intent-v2.tsv");
                Files.createFile(intent, PosixFilePermissions.asFileAttribute(
                        PosixFilePermissions.fromString("rw-------")));
                Files.writeString(intent, "BC_INITIAL_SPAWN_CREATION_INTENT_V2\n"
                        + "target\t" + target + "\n"
                        + "provenance\t" + (this.random ? "random" : "explicit") + "\n"
                        + "seed_input_base64\t" + Base64.getEncoder().encodeToString(
                            rawSeedInput.getBytes(StandardCharsets.UTF_8)) + "\n");
            } catch (IOException error) {
                throw new IllegalStateException("Cannot create a protected, unpublished world staging area", error);
            }
            next();
        }
        void next() {
            number++;
            stage = "bc-initial-" + UUID.randomUUID();
            waiting = true;
            retrying = false;
        }
        long remainingMillis() {
            return started == 0 ? TimeUnit.NANOSECONDS.toMillis(InitialSpawnSeedSearch.TOTAL_NANOS)
                    : Math.max(0, TimeUnit.NANOSECONDS.toMillis(
                    InitialSpawnSeedSearch.TOTAL_NANOS - (System.nanoTime() - started)));
        }
    }
    private SingleplayerInitialSpawn() {}

    public static void installBridge() {
        InitialSpawnClientBridge.install(new InitialSpawnClientBridge.Provider() {
            @Override public InitialSpawnClientBridge.Intent intent(net.minecraft.server.MinecraftServer server) {
                return SingleplayerInitialSpawn.intent(server);
            }
            @Override public boolean isRandom(net.minecraft.server.MinecraftServer server) {
                return SingleplayerInitialSpawn.isRandom(server);
            }
            @Override public int attemptNumber(net.minecraft.server.MinecraftServer server) {
                return SingleplayerInitialSpawn.attemptNumber(server);
            }
            @Override public void startSearch(net.minecraft.server.MinecraftServer server) {
                SingleplayerInitialSpawn.startSearch(server);
            }
            @Override public long remainingMillis(net.minecraft.server.MinecraftServer server) {
                return SingleplayerInitialSpawn.remainingMillis(server);
            }
            @Override public void recordSeed(net.minecraft.server.MinecraftServer server, long seed) {
                SingleplayerInitialSpawn.recordSeed(server, seed);
            }
        });
    }

    public static synchronized void begin(CreateWorldScreen screen, PrimaryLevelData.SpecialWorldProperty property,
                                          LayeredRegistryAccess<RegistryLayer> registries, Lifecycle lifecycle) {
        if (session != null && session.retrying) {
            if (screen != session.creation.screen()) throw new IllegalStateException("Unexpected world creation during initial spawn retry");
            session.waiting = true;
            session.retrying = false;
            return;
        }
        if (session != null) throw new IllegalStateException("A single-player initial world is already being prepared");
        Minecraft client = Minecraft.getInstance();
        String target = screen.getUiState().getTargetFolder();
        if (!InitialSpawnTargetPolicy.singleplayerFolder(target))
            throw new IllegalStateException("Invalid new world directory");
        if (Files.exists(client.getLevelSource().getBaseDir().resolve(target))
                || Files.isSymbolicLink(client.getLevelSource().getBaseDir().resolve(target)))
            throw new IllegalStateException("Refusing to replace an existing single-player world");
        session = new Session(client, new Creation(screen, property, registries, lifecycle), target,
                screen.getUiState().getSeed());
    }

    public static synchronized String stageFolder(String original) {
        return session == null ? original : session.stage;
    }

    public static synchronized LevelStorageSource stageSource(LevelStorageSource original) {
        return session == null ? original : session.stageSource;
    }

    public static synchronized void failedToStartServer() {
        if (session != null && session.waiting && (session.client.getSingleplayerServer() == null
                || session.client.getSingleplayerServer().isShutdown()))
            failedBeforeLaunch("Could not start the closed candidate server. Stage retained; no world was entered.");
    }

    public static synchronized void failedBeforeLaunch(String message) {
        if (session == null) return;
        Minecraft client = session.client;
        session = null;
        nextAction = null;
        client.setScreen(new AlertScreen(() -> client.setScreen(new TitleScreen()),
                Component.literal("World preparation stopped"), Component.literal(message)));
    }

    public static synchronized void copiedDataPacks(LevelStorageSource.LevelStorageAccess access) throws IOException {
        if (session == null || session.number <= 1) return;
        Path source = session.archived.resolve("first-datapacks");
        if (!Files.isDirectory(source)) return;
        Path destination = access.getLevelPath(LevelResource.DATAPACK_DIR);
        try (var paths = Files.walk(source)) {
            for (Path from : paths.toList()) {
                Path to = destination.resolve(source.relativize(from));
                if (Files.isSymbolicLink(from)) throw new IOException("Selected datapack contains a symlink");
                if (Files.isDirectory(from)) Files.createDirectories(to);
                else Files.copy(from, to);
            }
        }
    }

    public static synchronized InitialSpawnClientBridge.Intent intent(net.minecraft.server.MinecraftServer server) {
        if (session == null || !server.isSingleplayer()) return null;
        // Forge can expose the level root as stage/.; getFileName() on that raw
        // path returns "." instead of the actual staged folder name.
        String name = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize()
                .getFileName().toString();
        return name.equals(session.stage) ? new InitialSpawnClientBridge.Intent(
                session.target, session.transaction + "-" + session.number) : null;
    }
    public static synchronized void recordSeed(net.minecraft.server.MinecraftServer server, long seed) {
        if (intent(server) != null) session.usedSeeds.add(seed);
    }
    public static synchronized boolean isRandom(net.minecraft.server.MinecraftServer server) {
        return intent(server) != null && session.random;
    }
    public static synchronized int attemptNumber(net.minecraft.server.MinecraftServer server) {
        return intent(server) == null ? 1 : session.number;
    }
    public static synchronized void startSearch(net.minecraft.server.MinecraftServer server) {
        if (intent(server) != null && session.started == 0) session.started = System.nanoTime();
    }
    public static synchronized long remainingMillis(net.minecraft.server.MinecraftServer server) {
        return intent(server) == null ? 0 : session.remainingMillis();
    }

    /** Render the native loading screen while vanilla spins before its progress listener exists. */
    public static synchronized boolean pollBeforeProgressListener(Minecraft client,
            LevelStorageSource.LevelStorageAccess access) {
        Session current = session;
        if (current == null || current.client != client || !access.getLevelId().equals(current.stage)) return false;
        if (current.earlyScreen == null)
            current.earlyScreen = new LevelLoadingScreen(new StoringChunkProgressListener(0));
        long now = System.nanoTime();
        if (now - current.lastLoadingFrame >= TimeUnit.MILLISECONDS.toNanos(16)) {
            current.lastLoadingFrame = now;
            if (client.screen != current.earlyScreen) client.setScreen(current.earlyScreen);
            ((MinecraftTickInvoker) client).betterWorldManagement$runTick(false);
        }
        return startupDeadline(client, access, current);
    }

    /** Vanilla renders its real LevelLoadingScreen here; forward failed stages to the retry handler. */
    public static synchronized boolean pollBeforeServerReady(Minecraft client,
            LevelStorageSource.LevelStorageAccess access) {
        Session current = session;
        return current != null && current.client == client && access.getLevelId().equals(current.stage)
                && startupDeadline(client, access, current);
    }

    private static boolean startupDeadline(Minecraft client, LevelStorageSource.LevelStorageAccess access,
                                           Session current) {
        boolean searchExpired = current.started != 0 && current.remainingMillis() <= 0;
        boolean loadingStalled = current.started == 0
                && System.nanoTime() - current.loadingStarted > TimeUnit.MINUTES.toNanos(15);
        boolean serverStopped = client.getSingleplayerServer() != null
                && client.getSingleplayerServer().isShutdown();
        if (System.nanoTime() - current.lastFailureCheck >= TimeUnit.MILLISECONDS.toNanos(100)) {
            current.lastFailureCheck = System.nanoTime();
            Path failure = current.stageSource.getBaseDir().resolve(current.stage)
                    .resolve("data/better_world_management/initial-spawn-v1.tsv");
            try { current.observedFailure = Files.isRegularFile(failure)
                    && Files.readString(failure).strip().equals("failed"); }
            catch (IOException error) { current.observedFailure = true; }
        }
        if (!searchExpired && !loadingStalled && (serverStopped || current.observedFailure)) {
            // A failed native site has a protected retry outcome. Do not abort the
            // loading screen before beforeConnect can archive and rotate its seed.
            return beforeConnect(client, access);
        }
        if (!searchExpired && !loadingStalled) return false;
        current.outcome = searchExpired ? "Ten-minute spawn search expired before the server was ready"
                : "World loading did not reach its first seed probe in 15 minutes";
        if (client.getSingleplayerServer() != null) client.getSingleplayerServer().halt(false);
        current.waiting = false;
        nextAction = () -> failure(client, current.outcome);
        return true;
    }

    /** Called immediately before Minecraft.doWorldLoad would establish its local client connection. */
    private static double receiptTemperature(String line, String key) {
        if (!line.startsWith(key + "\t")) throw new IllegalArgumentException("Missing receipt temperature: " + key);
        double value = Double.parseDouble(line.substring(key.length() + 1));
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite receipt temperature: " + key);
        return value;
    }

    private static boolean validParametricReceipt(List<String> lines) {
        if (lines.size() != 12 || !lines.get(0).equals("BC_INITIAL_SPAWN_ATTEMPT_V3")) return false;
        try {
            double base = receiptTemperature(lines.get(9), "base_temp_mc");
            return new InitialSpawnClimate.Bounds(receiptTemperature(lines.get(10), "min_temp_mc"),
                    receiptTemperature(lines.get(11), "max_temp_mc")).contains(base);
        } catch (IllegalArgumentException | IllegalStateException invalid) { return false; }
    }

    public static boolean beforeConnect(Minecraft client, LevelStorageSource.LevelStorageAccess access) {
        Session current;
        synchronized (SingleplayerInitialSpawn.class) { current = session; }
        if (current == null || current.client != client || !access.getLevelId().equals(current.stage)) return false;
        // The raw LevelResource.ROOT path can end in "/.". Moving that path
        // yields EBUSY on Linux even after the integrated server has stopped.
        Path root = access.getLevelPath(LevelResource.ROOT).toAbsolutePath().normalize();
        long timeout = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(current.remainingMillis());
        Path receipt = root.resolve("data/better_world_management/initial-spawn-attempt-v2.tsv");
        Path state = root.resolve("data/better_world_management/initial-spawn-v1.tsv");
        Path outcome = root.resolve("data/better_world_management/initial-spawn-outcome-v2.tsv");
        if (Files.isSymbolicLink(root) || Files.isSymbolicLink(root.resolve("data"))
                || Files.isSymbolicLink(root.resolve("data/better_world_management"))
                || Files.isSymbolicLink(receipt) || Files.isSymbolicLink(state)
                || Files.isSymbolicLink(outcome) || Files.isSymbolicLink(root.resolve("level.dat"))) {
            current.outcome = "Unsafe staged-world path; publication refused";
            if (client.getSingleplayerServer() != null) client.getSingleplayerServer().halt(false);
            current.waiting = false;
            nextAction = () -> failure(client, current.outcome);
            return true; // The stage remains closed for manual recovery.
        }
        long failedSince = 0;
        while (!Files.isRegularFile(receipt) && System.nanoTime() - timeout < 0) {
            try {
                if (Files.isRegularFile(state) && Files.readString(state).strip().equals("failed")) {
                    if (Files.isRegularFile(outcome)) break;
                    if (failedSince == 0) failedSince = System.nanoTime();
                    if (System.nanoTime() - failedSince >= TimeUnit.SECONDS.toNanos(2)) break;
                }
                if (client.getSingleplayerServer() == null || client.getSingleplayerServer().isShutdown()) break;
                ((MinecraftTickInvoker) client).betterWorldManagement$runTick(false);
                Thread.sleep(16);
            } catch (Exception error) {
                PrestigeMod.LOGGER.error("Could not verify staged world progress", error);
                current.outcome = "Could not verify staged world progress; see the client log";
                break;
            }
        }
        boolean success = false;
        if (Files.isRegularFile(receipt)) {
            try {
                List<String> lines = Files.readAllLines(receipt);
                success = validParametricReceipt(lines)
                        && lines.get(1).equals("attempt\t" + current.transaction + "-" + current.number)
                        && lines.get(2).equals("target\t" + current.target)
                        && lines.get(3).equals("number\t" + current.number)
                        && lines.get(8).startsWith("biome\t")
                        && lines.get(5).equals("status\tresolved")
                        && (lines.get(6).equals("provenance\trandom") && current.random
                            || lines.get(6).equals("provenance\texplicit") && !current.random)
                        && Files.isRegularFile(state) && Files.readString(state).strip().equals("pending")
                        && !Files.exists(outcome);
                if (success) current.outcome = lines.get(5).substring("status\t".length());
            } catch (IOException error) {
                PrestigeMod.LOGGER.error("Could not read protected candidate receipt", error);
                current.outcome = "Could not read the candidate receipt; see the client log";
            }
        }
        var server = client.getSingleplayerServer();
        PrestigeMod.LOGGER.info("Protected candidate handoff: attempt={} server={} threadAlive={} success={}",
                current.number, server == null ? "null" : System.identityHashCode(server),
                server != null && server.getRunningThread().isAlive(), success);
        if (server != null) {
            server.halt(false);
            long stopDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            while (!server.isShutdown() && System.nanoTime() - stopDeadline < 0)
                ((MinecraftTickInvoker) client).betterWorldManagement$runTick(false);
            if (!server.isShutdown()) {
                current.outcome = "The staged integrated server did not stop; world was not published";
                current.waiting = false;
                nextAction = () -> failure(client, current.outcome);
                return true;
            }
        }
        PrestigeMod.LOGGER.info("Protected candidate server stopped: attempt={} server={} threadAlive={}",
                current.number, server == null ? "null" : System.identityHashCode(server),
                server != null && server.getRunningThread().isAlive());
        client.clearLevel();
        boolean retryable = false;
        if (Files.isRegularFile(outcome)) {
            try {
                List<String> result = Files.readAllLines(outcome);
                retryable = result.size() == 3 && result.get(0).equals("BC_INITIAL_SPAWN_OUTCOME_V2")
                        && result.get(1).equals("kind\tretry") && result.get(2).startsWith("reason\t");
            }
            catch (IOException error) { current.outcome = "Candidate outcome could not be read"; }
            success = false; // Conflicting receipt/outcome cannot be promoted.
        }
        try { access.close(); }
        catch (IOException error) {
            PrestigeMod.LOGGER.error("Could not release protected candidate save", error);
            success = false;
            current.outcome = "Could not release the staged save; publication was refused";
        }
        try {
            if (!Files.isDirectory(root) || Files.isSymbolicLink(root)) throw new IOException("Unsafe staged world directory");
            for (String name : List.of("playerdata", "advancements", "stats")) {
                Path data = root.resolve(name);
                if (Files.isSymbolicLink(data)) throw new IOException("Candidate has an unsafe player directory");
                if (Files.isDirectory(data)) {
                    try (var files = Files.list(data)) {
                        if (files.findAny().isPresent())
                            throw new IOException("Candidate has player data; refusing promotion or retry");
                    }
                }
            }
            if (Files.isRegularFile(root.resolve("data/better_world_management/initial-spawn-login-v1.tsv")))
                throw new IOException("Candidate has a login marker; refusing publication or retry");
            if (success) {
                List<String> lines = Files.readAllLines(receipt);
                var saved = NbtIo.readCompressed(root.resolve("level.dat").toFile()).getCompound("Data");
                String[] spawn = lines.get(7).split("\\t");
                if (!validParametricReceipt(lines))
                    throw new IOException("Saved candidate has no valid parametric biome receipt");
                if (spawn.length != 4 || !spawn[0].equals("spawn")
                        || saved.getInt("SpawnX") != Integer.parseInt(spawn[1])
                        || saved.getInt("SpawnY") != Integer.parseInt(spawn[2])
                        || saved.getInt("SpawnZ") != Integer.parseInt(spawn[3])
                        || !lines.get(4).equals("seed\t" + saved.getCompound("WorldGenSettings").getLong("seed")))
                    throw new IOException("Saved level.dat spawn or seed differs from the candidate receipt");
                for (Path durable : List.of(receipt, root.resolve("level.dat"))) {
                    try (FileChannel channel = FileChannel.open(durable, StandardOpenOption.WRITE)) {
                        channel.force(true);
                    }
                }
                if (current.remainingMillis() <= 0)
                    throw new IOException("Ten-minute candidate verification budget expired before publication");
                Path target = current.saves.resolve(current.target);
                if (Files.exists(target)) throw new IOException("World name already exists; refusing to publish candidate");
                Files.writeString(state, current.outcome + "\n");
                try (FileChannel channel = FileChannel.open(state, StandardOpenOption.WRITE)) {
                    channel.force(true);
                }
                // Do not request ATOMIC_MOVE here: its replace-existing semantics are provider-specific.
                // Same-filesystem Files.move without REPLACE_EXISTING refuses an occupied save name.
                Files.move(root, target);
                try (FileChannel directory = FileChannel.open(current.saves, StandardOpenOption.READ)) {
                    directory.force(true);
                }
                current.waiting = false;
                nextAction = () -> {
                    synchronized (SingleplayerInitialSpawn.class) { session = null; }
                    client.createWorldOpenFlows().loadLevel(new TitleScreen(), current.target);
                };
            } else {
                Files.createDirectories(current.archived, PosixFilePermissions.asFileAttribute(
                        PosixFilePermissions.fromString("rwx------")));
                if (current.number == 1) {
                    Path datapacks = root.resolve("datapacks");
                    if (Files.isDirectory(datapacks)) {
                        Path copy = current.archived.resolve("first-datapacks");
                        try (var paths = Files.walk(datapacks)) {
                            for (Path from : paths.toList()) {
                                Path to = copy.resolve(datapacks.relativize(from));
                                if (Files.isDirectory(from)) Files.createDirectories(to);
                                else Files.copy(from, to);
                            }
                        }
                    }
                }
                Files.move(root, current.archived.resolve("attempt-" + current.number), StandardCopyOption.ATOMIC_MOVE);
                if (!current.random || !retryable || current.remainingMillis() <= 0) {
                    current.outcome = current.outcome == null
                            ? "No safe spawn was published: " + InitialSpawnCreation.progress().reason()
                                + ". See the client log for recovery details."
                            : current.outcome;
                    current.waiting = false;
                    nextAction = () -> failure(client, current.outcome);
                } else {
                    current.next();
                    current.phaseReason = "No temperate biome; retrying with a different random seed";
                    PrestigeMod.LOGGER.info("Initial spawn preparation: phase=retry attempt={} reason=no temperate biome", current.number);
                    current.retrying = true;
                    current.waiting = false;
                    nextAction = () -> retryWithFreshRegistries(current);
                }
            }
        } catch (Exception error) {
            PrestigeMod.LOGGER.error("Initial single-player world publication failed; keeping the staged save", error);
            current.outcome = "Initial spawn publication failed; staged save was retained. See the client log.";
            current.waiting = false;
            nextAction = () -> failure(client, current.outcome);
        }
        return true;
    }

    /** Forge modifies biome holders on server start; a second server cannot reuse those holders. */
    private static void retryWithFreshRegistries(Session current) {
        PrestigeMod.LOGGER.info("Protected candidate registry reload: attempt={} activeServer={}",
                current.number, current.client.getSingleplayerServer() == null ? "null"
                        : System.identityHashCode(current.client.getSingleplayerServer()));
        var screen = current.creation.screen();
        var invoker = (com.bettercontent.betterworldmanagement.mixin.CreateWorldScreenInvoker) screen;
        long seed;
        do { seed = RANDOM.nextLong(); } while (!current.usedSeeds.add(seed));
        screen.getUiState().setSeed(Long.toString(seed));
        WorldDataConfiguration config = screen.getUiState().getSettings().dataConfiguration();
        // createNewWorld removed the temporary pack directory but retained its repository.
        // Rebuild the repository against the same selected packs before loading fresh holders.
        invoker.betterWorldManagement$setPackRepository(null);
        var selection = invoker.betterWorldManagement$selectionSettings(config);
        if (selection == null) {
            failure(current.client, "Could not reload world datapacks for a safe seed retry");
            return;
        }
        Path archivedPacks = current.archived.resolve("first-datapacks");
        try {
            if (Files.isDirectory(archivedPacks)) {
                try (var paths = Files.walk(archivedPacks)) {
                    for (Path from : paths.toList()) {
                        if (Files.isSymbolicLink(from)) throw new IOException("Selected datapack contains a symlink");
                        Path to = selection.getFirst().resolve(archivedPacks.relativize(from));
                        if (Files.isDirectory(from)) Files.createDirectories(to);
                        else Files.copy(from, to);
                    }
                }
                selection.getSecond().reload();
                selection.getSecond().setSelected(config.dataPacks().getEnabled());
            }
        } catch (IOException error) {
            PrestigeMod.LOGGER.error("Could not restore selected datapacks for retry", error);
            failure(current.client, "Could not reload selected datapacks for a safe seed retry");
            return;
        }
        // Vanilla's callback is invoked ONLY on reload failure/reset; a successful reload
        // changes uiState.settings and then restores the creation screen. Observe that
        // transition on the next client tick, after its handler has finished.
        current.awaitingContext = screen.getUiState().getSettings();
        invoker.betterWorldManagement$reloadPacks(selection.getSecond(), config, ignored -> {
            if (session == current) failure(current.client, "Could not reload world registries for a safe seed retry");
        });
    }

    private static void launchRefreshedCandidate(Session current) {
        if (session != current || current.remainingMillis() <= 0) {
            failure(current.client, "Ten-minute spawn search expired before a safe retry");
            return;
        }
        var screen = current.creation.screen();
        var settings = screen.getUiState().getSettings();
        var dimensions = settings.selectedDimensions().bake(settings.datapackDimensions());
        var registries = settings.worldgenRegistries().replaceFrom(RegistryLayer.DIMENSIONS,
                dimensions.dimensionsRegistryAccess());
        PrestigeMod.LOGGER.info("Protected candidate registry reload complete: attempt={}", current.number);
        ((com.bettercontent.betterworldmanagement.mixin.CreateWorldScreenInvoker) screen)
                .betterWorldManagement$retryWorld(dimensions.specialWorldProperty(), registries,
                        current.creation.lifecycle());
    }

    private static void failure(Minecraft client, String message) {
        synchronized (SingleplayerInitialSpawn.class) { session = null; }
        client.setScreen(new AlertScreen(() -> client.setScreen(new TitleScreen()),
                Component.literal("World preparation stopped"), Component.literal(message)));
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Session current = session;
        if (current != null && current.awaitingContext != null) {
            if (current.remainingMillis() <= 0) {
                current.awaitingContext = null;
                failure(current.client, "Ten-minute spawn search expired while reloading world registries");
                return;
            }
            if (current.creation.screen().getUiState().getSettings() != current.awaitingContext) {
                current.awaitingContext = null;
                nextAction = () -> launchRefreshedCandidate(current);
                return;
            }
        }
        Runnable action = nextAction;
        if (action == null || session != null && session.waiting) return;
        nextAction = null;
        action.run();
    }

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        Session current;
        synchronized (SingleplayerInitialSpawn.class) { current = session; }
        if (current == null || !(event.getScreen() instanceof net.minecraft.client.gui.screens.LevelLoadingScreen)) return;
        InitialSpawnCreation.Progress status = InitialSpawnCreation.progress();
        String line = "Temperate spawn: " + (status.attempt() == current.number ? status.phase() : "loading candidate")
                + " (attempt " + current.number + ", "
                + (current.started == 0 ? 0 : TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - current.started))
                + "s / 600s)";
        event.getGuiGraphics().drawCenteredString(Minecraft.getInstance().font, line,
                event.getScreen().width / 2, event.getScreen().height / 2 + 86, 0xffffff);
        String reason = status.attempt() == current.number ? status.reason() : current.phaseReason;
        if (!reason.isBlank()) event.getGuiGraphics().drawCenteredString(Minecraft.getInstance().font,
                reason, event.getScreen().width / 2, event.getScreen().height / 2 + 100, 0xffddaa);
    }
}
