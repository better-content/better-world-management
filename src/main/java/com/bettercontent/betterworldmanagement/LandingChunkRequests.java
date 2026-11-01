package com.bettercontent.betterworldmanagement;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Submit chunk-future acquisition away from the server tick; never wait for generation there. */
final class LandingChunkRequests {
    private LandingChunkRequests() {}

    static <T> CompletableFuture<T> offServerThread(Supplier<CompletableFuture<T>> request) {
        Objects.requireNonNull(request);
        // ServerChunkCache.getChunkFuture waits for completion when invoked on its
        // own main thread, even though it returns a CompletableFuture. Its public
        // off-thread branch enqueues getChunkFutureMainThread without waiting.
        // Keep exactly one outstanding request per landing search, then poll the
        // composed result from subsequent ordinary ticks. No watchdog/health
        // deadline or village/surface predicate is changed here.
        return CompletableFuture.supplyAsync(request).thenCompose(future -> future);
    }
}
