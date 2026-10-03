package com.bettercontent.betterworldmanagement.mixin;

import com.bettercontent.betterworldmanagement.InitialSpawnWorldOptionsAccess;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.storage.PrimaryLevelData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(PrimaryLevelData.class)
public abstract class PrimaryLevelDataMixin implements InitialSpawnWorldOptionsAccess {
    @Shadow @Final @Mutable private WorldOptions worldOptions;

    @Override
    public void betterWorldManagement$setWorldOptions(WorldOptions options) {
        this.worldOptions = options;
    }
}
