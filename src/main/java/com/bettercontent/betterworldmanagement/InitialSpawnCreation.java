package com.bettercontent.betterworldmanagement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;

/** Only generation-zero, unpublished initial worlds. Never use successor landing here. */
public final class InitialSpawnCreation {
    private static final int GRID_STEP = 32;
    private static final int WORLD_LIMIT = 29_999_984;
    private static final int PROBES_PER_TICK = 96;
    private static final String RECEIPT = "data/better_world_management/initial-spawn-attempt-v2.tsv";
    private static final String OUTCOME = "data/better_world_management/initial-spawn-outcome-v2.tsv";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Map<MinecraftServer, Attempt> ACTIVE = new WeakHashMap<>();
    private static volatile Progress progress = new Progress("idle", 0, 0, "");

    public record Progress(String phase, int attempt, long elapsedSeconds, String reason) {}
    private record Site(BlockPos feet, String biome, double base) {}
    private static final class EarlyLookupUnavailable extends RuntimeException {}
    private static final class Attempt {
        final MinecraftServer server;
        final InitialSpawnClimate.Bounds bounds;
        final List<Site> sites;
        final String id;
        final String target;
        final long startNanos;
        final long budgetNanos;
        final int number;
        final boolean random;
        final String provenance;
        final boolean deferred;
        final Set<Long> rejectedSeeds;
        long screeningNanos;
        long nativeStartedNanos;
        ServerLevel level;
        int siteIndex;
        int ring;
        int ringOffset;
        long lastLog;

        Attempt(MinecraftServer server, InitialSpawnClimate.Bounds bounds, List<Site> sites, String id, String target,
                long startNanos, long budgetNanos, int number, boolean random, String provenance,
                boolean deferred, Set<Long> rejectedSeeds) {
            this.server = server;
            this.bounds = bounds;
            this.sites = sites;
            this.id = id;
            this.target = target;
            this.startNanos = startNanos;
            this.budgetNanos = budgetNanos;
            this.number = number;
            this.random = random;
            this.provenance = provenance;
            this.deferred = deferred;
            this.rejectedSeeds = rejectedSeeds;
        }
        boolean totalExpired() { return System.nanoTime() - startNanos >= budgetNanos; }
        boolean timedOut() {
            return totalExpired() || nativeStartedNanos != 0 &&
                    screeningNanos + (System.nanoTime() - nativeStartedNanos) >= InitialSpawnSeedSearch.PER_SEED_NANOS;
        }
        long elapsedSeconds() { return TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startNanos); }
    }

    private InitialSpawnCreation() {}

    public static Progress progress() { return progress; }

    private static void report(Attempt attempt, String phase, String reason) {
        progress = new Progress(phase, attempt.number, attempt.elapsedSeconds(), reason);
        PrestigeMod.LOGGER.info("Initial spawn preparation: phase={} attempt={} elapsed={}s reason={}",
                phase, attempt.number, attempt.elapsedSeconds(), reason);
    }

    @SubscribeEvent
    public static void onAboutToStart(ServerAboutToStartEvent event) {
        MinecraftServer server = event.getServer();
        if (!(server.getWorldData() instanceof PrimaryLevelData data)) {
            if (System.getenv("BC_INITIAL_SPAWN_TARGET") != null
                    || server.isSingleplayer() && InitialSpawnClientBridge.intent(server) != null) {
                if (!server.isSingleplayer())
                    throw new IllegalStateException("Staged world data is unavailable; refusing player entry");
                try { PrestigeCoordinator.writeInitialSpawnStatus(server, "failed"); }
                catch (IOException error) { PrestigeMod.LOGGER.error("Could not persist missing-world-data failure", error); }
            }
            return;
        }
        if (data.overworldData().isInitialized()) {
            if (System.getenv("BC_INITIAL_SPAWN_TARGET") != null
                    || server.isSingleplayer() && InitialSpawnClientBridge.intent(server) != null) {
                if (server.isSingleplayer()) {
                    try { PrestigeCoordinator.writeInitialSpawnStatus(server, "failed"); }
                    catch (IOException error) { PrestigeMod.LOGGER.error("Could not persist already-initialized stage failure", error); }
                    return;
                }
                throw new IllegalStateException("Staged world is already initialized; refusing to reselect it");
            }
            return;
        }
        try {
            if (Files.isRegularFile(PrestigeService.control(server).resolve("successor-request-v5.tsv"))) {
                if (System.getenv("BC_INITIAL_SPAWN_TARGET") != null
                        || server.isSingleplayer() && InitialSpawnClientBridge.intent(server) != null)
                    throw new IllegalStateException("Conflicting successor request during initial spawn staging");
                return;
            }
            if (data.isDebugWorld()) throw new IllegalStateException("Debug worlds cannot satisfy initial-spawn selection");
            String target = System.getenv("BC_INITIAL_SPAWN_TARGET");
            String id = System.getenv("BC_INITIAL_SPAWN_ATTEMPT");
            boolean fixture = Boolean.getBoolean("forge.enableGameTest");
            if (!server.isSingleplayer() && (target == null || id == null) && !fixture)
                throw new IllegalStateException("Fresh dedicated worlds require supervised initial spawn staging via ./run.sh");
            if (server.isSingleplayer()) {
                InitialSpawnClientBridge.Intent intent = InitialSpawnClientBridge.intent(server);
                if (intent != null) { target = intent.target(); id = intent.id(); }
            }
            if (target == null || id == null) {
                if (!fixture) throw new IllegalStateException("Fresh single-player worlds require the closed creation screen");
                // Repository GameTest creates its own disposable native world, not a publishable pack save.
                target = server.getWorldPath(LevelResource.ROOT).getFileName().toString();
                id = "fixture";
            }
            if (!id.matches("[a-zA-Z0-9_-]{1,100}")
                    || (server.isSingleplayer() ? !InitialSpawnTargetPolicy.singleplayerFolder(target)
                    : !target.matches("[a-zA-Z0-9._-]{1,128}") || target.equals(".") || target.equals("..")))
                throw new IllegalStateException("Invalid initial spawn attempt identity");
            if (Files.exists(server.getWorldPath(LevelResource.ROOT)
                    .resolve("data/better_world_management/initial-spawn-v1.tsv")))
                throw new IllegalStateException("Existing initial-spawn state cannot be reselected or resumed");
            boolean random = server.isSingleplayer()
                    ? InitialSpawnClientBridge.isRandom(server)
                    : "random".equals(System.getenv("BC_INITIAL_SPAWN_PROVENANCE"));
            String provenance = server.isSingleplayer() ? (random ? "random" : "explicit")
                    : System.getenv().getOrDefault("BC_INITIAL_SPAWN_PROVENANCE", "unknown");
            if (!Set.of("random", "explicit", "unknown").contains(provenance))
                throw new IllegalStateException("Invalid seed provenance");
            int number = server.isSingleplayer() ? InitialSpawnClientBridge.attemptNumber(server)
                    : Integer.parseInt(System.getenv().getOrDefault("BC_INITIAL_SPAWN_NUMBER", "1"));
            if (server.isSingleplayer()) InitialSpawnClientBridge.startSearch(server);
            long budgetMillis = server.isSingleplayer() ? InitialSpawnClientBridge.remainingMillis(server)
                    : Long.parseLong(System.getenv().getOrDefault("BC_INITIAL_SPAWN_REMAINING_MS", "600000"));
            long start = System.nanoTime();
            Path begin = server.getWorldPath(LevelResource.ROOT)
                    .resolve("data/better_world_management/initial-spawn-start-v2.tsv");
            if (Files.exists(begin) || Files.exists(server.getWorldPath(LevelResource.ROOT).resolve(RECEIPT))
                    || Files.exists(server.getWorldPath(LevelResource.ROOT).resolve(OUTCOME)))
                throw new IllegalStateException("Existing initial-spawn attempt evidence cannot be reselected");
            Files.createDirectories(begin.getParent());
            Files.createFile(begin, java.nio.file.attribute.PosixFilePermissions.asFileAttribute(
                    java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")));
            Files.writeString(begin, "BC_INITIAL_SPAWN_START_V2\n");
            long budget = Math.min(InitialSpawnSeedSearch.TOTAL_NANOS,
                    TimeUnit.MILLISECONDS.toNanos(Math.max(0L, budgetMillis)));
            if (budget <= 0) throw new IllegalStateException("Initial spawn preparation exceeded 10 minutes");
            InitialSpawnClimate.Bounds bounds = InitialSpawnClimate.loadedBounds(server);
            var stems = server.registryAccess().registryOrThrow(Registries.LEVEL_STEM);
            var overworld = stems.get(net.minecraft.world.level.dimension.LevelStem.OVERWORLD);
            if (overworld == null) throw new IllegalStateException("Overworld generator is unavailable");
            ChunkGenerator generator = overworld.generator();
            boolean deferred = !(generator instanceof NoiseBasedChunkGenerator);
            List<Site> sites = List.of();
            Set<Long> rejected = new HashSet<>();
            long screeningNanos = 0;
            long seed = data.worldGenOptions().seed();
            if (server.isSingleplayer() && random) InitialSpawnClientBridge.recordSeed(server, seed);
            if (!deferred) {
                NoiseBasedChunkGenerator noise = (NoiseBasedChunkGenerator) generator;
                NoiseGeneratorSettings settings = noise.generatorSettings().value();
                LevelHeightAccessor height = LevelHeightAccessor.create(generator.getMinY(), generator.getGenDepth());
                long screeningStarted = System.nanoTime();
                try {
                    report(new Attempt(server, bounds, List.of(), id, target, start, budget, number,
                            random, provenance, false, rejected), "screening", "outward parametric biome probes");
                    // Rotate only between isolated staged worlds. Never rebind a running generator's seed.
                    // Reserve most of the seed budget for the loaded biome source;
                    // advisory pre-world screening must not consume all 30 seconds.
                    long preliminaryRemaining = Math.min(TimeUnit.SECONDS.toNanos(5),
                            budget - (System.nanoTime() - start));
                    var result = InitialSpawnSeedSearch.run(seed, false, preliminaryRemaining,
                            System::nanoTime, RANDOM::nextLong, (candidate, limit) -> {
                                RandomState state = RandomState.create(settings,
                                        server.registryAccess().lookupOrThrow(Registries.NOISE), candidate);
                                List<Site> found = screen(generator, state, height, bounds, limit, number, start);
                                if (found.isEmpty()) { rejected.add(candidate); return null; }
                                return found;
                            });
                    sites = result.value() == null ? List.of() : result.value();
                } catch (RuntimeException unavailable) {
                    // Screening is advisory; the loaded world can sample its biome source
                    // without requiring a candidate FULL chunk or terrain validation.
                    PrestigeMod.LOGGER.warn("Initial spawn pre-world biome lookup unavailable; deferring to loaded biome source", unavailable);
                    deferred = true;
                    sites = List.of();
                    report(new Attempt(server, bounds, sites, id, target, start, budget, number, random,
                            provenance, true, rejected), "deferred", "biome lookup requires a loaded world");
                } finally {
                    screeningNanos = Math.min(InitialSpawnSeedSearch.PER_SEED_NANOS,
                            System.nanoTime() - screeningStarted);
                }
            }
            Attempt attempt = new Attempt(server, bounds, sites, id, target, start, budget,
                    number, random, provenance, deferred, rejected);
            attempt.screeningNanos = screeningNanos;
            synchronized (ACTIVE) { ACTIVE.put(server, attempt); }
            PrestigeCoordinator.writeInitialSpawnStatus(server, "pending");
            report(attempt, "generating", sites.isEmpty() ? "searching in a closed world" :
                    "screened " + sites.size() + " candidate sites");
        } catch (Exception error) {
            PrestigeMod.LOGGER.error("Initial spawn preparation failed before admission", error);
            progress = new Progress("failed", 1, 0, "Technical failure; staged world was not published");
            if (server.isSingleplayer()) {
                try { PrestigeCoordinator.writeInitialSpawnStatus(server, "failed"); }
                catch (IOException writeError) { PrestigeMod.LOGGER.error("Could not persist failure state", writeError); }
                return;
            }
            throw new IllegalStateException("Initial spawn preparation failed before any player entered", error);
        }
    }

    private static List<Site> screen(ChunkGenerator generator, RandomState state,
                                     LevelHeightAccessor height, InitialSpawnClimate.Bounds bounds, long limitNanos,
                                     int attempt, long totalStarted) {
        long started = System.nanoTime();
        long lastReport = started;
        var source = generator.getBiomeSource();
        int seaQuart = QuartPos.fromBlock(generator.getSeaLevel());
        for (int ring = 0; ring <= WORLD_LIMIT / GRID_STEP; ring++) {
            int count = ring == 0 ? 1 : ring * 8;
            for (int offset = 0; offset < count; offset++) {
                if ((offset & 63) == 0) {
                    long now = System.nanoTime();
                    if (now - started >= limitNanos || Thread.currentThread().isInterrupted()) return List.of();
                    if (now - lastReport >= TimeUnit.SECONDS.toNanos(10)) {
                        lastReport = now;
                        long seconds = TimeUnit.NANOSECONDS.toSeconds(now - totalStarted);
                        progress = new Progress("screening", attempt, seconds, "outward parametric biome probes");
                        PrestigeMod.LOGGER.info("Initial spawn preparation: phase=screening attempt={} elapsed={}s", attempt, seconds);
                    }
                }
                int x = gridX(ring, offset) * GRID_STEP + 8;
                int z = gridZ(ring, offset) * GRID_STEP + 8;
                if (Math.abs((long) x) > WORLD_LIMIT || Math.abs((long) z) > WORLD_LIMIT) continue;
                if (!bounds.accepts(source.getNoiseBiome(
                        QuartPos.fromBlock(x), seaQuart, QuartPos.fromBlock(z), state.sampler()))) continue;
                // One predicted height lookup, with no FULL chunk or terrain-safety gate.
                int y = generator.getBaseHeight(x, z, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, height, state);
                if (y < height.getMinBuildHeight() || y >= height.getMaxBuildHeight()) continue;
                Holder<Biome> feetBiome = source.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y),
                        QuartPos.fromBlock(z), state.sampler());
                if (!bounds.accepts(feetBiome)) continue;
                double base = InitialSpawnClimate.baseTemperature(feetBiome);
                return List.of(new Site(new BlockPos(x, y, z), biomeId(feetBiome), base));
            }
        }
        return List.of();
    }

    private static String biomeId(Holder<Biome> biome) {
        if (biome == null) throw new EarlyLookupUnavailable();
        return biome.unwrapKey().map(key -> key.location().toString()).orElse("");
    }
    private static int gridX(int ring, int offset) {
        if (ring == 0) return 0;
        int edge = ring * 2;
        if (offset < edge + 1) return -ring + offset;
        if ((offset -= edge + 1) < edge) return ring;
        if ((offset -= edge) < edge) return ring - 1 - offset;
        return -ring;
    }
    private static int gridZ(int ring, int offset) {
        if (ring == 0) return 0;
        int edge = ring * 2;
        if (offset < edge + 1) return -ring;
        if ((offset -= edge + 1) < edge) return -ring + 1 + offset;
        if ((offset -= edge) < edge) return ring;
        return ring - 1 - (offset - edge);
    }

    @SubscribeEvent
    public static void onCreateSpawn(LevelEvent.CreateSpawnPosition event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        Attempt attempt;
        synchronized (ACTIVE) { attempt = ACTIVE.get(level.getServer()); }
        if (attempt == null || !attempt.random) return; // Fixed-seed vanilla fallback retains vanilla's own spawn.
        BlockPos provisional = attempt.sites.isEmpty()
                ? new BlockPos(0, level.getSeaLevel() + 2, 0) : attempt.sites.get(0).feet();
        event.getSettings().setSpawn(provisional, 0.0F);
        level.getGameRules().getRule(GameRules.RULE_SPAWN_RADIUS).set(0, level.getServer());
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent event) {
        Attempt attempt;
        synchronized (ACTIVE) { attempt = ACTIVE.get(event.getServer()); }
        if (attempt == null) return;
        attempt.level = event.getServer().overworld();
        attempt.nativeStartedNanos = System.nanoTime();
        report(attempt, "selecting", "checking temperate biome at the final shared spawn");
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Attempt attempt;
        synchronized (ACTIVE) { attempt = ACTIVE.get(event.getServer()); }
        if (attempt == null || attempt.level == null) return;
        if (attempt.timedOut()) {
            if (attempt.totalExpired()) fail(attempt, "deadline", "10-minute spawn-preparation budget expired");
            else fail(attempt, attempt.random ? "retry" : "error",
                    "30-second seed search ended without a temperate biome");
            return;
        }
        if (System.nanoTime() - attempt.lastLog >= TimeUnit.SECONDS.toNanos(10)) {
            attempt.lastLog = System.nanoTime();
            report(attempt, "selecting", "waiting for a temperate shared spawn");
        }
        try {
            while (attempt.siteIndex < attempt.sites.size()) {
                Site preliminary = attempt.sites.get(attempt.siteIndex++);
                Site finalSite = siteAt(attempt.level, preliminary.feet(), attempt.bounds);
                if (finalSite != null) { finish(attempt, finalSite); return; }
            }
            // The pre-world estimate may differ from the real biome-manager zoom.
            // Survey the loaded biome source without a FULL chunk or terrain checks.
            int sea = QuartPos.fromBlock(attempt.level.getSeaLevel());
            ChunkGenerator generator = attempt.level.getChunkSource().getGenerator();
            for (int i = 0; i < PROBES_PER_TICK && !attempt.timedOut(); i++) {
                int x = gridX(attempt.ring, attempt.ringOffset) * GRID_STEP + 8;
                int z = gridZ(attempt.ring, attempt.ringOffset) * GRID_STEP + 8;
                if (++attempt.ringOffset >= (attempt.ring == 0 ? 1 : attempt.ring * 8)) {
                    attempt.ringOffset = 0;
                    attempt.ring++;
                }
                if (attempt.ring > WORLD_LIMIT / GRID_STEP) {
                    fail(attempt, attempt.random ? "retry" : "error", "no temperate biome within world border"); return;
                }
                if (!attempt.bounds.accepts(attempt.level.getUncachedNoiseBiome(
                        QuartPos.fromBlock(x), sea, QuartPos.fromBlock(z)))) continue;
                int y = generator.getBaseHeight(x, z, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        attempt.level, attempt.level.getChunkSource().randomState());
                Site site = siteAt(attempt.level, new BlockPos(x, y, z), attempt.bounds);
                if (site != null) { finish(attempt, site); return; }
            }
        } catch (Exception error) {
            PrestigeMod.LOGGER.error("Initial spawn biome selection failed", error);
            fail(attempt, "error", "biome selection failed; see server diagnostics");
        }
    }

    private static Site siteAt(ServerLevel level, BlockPos feet, InitialSpawnClimate.Bounds bounds) {
        if (feet.getY() < level.getMinBuildHeight() || feet.getY() >= level.getMaxBuildHeight()
                || !level.getWorldBorder().isWithinBounds(feet)) return null;
        // Qualify the actual saved block with vanilla fuzzy block-to-quart zoom.
        // First-join vanilla spawn placement can raise a spectator one or two blocks;
        // do not accept an ocean (or unsuitable climate) immediately above that block.
        // These are biome lookups only: no generated chunk or terrain-safety gate.
        var biomes = level.getBiomeManager().withDifferentSource(level::getUncachedNoiseBiome);
        Holder<Biome> biome = biomes.getBiome(feet);
        if (!bounds.accepts(biome)) return null;
        for (int above = 1; above <= 2; above++) {
            if (feet.getY() + above >= level.getMaxBuildHeight()
                    || !bounds.accepts(biomes.getBiome(feet.above(above)))) return null;
        }
        return new Site(feet, biomeId(biome), InitialSpawnClimate.baseTemperature(biome));
    }

    private static void finish(Attempt attempt, Site selected) {
        try {
            PrestigeCoordinator.configureSuccessorSpawn(attempt.server, attempt.level, selected.feet());
            String status = "resolved";
            BlockPos feet = attempt.level.getSharedSpawnPos();
            Site finalSite = siteAt(attempt.level, feet, attempt.bounds);
            if (!feet.equals(selected.feet()) || finalSite == null || !finalSite.biome().equals(selected.biome()))
                throw new IllegalStateException("final spawn biome does not satisfy Cold Sweat bounds");
            // Persist level.dat and pending chunk saves, but never block the server tick
            // flushing every C2ME chunk. Normal shutdown flushes the closed candidate;
            // the supervisor verifies the saved spawn before atomic promotion.
            if (!attempt.server.saveEverything(true, false, true))
                throw new IllegalStateException("candidate metadata could not be saved");
            var data = NbtIo.readCompressed(attempt.server.getWorldPath(LevelResource.LEVEL_DATA_FILE).toFile())
                    .getCompound("Data");
            if (data.getInt("SpawnX") != feet.getX() || data.getInt("SpawnY") != feet.getY()
                    || data.getInt("SpawnZ") != feet.getZ())
                throw new IllegalStateException("saved level.dat does not contain the selected spawn");
            Path root = attempt.server.getWorldPath(LevelResource.ROOT);
            Path receipt = root.resolve(RECEIPT);
            if (Files.exists(receipt) || Files.isSymbolicLink(receipt))
                throw new IllegalStateException("Candidate receipt already exists; refusing to overwrite recovery evidence");
            Files.createDirectories(receipt.getParent());
            Path partial = receipt.resolveSibling(receipt.getFileName() + ".partial");
            Files.createFile(partial, java.nio.file.attribute.PosixFilePermissions.asFileAttribute(
                    java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")));
            Files.writeString(partial, "BC_INITIAL_SPAWN_ATTEMPT_V3\n"
                    + "attempt\t" + attempt.id + "\n"
                    + "target\t" + attempt.target + "\n"
                    + "number\t" + attempt.number + "\n"
                    + "seed\t" + attempt.level.getSeed() + "\n"
                    + "status\t" + status + "\n"
                    + "provenance\t" + attempt.provenance + "\n"
                    + "spawn\t" + feet.getX() + "\t" + feet.getY() + "\t" + feet.getZ() + "\n"
                    + "biome\t" + finalSite.biome() + "\n"
                    + "base_temp_mc\t" + Double.toString(finalSite.base()) + "\n"
                    + "min_temp_mc\t" + Double.toString(attempt.bounds.min()) + "\n"
                    + "max_temp_mc\t" + Double.toString(attempt.bounds.max()) + "\n");
            try { Files.move(partial, receipt, StandardCopyOption.ATOMIC_MOVE); }
            catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(partial, receipt, StandardCopyOption.REPLACE_EXISTING);
            }
            if ("fixture".equals(attempt.id)) PrestigeCoordinator.writeInitialSpawnStatus(attempt.server, status);
            report(attempt, status, "temperate shared spawn saved before first join");
            synchronized (ACTIVE) { ACTIVE.remove(attempt.server); }
        } catch (Exception error) {
            PrestigeMod.LOGGER.error("Initial spawn candidate persistence failed", error);
            fail(attempt, "error", "candidate persistence failed; see server diagnostics");
        }
    }

    private static void fail(Attempt attempt, String kind, String reason) {
        report(attempt, "failed", reason);
        try {
            PrestigeCoordinator.writeInitialSpawnStatus(attempt.server, "failed");
            Path file = attempt.server.getWorldPath(LevelResource.ROOT).resolve(OUTCOME);
            Files.createDirectories(file.getParent());
            Path partial = file.resolveSibling(file.getFileName() + ".partial");
            Files.createFile(partial, java.nio.file.attribute.PosixFilePermissions.asFileAttribute(
                    java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")));
            String diagnostic = reason.replaceAll("[\\r\\n\\t]", " ");
            Files.writeString(partial, "BC_INITIAL_SPAWN_OUTCOME_V2\nkind\t" + kind
                    + "\nreason\t" + diagnostic.substring(0, Math.min(180, diagnostic.length())) + "\n");
            Files.move(partial, file, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException error) { PrestigeMod.LOGGER.error("Could not write failed initial spawn state", error); }
        synchronized (ACTIVE) { ACTIVE.remove(attempt.server); }
    }

    private static boolean denyAdmission(String reason) {
        PrestigeMod.LOGGER.warn("Initial spawn admission rejected: {}", reason);
        return false;
    }

    public static boolean mayAdmit(MinecraftServer server) {
        synchronized (ACTIVE) { if (ACTIVE.containsKey(server)) return denyAdmission("staging still active"); }
        try {
            // Forge may expose the level root as world/.; normalize before comparing its
            // directory name with the supervisor's no-replace promotion receipt.
            Path root = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
            Path state = root.resolve("data/better_world_management/initial-spawn-v1.tsv");
            Path newAttempt = root.resolve("data/better_world_management/initial-spawn-start-v2.tsv");
            if (Files.isSymbolicLink(root) || Files.isSymbolicLink(root.resolve("data"))
                    || Files.isSymbolicLink(root.resolve("data/better_world_management"))
                    || Files.isSymbolicLink(state) || Files.isSymbolicLink(newAttempt)
                    || Files.isSymbolicLink(root.resolve(RECEIPT)) || Files.isSymbolicLink(root.resolve("level.dat")))
                return denyAdmission("unsafe evidence path");
            if (!Files.isRegularFile(state)) {
                if (Files.exists(newAttempt) || System.getenv("BC_INITIAL_SPAWN_TARGET") != null
                        || server.isSingleplayer() && InitialSpawnClientBridge.intent(server) != null)
                    return denyAdmission("missing status during staging");
                return true; // Established worlds predate this search.
            }
            String status = Files.readString(state).strip();
            if (status.equals("pending") && !Files.exists(newAttempt)
                    && System.getenv("BC_INITIAL_SPAWN_TARGET") == null
                    && (!server.isSingleplayer() || InitialSpawnClientBridge.intent(server) == null)) {
                // An already inhabited pre-v2 world must never be reselected or reset.
                Path players = root.resolve("playerdata");
                if (Files.isDirectory(players)) {
                    try (var files = Files.list(players)) {
                        if (files.anyMatch(Files::isRegularFile)) return true;
                    }
                }
            }
            if (!status.equals("resolved") && !status.equals("fallback")) return denyAdmission("unpublished status");
            Path receipt = root.resolve(RECEIPT);
            if (!Files.isRegularFile(receipt)) {
                // Historical status files predate v2 start/attempt receipts. New or
                // interrupted candidates always have the start marker and fail closed.
                return !Files.exists(newAttempt) && System.getenv("BC_INITIAL_SPAWN_TARGET") == null
                        && (!server.isSingleplayer() || InitialSpawnClientBridge.intent(server) == null);
            }
            List<String> lines = Files.readAllLines(receipt);
            boolean v3 = lines.size() == 12 && lines.get(0).equals("BC_INITIAL_SPAWN_ATTEMPT_V3");
            boolean v2 = lines.size() == 9 && lines.get(0).equals("BC_INITIAL_SPAWN_ATTEMPT_V2");
            if (!v3 && !v2) return denyAdmission("attempt receipt version or length");
            if (v3 && !status.equals("resolved")) return denyAdmission("parametric receipt must be resolved");
            if (!lines.get(5).equals("status\t" + status)) return denyAdmission("attempt receipt status");
            if (!lines.get(2).equals("target\t" + root.getFileName())) {
                PrestigeMod.LOGGER.warn("Initial spawn receipt target mismatch: receipt={}, saveDirectory={}",
                        lines.get(2), root.getFileName());
                return denyAdmission("attempt receipt target differs from save directory");
            }
            if (!lines.get(1).matches("attempt\\t[a-zA-Z0-9_-]{1,100}"))
                return denyAdmission("attempt receipt identifier");
            if (!lines.get(3).matches("number\\t[1-9][0-9]*"))
                return denyAdmission("attempt receipt ordinal");
            if (!lines.get(8).startsWith("biome\t")
                    || ResourceLocation.tryParse(lines.get(8).substring(6)) == null)
                return denyAdmission("attempt receipt biome identity");
            if (!lines.get(6).equals("provenance\trandom") && !lines.get(6).equals("provenance\texplicit")
                    && !lines.get(6).equals("provenance\tunknown")) return denyAdmission("attempt seed provenance");
            if (status.equals("fallback") && !lines.get(6).equals("provenance\texplicit")) return denyAdmission("unsafe fallback provenance");
            if (!lines.get(4).equals("seed\t" + server.overworld().getSeed())) return denyAdmission("live seed differs from receipt");
            var saved = NbtIo.readCompressed(root.resolve("level.dat").toFile()).getCompound("Data");
            String[] coords = lines.get(7).split("\\t");
            if (coords.length != 4 || !coords[0].equals("spawn")) return denyAdmission("malformed spawn receipt");
            BlockPos spawn = server.overworld().getSharedSpawnPos();
            boolean inhabited = inhabited(root);
            if (saved.getCompound("WorldGenSettings").getLong("seed") != server.overworld().getSeed())
                return denyAdmission("saved seed differs from live world");
            if (!inhabited && (spawn.getX() != Integer.parseInt(coords[1]) || spawn.getY() != Integer.parseInt(coords[2])
                    || spawn.getZ() != Integer.parseInt(coords[3])
                    || saved.getInt("SpawnX") != spawn.getX() || saved.getInt("SpawnY") != spawn.getY()
                    || saved.getInt("SpawnZ") != spawn.getZ()))
                return denyAdmission("fresh shared spawn differs from receipt");
            if (v3) {
                double base = receiptTemperature(lines.get(9), "base_temp_mc");
                double min = receiptTemperature(lines.get(10), "min_temp_mc");
                double max = receiptTemperature(lines.get(11), "max_temp_mc");
                InitialSpawnClimate.Bounds recorded = new InitialSpawnClimate.Bounds(min, max);
                if (!recorded.contains(base)) return denyAdmission("receipt base temperature outside bounds");
                // Never displace an inhabited world after a later biome/config update.
                if (!inhabited) {
                    InitialSpawnClimate.Bounds live = InitialSpawnClimate.loadedBounds(server);
                    Site actual = siteAt(server.overworld(), spawn, live);
                    if (actual == null || !actual.biome().equals(lines.get(8).substring(6))
                            || Double.compare(actual.base(), base) != 0
                            || Double.compare(live.min(), min) != 0 || Double.compare(live.max(), max) != 0)
                        return denyAdmission("live shared-spawn biome or limits differ from receipt");
                }
            }
            if (!inhabited && status.equals("resolved") && server.overworld().getGameRules().getInt(GameRules.RULE_SPAWN_RADIUS) != 0)
                return denyAdmission("spawn radius is not zero");
            return true;
        } catch (Exception error) {
            PrestigeMod.LOGGER.error("Could not verify initial spawn readiness", error);
            return false;
        }
    }

    private static boolean inhabited(Path root) throws IOException {
        Path marker = root.resolve("data/better_world_management/initial-spawn-login-v1.tsv");
        if (Files.isSymbolicLink(marker)) throw new IOException("Unsafe initial-spawn login marker");
        if (Files.isRegularFile(marker)) return true;
        Path players = root.resolve("playerdata");
        if (Files.isSymbolicLink(players)) throw new IOException("Unsafe playerdata directory");
        if (!Files.isDirectory(players)) return false;
        try (var files = Files.list(players)) { return files.anyMatch(Files::isRegularFile); }
    }

    private static double receiptTemperature(String line, String key) {
        if (!line.startsWith(key + "\t")) throw new IllegalArgumentException("Missing temperature receipt field " + key);
        double value = Double.parseDouble(line.substring(key.length() + 1));
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite temperature receipt field " + key);
        return value;
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        MinecraftServer server = event.getEntity().getServer();
        if (server == null || !Files.isRegularFile(server.getWorldPath(LevelResource.ROOT).resolve(RECEIPT))) return;
        try {
            Path marker = server.getWorldPath(LevelResource.ROOT)
                    .resolve("data/better_world_management/initial-spawn-login-v1.tsv");
            Files.createDirectories(marker.getParent());
            Files.writeString(marker, "BC_INITIAL_SPAWN_LOGIN_V1\n");
        } catch (IOException error) { PrestigeMod.LOGGER.error("Could not record staged-world admission", error); }
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent event) {
        synchronized (ACTIVE) { ACTIVE.remove(event.getServer()); }
    }
}
