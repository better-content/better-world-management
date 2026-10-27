package com.bettercontent.betterworldmanagement.worldshaping.exploration;

import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.structures.MineshaftPieces;
import net.minecraft.world.level.levelgen.structure.structures.MineshaftStructure;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Real native structure-manager/piece fixture, not evidence of natural mineshaft generation. Fresh test world required. */
@PrefixGameTestTemplate(false)
public final class ExplorationEntryGameTests {
    @GameTest(templateNamespace="better_world_management", template="empty", timeoutTicks=100)
    public static void explorationContainingNativePieceNotReferenceRecordsEntry(GameTestHelper helper) {
        var level = helper.getLevel(); var server = level.getServer();
        helper.assertTrue(!ExplorationDiscoveries.readIds(ExplorationEffects.discoveries(server)).contains("minecraft:mineshaft"),
                "Fixture needs a fresh world without a prior mineshaft receipt");
        var player = helper.makeMockServerPlayerInLevel();
        var anchor = helper.absolutePos(new net.minecraft.core.BlockPos(1, 2, 1));
        ChunkPos pos = new ChunkPos(anchor); var chunk = level.getChunk(pos.x, pos.z);
        var structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(new ResourceLocation("minecraft:mineshaft"));
        helper.assertTrue(structure != null, "Pinned vanilla mineshaft must be registered");
        var previousStart = chunk.getStartForStructure(structure);
        var previousReferences = new java.util.HashMap<>(chunk.getAllReferences());
        var room = new MineshaftPieces.MineShaftRoom(0, RandomSource.create(42), pos.getMinBlockX()+2,
                pos.getMinBlockZ()+2, MineshaftStructure.Type.NORMAL);
        room.move(0, anchor.getY() - room.getBoundingBox().minY(), 0);
        var start = new StructureStart(structure, pos, 0, new PiecesContainer(List.of(room)));
        try {
            chunk.setStartForStructure(structure, start);
            chunk.setAllReferences(new java.util.HashMap<>(previousReferences));
            chunk.getAllReferences().put(structure, new it.unimi.dsi.fastutil.longs.LongOpenHashSet());
            chunk.addReferenceForStructure(structure, pos.toLong());
            // The reference and containing chunk alone must not trigger discovery above the actual piece.
            player.setPos(room.getBoundingBox().minX()+0.5, room.getBoundingBox().maxY()+8, room.getBoundingBox().minZ()+0.5);
            ExplorationEffects.recordEntry(player);
            helper.assertTrue(!ExplorationDiscoveries.readIds(ExplorationEffects.discoveries(server)).contains("minecraft:mineshaft"),
                    "A reference without piece entry must not create a receipt");
            player.setPos(room.getBoundingBox().minX()+0.5, room.getBoundingBox().minY()+1, room.getBoundingBox().minZ()+0.5);
            ExplorationEffects.recordEntry(player);
            helper.assertTrue(ExplorationDiscoveries.readIds(ExplorationEffects.discoveries(server)).contains("minecraft:mineshaft"),
                    "Entering a real native containing piece must save the whitelisted ID");
            helper.succeed();
        } finally {
            chunk.setStartForStructure(structure, previousStart == null ? StructureStart.INVALID_START : previousStart);
            chunk.setAllReferences(previousReferences);
        }
    }
}
