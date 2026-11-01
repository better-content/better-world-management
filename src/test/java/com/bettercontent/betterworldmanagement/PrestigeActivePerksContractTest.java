package com.bettercontent.betterworldmanagement;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** Pure public-contract controls; not a Minecraft, reset, cold-boot or native acceptance test. */
class PrestigeActivePerksContractTest {
    private static final Path TEMP_ROOT = Path.of("/home/dev/.tmp");
    private static final List<String> KEYS = List.of("lineage", "generation", "perks");
    private static final String MAGIC = "BC_PRESTIGE_PERKS_V2";
    private static final PrestigeContracts.Lineage LINEAGE = new PrestigeContracts.Lineage("lineage-neutral-control", 23, 23);
    private Path temp;

    @BeforeEach void createExplicitLabTemporaryDirectory() throws IOException {
        if (!Files.isDirectory(TEMP_ROOT)) throw new IOException("explicit workspace test temporary root is missing");
        // Retain each isolated fixture for diagnosis; never use the system /tmp or a runtime/world.
        temp = Files.createTempDirectory(TEMP_ROOT, "neutral-active-codec-junit-").toAbsolutePath();
    }

    @Test void exactNeutralBytesAndTypedPublicReadbacksBindActualPublicLineage() throws Exception {
        var lineage = new PrestigeContracts.Lineage(PrestigeContracts.newLineageId(), 23, 23);
        Path lineageFile = temp.resolve("lineage.tsv");
        PrestigeContracts.writeLineage(lineageFile, lineage);
        var actualLineage = PrestigeContracts.readLineage(lineageFile);
        assertEquals(lineage, actualLineage);
        assertEquals(MAGIC, PrestigeActivePerksContract.ACTIVE_MAGIC);
        Path first = temp.resolve("first-neutral.tsv");
        Path second = temp.resolve("second-neutral.tsv");
        PrestigeActivePerksContract.writeNeutral(first, actualLineage);
        PrestigeActivePerksContract.writeNeutral(second, actualLineage);
        assertArrayEquals(canonical(actualLineage).getBytes(StandardCharsets.UTF_8), Files.readAllBytes(first));
        assertArrayEquals(Files.readAllBytes(first), Files.readAllBytes(second));
        assertEquals(KEYS, new ArrayList<>(PrestigeActivePerksContract.readFields(first).keySet()));
        assertEquals(new PrestigeActivePerksContract.Neutral(lineage.lineageId(), 23),
                PrestigeActivePerksContract.readNeutral(first, actualLineage));
        assertEquals(PrestigeActivePerksContract.readNeutral(first, actualLineage),
                PrestigeActivePerksContract.readNeutral(second, actualLineage));
        assertEquals(1, ((Number) Files.getAttribute(first, "unix:nlink")).intValue());
        assertEquals(1, ((Number) Files.getAttribute(second, "unix:nlink")).intValue());
        assertNoPartial();
    }

    @Test void constructedLineageRecordsAreExplicitlyValidatedByBothNeutralApis() throws Exception {
        Path existing = write("existing.tsv", canonical(LINEAGE));
        int index = 0;
        for (var invalid : List.of(new PrestigeContracts.Lineage("INVALID", 23, 23),
                new PrestigeContracts.Lineage("-invalid", 23, 23), new PrestigeContracts.Lineage(null, 23, 23),
                new PrestigeContracts.Lineage(LINEAGE.lineageId(), -1, -1),
                new PrestigeContracts.Lineage(LINEAGE.lineageId(), 23, -1),
                new PrestigeContracts.Lineage(LINEAGE.lineageId(), 23, 22),
                new PrestigeContracts.Lineage(LINEAGE.lineageId(), 0, 23))) {
            Path absent = temp.resolve("invalid-" + index++);
            assertThrows(IllegalArgumentException.class, () -> PrestigeActivePerksContract.writeNeutral(absent, invalid));
            assertFalse(Files.exists(absent));
            assertThrows(IllegalArgumentException.class, () -> PrestigeActivePerksContract.readNeutral(existing, invalid));
        }
        assertEquals(canonical(LINEAGE), Files.readString(existing));
        assertNoPartial();
    }

    @Test void currentGenerationIsWrittenWithoutIncrementEvenAtLongMax() throws Exception {
        for (long generation : new long[]{0, 23, Long.MAX_VALUE}) {
            var lineage = new PrestigeContracts.Lineage(LINEAGE.lineageId(), generation, generation);
            Path file = temp.resolve("generation-" + generation);
            PrestigeActivePerksContract.writeNeutral(file, lineage);
            assertEquals(canonical(lineage), Files.readString(file));
            assertEquals(Long.toString(generation), PrestigeActivePerksContract.readFields(file).get("generation"));
            assertEquals(new PrestigeActivePerksContract.Neutral(lineage.lineageId(), generation),
                    PrestigeActivePerksContract.readNeutral(file, lineage));
        }
    }

    @Test void existingRegularOrNeutralTargetIsNeverOverwritten() throws Exception {
        Path sentinel = write("sentinel.tsv", "PREEXISTING STATE MUST NOT CHANGE\n");
        byte[] before = Files.readAllBytes(sentinel);
        assertThrows(FileAlreadyExistsException.class, () -> PrestigeActivePerksContract.writeNeutral(sentinel, LINEAGE));
        assertArrayEquals(before, Files.readAllBytes(sentinel));
        Path neutral = temp.resolve("neutral.tsv");
        PrestigeActivePerksContract.writeNeutral(neutral, LINEAGE);
        byte[] neutralBefore = Files.readAllBytes(neutral);
        assertThrows(FileAlreadyExistsException.class, () -> PrestigeActivePerksContract.writeNeutral(neutral, LINEAGE));
        assertArrayEquals(neutralBefore, Files.readAllBytes(neutral));
        assertNoPartial();
    }

    @Test void symlinkDanglingDirectoryAndSymlinkParentTargetsAreRefused() throws Exception {
        Path sentinel = write("referent", "UNCHANGED\n");
        Path link = temp.resolve("link"); Files.createSymbolicLink(link, sentinel);
        assertThrows(IOException.class, () -> PrestigeActivePerksContract.writeNeutral(link, LINEAGE));
        assertTrue(Files.isSymbolicLink(link)); assertEquals("UNCHANGED\n", Files.readString(sentinel));
        Path absent = temp.resolve("absent");
        Path dangling = temp.resolve("dangling"); Files.createSymbolicLink(dangling, absent);
        assertThrows(IOException.class, () -> PrestigeActivePerksContract.writeNeutral(dangling, LINEAGE));
        assertTrue(Files.isSymbolicLink(dangling)); assertFalse(Files.exists(absent));
        Path directory = temp.resolve("directory"); Files.createDirectory(directory);
        assertThrows(FileAlreadyExistsException.class, () -> PrestigeActivePerksContract.writeNeutral(directory, LINEAGE));
        Path parentLink = temp.resolve("parent-link"); Files.createSymbolicLink(parentLink, directory);
        assertThrows(IOException.class, () -> PrestigeActivePerksContract.writeNeutral(parentLink.resolve("contract"), LINEAGE));
        assertFalse(Files.exists(directory.resolve("contract")));
        assertNoPartial();
    }

    @Test void writerDoesNotCreateParentsOrAcceptTraversalSyntax() throws Exception {
        assertThrows(IOException.class,
                () -> PrestigeActivePerksContract.writeNeutral(temp.resolve("missing/contract"), LINEAGE));
        assertFalse(Files.exists(temp.resolve("missing")));
        assertThrows(IllegalArgumentException.class,
                () -> PrestigeActivePerksContract.writeNeutral(temp.resolve("child/../contract"), LINEAGE));
        assertFalse(Files.exists(temp.resolve("contract")));
        assertNoPartial();
    }

    @Test void neutralReaderRejectsWrongExpectedIdentityOrCurrentGeneration() throws Exception {
        Path file = write("neutral.tsv", canonical(LINEAGE));
        assertThrows(IllegalArgumentException.class, () -> PrestigeActivePerksContract.readNeutral(file,
                new PrestigeContracts.Lineage("lineage-other", 23, 23)));
        assertThrows(IllegalArgumentException.class, () -> PrestigeActivePerksContract.readNeutral(file,
                new PrestigeContracts.Lineage(LINEAGE.lineageId(), 24, 24)));
        assertEquals(canonical(LINEAGE), Files.readString(file));
    }

    @Test void neutralReaderRejectsPaidOrForgedStateWithoutRepairingIt() throws Exception {
        String valid = canonical(LINEAGE);
        int index = 0;
        for (String source : List.of(valid.replace("perks\t-", "perks\tbiome_selection"),
                valid.replace("generation\t23", "generation\t24"),
                valid.replace("generation\t23", "generation\t023"),
                valid.replace("generation\t23", "generation\t+23"),
                valid.replace("generation\t23", "generation\t-1"),
                valid.replace(LINEAGE.lineageId(), "lineage-other"), valid + "extra\tfield\n")) {
            Path file = write("forged-" + index++, source);
            byte[] before = Files.readAllBytes(file);
            assertThrows(IllegalArgumentException.class, () -> PrestigeActivePerksContract.readNeutral(file, LINEAGE));
            assertArrayEquals(before, Files.readAllBytes(file));
        }
    }

    @Test void neutralReaderNeverInventsMissingStateEvenAtGenerationZero() {
        Path missing = temp.resolve("missing.tsv");
        for (long generation : new long[]{0, 23}) {
            var lineage = new PrestigeContracts.Lineage(LINEAGE.lineageId(), generation, generation);
            assertThrows(NoSuchFileException.class, () -> PrestigeActivePerksContract.readNeutral(missing, lineage));
            assertFalse(Files.exists(missing));
        }
    }

    @Test void closedFrameRejectsMalformedAndDuplicateFields() throws Exception {
        String valid = canonical(LINEAGE);
        int index = 0;
        for (String source : List.of("", MAGIC + "\n", valid + "\n", valid + "extra\tfield\n",
                valid.replace(MAGIC, "BC_PRESTIGE_PERKS_V1"), "\ufeff" + valid,
                valid.replace("lineage\t", "unknown\t"),
                valid.replace("generation\t23\nperks\t-", "perks\t-\ngeneration\t23"),
                valid.replace("perks\t-", "generation\t23"), valid.replace("perks\t-", "perks\t"),
                valid.replace("perks\t-", "perks\t-\textra"), valid.replace("perks\t-", "perks -"),
                valid.replace("lineage\t" + LINEAGE.lineageId(), "lineage\t"), valid.replace("generation\t23\n", ""))) {
            Path file = write("malformed-" + index++, source);
            assertThrows(IllegalArgumentException.class, () -> PrestigeActivePerksContract.readFields(file));
        }
    }

    @Test void exactOldFramingSemanticsIncludingWhitespaceLineEndingsAndIoFailures() throws Exception {
        String valid = canonical(LINEAGE);
        int index = 0;
        for (String source : List.of(valid, valid.stripTrailing(), valid.replace("\n", "\r\n"), valid.replace("\n", "\r"),
                valid.replace("perks\t-", "perks\tUNKNOWN_POLICY_ID"),
                valid.replace("generation\t23", "generation\t023"),
                valid.replace("generation\t23", "generation\t+23"),
                valid.replace("generation\t23", "generation\t-1"),
                valid.replace("generation\t23", "generation\t "), "", MAGIC + "\n", valid + "\n",
                valid.replace("perks\t-", "generation\t23"), valid.replace("perks\t-", "perks\t"))) {
            Path file = write("equivalence-" + index++, source);
            byte[] before = Files.readAllBytes(file);
            assertEquivalent(file);
            assertArrayEquals(before, Files.readAllBytes(file));
        }
        assertEquivalent(temp.resolve("missing-input"));
        Path malformedUtf8 = temp.resolve("malformed-utf8");
        Files.write(malformedUtf8, new byte[]{(byte) 0xc3, (byte) 0x28}, StandardOpenOption.CREATE_NEW);
        assertEquivalent(malformedUtf8);
    }

    @Test void genericFramingDoesNotPretendToValidatePaidPolicy() throws Exception {
        Path file = write("unknown-paid-policy.tsv", canonical(LINEAGE).replace("perks\t-", "perks\tUNKNOWN_POLICY_ID"));
        assertEquals("UNKNOWN_POLICY_ID", PrestigeActivePerksContract.readFields(file).get("perks"));
        assertThrows(IllegalArgumentException.class, () -> PrestigeActivePerksContract.readNeutral(file, LINEAGE));
    }

    private Path write(String name, String text) throws IOException {
        Path path = temp.resolve(name);
        return Files.writeString(path, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    }
    private static String canonical(PrestigeContracts.Lineage lineage) {
        return MAGIC + "\nlineage\t" + lineage.lineageId() + "\ngeneration\t" + lineage.generation() + "\nperks\t-\n";
    }
    private void assertNoPartial() throws IOException {
        try (var paths = Files.list(temp)) {
            assertFalse(paths.anyMatch(p -> p.getFileName().toString().endsWith(".partial")));
        }
    }
    private interface Reader { Map<String, String> read() throws Exception; }
    private record ReadResult(Map<String, String> fields, List<String> order, String failure, String message) {}
    private static ReadResult capture(Reader reader) {
        try {
            var result = reader.read();
            return new ReadResult(result, new ArrayList<>(result.keySet()), null, null);
        } catch (Exception failure) {
            return new ReadResult(null, null, failure.getClass().getName(), failure.getMessage());
        }
    }
    private static void assertEquivalent(Path path) {
        assertEquals(capture(() -> legacyReadFields(path, MAGIC, KEYS)),
                capture(() -> PrestigeActivePerksContract.readFields(path)));
    }

    // Exact original PrestigePerks framing body; no production private calls/reflection or MC stubs.
    private static Map<String, String> legacyReadFields(Path path, String magic, List<String> keys) throws IOException {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        if (lines.size() != keys.size() + 1 || !lines.get(0).equals(magic)) throw new IllegalArgumentException("invalid prestige perk contract");
        Map<String, String> fields = new LinkedHashMap<>();
        for (int index = 0; index < keys.size(); index++) {
            String[] row = lines.get(index + 1).split("\\t", -1);
            if (row.length != 2 || !row[0].equals(keys.get(index)) || row[1].isEmpty()) throw new IllegalArgumentException("invalid prestige perk fields");
            fields.put(row[0], row[1]);
        }
        return fields;
    }
}
