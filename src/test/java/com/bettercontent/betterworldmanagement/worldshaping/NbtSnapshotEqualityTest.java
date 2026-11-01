package com.bettercontent.betterworldmanagement.worldshaping;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

/** The disabled native fixture must compare complete saved NBT, not Java NaN equality. */
class NbtSnapshotEqualityTest {
    private static byte[] saved(Tag tag) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeByte(tag.getId());
            tag.write(output);
        }
        return bytes.toByteArray();
    }

    @Test void independentNativeNanTagsHaveIdenticalSavedBytesButJavaEqualityFails() throws IOException {
        CompoundTag first = new CompoundTag();
        CompoundTag second = new CompoundTag();
        first.put("Base", DoubleTag.valueOf(Double.NaN));
        second.put("Base", DoubleTag.valueOf(Double.NaN));
        assertFalse(first.equals(second), "Minecraft DoubleTag.equals compares NaN with dcmpl");
        assertArrayEquals(saved(first), saved(second), "Identical persisted NBT must be treated as unchanged");
    }

    @Test void SavedByteComparisonRejectsEveryRealAttributeAndListMutation() throws IOException {
        CompoundTag original = new CompoundTag();
        original.putDouble("Base", Double.NaN);
        CompoundTag altered = original.copy();
        altered.putDouble("Base", 0.5);
        assertFalse(Arrays.equals(saved(original), saved(altered)), "Finite attribute changed");
        CompoundTag extra = original.copy();
        extra.putString("Other", "present");
        assertFalse(Arrays.equals(saved(original), saved(extra)), "New field changed");
        ListTag originalList = new ListTag();
        originalList.add(original);
        ListTag alteredList = new ListTag();
        alteredList.add(original.copy());
        alteredList.add(extra);
        assertFalse(Arrays.equals(saved(originalList), saved(alteredList)), "Added inventory element changed");
    }
}
