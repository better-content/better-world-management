package com.bettercontent.betterworldmanagement;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Shared legacy active-perk framing and neutral bootstrap contracts.
 * Framing is not paid-policy validation: the production reader retains its
 * missing-state, identity, parsePerks and validatePaidSet guards around this call.
 */
public final class PrestigeActivePerksContract {
    public static final String ACTIVE_MAGIC = "BC_PRESTIGE_PERKS_V2";
    private static final List<String> KEYS = List.of("lineage", "generation", "perks");

    private PrestigeActivePerksContract() {}

    /** Exact existing PrestigePerks.readFields semantics, with fixed active schema. */
    public static Map<String, String> readFields(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        if (lines.size() != KEYS.size() + 1 || !lines.get(0).equals(ACTIVE_MAGIC)) throw new IllegalArgumentException("invalid prestige perk contract");
        Map<String, String> fields = new LinkedHashMap<>();
        for (int index = 0; index < KEYS.size(); index++) {
            String[] row = lines.get(index + 1).split("\\t", -1);
            if (row.length != 2 || !row[0].equals(KEYS.get(index)) || row[1].isEmpty()) throw new IllegalArgumentException("invalid prestige perk fields");
            fields.put(row[0], row[1]);
        }
        return fields;
    }

    /** Typed result of a neutral-only public contract read, never a paid-set grant. */
    public record Neutral(String lineageId, long generation) {}

    /**
     * Read the neutral bootstrap contract against an actual public lineage readback.
     * This stricter semantic API is NOT a replacement for general production
     * paid-perk validation; the production reader delegates only readFields.
     */
    public static Neutral readNeutral(Path path, PrestigeContracts.Lineage expected) throws IOException {
        PrestigeContracts.validateId("lineage ID", expected.lineageId());
        if (expected.totalPrestiges() < 0 || expected.generation() < 0
                || expected.generation() != expected.totalPrestiges()) {
            throw new IllegalArgumentException("invalid lineage counters");
        }
        Map<String, String> fields = readFields(path);
        if (!fields.get("lineage").equals(expected.lineageId())
                || !fields.get("generation").equals(Long.toString(expected.generation()))
                || !fields.get("perks").equals("-")) {
            throw new IllegalArgumentException("active contract is not neutral for the expected lineage generation");
        }
        return new Neutral(fields.get("lineage"), expected.generation());
    }

    /**
     * Publish ONLY a neutral empty legacy set for a valid current public lineage.
     * Caller owns a preexisting private parent directory and explicit fixture intent.
     * Never overwrites an existing target. This does not create or advance lineage,
     * fabricate historical resets, repair missing production state, or grant perks.
     * A failed partial publication is retained; callers must abandon that attempt.
     */
    public static void writeNeutral(Path path, PrestigeContracts.Lineage lineage) throws IOException {
        PrestigeContracts.validateId("lineage ID", lineage.lineageId());
        if (lineage.totalPrestiges() < 0 || lineage.generation() < 0
                || lineage.generation() != lineage.totalPrestiges()) {
            throw new IllegalArgumentException("invalid lineage counters");
        }
        Path target = path.toAbsolutePath();
        if (!target.normalize().equals(target)) throw new IllegalArgumentException("noncanonical contract path");
        for (Path cursor = target; cursor != null; cursor = cursor.getParent()) {
            if (Files.isSymbolicLink(cursor)) throw new IOException("symlink contract path");
        }
        Path parent = target.getParent();
        if (parent == null || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) throw new IOException("contract parent must already exist");
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) throw new FileAlreadyExistsException(target.toString());
        byte[] bytes = (ACTIVE_MAGIC + "\nlineage\t" + lineage.lineageId()
                + "\ngeneration\t" + lineage.generation() + "\nperks\t-\n").getBytes(StandardCharsets.UTF_8);
        Path partial = target.resolveSibling(target.getFileName() + "." + UUID.randomUUID() + ".partial");
        try (FileChannel file = FileChannel.open(partial, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) file.write(buffer);
            file.force(true);
        }
        // Atomic CREATE semantics, unlike ATOMIC_MOVE which can replace a target.
        // Both names are on the same filesystem; the parent remains caller-owned.
        Files.createLink(target, partial);
        forceDirectory(parent);
        Files.delete(partial);
        forceDirectory(parent);
    }

    private static void forceDirectory(Path parent) throws IOException {
        try (FileChannel directory = FileChannel.open(parent, StandardOpenOption.READ)) {
            directory.force(true);
        }
    }
}
