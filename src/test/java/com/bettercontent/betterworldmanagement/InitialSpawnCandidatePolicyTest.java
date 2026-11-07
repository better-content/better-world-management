package com.bettercontent.betterworldmanagement;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

final class InitialSpawnCandidatePolicyTest {
    @Test void impossibleBiomeSetsStopEarlyButUnknownSourcesAreDeferred() {
        List<String> preferred = List.of("minecraft:plains", "minecraft:forest");
        assertTrue(InitialSpawnCandidatePolicy.impossibleBiomeSet(preferred, Set.of("minecraft:desert")));
        assertFalse(InitialSpawnCandidatePolicy.impossibleBiomeSet(preferred, Set.of("minecraft:forest")));
        assertFalse(InitialSpawnCandidatePolicy.impossibleBiomeSet(preferred, Set.of()));
    }

    @Test void predictedFeetRequireTheActualFeetBiomeSolidDryGroundHeadroomAndBorder() {
        assertTrue(InitialSpawnCandidatePolicy.feetAreSafe(true, true, true, true, true,
                true, 72, -64, 320));
        for (int missing = 0; missing < 6; missing++) {
            boolean[] flags = {true, true, true, true, true, true};
            flags[missing] = false;
            assertFalse(InitialSpawnCandidatePolicy.feetAreSafe(
                    flags[0], flags[1], flags[2], flags[3], flags[4], flags[5], 72, -64, 320));
        }
        assertFalse(InitialSpawnCandidatePolicy.feetAreSafe(true, true, true, true, true,
                true, -63, -64, 320));
        assertFalse(InitialSpawnCandidatePolicy.feetAreSafe(true, true, true, true, true,
                true, 318, -64, 320));
    }

    @Test void vanillaFallbackIsRestrictedToKnownExplicitSeed() {
        assertTrue(InitialSpawnCandidatePolicy.vanillaFallbackAllowed("explicit"));
        assertFalse(InitialSpawnCandidatePolicy.vanillaFallbackAllowed("random"));
        assertFalse(InitialSpawnCandidatePolicy.vanillaFallbackAllowed("unknown"));
        assertFalse(InitialSpawnCandidatePolicy.vanillaFallbackAllowed(null));
    }
}
