package com.bettercontent.betterworldmanagement;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.function.LongSupplier;

/** Pre-creation, chunk-free seed screening. Deadlines are measured with a monotonic clock. */
final class InitialSpawnSeedSearch {
    static final long PER_SEED_NANOS = Duration.ofSeconds(30).toNanos();
    static final long TOTAL_NANOS = Duration.ofMinutes(10).toNanos();

    interface Probe<T> {
        T find(long seed, long budgetNanos);
    }

    record Result<T>(long seed, T value, int attempts) {}

    private InitialSpawnSeedSearch() {}

    static <T> Result<T> run(long firstSeed, boolean randomSeed, long remainingNanos,
                             LongSupplier clock, LongSupplier nextSeed, Probe<T> probe) {
        if (remainingNanos <= 0) throw new IllegalStateException("Initial spawn preparation exceeded its 10-minute budget");
        long started = clock.getAsLong();
        Set<Long> attempted = new HashSet<>();
        long seed = firstSeed;
        for (int attempts = 1; ; attempts++) {
            if (Thread.currentThread().isInterrupted())
                throw new IllegalStateException("Initial spawn preparation was interrupted");
            long remaining = remainingNanos - (clock.getAsLong() - started);
            if (remaining <= 0) throw new IllegalStateException("Initial spawn preparation exceeded its 10-minute budget");
            attempted.add(seed);
            T sites = probe.find(seed, Math.min(PER_SEED_NANOS, remaining));
            if (sites != null) return new Result<>(seed, sites, attempts);
            if (!randomSeed) return new Result<>(seed, null, attempts);
            do {
                if (remainingNanos - (clock.getAsLong() - started) <= 0)
                    throw new IllegalStateException("Initial spawn preparation exceeded its 10-minute budget");
                seed = nextSeed.getAsLong();
            } while (attempted.contains(seed));
        }
    }
}
