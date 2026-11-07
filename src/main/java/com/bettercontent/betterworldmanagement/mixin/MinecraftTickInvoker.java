package com.bettercontent.betterworldmanagement.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Minecraft.class)
public interface MinecraftTickInvoker {
    @Invoker("runTick") void betterWorldManagement$runTick(boolean render);
}
