package com.bettercontent.betterworldmanagement.feasibility.observer.mixin;

import com.bettercontent.betterworldmanagement.feasibility.observer.NativeAmbientObserver;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Optional nonshipping HEAD/RETURN observation; never cancels or changes the native result. */
@Mixin(targets = "com.bettercontent.betterindustrialheat.ColdSweatAmbientSampler", remap = false)
public abstract class HeatAmbientSamplerObserverMixin {
    @Inject(method = "sampleWorldTemp(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)D", at = @At("HEAD"), remap = false)
    private void axioms$observeHeatHead(Level level, BlockPos holder, CallbackInfoReturnable<Double> callback) {
        NativeAmbientObserver.heatHead(level, holder);
    }
    @Inject(method = "sampleWorldTemp(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)D", at = @At("RETURN"), remap = false)
    private void axioms$observeHeatReturn(Level level, BlockPos holder, CallbackInfoReturnable<Double> callback) {
        NativeAmbientObserver.heatReturn(callback.getReturnValue());
    }
}
