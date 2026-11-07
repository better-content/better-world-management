package com.bettercontent.betterworldmanagement.mixin;

import com.bettercontent.betterworldmanagement.SingleplayerInitialSpawn;
import net.minecraft.client.Minecraft;
import net.minecraft.server.WorldStem;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Intercept the single-player connection before any player enters an unpublished stage. */
@Mixin(Minecraft.class)
public abstract class MinecraftInitialSpawnMixin {
    @Inject(method = "doWorldLoad", at = @At(value = "INVOKE",
            target = "Ljava/util/concurrent/atomic/AtomicReference;get()Ljava/lang/Object;", ordinal = 0),
            cancellable = true)
    private void betterWorldManagement$renderBeforeWorldgenListener(String name,
            LevelStorageSource.LevelStorageAccess access, PackRepository packs, WorldStem world,
            boolean newWorld, CallbackInfo ci) {
        if (SingleplayerInitialSpawn.pollBeforeProgressListener((Minecraft) (Object) this, access)) ci.cancel();
    }

    @Inject(method = "doWorldLoad", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/server/IntegratedServer;isReady()Z"), cancellable = true)
    private void betterWorldManagement$boundedWorldgenScreen(String name,
            LevelStorageSource.LevelStorageAccess access, PackRepository packs, WorldStem world,
            boolean newWorld, CallbackInfo ci) {
        if (SingleplayerInitialSpawn.pollBeforeServerReady((Minecraft) (Object) this, access)) ci.cancel();
    }

    @Inject(method = "doWorldLoad", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/Connection;connectToLocalServer(Ljava/net/SocketAddress;)Lnet/minecraft/network/Connection;"),
            cancellable = true)
    private void betterWorldManagement$waitForSpawnBeforeJoining(String name,
            LevelStorageSource.LevelStorageAccess access, PackRepository packs, WorldStem world,
            boolean newWorld, CallbackInfo ci) {
        if (SingleplayerInitialSpawn.beforeConnect((Minecraft) (Object) this, access)) ci.cancel();
    }
}
