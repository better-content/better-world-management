package com.bettercontent.betterworldmanagement;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class PrestigeRejectionTest {
    @Test
    void malformedDurableLineageIsNotAnInputRejection() throws IOException {
        var directory = java.nio.file.Path.of("build", "rejection-regression", java.util.UUID.randomUUID().toString());
        java.nio.file.Files.createDirectories(directory); // Retained fixture, not auto-deleted.
        var path = directory.resolve("lineage.tsv");
        java.nio.file.Files.writeString(path, "malformed-lineage\n");
        var error = assertThrows(IllegalArgumentException.class, () -> PrestigeContracts.readLineage(path));
        assertFalse(error instanceof PrestigeService.RejectedArgument);
        List<Throwable> warnings = new ArrayList<>();
        assertTrue(PrestigeService.reportOperationalFailure(error, "Fixed operation failure", (message, cause) -> warnings.add(cause)));
        assertEquals(1, warnings.size());
        assertSame(error, warnings.get(0));
    }

    @Test
    void authoredRejectionRetainsBaseApiAndDoesNotBecomeOperationalWarning() {
        var rejection = new PrestigeService.Rejected("prestige selection is locked");
        assertInstanceOf(IllegalStateException.class, rejection);
        assertEquals("prestige selection is locked", rejection.getMessage());
        List<Throwable> warnings = new ArrayList<>();
        assertFalse(PrestigeService.reportOperationalFailure(rejection, "Fixed operation failure",
                (message, cause) -> warnings.add(cause)));
        var argument = new PrestigeService.RejectedArgument("invalid input");
        assertInstanceOf(IllegalArgumentException.class, argument);
        assertFalse(PrestigeService.reportOperationalFailure(argument,
                "Fixed operation failure", (message, cause) -> warnings.add(cause)));
        var original = new IllegalArgumentException("invalid authored input");
        var typed = assertThrows(PrestigeService.RejectedArgument.class,
                () -> PrestigeService.validateUserArgument(() -> { throw original; }));
        assertEquals(original.getMessage(), typed.getMessage());
        assertSame(original, typed.getCause());
        assertTrue(warnings.isEmpty());
    }

    @Test
    void unexpectedStateRuntimeAndIoRemainVisibleWithFixedMessageAndExactCause() {
        // Same text as an authored rejection must NOT reclassify a generic state failure.
        for (Exception error : List.of(new IllegalStateException("prestige selection is locked"),
                new IllegalArgumentException("unexpected persisted-state validation failure"),
                new NumberFormatException("unexpected persisted numeric field"), new NullPointerException("synthetic unexpected failure"), new IOException("synthetic IO failure"),
                new RuntimeException(new IllegalArgumentException("nested cause is not a validation rejection")))) {
            List<String> messages = new ArrayList<>();
            List<Throwable> warnings = new ArrayList<>();
            assertTrue(PrestigeService.reportOperationalFailure(error, "Fixed operation failure", (message, cause) -> {
                messages.add(message);
                warnings.add(cause);
            }));
            assertEquals(List.of("Fixed operation failure"), messages);
            assertEquals(1, warnings.size());
            assertSame(error, warnings.get(0));
        }
    }
}
