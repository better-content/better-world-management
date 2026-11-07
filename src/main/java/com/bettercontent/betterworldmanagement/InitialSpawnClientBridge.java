package com.bettercontent.betterworldmanagement;

import net.minecraft.server.MinecraftServer;

/** Common-side boundary; the dedicated server must not resolve client-only creation classes. */
final class InitialSpawnClientBridge {
    record Intent(String target, String id) {}

    interface Provider {
        Intent intent(MinecraftServer server);
        boolean isRandom(MinecraftServer server);
        int attemptNumber(MinecraftServer server);
        void startSearch(MinecraftServer server);
        long remainingMillis(MinecraftServer server);
        void recordSeed(MinecraftServer server, long seed);
    }

    private static volatile Provider provider;

    private InitialSpawnClientBridge() {}

    static void install(Provider client) {
        if (provider != null) throw new IllegalStateException("Initial-spawn client provider was already installed");
        provider = client;
    }

    static Intent intent(MinecraftServer server) {
        Provider current = provider;
        return current == null ? null : current.intent(server);
    }

    static boolean isRandom(MinecraftServer server) {
        Provider current = provider;
        return current != null && current.isRandom(server);
    }

    static int attemptNumber(MinecraftServer server) {
        Provider current = provider;
        return current == null ? 1 : current.attemptNumber(server);
    }

    static void startSearch(MinecraftServer server) {
        Provider current = provider;
        if (current != null) current.startSearch(server);
    }

    static long remainingMillis(MinecraftServer server) {
        Provider current = provider;
        return current == null ? 0 : current.remainingMillis(server);
    }

    static void recordSeed(MinecraftServer server, long seed) {
        Provider current = provider;
        if (current != null) current.recordSeed(server, seed);
    }
}
