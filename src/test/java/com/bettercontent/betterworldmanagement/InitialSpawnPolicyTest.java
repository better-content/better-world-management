package com.bettercontent.betterworldmanagement;

import com.bettercontent.betterworldmanagement.api.InitialSpawnService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class InitialSpawnPolicyTest {
    @Test void aMissingSiteRetriesWithADistinctSeed() {
        long[] now = {100L};
        long[] seeds = {5L, 7L, 7L, 9L};
        int[] next = {0};
        var result = InitialSpawnSeedSearch.run(5L, () -> now[0], () -> seeds[next[0]++],
                (seed, deadline) -> {
                    assertEquals(now[0] + InitialSpawnSeedSearch.PER_SEED_NANOS, deadline);
                    return seed == 9L ? "plains" : null;
                });
        assertEquals(9L, result.seed());
        assertEquals(3, result.attempts());
        assertEquals("plains", result.value());
    }

    @Test void initialSpawnStatusHasSafeUnknownFallback() {
        assertEquals(InitialSpawnService.Status.NOT_APPLICABLE, PrestigeCoordinator.parseInitialSpawnStatus(null));
        assertEquals(InitialSpawnService.Status.PENDING, PrestigeCoordinator.parseInitialSpawnStatus("pending"));
        assertEquals(InitialSpawnService.Status.RESOLVED, PrestigeCoordinator.parseInitialSpawnStatus("resolved"));
        assertEquals(InitialSpawnService.Status.FALLBACK, PrestigeCoordinator.parseInitialSpawnStatus("fallback"));
        assertEquals(InitialSpawnService.Status.NOT_APPLICABLE, PrestigeCoordinator.parseInitialSpawnStatus("unknown"));
    }

    @Test void defaultInitialPreferencesMatchThePackTemperateList() {
        assertEquals(List.of(
                "minecraft:plains", "minecraft:sunflower_plains", "minecraft:meadow",
                "minecraft:forest", "minecraft:flower_forest", "minecraft:birch_forest"),
                PrestigeCoordinator.SAFE_TEMPERATE_DEFAULT_BIOMES);
    }

    @Test void unavailableEarlyBiomeDefersSelectionInsteadOfDereferencingNull() {
        assertThrows(InitialSpawnCreation.BiomeLookupUnavailable.class,
                () -> InitialSpawnCreation.earlyBiomeId(null));
    }

    @Test void unavailableGeneratedBiomeReportsWhySpawnCannotBeVerified() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> InitialSpawnCreation.generatedBiomeId(null, 32, -64));
        assertTrue(error.getMessage().contains("x=32 z=-64"));
        assertTrue(error.getMessage().contains("cannot verify a safe temperate spawn"));
    }
}
