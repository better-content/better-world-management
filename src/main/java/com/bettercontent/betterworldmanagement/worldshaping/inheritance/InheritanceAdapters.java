package com.bettercontent.betterworldmanagement.worldshaping.inheritance;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Typed inheritance only. The caller owns durable effect receipts and communal item escrow. */
public final class InheritanceAdapters {
    private InheritanceAdapters() {}

    public static CompoundTag capture(ServerPlayer player, String id, CompoundTag previous) {
        CompoundTag out = previous == null ? new CompoundTag() : previous.copy();
        validateShape(id, out, false);
        switch (id) {
            case "ark" -> {
                requireMod("eanimod");
                Entity target = lookedAt(player);
                CompoundTag cow = CattleInheritance.capture(target);
                String slot = !out.contains("first") ? "first" : "second";
                if (slot.equals("second")) {
                    CompoundTag first = out.getCompound("first");
                    require(!first.getUUID("source").equals(cow.getUUID("source")), "Nominate two distinct cattle");
                    require(first.getBoolean("female") != cow.getBoolean("female"), "Ark requires a breeding pair");
                }
                out.put(slot, cow);
            }
            case "seed_covenant" -> {
                ItemStack held = player.getMainHandItem();
                String item = ForgeRegistries.ITEMS.getKey(held.getItem()).toString();
                require(held.getCount() >= 4 && allowedSeed(item), "Hold at least four supported cultivar seeds");
                out = new CompoundTag();
                out.putString("item", item);
            }
            case "patient_hive" -> {
                requireMod("complicated_bees");
                ItemStack held = player.getMainHandItem();
                String item = ForgeRegistries.ITEMS.getKey(held.getItem()).toString();
                require(item.equals("complicated_bees:princess") || item.equals("complicated_bees:drone"), "Hold a princess or drone");
                out.put(item.endsWith(":princess") ? "princess" : "drone", BeeInheritance.capture(held));
            }
            case "familiars_name" -> {
                Entity target = lookedAt(player);
                require(target != null && target.getType() == EntityType.WOLF && target instanceof Wolf, "Look at a vanilla wolf");
                Wolf wolf = (Wolf) target;
                require(wolf.isTame() && player.getUUID().equals(wolf.getOwnerUUID()), "Wolf must belong to the nominating player");
                out = new CompoundTag();
                out.putUUID("source", wolf.getUUID());
                out.putUUID("owner", player.getUUID());
                out.putInt("collar", wolf.getCollarColor().getId());
                out.putString("name", wolf.hasCustomName() ? wolf.getCustomName().getString() : "");
            }
            default -> throw invalid("Unsupported inheritance axiom");
        }
        validateShape(id, out, false);
        return out;
    }

    public static void validate(MinecraftServer server, String id, CompoundTag payload) {
        require(server != null, "Server required");
        validateShape(id, payload, true);
    }

    /** Freeze-time check against the nominated player's actual old-world entities/inventory. */
    public static void revalidateSources(ServerPlayer player, String id, CompoundTag payload) {
        validate(player.getServer(), id, payload);
        switch (id) {
            case "ark" -> {
                for (String slot : List.of("first", "second")) {
                    CompoundTag stored = payload.getCompound(slot);
                    Entity entity = find(player.getServer(), stored.getUUID("source"));
                    require(stored.equals(CattleInheritance.capture(entity)), "Nominated cattle changed or are not loaded");
                }
            }
            case "familiars_name" -> {
                Entity source = find(player.getServer(), payload.getUUID("source"));
                require(source instanceof Wolf && source.getType() == EntityType.WOLF && source.isAlive(), "Nominated wolf missing");
                Wolf wolf = (Wolf) source;
                require(wolf.isTame() && player.getUUID().equals(wolf.getOwnerUUID())
                    && player.getUUID().equals(payload.getUUID("owner"))
                    && wolf.getCollarColor().getId() == payload.getInt("collar")
                    && (wolf.hasCustomName() ? wolf.getCustomName().getString() : "").equals(payload.getString("name")), "Nominated wolf changed");
            }
            case "seed_covenant" -> {
                int count = 0;
                for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                    ItemStack stack = player.getInventory().getItem(i);
                    if (payload.getString("item").equals(ForgeRegistries.ITEMS.getKey(stack.getItem()).toString())) count += stack.getCount();
                }
                require(count >= 4, "Four nominated seeds must still be present");
            }
            case "patient_hive" -> {
                for (String slot : List.of("princess", "drone")) {
                    boolean found = false;
                    for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                        ItemStack stack = player.getInventory().getItem(i);
                        if (!stack.isEmpty() && ("complicated_bees:" + slot).equals(ForgeRegistries.ITEMS.getKey(stack.getItem()).toString())
                            && payload.getCompound(slot).equals(BeeInheritance.capture(stack))) found = true;
                    }
                    require(found, "Nominated " + slot + " genome must still be present");
                }
            }
            default -> throw invalid("Unsupported inheritance axiom");
        }
    }

    public static void apply(ServerLevel level, BlockPos anchor, String id, CompoundTag payload, String transaction) {
        validate(level.getServer(), id, payload);
        require(transaction != null && !transaction.isBlank() && transaction.length() <= 256, "Invalid transaction");
        require(!id.equals("seed_covenant") && !id.equals("patient_hive"), "Item inheritance requires root-owned durable communal escrow via items()");
        List<String> slots = id.equals("ark") ? List.of("first", "second") : List.of("wolf");
        for (int i = 0; i < slots.size(); i++) {
            String slot = slots.get(i);
            CompoundTag data = id.equals("ark") ? payload.getCompound(slot) : payload;
            UUID uuid = restoredUuid(transaction, id, slot);
            // Load the journaled initial spawn chunk before looking up a replayed entity.
            level.getChunk(anchor.offset(i * 2, 0, 0));
            Entity existing = find(level.getServer(), uuid);
            if (existing != null) {
                require(existing.level() == level && (id.equals("ark") ? CattleInheritance.matches(existing, data) : matchesWolf(existing, data)), "Restoration UUID collision or changed entity");
                continue;
            }
            Entity entity;
            if (id.equals("ark")) entity = CattleInheritance.restore(level, data);
            else {
                Wolf wolf = EntityType.WOLF.create(level);
                require(wolf != null, "Unable to create wolf");
                wolf.setTame(true);
                wolf.setOwnerUUID(data.getUUID("owner"));
                wolf.setCollarColor(DyeColor.byId(data.getInt("collar")));
                wolf.setOrderedToSit(true);
                if (!data.getString("name").isEmpty()) wolf.setCustomName(net.minecraft.network.chat.Component.literal(data.getString("name")));
                wolf.setHealth(wolf.getMaxHealth());
                entity = wolf;
            }
            entity.setUUID(uuid);
            entity.moveTo(anchor.getX() + 0.5 + i * 2, anchor.getY(), anchor.getZ() + 0.5, 0, 0);
            if (entity instanceof Mob mob) mob.setPersistenceRequired();
            require(level.noCollision(entity), "Inheritance spawn position obstructed");
            require(level.addFreshEntity(entity), "Inheritance spawn rejected");
        }
        // Root must save entities/chunks BEFORE committing its durable effect receipt.
    }

    public static List<ItemStack> items(ServerLevel level, String id, CompoundTag payload) {
        validate(level.getServer(), id, payload);
        if (id.equals("seed_covenant")) return List.of(new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation(payload.getString("item"))), 4));
        if (id.equals("patient_hive")) return List.of(BeeInheritance.restore("princess", payload.getCompound("princess")), BeeInheritance.restore("drone", payload.getCompound("drone")));
        return List.of();
    }

    public static UUID restoredUuid(String transaction, String id, String slot) {
        return InheritanceIdentity.restoredUuid(transaction, id, slot);
    }

    private static boolean matchesWolf(Entity entity, CompoundTag data) {
        return entity.getType() == EntityType.WOLF && entity instanceof Wolf wolf && wolf.isTame()
            && data.getUUID("owner").equals(wolf.getOwnerUUID()) && data.getInt("collar") == wolf.getCollarColor().getId()
            && data.getString("name").equals(wolf.hasCustomName() ? wolf.getCustomName().getString() : "");
    }

    private static void validateShape(String id, CompoundTag p, boolean complete) {
        require(p != null && p.sizeInBytes() <= 32768, "Inheritance payload exceeds limit");
        switch (id) {
            case "ark" -> {
                requireMod("eanimod");
                keys(p, Set.of("first", "second"));
                for (String key : p.getAllKeys()) { typed(p, key, Tag.TAG_COMPOUND); CattleInheritance.validate(p.getCompound(key)); }
                if (complete || p.contains("second")) {
                    typed(p, "first", Tag.TAG_COMPOUND); typed(p, "second", Tag.TAG_COMPOUND);
                    require(!p.getCompound("first").getUUID("source").equals(p.getCompound("second").getUUID("source")), "Duplicate cattle");
                    require(p.getCompound("first").getBoolean("female") != p.getCompound("second").getBoolean("female"), "Ark requires a breeding pair");
                }
            }
            case "seed_covenant" -> {
                keys(p, Set.of("item"));
                if (complete || !p.isEmpty()) { typed(p, "item", Tag.TAG_STRING); require(allowedSeed(p.getString("item")), "Unsupported cultivar seed"); }
            }
            case "patient_hive" -> {
                requireMod("complicated_bees");
                keys(p, Set.of("princess", "drone"));
                for (String key : p.getAllKeys()) { typed(p, key, Tag.TAG_COMPOUND); BeeInheritance.validate(p.getCompound(key)); }
                if (complete) { typed(p, "princess", Tag.TAG_COMPOUND); typed(p, "drone", Tag.TAG_COMPOUND); }
            }
            case "familiars_name" -> {
                keys(p, Set.of("source", "owner", "collar", "name"));
                if (complete || !p.isEmpty()) {
                    require(p.hasUUID("source") && p.hasUUID("owner"), "Wolf UUIDs required");
                    typed(p, "collar", Tag.TAG_INT); typed(p, "name", Tag.TAG_STRING);
                    require(p.getInt("collar") >= 0 && p.getInt("collar") < 16, "Invalid collar");
                    String name = p.getString("name");
                    require(name.length() <= 64 && name.chars().noneMatch(Character::isISOControl), "Invalid wolf name");
                }
            }
            default -> throw invalid("Unsupported inheritance axiom");
        }
    }

    private static boolean allowedSeed(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null || !ForgeRegistries.ITEMS.containsKey(location)) return false;
        return ModList.get().isLoaded("better_bumblezone_crops") && CultivarInheritance.allowed(id);
    }

    private static Entity lookedAt(ServerPlayer player) {
        Vec3 start = player.getEyePosition();
        Vec3 end = player.pick(6, 0, false).getLocation();
        Entity closest = null;
        double distance = start.distanceToSqr(end);
        for (Entity entity : player.serverLevel().getEntities(player, player.getBoundingBox().expandTowards(end.subtract(start)).inflate(1), e -> e.isAlive() && e.isPickable() && !e.isSpectator())) {
            var hit = entity.getBoundingBox().inflate(entity.getPickRadius()).clip(start, end);
            if (hit.isPresent() && start.distanceToSqr(hit.get()) <= distance) {
                closest = entity; distance = start.distanceToSqr(hit.get());
            }
        }
        return closest;
    }

    private static Entity find(MinecraftServer server, UUID uuid) {
        for (ServerLevel level : server.getAllLevels()) { Entity entity = level.getEntity(uuid); if (entity != null) return entity; }
        return null;
    }
    static void keys(CompoundTag p, Set<String> allowed) { require(allowed.containsAll(p.getAllKeys()), "Unknown inheritance field"); }
    static void typed(CompoundTag p, String key, int type) { require(p.contains(key, type), "Missing or wrongly typed " + key); }
    static void require(boolean condition, String message) { if (!condition) throw invalid(message); }
    static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
    private static void requireMod(String id) { require(ModList.get().isLoaded(id), "Required inheritance adapter unavailable: " + id); }
}
