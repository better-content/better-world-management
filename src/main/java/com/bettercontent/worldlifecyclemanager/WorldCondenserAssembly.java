package com.bettercontent.worldlifecyclemanager;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.registries.ForgeRegistries;

final class WorldCondenserAssembly {
    private static final ResourceLocation FONT = new ResourceLocation("dimension_drink", "dimensional_font");

    private WorldCondenserAssembly() {}

    static boolean valid(LevelReader level, BlockPos devicePos) {
        if (!level.getBlockState(devicePos.below()).isAir()) return false;
        BlockState font = level.getBlockState(devicePos.below(2));
        if (!FONT.equals(ForgeRegistries.BLOCKS.getKey(font.getBlock()))) return false;
        for (Property<?> property : font.getProperties()) {
            if ("bound".equals(property.getName())) return Boolean.FALSE.equals(font.getValue(property));
        }
        return false;
    }
}
