package com.bettercontent.betterworldmanagement.worldshaping;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.saveddata.SavedData;

/** Ordinary public writer API, outside the Mixin package; interception retains its exact owner guards. */
public final class AxiomSavedDataWriter {
    private AxiomSavedDataWriter() {}

    public static void write(SavedData data, Path file) {
        if (!data.isDirty()) return;
        try {
            CompoundTag root = new CompoundTag();
            root.put("data", data.save(new CompoundTag()));
            // This is exactly the version helper called by the pinned vanilla SavedData.save(File).
            NbtUtils.addCurrentDataVersion(root);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            NbtIo.writeCompressed(root, bytes);
            AxiomContracts.atomic(file, bytes.toByteArray());
            // Never clear a receipt until the file AND its parent directory have been forced.
            data.setDirty(false);
        } catch (IOException failure) {
            throw new UncheckedIOException("Atomic world-law SavedData save failed: " + file, failure);
        } catch (RuntimeException failure) {
            // No fallback to vanilla's direct/truncating write, and no dirty-bit clearing on failure.
            throw new UncheckedIOException("Atomic world-law SavedData encoding/save failed: " + file, new IOException(failure));
        }
    }
}
