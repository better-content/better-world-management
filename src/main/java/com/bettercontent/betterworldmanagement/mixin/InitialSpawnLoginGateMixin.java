package com.bettercontent.betterworldmanagement.mixin;

import com.bettercontent.betterworldmanagement.InitialSpawnCreation;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Deny admission at PlayerList's entry point, before any player data or terrain interaction. */
@Mixin(PlayerList.class)
public abstract class InitialSpawnLoginGateMixin {
    @Shadow @Final private MinecraftServer server;

    @Inject(method = "placeNewPlayer", at = @At("HEAD"), cancellable = true)
    private void betterWorldManagement$onlyPublishVerifiedWorld(Connection connection, ServerPlayer player, CallbackInfo ci) {
        if (InitialSpawnCreation.mayAdmit(server)) return;
        connection.disconnect(Component.literal("World spawn is not ready; creation is still in progress or failed. See the server console."));
        ci.cancel();
    }
}
