package com.bettercontent.betterworldmanagement.worldshaping.exploration;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/** Native clipping view with a hard read budget and no chunk loading, including shape neighbor queries. */
final class LoadedSurveyView implements BlockGetter {
    private final ServerLevel level;
    private final int budget;
    int reads;
    boolean exhausted, unknown;
    LoadedSurveyView(ServerLevel level, int budget) { this.level = level; this.budget = budget; }
    @Override public BlockState getBlockState(BlockPos pos) {
        if (reads >= budget) { exhausted = true; return Blocks.BEDROCK.defaultBlockState(); }
        reads++;
        var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) { unknown = true; return Blocks.BEDROCK.defaultBlockState(); }
        return chunk.getBlockState(pos);
    }
    @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
    @Override public BlockEntity getBlockEntity(BlockPos pos) {
        // Visibility may not depend on unknown/custom block-entity logic. Fail this candidate closed.
        unknown = true;
        return null;
    }
    @Override public int getHeight() { return level.getHeight(); }
    @Override public int getMinBuildHeight() { return level.getMinBuildHeight(); }
}
