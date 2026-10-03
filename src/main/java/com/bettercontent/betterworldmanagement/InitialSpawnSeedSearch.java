package com.bettercontent.betterworldmanagement;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.function.LongSupplier;

/** Retries distinct seeds while keeping each outward search on a monotonic wall-clock budget. */
final class InitialSpawnSeedSearch {
    static final long PER_SEED_NANOS = Duration.ofSeconds(60).toNanos();

    interface Probe<T> {
        T find(long seed, long deadlineNanos);
    }

    record Result<T>(long seed, T value, int attempts) {}

    private InitialSpawnSeedSearch() {}

    static <T> Result<T> run(long firstSeed, LongSupplier clock, LongSupplier nextSeed, Probe<T> probe) {
        Set<Long> attempted = new HashSet<>();
        long seed = firstSeed;
        int attempts = 0;
        while (true) {
            if (Thread.currentThread().isInterrupted()) throw new IllegalStateException("Initial spawn preparation cancelled");
            attempted.add(seed);
            attempts++;
            long deadline = clock.getAsLong() + PER_SEED_NANOS;
            T value = probe.find(seed, deadline);
            if (value != null) return new Result<>(seed, value, attempts);
            do { seed = nextSeed.getAsLong(); } while (attempted.contains(seed));
        }
    }
}
