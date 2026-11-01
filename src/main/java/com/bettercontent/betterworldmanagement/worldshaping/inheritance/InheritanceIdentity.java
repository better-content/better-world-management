package com.bettercontent.betterworldmanagement.worldshaping.inheritance;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Pure, versioned identity derivation shared with recovery fixtures. */
public final class InheritanceIdentity {
    private InheritanceIdentity() {}
    public static UUID restoredUuid(String transaction, String id, String slot) {
        if (transaction == null || transaction.isBlank() || transaction.length() > 256 || transaction.indexOf('\0') >= 0)
            throw new IllegalArgumentException("Invalid inheritance transaction");
        if (!("ark".equals(id) && ("first".equals(slot) || "second".equals(slot)))
                && !("familiars_name".equals(id) && "wolf".equals(slot)))
            throw new IllegalArgumentException("Invalid inheritance entity slot");
        return UUID.nameUUIDFromBytes(("world_axioms_inheritance_v1\u0000" + transaction + "\u0000" + id + "\u0000" + slot).getBytes(StandardCharsets.UTF_8));
    }
}
