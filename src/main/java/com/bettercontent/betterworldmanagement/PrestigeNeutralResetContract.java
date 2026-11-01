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
import java.util.List;
import java.util.UUID;

/**
 * Explicit fixture-only bridge for a synthetic parent at generation 23. It publishes
 * an EMPTY legacy paid-perk reset, not a paid claim or a real historical reset.
 * The real supervisor, staged-reset validator, World Axiom provenance and health
 * gates remain authoritative; this helper cannot grant or promote any law.
 */
public final class PrestigeNeutralResetContract {
    private PrestigeNeutralResetContract() {}

    public record Neutral(String lineageId, long baseGeneration, long targetGeneration,
                          String transactionId, List<String> biomes) {
        public Neutral { biomes = List.copyOf(biomes); }
    }

    /** Read and bind all THREE existing public source contracts before trusting reset bytes. */
    public static Neutral readNeutral(Path path, Path lineagePath, Path resetPath, Path activePath) throws IOException {
        Source source = source(lineagePath, resetPath, activePath);
        byte[] actual = Files.readAllBytes(path);
        if (!java.util.Arrays.equals(actual, encoded(source))) {
            throw new IllegalArgumentException("legacy perk reset is not the exact neutral source-bound frame");
        }
        return source.neutral();
    }

    /**
     * Exclusive, durable one-shot fixture publication: never replace an existing
     * committed perk frame and never claim 23 real resets. An interrupted partial
     * publication is evidence to retain, not an invitation to repair/retry it.
     */
    public static Neutral writeNeutral(Path path, Path lineagePath, Path resetPath, Path activePath) throws IOException {
        Source source = source(lineagePath, resetPath, activePath);
        Path target = path.toAbsolutePath();
        if (!target.normalize().equals(target)) throw new IllegalArgumentException("noncanonical reset path");
        for (Path cursor = target; cursor != null; cursor = cursor.getParent()) {
            if (Files.isSymbolicLink(cursor)) throw new IOException("symlink reset path");
        }
        Path parent = target.getParent();
        if (parent == null || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("reset parent must already exist");
        }
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) throw new FileAlreadyExistsException(target.toString());
        byte[] beforeLineage = Files.readAllBytes(lineagePath);
        byte[] beforeReset = Files.readAllBytes(resetPath);
        byte[] beforeActive = Files.readAllBytes(activePath);
        byte[] body = encoded(source);
        Path partial = target.resolveSibling(target.getFileName() + "." + UUID.randomUUID() + ".partial");
        try (FileChannel file = FileChannel.open(partial, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            ByteBuffer bytes = ByteBuffer.wrap(body);
            while (bytes.hasRemaining()) file.write(bytes);
            file.force(true);
        }
        if (!java.util.Arrays.equals(beforeLineage, Files.readAllBytes(lineagePath))
                || !java.util.Arrays.equals(beforeReset, Files.readAllBytes(resetPath))
                || !java.util.Arrays.equals(beforeActive, Files.readAllBytes(activePath))
                || !source.equals(source(lineagePath, resetPath, activePath))) {
            throw new IOException("source contracts changed during neutral reset publication");
        }
        Files.createLink(target, partial); // CREATE_NEW semantics: never overwrite an existing reset.
        forceDirectory(parent);
        Files.delete(partial);
        forceDirectory(parent);
        return readNeutral(target, lineagePath, resetPath, activePath);
    }

    private record Source(PrestigeContracts.Lineage lineage, PrestigeContracts.Reset reset,
                          PrestigeActivePerksContract.Neutral active, Neutral neutral) {}

    private static Source source(Path lineagePath, Path resetPath, Path activePath) throws IOException {
        var lineage = PrestigeContracts.readLineage(lineagePath);
        var reset = PrestigeContracts.readReset(resetPath);
        var active = PrestigeActivePerksContract.readNeutral(activePath, lineage);
        if (lineage.generation() != 23 || lineage.totalPrestiges() != 23
                || reset.baseGeneration() != 23 || !reset.lineageId().equals(lineage.lineageId())) {
            throw new IllegalArgumentException("only a public neutral synthetic parent23 is supported");
        }
        if (reset.biomes().isEmpty()) throw new IllegalArgumentException("neutral fixture must choose a biome");
        return new Source(lineage, reset, active,
                new Neutral(lineage.lineageId(), 23, 24, reset.transactionId(), reset.biomes()));
    }

    private static byte[] encoded(Source source) {
        Neutral n = source.neutral();
        List<String> biomes = n.biomes();
        return (PrestigePerks.RESET_MAGIC + "\nlineage\t" + n.lineageId()
                + "\nbase_generation\t23\ntarget_generation\t24\ntransaction\t" + n.transactionId()
                + "\nperks\t-\nbiome_1\t" + biomes.get(0)
                + "\nbiome_2\t" + (biomes.size() > 1 ? biomes.get(1) : "-")
                + "\nbiome_3\t" + (biomes.size() > 2 ? biomes.get(2) : "-") + "\n")
                .getBytes(StandardCharsets.UTF_8);
    }

    private static void forceDirectory(Path path) throws IOException {
        try (FileChannel directory = FileChannel.open(path, StandardOpenOption.READ)) {
            directory.force(true);
        }
    }
}
