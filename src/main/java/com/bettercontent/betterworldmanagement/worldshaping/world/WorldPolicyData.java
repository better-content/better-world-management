package com.bettercontent.betterworldmanagement.worldshaping.world;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Saved with the successor world, never in the editable lineage draft. */
public final class WorldPolicyData extends SavedData {
    WorldPolicyData() {}
    private CompoundTag journal = new CompoundTag();
    static WorldPolicyData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(WorldPolicyData::load, WorldPolicyData::new,
                "better_world_management_axiom_world_v1");
    }
    private static WorldPolicyData load(CompoundTag tag) {
        if (tag.getInt("version") != 1) throw new IllegalStateException("Unknown world policy journal");
        WorldPolicyData data = new WorldPolicyData();
        data.journal = tag.getCompound("journal").copy();
        return data;
    }
    CompoundTag entry(String transaction) {
        if (transaction == null || transaction.isBlank() || transaction.length() > 256)
            throw new IllegalArgumentException("Invalid world policy transaction");
        String prior = journal.getString("transaction");
        if (!prior.isEmpty() && !prior.equals(transaction))
            throw new IllegalStateException("Successor already belongs to another transaction");
        journal.putString("transaction", transaction);
        setDirty();
        return journal;
    }
    @Override public CompoundTag save(CompoundTag tag) {
        tag.putInt("version", 1);
        tag.put("journal", journal.copy());
        return tag;
    }
}
