package com.bettercontent.betterworldmanagement.worldshaping.exploration;

import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** World-local entry receipts. Succession exports identifiers, never positions or old-world NBT. */
public final class ExplorationDiscoveries extends SavedData {
    public static final Set<String> ALLOWED = Set.of("minecraft:monument", "minecraft:mansion",
            "minecraft:mineshaft", "minecraft:fortress");
    private final Set<String> entered = new LinkedHashSet<>();

    public static ExplorationDiscoveries get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(ExplorationDiscoveries::load,
                ExplorationDiscoveries::new, "better_world_management_exploration");
    }
    public static ExplorationDiscoveries load(CompoundTag tag) {
        ExplorationDiscoveries data = new ExplorationDiscoveries();
        data.entered.addAll(readIds(tag));
        return data;
    }
    public static Set<String> readIds(CompoundTag tag) {
        Set<String> result = new LinkedHashSet<>();
        ListTag list = tag.getList("structures", Tag.TAG_STRING);
        for (int i = 0; i < Math.min(list.size(), 4); i++) {
            String id = list.getString(i);
            if (ALLOWED.contains(id)) result.add(id);
        }
        return Set.copyOf(result);
    }
    public void recordEntry(String id) {
        if (ALLOWED.contains(id) && entered.add(id)) setDirty();
    }
    @Override public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        entered.stream().sorted().forEach(id -> list.add(StringTag.valueOf(id)));
        tag.put("structures", list);
        return tag;
    }
    public CompoundTag export() { return save(new CompoundTag()); }
}
