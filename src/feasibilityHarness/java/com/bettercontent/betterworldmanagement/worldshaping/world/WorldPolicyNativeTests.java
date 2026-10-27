package com.bettercontent.betterworldmanagement.worldshaping.world;

import com.bettercontent.betterworldmanagement.worldshaping.WorldAxiomService;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Non-shipping fixture; not native acceptance until explicitly registered and executed by root. */
@GameTestHolder("better_world_management")
@PrefixGameTestTemplate(false)
public final class WorldPolicyNativeTests {
    private WorldPolicyNativeTests() {}

    @GameTest(templateNamespace = "better_world_management", template = "empty", timeoutTicks = 100)
    public static void archiveWritesFourBoundedBooksAndRetriesWithoutDuplication(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(5, 3, 5));
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) {
            helper.getLevel().setBlockAndUpdate(origin.offset(x, -1, z), Blocks.STONE.defaultBlockState());
            for (int y = 0; y <= 4; y++) helper.getLevel().setBlockAndUpdate(origin.offset(x, y, z), Blocks.AIR.defaultBlockState());
        }
        CompoundTag config = new CompoundTag();
        config.putString("plaque", "The previous generation remembers.");
        for (int i = 1; i <= 4; i++) config.putString("book" + i, "Book " + i + "\nSecond page\nThird page\n" + "x".repeat(256));
        CompoundTag journal = new CompoundTag();
        journal.putLong("archiveOrigin", origin.asLong());
        WorldPolicyData data = new WorldPolicyData();
        CommunalArchive.apply(helper.getLevel(), origin, config, journal, data);
        CommunalArchive.apply(helper.getLevel(), origin, config, journal, data);
        helper.assertTrue(helper.getLevel().getBlockEntity(origin.offset(0, 1, 2)) instanceof ChestBlockEntity, "Missing native chest");
        ChestBlockEntity chest = (ChestBlockEntity) helper.getLevel().getBlockEntity(origin.offset(0, 1, 2));
        int count = 0;
        for (int i = 0; i < chest.getContainerSize(); i++) {
            if (!chest.getItem(i).isEmpty()) {
                helper.assertTrue(chest.getItem(i).is(Items.WRITTEN_BOOK), "Unexpected archive item");
                helper.assertTrue(chest.getItem(i).getTag().getList("pages", 8).size() == 4, "Wrong native book pages");
                count += chest.getItem(i).getCount();
            }
        }
        helper.assertTrue(count == 4, "Archive retry duplicated books");
        helper.succeed();
    }

    @GameTest(templateNamespace = "better_world_management", template = "empty", timeoutTicks = 100)
    public static void archiveRefusesObstructionWithoutPartialPlacement(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(5, 3, 5));
        CompoundTag config = new CompoundTag();
        CompoundTag journal = new CompoundTag();
        journal.putLong("archiveOrigin", origin.asLong());
        helper.getLevel().setBlockAndUpdate(origin, Blocks.DIAMOND_BLOCK.defaultBlockState());
        boolean rejected = false;
        try { CommunalArchive.apply(helper.getLevel(), origin, config, journal, new WorldPolicyData()); }
        catch (IllegalStateException expected) { rejected = true; }
        helper.assertTrue(rejected, "Obstructed plan accepted");
        helper.assertTrue(helper.getLevel().getBlockState(origin).is(Blocks.DIAMOND_BLOCK), "Existing block overwritten");
        helper.succeed();
    }

    /** Authored native metadata ONLY: not proof of a naturally generated successor village. */
    @GameTest(templateNamespace = "better_world_management", template = "empty", timeoutTicks = 100)
    public static void loadedVillagePiecePredicateAcceptsRoadRejectsHazardsAndMissingChunks(GameTestHelper helper) {
        var level = helper.getLevel();
        var chunk = level.getChunkAt(helper.absolutePos(new BlockPos(4, 3, 4)));
        BlockPos feet = new BlockPos(chunk.getPos().getMinBlockX() + 8, helper.absolutePos(new BlockPos(0, 3, 0)).getY(), chunk.getPos().getMinBlockZ() + 8);
        var oldStarts = new java.util.HashMap<>(chunk.getAllStarts());
        var oldReferences = new java.util.HashMap<net.minecraft.world.level.levelgen.structure.Structure, it.unimi.dsi.fastutil.longs.LongSet>();
        chunk.getAllReferences().forEach((structure, refs) -> oldReferences.put(structure, new it.unimi.dsi.fastutil.longs.LongOpenHashSet(refs)));
        var oldBelow = level.getBlockState(feet.below());
        var oldFeet = level.getBlockState(feet);
        var oldHead = level.getBlockState(feet.above());
        try {
            var registry = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);
            var village = registry.getTag(net.minecraft.tags.StructureTags.VILLAGE).orElseThrow().iterator().next().value();
            chunk.setAllStarts(new java.util.HashMap<>());
            chunk.setAllReferences(new java.util.HashMap<>());
            level.setBlockAndUpdate(feet.below(), Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(feet, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(feet.above(), Blocks.AIR.defaultBlockState());
            String biome = level.getBiome(feet).unwrapKey().orElseThrow().location().toString();
            helper.assertTrue(!WorldPolicies.eligibleVillagePiece(level, feet, biome), "Safe terrain without native village metadata accepted");
            var road = new MetadataOnlyPiece(new net.minecraft.world.level.levelgen.structure.BoundingBox(feet.getX(), feet.getY() - 1, feet.getZ(), feet.getX(), feet.getY() - 1, feet.getZ()));
            var start = new net.minecraft.world.level.levelgen.structure.StructureStart(village, chunk.getPos(), 0,
                    new net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer(java.util.List.of(road)));
            chunk.setStartForStructure(village, start);
            chunk.addReferenceForStructure(village, chunk.getPos().toLong());
            helper.assertTrue(WorldPolicies.eligibleVillagePiece(level, feet, biome), "Native tagged village piece immediately below feet rejected");
            helper.assertTrue(!WorldPolicies.eligibleVillagePiece(level, feet, "fixture:wrong_biome"), "Wrong biome accepted");
            level.setBlockAndUpdate(feet.below(), Blocks.MAGMA_BLOCK.defaultBlockState());
            helper.assertTrue(!WorldPolicies.eligibleVillagePiece(level, feet, biome), "Hazardous support accepted");
            level.setBlockAndUpdate(feet.below(), Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(feet, Blocks.WATER.defaultBlockState());
            helper.assertTrue(!WorldPolicies.eligibleVillagePiece(level, feet, biome), "Fluid feet accepted");
            level.setBlockAndUpdate(feet, Blocks.AIR.defaultBlockState());
            BlockPos distant = new BlockPos(29_000_008, feet.getY(), 29_000_008);
            int loadedBefore = level.getChunkSource().getLoadedChunksCount();
            helper.assertTrue(!WorldPolicies.eligibleVillagePiece(level, distant, biome), "Unloaded candidate accepted");
            helper.assertTrue(level.getChunkSource().getLoadedChunksCount() == loadedBefore, "Predicate activated a missing chunk");
        } finally {
            chunk.setAllStarts(oldStarts);
            chunk.setAllReferences(oldReferences);
            level.setBlockAndUpdate(feet.below(), oldBelow);
            level.setBlockAndUpdate(feet, oldFeet);
            level.setBlockAndUpdate(feet.above(), oldHead);
        }
        helper.succeed();
    }

    private static final class MetadataOnlyPiece extends net.minecraft.world.level.levelgen.structure.StructurePiece {
        MetadataOnlyPiece(net.minecraft.world.level.levelgen.structure.BoundingBox bounds) {
            super(net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType.MINE_SHAFT_ROOM, 0, bounds);
        }
        @Override protected void addAdditionalSaveData(net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext context, CompoundTag tag) {}
        @Override public void postProcess(net.minecraft.world.level.WorldGenLevel level, net.minecraft.world.level.StructureManager manager,
                net.minecraft.world.level.chunk.ChunkGenerator generator, net.minecraft.util.RandomSource random,
                net.minecraft.world.level.levelgen.structure.BoundingBox bounds, net.minecraft.world.level.ChunkPos chunk, BlockPos pos) {}
    }

    @GameTest(templateNamespace = "better_world_management", template = "empty", timeoutTicks = 100)
    public static void disabledWorldPoliciesDoNotPlaceOrAdjust(GameTestHelper helper) {
        var level = helper.getLevel();
        for (String id : java.util.List.of("turning_year", "founders_exchange", "chosen_omen", "communal_archive", "settled_arrival"))
            helper.assertTrue(!WorldAxiomService.has(level.getServer(), id), "Disabled fixture has paid policy " + id);
        BlockPos p = helper.absolutePos(new BlockPos(3, 3, 3));
        var before = level.getBlockState(p);
        WorldPolicies.apply(level, p, "disabled-world-policy-fixture");
        helper.assertTrue(WorldPolicies.adjustLanding(level.getServer(), level, p, "minecraft:plains").equals(p), "Disabled landing changed");
        helper.assertTrue(WorldPolicies.eligibleLanding(level, new BlockPos(29_000_008, p.getY(), 29_000_008), "fixture:wrong_biome"), "Disabled landing predicate was not a no-op");
        helper.assertTrue(level.getBlockState(p).equals(before), "Disabled policies mutated terrain");
        helper.succeed();
    }
}
