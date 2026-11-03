package com.bettercontent.betterworldmanagement.diagnostics;

/** Pure observation of the owning feature's existing outcome, with no native reads. */
public final class FeatureTelemetry {
    private FeatureTelemetry() {}
    public static void health(PlaytestLog log, String transaction, boolean fresh, boolean found) {
        log.event("prestige", "prestige.health", "server", "published", found ? "exact_landing" : "no_exact_landing", () -> PlaytestLog.fields("transaction", transaction, "fresh", fresh, "found_exact", found));
    }
}
