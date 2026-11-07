package com.bettercontent.betterworldmanagement;

import java.util.Collection;

/** Pure safety decisions shared by preliminary and native initial-spawn selection. */
final class InitialSpawnCandidatePolicy {
    private InitialSpawnCandidatePolicy() {}

    static boolean impossibleBiomeSet(Collection<String> preferred, Collection<String> possible) {
        return !possible.isEmpty() && preferred.stream().noneMatch(possible::contains);
    }

    static boolean feetAreSafe(boolean preferredBiome, boolean solidGround, boolean dryGround,
                               boolean feetAir, boolean headAir, boolean inBorder,
                               int y, int minY, int maxY) {
        return preferredBiome && solidGround && dryGround && feetAir && headAir && inBorder
                && y > minY + 1 && y < maxY - 2;
    }

    static boolean vanillaFallbackAllowed(String provenance) {
        return "explicit".equals(provenance);
    }
}
