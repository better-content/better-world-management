package com.bettercontent.betterworldmanagement.worldshaping.world;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/** Bounded 9x9 open pavilion. No clearing, flattening, terrain replacement or inventory overwrite. */
final class CommunalArchive {
    private CommunalArchive() {}
    static void apply(ServerLevel level, BlockPos anchor, CompoundTag config, CompoundTag journal, WorldPolicyData data) {
        String plaque = ArchiveText.plaque(config.getString("plaque"));
        ItemStack[] books = new ItemStack[4];
        for (int i = 0; i < 4; i++) {
            books[i] = new ItemStack(Items.WRITTEN_BOOK);
            CompoundTag book = books[i].getOrCreateTag();
            book.putString("title", "Communal Archive " + (i + 1));
            book.putString("author", "The previous generation");
            book.putBoolean("resolved", true);
            ListTag pages = new ListTag();
            for (String page : ArchiveText.pages(config.getString("book" + (i + 1))))
                pages.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(page))));
            book.put("pages", pages);
        }
        Site site;
        if (journal.contains("archiveOrigin")) {
            int[] legs = new int[4];
            if (journal.getInt("archiveSupportVersion") == 1)
                for (int i = 0; i < legs.length; i++) legs[i] = journal.getInt("archiveLeg" + i);
            for (int depth : legs) if (depth < 0 || depth > 16)
                throw new IllegalStateException("Invalid archived pavilion support depth");
            site = new Site(BlockPos.of(journal.getLong("archiveOrigin")), legs);
        } else {
            site = findSite(level, anchor);
            // Flush the complete ownership plan BEFORE the first block write.
            journal.putLong("archiveOrigin", site.origin().asLong());
            journal.putInt("archiveSupportVersion", 1);
            for (int i = 0; i < 4; i++) journal.putInt("archiveLeg" + i, site.legs()[i]);
            data.setDirty();
            level.getDataStorage().save();
        }
        BlockPos origin = site.origin();
        Map<BlockPos, BlockState> plan = plan(site);
        // Whole-plan retry preflight: permit only air or the exact journal-owned intended block.
        for (Map.Entry<BlockPos, BlockState> e : plan.entrySet()) {
            BlockState existing = level.getBlockState(e.getKey());
            if (!existing.isAir() && !existing.equals(e.getValue()))
                throw new IllegalStateException("Archive plan obstructed at " + e.getKey());
        }
        BlockPos chestPos = origin.offset(0, 1, 2);
        if (level.getBlockEntity(chestPos) instanceof ChestBlockEntity chest) {
            for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                ItemStack expected = slot < 4 ? books[slot] : ItemStack.EMPTY;
                ItemStack found = chest.getItem(slot);
                if (!found.isEmpty() && !ItemStack.matches(found, expected))
                    throw new IllegalStateException("Archive chest contains unowned inventory");
            }
        }
        for (Map.Entry<BlockPos, BlockState> e : plan.entrySet()) {
            if (level.getBlockState(e.getKey()).isAir() && !level.setBlock(e.getKey(), e.getValue(), 3))
                throw new IllegalStateException("Archive placement rejected at " + e.getKey());
        }
        if (!(level.getBlockEntity(chestPos) instanceof ChestBlockEntity chest))
            throw new IllegalStateException("Archive chest missing");
        for (int i = 0; i < 4; i++) if (chest.getItem(i).isEmpty()) chest.setItem(i, books[i]);
        chest.setChanged();
        BlockPos signPos = origin.offset(0, 1, -4);
        if (!(level.getBlockEntity(signPos) instanceof SignBlockEntity sign))
            throw new IllegalStateException("Archive plaque missing");
        Component[] lines = new Component[4];
        for (int i = 0; i < 4; i++) lines[i] = Component.literal(plaque.substring(Math.min(i * 64, plaque.length()), Math.min((i + 1) * 64, plaque.length())));
        sign.setText(new SignText(lines, lines, DyeColor.BLACK, false), true);
        sign.setWaxed(true);
        sign.setChanged();
        level.sendBlockUpdated(signPos, level.getBlockState(signPos), level.getBlockState(signPos), 3);
    }

    private record Site(BlockPos origin, int[] legs) {}

    private static Site findSite(ServerLevel level, BlockPos anchor) {
        for (int candidate = 0; candidate < 800; candidate++) {
            int[] offset = ArchiveText.candidate(candidate);
            BlockPos center = anchor.offset(offset[0], 0, offset[1]);
            int floorY = level.getMinBuildHeight() + 1;
            for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++)
                floorY = Math.max(floorY, level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        center.offset(x, 0, z)).getY());
            if (floorY + 4 >= level.getMaxBuildHeight()) continue;
            BlockPos origin = new BlockPos(center.getX(), floorY, center.getZ());
            boolean safe = true;
            for (int x = -4; x <= 4 && safe; x++) for (int z = -4; z <= 4 && safe; z++) {
                BlockPos base = origin.offset(x, 0, z);
                if (!level.getWorldBorder().isWithinBounds(base)) { safe = false; break; }
                for (int y = 0; y <= 4; y++) if (!level.getBlockState(base.above(y)).isAir()
                        || !level.getFluidState(base.above(y)).isEmpty()) { safe = false; break; }
            }
            if (!safe) continue;
            int[] legs = new int[4];
            int i = 0;
            for (int x : new int[]{-4, 4}) for (int z : new int[]{-4, 4}) {
                BlockPos below = origin.offset(x, -1, z);
                int depth = 0;
                while (depth <= 16 && level.getBlockState(below).isAir()
                        && level.getFluidState(below).isEmpty() && level.getBlockEntity(below) == null) {
                    depth++;
                    below = below.below();
                }
                if (depth > 16 || below.getY() <= level.getMinBuildHeight()
                        || !level.getBlockState(below).isSolidRender(level, below)
                        || !level.getFluidState(below).isEmpty() || level.getBlockEntity(below) != null) {
                    safe = false; break;
                }
                legs[i++] = depth;
            }
            if (safe) return new Site(origin, legs);
        }
        throw new IllegalStateException("No unobstructed, naturally supported 9x9 archive site in 800 bounded candidates; terrain left intact");
    }

    private static Map<BlockPos, BlockState> plan(Site site) {
        BlockPos origin = site.origin();
        Map<BlockPos, BlockState> plan = new LinkedHashMap<>();
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) {
            plan.put(origin.offset(x, 0, z), Blocks.OAK_PLANKS.defaultBlockState());
            plan.put(origin.offset(x, 4, z), Blocks.OAK_PLANKS.defaultBlockState());
        }
        for (int x : new int[]{-4, 4}) for (int z : new int[]{-4, 4}) for (int y = 1; y <= 3; y++)
            plan.put(origin.offset(x, y, z), Blocks.OAK_LOG.defaultBlockState());
        // Durable per-corner depths make a partially placed pavilion replay the same plan.
        int i = 0;
        for (int x : new int[]{-4, 4}) for (int z : new int[]{-4, 4}) {
            for (int down = 1; down <= site.legs()[i]; down++)
                plan.put(origin.offset(x, -down, z), Blocks.OAK_LOG.defaultBlockState());
            i++;
        }
        // Wall sign has a solid backing and is placed after the backing, never floating.
        plan.put(origin.offset(0, 1, -3), Blocks.OAK_PLANKS.defaultBlockState());
        plan.put(origin.offset(0, 1, -4), Blocks.OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, Direction.NORTH));
        plan.put(origin.offset(0, 1, 2), Blocks.CHEST.defaultBlockState());
        return plan;
    }
}
