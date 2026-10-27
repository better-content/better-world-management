package com.bettercontent.betterworldmanagement.worldshaping.exploration;

import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.Comparator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;

/** Vanilla locate radius counts placement cells. Explicit candidate admission enforces a real chunk bound. */
final class BoundedStructureSearch {
    private BoundedStructureSearch() {}
    static boolean within(int originX, int originZ, int x, int z) {
        return Math.abs((long) x - originX) <= 64 && Math.abs((long) z - originZ) <= 64;
    }
    static Pair<BlockPos, Holder<Structure>> find(ServerLevel level, Holder<Structure> structure, BlockPos origin) {
        var source = level.getChunkSource(); var state = source.getGeneratorState();
        var placements = state.getPlacementsForStructure(structure);
        if (placements.isEmpty()) return null; // native dimension/biome cannot place this structure
        if (placements.size() != 1 || !(placements.get(0) instanceof RandomSpreadStructurePlacement placement))
            throw new IllegalStateException("Unsupported native structure placement; bounded locate refused");
        ChunkPos from = new ChunkPos(origin); int spacing = placement.spacing();
        var candidates = new ArrayList<ChunkPos>();
        for (int cellX = Math.floorDiv(from.x - 64, spacing); cellX <= Math.floorDiv(from.x + 64, spacing); cellX++)
            for (int cellZ = Math.floorDiv(from.z - 64, spacing); cellZ <= Math.floorDiv(from.z + 64, spacing); cellZ++) {
                ChunkPos pos = placement.getPotentialStructureChunk(level.getSeed(), cellX * spacing, cellZ * spacing);
                if (within(from.x, from.z, pos.x, pos.z) && placement.isStructureChunk(state, pos.x, pos.z)) candidates.add(pos);
            }
        candidates.sort(Comparator.comparingDouble((ChunkPos p) -> p.getMiddleBlockPosition(origin.getY()).distSqr(origin))
                .thenComparingInt(p -> p.x).thenComparingInt(p -> p.z));
        for (ChunkPos candidate : candidates) {
            // Radius zero checks only this native placement cell. The seed chooses the same admitted chunk.
            var result = source.getGenerator().findNearestMapStructure(level, HolderSet.direct(structure),
                    candidate.getMiddleBlockPosition(origin.getY()), 0, false);
            if (result != null) {
                ChunkPos found = new ChunkPos(result.getFirst());
                if (within(from.x, from.z, found.x, found.z)) return result;
            }
        }
        return null;
    }
}
