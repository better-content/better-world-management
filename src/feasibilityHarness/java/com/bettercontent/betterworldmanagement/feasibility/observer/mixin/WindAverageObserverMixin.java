package com.bettercontent.betterworldmanagement.feasibility.observer.mixin;

import com.bettercontent.betterworldmanagement.feasibility.observer.NativeAmbientObserver;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Actual native scan receives CHUNK coordinates, unlike the cached caller's block coordinates. */
@Mixin(targets = "weather2.weathersystem.wind.WindManager", remap = false)
public abstract class WindAverageObserverMixin {
    @Inject(method = "calculateAverageChunkHeightAround(Lnet/minecraft/core/BlockPos;)I", at = @At("HEAD"), remap = false)
    private void axioms$observeWindHead(BlockPos chunkCenter, CallbackInfoReturnable<Integer> callback) {
        NativeAmbientObserver.windHead(chunkCenter);
    }
    @Inject(method = "calculateAverageChunkHeightAround(Lnet/minecraft/core/BlockPos;)I", at = @At("RETURN"), remap = false)
    private void axioms$observeWindReturn(BlockPos chunkCenter, CallbackInfoReturnable<Integer> callback) {
        NativeAmbientObserver.windReturn(callback.getReturnValue());
    }
}
