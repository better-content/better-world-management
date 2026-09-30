package com.bettercontent.betterworldmanagement.api.event;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.Event;

import java.util.Objects;

/** Posted after an operator successfully reaches a formed World Condenser interface. */
public final class WorldCondenserAccessedEvent extends Event {
    private final ServerPlayer player;
    private final ResourceKey<Level> dimension;
    private final BlockPos position;
    private final String episodeId;

    public WorldCondenserAccessedEvent(ServerPlayer player, ResourceKey<Level> dimension,
                                       BlockPos position, String episodeId) {
        this.player = Objects.requireNonNull(player, "player");
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.position = Objects.requireNonNull(position, "position").immutable();
        this.episodeId = Objects.requireNonNull(episodeId, "episodeId");
    }

    public ServerPlayer getPlayer() { return player; }
    public ResourceKey<Level> getDimension() { return dimension; }
    public BlockPos getPosition() { return position; }
    public String getEpisodeId() { return episodeId; }
}
