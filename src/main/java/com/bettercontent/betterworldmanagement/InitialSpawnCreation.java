package com.bettercontent.betterworldmanagement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/** Chooses a fresh world's seed before level generation and its spawn before logins. */
public final class InitialSpawnCreation {
    private static final int GRID_STEP = 32;
    private static final int CANDIDATE_COUNT = 256;
    private static final int WORLD_LIMIT = 29_999_984;
    private static final Map<MinecraftServer, Selection> SELECTIONS = new WeakHashMap<>();

    private record Site(BlockPos feet, String biome) {}
    private record Selection(long seed, List<Site> sites, List<String> preferences, int attempts) {}

    private InitialSpawnCreation() {}

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        MinecraftServer server = event.getServer();
        if (server.getWorldData().overworldData().isInitialized() || server.getWorldData().isDebugWorld()) return;
        if (!(server.getWorldData() instanceof PrimaryLevelData data)) return;
        try {
            if (Files.isRegularFile(PrestigeService.control(server).resolve("successor-request-v5.tsv"))) return;
            var stems = server.registryAccess().registryOrThrow(Registries.LEVEL_STEM);
            var overworld = stems.get(net.minecraft.world.level.dimension.LevelStem.OVERWORLD);
            if (overworld == null) return;
            ChunkGenerator generator = overworld.generator();
            List<String> preferences = PrestigeCoordinator.initialSpawnPreferences(server);
            Set<String> possible = generator.getBiomeSource().possibleBiomes().stream()
                    .map(holder -> holder.unwrapKey().map(key -> key.location().toString()).orElse(""))
                    .collect(Collectors.toSet());
            if (preferences.stream().noneMatch(possible::contains)) {
                PrestigeMod.LOGGER.info("Initial temperate spawn bypassed: preset cannot produce a configured biome");
                return;
            }
            if (!(generator instanceof NoiseBasedChunkGenerator noiseGenerator)) {
                PrestigeMod.LOGGER.info("Initial temperate spawn bypassed: preset uses a non-noise Overworld generator");
                return;
            }
            var noiseRegistry = server.registryAccess().lookupOrThrow(Registries.NOISE);
            NoiseGeneratorSettings settings = noiseGenerator.generatorSettings().value();
            LevelHeightAccessor height = LevelHeightAccessor.create(generator.getMinY(), generator.getGenDepth());
            long firstSeed = data.worldGenOptions().seed();
            InitialSpawnSeedSearch.Result<List<Site>> chosen = InitialSpawnSeedSearch.run(
                    firstSeed, System::nanoTime, () -> ThreadLocalRandom.current().nextLong(),
                    (seed, deadline) -> {
                        RandomState state = RandomState.create(settings, noiseRegistry, seed);
                        List<Site> sites = search(generator, state, height, preferences, deadline);
                        if (sites.isEmpty()) {
                            PrestigeMod.LOGGER.info("Initial temperate spawn: no site on seed {} within 60s; trying another seed", seed);
                            return null;
                        }
                        return sites;
                    });
            ObfuscationReflectionHelper.setPrivateValue(PrimaryLevelData.class, data,
                    data.worldGenOptions().withSeed(java.util.OptionalLong.of(chosen.seed())), "f_244409_");
            Selection selection = new Selection(chosen.seed(), chosen.value(), preferences, chosen.attempts());
            SELECTIONS.put(server, selection);
            PrestigeMod.LOGGER.info("Initial temperate spawn prepared seed={} attempts={} candidate_sites={}",
                    selection.seed(), selection.attempts(), selection.sites().size());
        } catch (Exception error) {
            throw new IllegalStateException("Could not prepare the initial temperate spawn before world creation", error);
        }
    }

    private static List<Site> search(ChunkGenerator generator, RandomState state, LevelHeightAccessor height,
                                     List<String> preferences, long deadline) {
        Set<String> allowed = Set.copyOf(preferences);
        List<Site> found = new ArrayList<>(CANDIDATE_COUNT);
        var source = generator.getBiomeSource();
        int seaQuart = QuartPos.fromBlock(generator.getSeaLevel());
        for (int ring = 0; ring <= WORLD_LIMIT / GRID_STEP; ring++) {
            int count = ring == 0 ? 1 : ring * 8;
            for (int offset = 0; offset < count; offset++) {
                if ((offset & 63) == 0 && (System.nanoTime() >= deadline || Thread.currentThread().isInterrupted())) return found;
                int gridX;
                int gridZ;
                if (ring == 0) { gridX = 0; gridZ = 0; }
                else {
                    int edge = ring * 2;
                    int cursor = offset;
                    if (cursor < edge + 1) { gridX = -ring + cursor; gridZ = -ring; }
                    else if ((cursor -= edge + 1) < edge) { gridX = ring; gridZ = -ring + 1 + cursor; }
                    else if ((cursor -= edge) < edge) { gridX = ring - 1 - cursor; gridZ = ring; }
                    else { cursor -= edge; gridX = -ring; gridZ = ring - 1 - cursor; }
                }
                int x = gridX * GRID_STEP;
                int z = gridZ * GRID_STEP;
                String coarse = source.getNoiseBiome(QuartPos.fromBlock(x), seaQuart,
                        QuartPos.fromBlock(z), state.sampler()).unwrapKey()
                        .map(key -> key.location().toString()).orElse("");
                if (!allowed.contains(coarse)) continue;
                int y = generator.getBaseHeight(x, z, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, height, state);
                if (y <= height.getMinBuildHeight() + 1 || y >= height.getMaxBuildHeight() - 2) continue;
                var column = generator.getBaseColumn(x, z, height, state);
                if (!column.getBlock(y - 1).blocksMotion() || !column.getBlock(y - 1).getFluidState().isEmpty()
                        || !column.getBlock(y).isAir() || !column.getBlock(y + 1).isAir()) continue;
                String feetBiome = source.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y),
                        QuartPos.fromBlock(z), state.sampler()).unwrapKey()
                        .map(key -> key.location().toString()).orElse("");
                if (!allowed.contains(feetBiome)) continue;
                found.add(new Site(new BlockPos(x, y, z), feetBiome));
                if (found.size() >= CANDIDATE_COUNT) return found;
            }
        }
        return found;
    }

    private static Site verifySite(ServerLevel level, Site site) {
        BlockPos probe = site.feet();
        var chunk = level.getChunk(probe.getX() >> 4, probe.getZ() >> 4);
        int y = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, probe.getX() & 15, probe.getZ() & 15) + 1;
        BlockPos feet = new BlockPos(probe.getX(), y, probe.getZ());
        String actual = level.getBiome(feet).unwrapKey().map(key -> key.location().toString()).orElse("");
        if (!site.biome().equals(actual) || !level.getBlockState(feet.below()).blocksMotion()
                || !level.getFluidState(feet.below()).isEmpty() || !level.getBlockState(feet).isAir()
                || !level.getBlockState(feet.above()).isAir()) return null;
        return new Site(feet, actual);
    }

    private static Site verifyAnyAllowedSite(ServerLevel level, int x, int z, Set<String> allowed) {
        var chunk = level.getChunk(x >> 4, z >> 4);
        int y = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15) + 1;
        BlockPos feet = new BlockPos(x, y, z);
        String actual = level.getBiome(feet).unwrapKey().map(key -> key.location().toString()).orElse("");
        if (!allowed.contains(actual) || !level.getBlockState(feet.below()).blocksMotion()
                || !level.getFluidState(feet.below()).isEmpty() || !level.getBlockState(feet).isAir()
                || !level.getBlockState(feet.above()).isAir()) return null;
        return new Site(feet, actual);
    }

    private static Site searchGeneratedTerrain(ServerLevel level, List<String> preferences) {
        Set<String> allowed = Set.copyOf(preferences);
        long lastProgress = System.nanoTime();
        int seaQuart = QuartPos.fromBlock(level.getSeaLevel());
        for (int ring = 0; ring <= WORLD_LIMIT / GRID_STEP; ring++) {
            int count = ring == 0 ? 1 : ring * 8;
            for (int offset = 0; offset < count; offset++) {
                if (Thread.currentThread().isInterrupted()) throw new IllegalStateException("Initial spawn preparation cancelled");
                if (System.nanoTime() - lastProgress >= InitialSpawnSeedSearch.PER_SEED_NANOS) {
                    PrestigeMod.LOGGER.warn("Initial temperate spawn still checking generated terrain at ring {}", ring);
                    lastProgress = System.nanoTime();
                }
                int gridX;
                int gridZ;
                if (ring == 0) { gridX = 0; gridZ = 0; }
                else {
                    int edge = ring * 2;
                    int cursor = offset;
                    if (cursor < edge + 1) { gridX = -ring + cursor; gridZ = -ring; }
                    else if ((cursor -= edge + 1) < edge) { gridX = ring; gridZ = -ring + 1 + cursor; }
                    else if ((cursor -= edge) < edge) { gridX = ring - 1 - cursor; gridZ = ring; }
                    else { cursor -= edge; gridX = -ring; gridZ = ring - 1 - cursor; }
                }
                int x = gridX * GRID_STEP;
                int z = gridZ * GRID_STEP;
                String coarse = level.getUncachedNoiseBiome(QuartPos.fromBlock(x), seaQuart,
                        QuartPos.fromBlock(z)).unwrapKey()
                        .map(key -> key.location().toString()).orElse("");
                if (!allowed.contains(coarse)) continue;
                Site confirmed = verifyAnyAllowedSite(level, x, z, allowed);
                if (confirmed != null) return confirmed;
            }
        }
        throw new IllegalStateException("No safe temperate site exists inside the Overworld coordinate limit");
    }

    @SubscribeEvent
    public static void onCreateSpawn(LevelEvent.CreateSpawnPosition event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !level.dimension().equals(Level.OVERWORLD)) return;
        MinecraftServer server = level.getServer();
        Selection selection = SELECTIONS.get(server);
        if (selection == null) return;
        Site confirmed = null;
        for (Site site : selection.sites()) {
            confirmed = verifySite(level, site);
            if (confirmed != null) break;
        }
        if (confirmed == null) confirmed = searchGeneratedTerrain(level, selection.preferences());
        event.getSettings().setSpawn(confirmed.feet(), 0.0F);
        level.getGameRules().getRule(GameRules.RULE_SPAWN_RADIUS).set(0, server);
        event.setCanceled(true);
        try { PrestigeCoordinator.writeInitialSpawnStatus(server, "resolved"); }
        catch (Exception error) { throw new IllegalStateException("Could not record initial temperate spawn", error); }
        PrestigeMod.LOGGER.info("Initial shared spawn selected seed={} biome={} feet={} attempts={}",
                selection.seed(), confirmed.biome(), confirmed.feet(), selection.attempts());
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) return;
        Selection selected = SELECTIONS.get(player.server);
        if (selected != null && selected.attempts() > 1 && player.server.isSingleplayer()) {
            player.sendSystemMessage(Component.literal("A temperate starting site was found using seed "
                    + selected.seed() + " after " + selected.attempts() + " seed attempts."));
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        SELECTIONS.remove(event.getServer());
    }
}
