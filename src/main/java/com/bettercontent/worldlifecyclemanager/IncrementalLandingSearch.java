package com.bettercontent.worldlifecyclemanager;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/** Deterministic, budgeted search cursor used by successor landing resolution. */
final class IncrementalLandingSearch {
    record Probe(String preference, int x, int z) {}
    record Step(Probe candidate, boolean exhausted, boolean cancelled) {
        static Step pending() { return new Step(null, false, false); }
        static Step found(Probe candidate) { return new Step(candidate, false, false); }
        static Step exhaustedResult() { return new Step(null, true, false); }
        static Step cancelledResult() { return new Step(null, false, true); }
    }

    private final List<String> preferences;
    private final int originX;
    private final int originZ;
    private final int stepBlocks;
    private final int maxRing;
    private final int maxTicks;
    private int preferenceIndex;
    private int ring;
    private int ringOffset;
    private int elapsedTicks;
    private boolean cancelled;

    IncrementalLandingSearch(List<String> preferences, int originX, int originZ,
                             int stepBlocks, int maxRadiusBlocks, int maxTicks) {
        this.preferences = List.copyOf(preferences);
        this.originX = originX;
        this.originZ = originZ;
        if (stepBlocks <= 0 || maxRadiusBlocks < 0 || maxRadiusBlocks % stepBlocks != 0 || maxTicks <= 0) {
            throw new IllegalArgumentException("landing search bounds must be non-negative multiples");
        }
        this.stepBlocks = stepBlocks;
        this.maxRing = maxRadiusBlocks / stepBlocks;
        this.maxTicks = maxTicks;
    }

    boolean deadlineReached() {
        if (cancelled) return true;
        if (++elapsedTicks < maxTicks) return false;
        cancel();
        return true;
    }

    Step advance(int probeBudget, Predicate<Probe> matches) {
        if (probeBudget <= 0) throw new IllegalArgumentException("probe budget must be positive");
        Objects.requireNonNull(matches, "matches");
        if (cancelled) return Step.cancelledResult();
        for (int probes = 0; probes < probeBudget && preferenceIndex < preferences.size(); probes++) {
            Probe probe = currentProbe();
            moveNext();
            if (matches.test(probe)) return Step.found(probe);
        }
        return preferenceIndex >= preferences.size() ? Step.exhaustedResult() : Step.pending();
    }

    void cancel() { cancelled = true; }

    private Probe currentProbe() {
        int gridX;
        int gridZ;
        if (ring == 0) {
            gridX = 0;
            gridZ = 0;
        } else {
            int cursor = ringOffset;
            int edge = ring * 2;
            if (cursor < edge + 1) {
                gridX = -ring + cursor;
                gridZ = -ring;
            } else if ((cursor -= edge + 1) < edge) {
                gridX = ring;
                gridZ = -ring + 1 + cursor;
            } else if ((cursor -= edge) < edge) {
                gridX = ring - 1 - cursor;
                gridZ = ring;
            } else {
                cursor -= edge;
                gridX = -ring;
                gridZ = ring - 1 - cursor;
            }
        }
        return new Probe(preferences.get(preferenceIndex), originX + gridX * stepBlocks,
                originZ + gridZ * stepBlocks);
    }

    private void moveNext() {
        int positionsInRing = ring == 0 ? 1 : ring * 8;
        if (++ringOffset < positionsInRing) return;
        ringOffset = 0;
        if (++ring <= maxRing) return;
        ring = 0;
        preferenceIndex++;
    }
}
