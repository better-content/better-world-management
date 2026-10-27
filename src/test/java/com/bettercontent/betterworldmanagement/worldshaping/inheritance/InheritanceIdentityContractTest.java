package com.bettercontent.betterworldmanagement.worldshaping.inheritance;

import java.util.HashSet;
import java.util.UUID;

/** Standalone Java-17 contract test; intentionally no Minecraft bootstrap or JUnit dependency. */
public final class InheritanceIdentityContractTest {
    public static void main(String[] args) {
        UUID expected = InheritanceIdentity.restoredUuid("transaction-a", "ark", "first");
        check(expected.equals(InheritanceIdentity.restoredUuid("transaction-a", "ark", "first")), "deterministic replay");
        check(!expected.equals(InheritanceIdentity.restoredUuid("transaction-a", "ark", "second")), "slot separation");
        check(!expected.equals(InheritanceIdentity.restoredUuid("transaction-b", "ark", "first")), "transaction separation");
        check(!expected.equals(InheritanceIdentity.restoredUuid("transaction-a", "familiars_name", "wolf")), "axiom separation");
        check(expected.version() == 3 && expected.variant() == 2, "standard name UUID");
        HashSet<UUID> unique = new HashSet<>();
        for (int i = 0; i < 1000; i++) for (String slot : new String[]{"first", "second"})
            check(unique.add(InheritanceIdentity.restoredUuid("fixture-" + i, "ark", slot)), "fixture collision");
        rejects(() -> InheritanceIdentity.restoredUuid(null, "ark", "first"));
        rejects(() -> InheritanceIdentity.restoredUuid(" ", "ark", "first"));
        rejects(() -> InheritanceIdentity.restoredUuid("x".repeat(257), "ark", "first"));
        rejects(() -> InheritanceIdentity.restoredUuid("x\0ark", "ark", "first"));
        rejects(() -> InheritanceIdentity.restoredUuid("x", "patient_hive", "first"));
        rejects(() -> InheritanceIdentity.restoredUuid("x", "ark", "wolf"));
        rejects(() -> InheritanceIdentity.restoredUuid("x", "familiars_name", "first"));
        System.out.println("InheritanceIdentityContractTest PASS: deterministic/replay, slot/transaction/axiom isolation, 2000 unique fixtures, malformed/unsupported input rejection");
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void rejects(Runnable operation) {
        try { operation.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Expected rejection");
    }
}
