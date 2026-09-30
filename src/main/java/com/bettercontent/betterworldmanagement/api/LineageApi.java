package com.bettercontent.betterworldmanagement.api;

import com.bettercontent.betterworldmanagement.PrestigeService;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.util.Objects;

/** Stable read-only view of the lineage identity shared by Better Content systems. */
public final class LineageApi {
    private LineageApi() {}

    public static Snapshot snapshot(MinecraftServer server) throws IOException {
        Objects.requireNonNull(server, "server");
        var lineage = PrestigeService.lineage(server);
        return new Snapshot(lineage.lineageId(), lineage.totalPrestiges(), lineage.generation());
    }

    public record Snapshot(String lineageId, long totalPrestiges, long generation) {
        public Snapshot { Objects.requireNonNull(lineageId, "lineageId"); }
    }
}
