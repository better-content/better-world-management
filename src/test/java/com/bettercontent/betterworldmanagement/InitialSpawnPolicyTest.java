package com.bettercontent.betterworldmanagement;

import com.bettercontent.betterworldmanagement.api.InitialSpawnService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class InitialSpawnPolicyTest {
    @Test void onlyFreshGenerationZeroWorldsStartTheInitialSearch() {
        assertTrue(PrestigeCoordinator.shouldStartInitialSpawnSearch(false, null, true, 0));
        assertFalse(PrestigeCoordinator.shouldStartInitialSpawnSearch(false, null, false, 0));
        assertFalse(PrestigeCoordinator.shouldStartInitialSpawnSearch(false, null, true, 1));
        assertFalse(PrestigeCoordinator.shouldStartInitialSpawnSearch(true, null, true, 0));
    }

    @Test void pendingSearchResumesAfterWorldBecomesInhabited() {
        assertTrue(PrestigeCoordinator.shouldStartInitialSpawnSearch(true, "pending", false, 0));
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
}
