package com.bettercontent.betterworldmanagement.feasibility.observer.mixin;

import com.bettercontent.betterworldmanagement.feasibility.observer.HazardsLootPolicyObserver;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Observe the exact first-party static guard; no return/argument/state changes. */
@Mixin(targets = "com.bettercontent.betterchemlibhazards.sim.InventoryScanPolicy", remap = false)
abstract class InventoryScanPolicyObservationMixin {
    @Inject(method = "hasPendingLoot(Ljava/lang/Object;)Z", at = @At("RETURN"), remap = false, require = 1)
    private static void betterWorldManagement$observePendingLoot(Object holder, CallbackInfoReturnable<Boolean> callback) {
        HazardsLootPolicyObserver.observe(holder, callback.getReturnValueZ());
    }
}
