package com.bettercontent.betterworldmanagement.worldshaping;

import com.bettercontent.betterworldmanagement.mixin.AxiomPlayerListAccess;
import com.bettercontent.betterworldmanagement.mixin.AxiomRegionFileStorageAccess;
import com.bettercontent.betterworldmanagement.worldshaping.inheritance.InheritanceAdapters;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;

/** A successful in-memory operation is not a durable receipt. Verify actual native saved records. */
public final class AxiomDurability {
    private AxiomDurability() {}
    public static void savePlayer(ServerPlayer player, String token) throws IOException {
        ((AxiomPlayerListAccess) player.server.getPlayerList()).worldAxioms$save(player);
        Path path = player.server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(player.getUUID() + ".dat");
        if (!Files.isRegularFile(path)) throw new IOException("Native player receipt was not saved; entitlement remains prepared");
        CompoundTag saved = NbtIo.readCompressed(path.toFile());
        if (saved == null || !saved.getCompound("ForgeData").getCompound("WorldAxiomClaims").getBoolean(token)) throw new IOException("Native player inventory/receipt flush was not confirmed");
        forceNativeFile(path);
    }
    public static void verifyEntities(ServerLevel level, BlockPos anchor, String id, String transaction) throws IOException {
        Path directory = DimensionType.getStorageFolder(level.dimension(), level.getServer().getWorldPath(LevelResource.ROOT)).resolve("entities");
        List<String> slots = id.equals("ark") ? List.of("first", "second") : List.of("wolf");
        Map<UUID, Integer> counts = new HashMap<>(); Set<ChunkPos> chunks = new HashSet<>();
        for (int i = 0; i < slots.size(); i++) { counts.put(InheritanceAdapters.restoredUuid(transaction, id, slots.get(i)), 0); chunks.add(new ChunkPos(anchor.offset(i * 2, 0, 0))); }
        Set<Path> regions = new HashSet<>();
        for (ChunkPos pos : chunks) {
            Path region = directory.resolve("r." + (pos.x >> 5) + "." + (pos.z >> 5) + ".mca");
            if (!Files.isRegularFile(region)) throw new IOException("Native inheritance entity region was not flushed");
            regions.add(region);
        }
        try (RegionFileStorage storage = AxiomRegionFileStorageAccess.worldAxioms$open(directory, false)) {
            for (ChunkPos pos : chunks) {
                CompoundTag saved = storage.read(pos);
                if (saved == null) throw new IOException("Native inheritance chunk entity record is missing");
                ListTag entities = saved.getList("Entities", Tag.TAG_COMPOUND);
                for (int i = 0; i < entities.size(); i++) {
                    CompoundTag entity = entities.getCompound(i);
                    if (entity.hasUUID("UUID") && counts.containsKey(entity.getUUID("UUID"))) counts.compute(entity.getUUID("UUID"), (key, count) -> count + 1);
                }
            }
        }
        if (counts.values().stream().anyMatch(count -> count != 1)) throw new IOException("Native inheritance entities were not saved exactly once; receipt remains prepared");
        for (Path region : regions) forceNativeFile(region);
    }
    private static void forceNativeFile(Path path) throws IOException {
        // Readback alone can observe the OS page cache. Force both content and
        // the native rename/directory entry before advancing our durable WAL.
        try (FileChannel file = FileChannel.open(path, StandardOpenOption.WRITE)) { file.force(true); }
        try (FileChannel parent = FileChannel.open(path.toAbsolutePath().getParent(), StandardOpenOption.READ)) { parent.force(true); }
    }
}
