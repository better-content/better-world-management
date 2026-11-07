package com.bettercontent.betterworldmanagement;

import com.mojang.datafixers.util.Either;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

final class LandingChunkRequestsTest {
    @Test void acquisitionIsDeferredOffCallerAndItsUncompletedChunkIsNotWaitedFor() throws Exception {
        Thread caller = Thread.currentThread();
        var entered = new CountDownLatch(1);
        var chunk = new CompletableFuture<String>();
        CompletableFuture<String> result = LandingChunkRequests.offServerThread(() -> {
            assertNotSame(caller, Thread.currentThread(), "server tick must never request a remote chunk synchronously");
            entered.countDown();
            return chunk;
        });
        assertTrue(entered.await(3, TimeUnit.SECONDS));
        assertFalse(result.isDone(), "a pending remote chunk must not block the caller");
        chunk.complete("full native chunk");
        assertEquals("full native chunk", result.get(3, TimeUnit.SECONDS));
    }

    @Test void unloadedChunkIsNeverAcceptedAsGeneratedTerrain() {
        assertNull(LandingChunkRequests.generatedOrNull(Either.<String, String>right("Unloaded")));
        assertEquals("generated", LandingChunkRequests.generatedOrNull(Either.<String, String>left("generated")));
    }

    @Test void requestFailureIsNotTurnedIntoAnEligibleLanding() {
        CompletableFuture<String> result = LandingChunkRequests.offServerThread(() -> {
            throw new IllegalStateException("remote chunk request refused");
        });
        ExecutionException error = assertThrows(ExecutionException.class, () -> result.get(3, TimeUnit.SECONDS));
        assertInstanceOf(IllegalStateException.class, error.getCause());
    }

    @Test void coordinatorNeverCallsGetChunkFutureOnTheTickThread() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/bettercontent/betterworldmanagement/PrestigeCoordinator.java"));
        assertTrue(source.contains("LandingChunkRequests.offServerThread(() ->\n"
                + "                    level.getChunkSource().getChunkFuture("));
        assertEquals(1, source.split("getChunkFuture\\(", -1).length - 1);
        assertTrue(source.contains("if (surfaceProbe != null || pendingChunk != null) return TickResult.pending();"));
    }
}
