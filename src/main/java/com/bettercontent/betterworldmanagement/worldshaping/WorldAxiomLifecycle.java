package com.bettercontent.betterworldmanagement.worldshaping;

import com.bettercontent.betterworldmanagement.PrestigeMod;
import com.bettercontent.betterworldmanagement.PrestigeService;
import com.bettercontent.betterworldmanagement.WorldCondenserMenu;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.NetworkHooks;
import java.nio.file.Files;

/** Runtime binding and read-only command entry; physical menu is still mandatory for mutations. */
public final class WorldAxiomLifecycle {
    private WorldAxiomLifecycle() {}
    @SubscribeEvent public static void start(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        WorldAxiomService.clear(server);
        if (!server.isDedicatedServer()) return;
        try {
            WorldAxiomService.active(server); // validates immutable boot projection before any health is published
            AxiomClaims.recover(server);
            if (!Files.exists(WorldAxiomService.control(server, "successor-request-v5.tsv"))) {
                WorldAxiomService.applyWorld(server, server.overworld().getSharedSpawnPos());
            }
        } catch (Exception error) {
            PrestigeMod.LOGGER.error("World Axiom startup/recovery rejected", error);
            try { WorldAxiomService.writeHealth(server, false, error.getMessage()); }
            catch (Exception healthError) { PrestigeMod.LOGGER.error("Could not publish world-law failure", healthError); }
            server.halt(false);
        }
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) { AxiomClaims.clear(event.getServer()); WorldAxiomService.clear(event.getServer()); }
    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        if (event.getCommandSelection() == Commands.CommandSelection.INTEGRATED) return;
        event.getDispatcher().register(Commands.literal("world_axioms")
            .executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                NetworkHooks.openScreen(player, new SimpleMenuProvider((id, inventory, ignored) -> new WorldCondenserMenu(id, inventory, BlockPos.ZERO, true, 0), Component.literal("World Axioms")), buffer -> {
                    buffer.writeBlockPos(BlockPos.ZERO); buffer.writeBoolean(true); buffer.writeVarInt(0);
                });
                WorldAxiomNetwork.send(player, ""); return 1;
            })
            .then(Commands.literal("status").executes(context -> {
                try {
                    var active = WorldAxiomService.active(context.getSource().getServer());
                    context.getSource().sendSuccess(() -> Component.literal(active.map(build -> "World Axioms generation=" + build.targetGeneration() + " transaction=" + build.transaction() + " active=" + String.join(",", new java.util.TreeSet<>(build.selected()))).orElse("World Axioms: no paid laws in this generation")), false);
                    return 1;
                } catch (Exception error) { context.getSource().sendFailure(Component.literal("World-law state rejected: " + error.getMessage())); return 0; }
            }))
            .then(Commands.literal("discard-draft").requires(source -> source.hasPermission(4)).executes(context -> {
                try {
                    ServerPlayer player = context.getSource().getPlayerOrException(); WorldAxiomService.requireAccess(player, true);
                    if (WorldAxiomService.locked(player.server)) throw new IllegalStateException("Cancel the staged succession before discarding a draft");
                    WorldAxiomService.discardDraft(player.server); WorldAxiomNetwork.send(player, ""); return 1;
                } catch (Exception error) { context.getSource().sendFailure(Component.literal("World-law recovery refused: " + error.getMessage())); return 0; }
            })));
    }
}
