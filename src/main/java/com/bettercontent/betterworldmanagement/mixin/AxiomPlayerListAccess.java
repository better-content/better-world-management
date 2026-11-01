package com.bettercontent.betterworldmanagement.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Invoke Minecraft's own single-player persistence path; never emulate the player serializer. */
@Mixin(PlayerList.class)
public interface AxiomPlayerListAccess {
    @Invoker("save") void worldAxioms$save(ServerPlayer player);
}
