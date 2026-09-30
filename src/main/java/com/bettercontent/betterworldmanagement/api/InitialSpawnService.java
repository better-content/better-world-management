package com.bettercontent.betterworldmanagement.api;

import com.bettercontent.betterworldmanagement.PrestigeCoordinator;
import net.minecraft.server.MinecraftServer;

/** Read-only onboarding bridge for the fresh world's shared-spawn preparation state. */
public final class InitialSpawnService {
    public enum Status { NOT_APPLICABLE, PENDING, RESOLVED, FALLBACK }

    private InitialSpawnService() {}

    public static Status status(MinecraftServer server) {
        return PrestigeCoordinator.initialSpawnStatus(server);
    }
}
