package com.bettercontent.betterworldmanagement;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** One biome column in each chunk of the 3-by-3 initial-spawn neighborhood. */
final class InitialSpawnNeighborhood {
    static final int SAMPLE_COUNT = 9;

    private InitialSpawnNeighborhood() {}

    static BlockPos sample(BlockPos spawn, int index) {
        if (index < 0 || index >= SAMPLE_COUNT) throw new IllegalArgumentException("Invalid neighborhood index");
        int dx = index % 3 - 1;
        int dz = index / 3 - 1;
        if (dx == 0 && dz == 0) return spawn; // The center must be the actual saved spawn.
        int x = (Math.floorDiv(spawn.getX(), 16) + dx) * 16 + 8;
        int z = (Math.floorDiv(spawn.getZ(), 16) + dz) * 16 + 8;
        return new BlockPos(x, spawn.getY(), z); // Caller supplies each neighbor's predicted height.
    }

    static <T> List<T> qualify(BlockPos spawn, Function<BlockPos, T> sampler) {
        T center = sampler.apply(spawn);
        if (center == null) return List.of(); // Reject ocean/invalid center before eight height lookups.
        List<T> result = new ArrayList<>(java.util.Collections.nCopies(SAMPLE_COUNT, null));
        result.set(4, center);
        for (int i = 0; i < SAMPLE_COUNT; i++) {
            if (i == 4) continue;
            T neighbor = sampler.apply(sample(spawn, i));
            if (neighbor == null) return List.of(); // All nine chunks must qualify.
            result.set(i, neighbor);
        }
        return List.copyOf(result);
    }
}
