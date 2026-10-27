package com.bettercontent.betterworldmanagement.worldshaping;

import com.bettercontent.betterworldmanagement.worldshaping.inheritance.InheritanceAdapters;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Communal exactly-once delivery using WAL + atomically saved player receipt/inventory pairs. */
public final class AxiomClaims {
    private record Pending(MinecraftServer server, ServerLevel level, BlockPos pos) {}
    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private AxiomClaims() {}
    public static void claim(ServerPlayer player, String id) throws IOException {
        MinecraftServer server = player.server;
        AxiomContracts.Build build = WorldAxiomService.active(server).orElseThrow();
        AxiomJournal journal = AxiomJournal.open(server);
        if (journal.completed(id)) { release(server, journal, id, build.transaction()); throw new IllegalStateException("This communal inheritance was already claimed"); }
        CompoundTag payload = WorldAxiomService.configuration(server, id);
        InheritanceAdapters.validate(server, id, payload);
        if (id.equals("seed_covenant") || id.equals("patient_hive")) claimItems(player, id, payload, build, journal);
        else claimEntities(player, id, payload, build, journal);
    }
    private static void claimItems(ServerPlayer player, String id, CompoundTag payload, AxiomContracts.Build build, AxiomJournal journal) throws IOException {
        String token = build.transaction() + "/" + id;
        AxiomJournal.Receipt receipt = journal.receipt(id);
        if (receipt != null && !receipt.actor().equals(player.getUUID().toString())) throw new IllegalStateException("A pending delivery is reserved for the original claiming operator; it will not be duplicated");
        CompoundTag markers = player.getPersistentData().getCompound("WorldAxiomClaims");
        if (markers.getBoolean(token)) {
            // A crash may have occurred after the atomic player save but before the communal receipt.
            AxiomDurability.savePlayer(player, token); journal.complete(id); journal.released(id); return;
        }
        List<ItemStack> stacks = InheritanceAdapters.items(player.serverLevel(), id, payload);
        if (stacks.isEmpty()) throw new IllegalStateException("Inheritance has no reconstructed items");
        if (!fits(player, stacks)) throw new IllegalStateException("Inventory is full; communal entitlement remains pending");
        journal.prepare(id, player.getUUID().toString(), player.serverLevel().dimension().location().toString(), player.blockPosition());
        for (ItemStack stack : stacks) if (!player.getInventory().add(stack.copy())) throw new IllegalStateException("Unexpected inventory mutation; pending delivery retained for recovery");
        markers.putBoolean(token, true); player.getPersistentData().put("WorldAxiomClaims", markers);
        // Minecraft writes the inventory and Forge persistent receipt in the SAME atomic player .dat.
        AxiomDurability.savePlayer(player, token);
        journal.complete(id); journal.released(id);
        player.containerMenu.broadcastChanges();
    }
    static boolean fits(ServerPlayer player, List<ItemStack> incoming) {
        List<ItemStack> slots = new ArrayList<>();
        for (int i = 0; i < 36; i++) slots.add(player.getInventory().getItem(i).copy());
        for (ItemStack source : incoming) {
            int remaining = source.getCount();
            for (ItemStack existing : slots) if (!existing.isEmpty() && ItemStack.isSameItemSameTags(existing, source)) {
                int space = Math.max(0, Math.min(64, existing.getMaxStackSize()) - existing.getCount());
                int moved = Math.min(space, remaining); existing.grow(moved); remaining -= moved;
            }
            for (int i = 0; remaining > 0 && i < slots.size(); i++) if (slots.get(i).isEmpty()) {
                int moved = Math.min(remaining, Math.min(64, source.getMaxStackSize())); ItemStack placed = source.copy(); placed.setCount(moved); slots.set(i, placed); remaining -= moved;
            }
            if (remaining > 0) return false;
        }
        return true;
    }
    private static void claimEntities(ServerPlayer player, String id, CompoundTag payload, AxiomContracts.Build build, AxiomJournal journal) throws IOException {
        AxiomJournal.Receipt receipt = journal.receipt(id);
        ServerLevel level; BlockPos anchor;
        if (receipt == null) {
            level = player.serverLevel(); anchor = safeAnchor(level, ((com.bettercontent.betterworldmanagement.WorldCondenserMenu) player.containerMenu).pos());
            receipt = journal.prepare(id, player.getUUID().toString(), level.dimension().location().toString(), anchor);
        } else {
            level = player.server.getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(receipt.dimension())));
            if (level == null) throw new IllegalStateException("Pending inheritance dimension is unavailable");
            anchor = receipt.anchor();
        }
        List<String> slots = id.equals("ark") ? List.of("first", "second") : List.of("wolf");
        for (int i = 0; i < slots.size(); i++) {
            BlockPos pos = anchor.offset(i * 2, 0, 0); level.getChunk(pos);
            UUID uuid = InheritanceAdapters.restoredUuid(build.transaction(), id, slots.get(i));
            PENDING.put(uuid, new Pending(player.server, level, pos));
            Entity found = level.getEntity(uuid); if (found != null) freeze(found, pos);
        }
        try {
            InheritanceAdapters.apply(level, anchor, id, payload, build.transaction());
            if (!player.server.saveEverything(false, true, true)) throw new IOException("Native inheritance save did not complete");
            AxiomDurability.verifyEntities(level, anchor, id, build.transaction());
            journal.complete(id);
            release(player.server, journal, id, build.transaction());
        } catch (RuntimeException | IOException error) {
            // Every partial entity stays pinned and invulnerable at its journaled position until replay.
            player.server.saveEverything(false, true, true);
            throw error;
        }
    }
    private static BlockPos safeAnchor(ServerLevel level, BlockPos condenser) {
        for (int radius = 3; radius <= 8; radius++) for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            if (Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
            for (int dy : new int[]{0, 1, -1, 2, -2}) {
                BlockPos pos = condenser.offset(dx, dy, dz);
                if (safe(level, pos) && safe(level, pos.offset(2, 0, 0))) return pos;
            }
        }
        throw new IllegalStateException("No safe inheritance space near the Condenser; entitlement remains pending");
    }
    private static boolean safe(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos) || !level.hasChunkAt(pos.offset(1, 0, 1)) || !level.getWorldBorder().isWithinBounds(pos)) return false;
        for (int x = 0; x <= 1; x++) for (int z = 0; z <= 1; z++) {
            BlockPos p = pos.offset(x, 0, z);
            if (!level.getBlockState(p.below()).isFaceSturdy(level, p.below(), net.minecraft.core.Direction.UP)
                || level.getBlockState(p.below()).is(Blocks.MAGMA_BLOCK) || level.getBlockState(p.below()).is(Blocks.CAMPFIRE)
                || !level.getBlockState(p).isAir() || !level.getBlockState(p.above()).isAir()
                || !level.getFluidState(p).isEmpty()) return false;
        }
        return true;
    }
    private static void freeze(Entity entity, BlockPos pos) {
        if (entity instanceof Mob mob) mob.setNoAi(true);
        entity.setInvulnerable(true); entity.setNoGravity(true); entity.setDeltaMovement(Vec3.ZERO);
        entity.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
    }
    private static void release(MinecraftServer server, AxiomJournal journal, String id, String transaction) throws IOException {
        AxiomJournal.Receipt receipt = journal.receipt(id);
        if (receipt == null || receipt.state().equals("released") || !journal.completed(id)) return;
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(receipt.dimension())));
        if (level == null) throw new IllegalStateException("Inheritance release dimension is unavailable");
        List<String> slots = id.equals("ark") ? List.of("first", "second") : List.of("wolf");
        if (!id.equals("seed_covenant") && !id.equals("patient_hive")) for (int i = 0; i < slots.size(); i++) {
            level.getChunk(receipt.anchor().offset(i * 2, 0, 0));
            UUID uuid = InheritanceAdapters.restoredUuid(transaction, id, slots.get(i)); Entity entity = level.getEntity(uuid);
            if (entity != null) { if (entity instanceof Mob mob) mob.setNoAi(false); entity.setInvulnerable(false); entity.setNoGravity(false); }
            PENDING.remove(uuid);
        }
        if (!server.saveEverything(false, true, true)) throw new IOException("Native inheritance release save did not complete");
        journal.released(id);
    }
    public static void recover(MinecraftServer server) throws IOException {
        if (WorldAxiomService.active(server).isEmpty()) return;
        String tx = WorldAxiomService.active(server).orElseThrow().transaction(); AxiomJournal journal = AxiomJournal.open(server);
        for (var entry : journal.receipts().entrySet()) {
            String id = entry.getKey(); AxiomJournal.Receipt receipt = entry.getValue();
            if (!id.equals("ark") && !id.equals("familiars_name")) continue;
            if (journal.completed(id)) { release(server, journal, id, tx); continue; }
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(receipt.dimension())));
            if (level == null) throw new IllegalStateException("Pending inheritance dimension missing");
            List<String> slots = id.equals("ark") ? List.of("first", "second") : List.of("wolf");
            for (int i = 0; i < slots.size(); i++) {
                BlockPos pos = receipt.anchor().offset(i * 2, 0, 0); level.getChunk(pos);
                UUID uuid = InheritanceAdapters.restoredUuid(tx, id, slots.get(i)); PENDING.put(uuid, new Pending(server, level, pos));
                Entity entity = level.getEntity(uuid); if (entity != null) freeze(entity, pos);
            }
        }
    }
    public static void clear(MinecraftServer server) { PENDING.entrySet().removeIf(entry -> entry.getValue().server() == server); }
    @SubscribeEvent public static void onJoin(EntityJoinLevelEvent event) {
        Pending pending = PENDING.get(event.getEntity().getUUID()); if (pending != null && pending.level() == event.getLevel()) freeze(event.getEntity(), pending.pos());
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        PENDING.forEach((uuid, pending) -> { if (pending.server() == event.getServer()) { Entity entity = pending.level().getEntity(uuid); if (entity != null) freeze(entity, pending.pos()); } });
    }
}
