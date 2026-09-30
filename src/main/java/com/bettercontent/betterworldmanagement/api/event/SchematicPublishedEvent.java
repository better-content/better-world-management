package com.bettercontent.betterworldmanagement.api.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;

import java.util.Objects;

/** Posted after a schematic has been durably published into the lineage library. */
public final class SchematicPublishedEvent extends Event {
    private final ServerPlayer player;
    private final String entryId;
    private final String sha256;
    private final String originalName;
    private final long generation;
    private final String episodeId;

    public SchematicPublishedEvent(ServerPlayer player, String entryId, String sha256,
                                   String originalName, long generation, String episodeId) {
        this.player = Objects.requireNonNull(player, "player");
        this.entryId = Objects.requireNonNull(entryId, "entryId");
        this.sha256 = Objects.requireNonNull(sha256, "sha256");
        this.originalName = Objects.requireNonNull(originalName, "originalName");
        this.generation = generation;
        this.episodeId = Objects.requireNonNull(episodeId, "episodeId");
    }

    public ServerPlayer getPlayer() { return player; }
    public String getEntryId() { return entryId; }
    public String getSha256() { return sha256; }
    public String getOriginalName() { return originalName; }
    public long getGeneration() { return generation; }
    public String getEpisodeId() { return episodeId; }
}
