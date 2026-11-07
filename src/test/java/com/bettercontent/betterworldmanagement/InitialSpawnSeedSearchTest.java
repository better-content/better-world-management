package com.bettercontent.betterworldmanagement;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

final class InitialSpawnSeedSearchTest {
    @Test void randomRetriesDistinctSeedsAndPreservesFirstSeed() {
        AtomicLong clock = new AtomicLong();
        AtomicLong next = new AtomicLong(11);
        List<Long> tried = new ArrayList<>();
        var result = InitialSpawnSeedSearch.run(11, true, Duration.ofMinutes(10).toNanos(),
                clock::get, next::getAndIncrement, (seed, budget) -> {
                    tried.add(seed);
                    clock.addAndGet(Duration.ofSeconds(25).toNanos());
                    assertEquals(InitialSpawnSeedSearch.PER_SEED_NANOS, budget);
                    return seed == 13 ? "safe" : null;
                });
        assertEquals(List.of(11L, 12L, 13L), tried);
        assertEquals(13, result.seed());
        assertEquals("safe", result.value());
        assertEquals(3, result.attempts());
    }

    @Test void fixedAndUnknownProvenanceNeverRotateSeeds() {
        for (String provenance : List.of("explicit", "unknown")) {
            AtomicLong nextCalls = new AtomicLong();
            var result = InitialSpawnSeedSearch.run(453, provenance.equals("random"), InitialSpawnSeedSearch.TOTAL_NANOS,
                    System::nanoTime, () -> { nextCalls.incrementAndGet(); return 678; },
                    (seed, budget) -> null);
            assertEquals(453, result.seed());
            assertNull(result.value()); // Vanilla fallback is a separate explicit-only decision.
            assertEquals(1, result.attempts());
            assertEquals(0, nextCalls.get());
        }
    }

    @Test void eachSeedAndOverallBudgetAreBoundedByMonotonicElapsedTime() {
        AtomicLong clock = new AtomicLong(1_000);
        List<Long> budgets = new ArrayList<>();
        assertThrows(IllegalStateException.class, () -> InitialSpawnSeedSearch.run(
                1, true, Duration.ofSeconds(95).toNanos(), clock::get,
                new AtomicLong(2)::getAndIncrement, (seed, budget) -> {
                    budgets.add(budget);
                    clock.addAndGet(Duration.ofSeconds(30).toNanos());
                    return null;
                }));
        assertEquals(List.of(Duration.ofSeconds(30).toNanos(), Duration.ofSeconds(30).toNanos(),
                Duration.ofSeconds(30).toNanos(), Duration.ofSeconds(5).toNanos()), budgets);
    }

    @Test void unsuccessfulRandomSeedsUseTheEntireTenMinuteWindow() {
        AtomicLong clock = new AtomicLong();
        AtomicLong nextSeed = new AtomicLong(2);
        List<Long> budgets = new ArrayList<>();
        assertThrows(IllegalStateException.class, () -> InitialSpawnSeedSearch.run(
                1, true, InitialSpawnSeedSearch.TOTAL_NANOS, clock::get,
                nextSeed::getAndIncrement, (seed, budget) -> {
                    budgets.add(budget);
                    clock.addAndGet(Duration.ofSeconds(30).toNanos());
                    return null;
                }));
        assertEquals(20, budgets.size(), "twenty distinct 30-second seeds exhaust ten minutes");
        assertTrue(budgets.stream().allMatch(budget -> budget == InitialSpawnSeedSearch.PER_SEED_NANOS));
        assertEquals(InitialSpawnSeedSearch.TOTAL_NANOS, clock.get());
    }

    @Test void zeroRemainingTimeDoesNotProbeOrAllocateAnotherWorld() {
        assertThrows(IllegalStateException.class, () -> InitialSpawnSeedSearch.run(
                1, true, 0, System::nanoTime, () -> 2L,
                (seed, budget) -> { fail("probe after deadline"); return null; }));
    }
}
