package com.bettercontent.betterworldmanagement;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Pure fixture contract tests; not a source-world restart or a native paid-perk pass. */
class PrestigeNeutralResetContractTest {
    private record Inputs(Path dir, Path lineage, Path reset, Path active, Path target) {}

    private Inputs inputs(String id, long generation) throws Exception {
        Path dir = Files.createTempDirectory(Path.of("/home/dev/.tmp"), "axiom-neutral-reset-" + id + "-");
        Path lineage = dir.resolve("lineage-v5.tsv"), reset = dir.resolve("reset-request-v5.tsv");
        Path active = dir.resolve("perks-v2.tsv"), target = dir.resolve("reset-perks-v2.tsv");
        var record = new PrestigeContracts.Lineage("lineage-fixture-" + id, generation, generation);
        PrestigeContracts.writeLineage(lineage, record);
        PrestigeContracts.writeReset(reset, new PrestigeContracts.Reset(record.lineageId(), generation,
                "transaction-fixture-" + id, "world", 0, List.of("minecraft:plains")));
        PrestigeActivePerksContract.writeNeutral(active, record);
        return new Inputs(dir, lineage, reset, active, target);
    }
    private PrestigeNeutralResetContract.Neutral write(Inputs in) throws Exception {
        return PrestigeNeutralResetContract.writeNeutral(in.target(), in.lineage(), in.reset(), in.active());
    }
    private PrestigeNeutralResetContract.Neutral read(Inputs in) throws Exception {
        return PrestigeNeutralResetContract.readNeutral(in.target(), in.lineage(), in.reset(), in.active());
    }

    @Test void exactOrderedEmptyPaidFrameBindsThreePublicContractsAndNeverInventsHistory() throws Exception {
        Inputs in = inputs("valid", 23);
        var expected = new PrestigeNeutralResetContract.Neutral("lineage-fixture-valid", 23, 24,
                "transaction-fixture-valid", List.of("minecraft:plains"));
        assertEquals(expected, write(in));
        assertEquals(expected, read(in));
        assertEquals("BC_PRESTIGE_RESET_PERKS_V2\nlineage\tlineage-fixture-valid\nbase_generation\t23\n"
                + "target_generation\t24\ntransaction\ttransaction-fixture-valid\nperks\t-\n"
                + "biome_1\tminecraft:plains\nbiome_2\t-\nbiome_3\t-\n", Files.readString(in.target()));
        assertEquals(1, ((Number) Files.getAttribute(in.target(), "unix:nlink")).intValue());
        assertEquals(List.of(), Files.list(in.dir()).filter(p -> p.getFileName().toString().endsWith(".partial")).toList());
    }

    @Test void rejectsWrongSyntheticGenerationBeforePublication() throws Exception {
        for (long generation : new long[]{0, 22, 24}) {
            Inputs in = inputs("generation" + generation, generation);
            assertThrows(IllegalArgumentException.class, () -> write(in));
            assertFalse(Files.exists(in.target()));
        }
    }

    @Test void rejectsPaidActiveWrongIdentityOrAlteredSourceWithoutPublishing() throws Exception {
        Inputs paid = inputs("paid", 23);
        Files.writeString(paid.active(), Files.readString(paid.active()).replace("perks\t-", "perks\tbiome_selection"));
        assertThrows(IllegalArgumentException.class, () -> write(paid));
        assertFalse(Files.exists(paid.target()));
        Inputs identity = inputs("identity", 23);
        Files.writeString(identity.reset(), Files.readString(identity.reset()).replace("lineage-fixture-identity", "lineage-other"));
        assertThrows(IllegalArgumentException.class, () -> write(identity));
        assertFalse(Files.exists(identity.target()));
        Inputs missing = inputs("missing", 23);
        Files.delete(missing.active());
        assertThrows(java.io.IOException.class, () -> write(missing));
        assertFalse(Files.exists(missing.target()));
    }

    @Test void refusesOverwriteCorruptionAndForeignTransaction() throws Exception {
        Inputs in = inputs("overwrite", 23);
        write(in);
        byte[] first = Files.readAllBytes(in.target());
        assertThrows(FileAlreadyExistsException.class, () -> write(in));
        assertArrayEquals(first, Files.readAllBytes(in.target()));
        Files.writeString(in.target(), Files.readString(in.target()).replace("perks\t-", "perks\tbiome_selection"));
        assertThrows(IllegalArgumentException.class, () -> read(in));
        Files.write(in.target(), first);
        Files.writeString(in.reset(), Files.readString(in.reset()).replace("transaction-fixture-overwrite", "transaction-foreign"), StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class, () -> read(in));
    }

    @Test void symlinkTargetAndParentAreNeverPublished() throws Exception {
        Inputs in = inputs("symlink", 23);
        Path sentinel = in.dir().resolve("sentinel"); Files.writeString(sentinel, "untouched");
        Files.createSymbolicLink(in.target(), sentinel);
        assertThrows(java.io.IOException.class, () -> write(in));
        assertEquals("untouched", Files.readString(sentinel));
        Files.delete(in.target());
        Path link = in.dir().resolve("linked"); Files.createSymbolicLink(link, in.dir());
        assertThrows(java.io.IOException.class, () -> PrestigeNeutralResetContract.writeNeutral(link.resolve("new.tsv"),
                in.lineage(), in.reset(), in.active()));
        assertFalse(Files.exists(in.dir().resolve("new.tsv")));
    }
}
