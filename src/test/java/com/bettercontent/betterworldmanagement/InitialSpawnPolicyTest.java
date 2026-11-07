package com.bettercontent.betterworldmanagement;

import com.bettercontent.betterworldmanagement.api.InitialSpawnService;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class InitialSpawnPolicyTest {
    @Test void onlyFreshGenerationZeroWorldsStartTheInitialSearch() {
        assertTrue(PrestigeCoordinator.shouldStartInitialSpawnSearch(false, null, true, 0));
        assertFalse(PrestigeCoordinator.shouldStartInitialSpawnSearch(false, null, false, 0));
        assertFalse(PrestigeCoordinator.shouldStartInitialSpawnSearch(false, null, true, 1));
        assertFalse(PrestigeCoordinator.shouldStartInitialSpawnSearch(true, null, true, 0));
    }

    @Test void pendingMarkerNeverResumesAfterInterruptionOrInhabitation() {
        assertFalse(PrestigeCoordinator.shouldStartInitialSpawnSearch(true, "pending", false, 0));
        assertFalse(PrestigeCoordinator.shouldStartInitialSpawnSearch(true, "pending", true, 0));
    }

    @Test void initialSpawnStatusFailsClosedOnUnknownOrCorruptValues() {
        assertEquals(InitialSpawnService.Status.NOT_APPLICABLE, PrestigeCoordinator.parseInitialSpawnStatus(null));
        assertEquals(InitialSpawnService.Status.PENDING, PrestigeCoordinator.parseInitialSpawnStatus("pending"));
        assertEquals(InitialSpawnService.Status.RESOLVED, PrestigeCoordinator.parseInitialSpawnStatus("resolved"));
        assertEquals(InitialSpawnService.Status.FALLBACK, PrestigeCoordinator.parseInitialSpawnStatus("fallback"));
        assertEquals(InitialSpawnService.Status.FAILED, PrestigeCoordinator.parseInitialSpawnStatus("unknown"));
        assertEquals(InitialSpawnService.Status.FAILED, PrestigeCoordinator.parseInitialSpawnStatus("failed"));
    }

    @Test void initialCandidateDoesNotFlushAllChunksInsideTheServerTick() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/bettercontent/betterworldmanagement/InitialSpawnCreation.java"));
        assertTrue(source.contains("saveEverything(true, false, true)"),
                "level.dat must be saved before the receipt, with chunks flushed on shutdown");
        assertFalse(source.contains("saveEverything(true, true, true)"),
                "flush-all-chunks may deadlock a C2ME server tick before candidate shutdown");
    }

    @Test void failedSingleplayerCandidateReachesRetryHandlerBeforeWorldEntry() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/bettercontent/betterworldmanagement/SingleplayerInitialSpawn.java"));
        assertTrue(source.contains("if (!searchExpired && !loadingStalled && (serverStopped || current.observedFailure)) {\n"
                + "            // A failed native site has a protected retry outcome."));
        assertTrue(source.contains("return beforeConnect(client, access);"));
        assertTrue(source.contains("server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize()"),
                "integrated server stage identity must not use a trailing /.");
        assertTrue(source.contains("access.getLevelPath(LevelResource.ROOT).toAbsolutePath().normalize()"),
                "staged world must be movable after closing a level root ending in /.");
        assertTrue(source.contains("if (Files.isRegularFile(outcome)) break;"),
                "retry receipt must be allowed to arrive after the failed status marker");
    }

    @Test void defaultInitialPreferencesMatchThePackTemperateList() {
        assertEquals(List.of(
                "minecraft:plains", "minecraft:sunflower_plains", "minecraft:meadow",
                "minecraft:forest", "minecraft:flower_forest", "minecraft:birch_forest"),
                PrestigeCoordinator.SAFE_TEMPERATE_DEFAULT_BIOMES);
    }
}
