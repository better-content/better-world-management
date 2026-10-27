package com.bettercontent.betterworldmanagement.mixin;

import com.bettercontent.betterworldmanagement.worldshaping.AxiomSavedDataWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.level.saveddata.SavedData;
import org.junit.jupiter.api.Test;

/** Actual NBT/filesystem writer checks only. No Mixin bootstrap, native clock, calendar, season or restart claim. */
public final class AxiomSavedDataAtomicWriterTest {
    private static int assertions;
    @Test void nativeNbtAndAtomicFilesystemContract() throws Exception { runChecks(); }
    public static void main(String[] args) throws Exception { runChecks(); }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
    private static void runChecks() throws Exception {
        SharedConstants.tryDetectVersion();
        assertions = 0;
        Path root = Path.of(System.getProperty("user.home"), ".tmp", "axiom-saveddata-writer-" + UUID.randomUUID());
        Files.createDirectories(root);
        Path vanilla = root.resolve("vanilla.dat"), atomic = root.resolve("atomic.dat");
        PayloadData baseline = new PayloadData(); baseline.setDirty(); baseline.save(vanilla.toFile());
        check(baseline.calls == 1 && !baseline.isDirty(), "Native baseline must actually serialize once");
        PayloadData current = new PayloadData(); current.setDirty();
        AxiomSavedDataWriter.write(current, atomic);
        check(current.calls == 1 && !current.isDirty(), "Successful atomic save must clear dirty only after serialization/durability");
        CompoundTag nativeRoot = NbtIo.readCompressed(vanilla.toFile());
        CompoundTag atomicRoot = NbtIo.readCompressed(atomic.toFile());
        check(nativeRoot.equals(atomicRoot), "Actual NBT decoded wrapper/payload must equal native SavedData.save(File)");
        check(atomicRoot.getAllKeys().equals(Set.of("data", "DataVersion")), "No new on-disk wrapper fields are permitted");
        check(atomicRoot.getInt("DataVersion") == SharedConstants.getCurrentVersion().getDataVersion().getVersion(), "Native current data version must be preserved");
        check(atomicRoot.getCompound("data").equals(PayloadData.payload()), "Longs, nested receipts and native list tags must roundtrip exactly");
        byte[] before = Files.readAllBytes(atomic);
        PayloadData clean = new PayloadData();
        AxiomSavedDataWriter.write(clean, atomic);
        check(clean.calls == 0 && !clean.isDirty(), "Clean writer path must not serialize or mark dirty");
        check(Arrays.equals(before, Files.readAllBytes(atomic)), "Clean path must not replace existing bytes");

        PayloadData broken = new PayloadData(); broken.fail = true; broken.setDirty();
        try {
            AxiomSavedDataWriter.write(broken, atomic);
            throw new AssertionError("Runtime serializer failure must propagate");
        } catch (UncheckedIOException expected) {
            check(expected.getCause().getCause() instanceof IllegalStateException, "Runtime serializer failure must retain its original cause");
        }
        check(broken.isDirty(), "Serializer failure must preserve the dirty receipt");
        check(Arrays.equals(before, Files.readAllBytes(atomic)), "Serializer failure before staging must preserve the old valid file");

        Path blocked = root.resolve("blocked-parent"); Files.writeString(blocked, "regular file, not a directory");
        PayloadData ioFailure = new PayloadData(); ioFailure.setDirty();
        try {
            AxiomSavedDataWriter.write(ioFailure, blocked.resolve("data.dat"));
            throw new AssertionError("Filesystem failure must propagate, never fall back to direct write");
        } catch (UncheckedIOException expected) {
            check(expected.getCause() instanceof IOException, "Filesystem failure must retain its checked I/O cause");
        }
        check(ioFailure.isDirty(), "Failed atomic filesystem write must not clear dirty");
        check(Files.readString(blocked).equals("regular file, not a directory"), "Filesystem failure must not truncate the blocking file");
        check(Arrays.equals(before, Files.readAllBytes(atomic)), "Other valid receipt files must remain untouched");

        // Actual non-root POSIX denial exercises preservation of an existing target, before any rename.
        Path protectedDir = root.resolve("protected"); Files.createDirectory(protectedDir);
        Path prior = protectedDir.resolve("receipt.dat"); Files.write(prior, before);
        Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(protectedDir);
        try {
            Files.setPosixFilePermissions(protectedDir, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_EXECUTE));
            check(!Files.isWritable(protectedDir), "Writer test must run as lane user, not a privileged bypass");
            PayloadData denied = new PayloadData(); denied.setDirty();
            try {
                AxiomSavedDataWriter.write(denied, prior);
                throw new AssertionError("Denied staging must not silently write the old target directly");
            } catch (UncheckedIOException expected) {
                check(denied.isDirty(), "Denied staging must retain dirty");
            }
            check(Arrays.equals(before, Files.readAllBytes(prior)), "Denied staging must preserve the prior complete compressed NBT bytes");
            check(NbtIo.readCompressed(prior.toFile()).equals(nativeRoot), "Prior receipt must still decode with actual native NBT reader");
        } finally { Files.setPosixFilePermissions(protectedDir, permissions); }
        try (var files = Files.walk(root)) {
            check(files.noneMatch(path -> path.getFileName().toString().endsWith(".partial")), "Normal/failed staging must not leave incomplete temporary files");
        }
        System.out.println("PASS writer-only assertions=" + assertions + " evidence=" + root);
    }

    private static final class PayloadData extends SavedData {
        int calls; boolean fail;
        @Override public CompoundTag save(CompoundTag tag) {
            calls++;
            if (fail) throw new IllegalStateException("deliberate writer-only serializer failure");
            return payload();
        }
        static CompoundTag payload() {
            CompoundTag tag = new CompoundTag(); tag.putLong("nativeTime", Long.MAX_VALUE - 1);
            tag.putInt("version", 1); tag.putBoolean("prepared", true);
            CompoundTag receipt = new CompoundTag(); receipt.putString("transaction", "writer-fixture");
            receipt.putLongArray("plan", new long[] {Long.MIN_VALUE, 0, Long.MAX_VALUE});
            ListTag list = new ListTag(); list.add(StringTag.valueOf("first")); list.add(StringTag.valueOf("second"));
            receipt.put("savedOrder", list); tag.put("journal", receipt); return tag;
        }
    }
}
