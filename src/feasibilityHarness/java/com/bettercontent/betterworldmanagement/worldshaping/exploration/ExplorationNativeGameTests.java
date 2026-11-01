package com.bettercontent.betterworldmanagement.worldshaping.exploration;

import java.util.Set;
import com.bettercontent.celestial.calendar.CalendarSavedData;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/** Non-shipping native fixtures. Requires all three pinned owner mods; absent APIs fail rather than skip. */
@PrefixGameTestTemplate(false)
public final class ExplorationNativeGameTests {
    private ExplorationNativeGameTests() {}
    @GameTest(templateNamespace="better_world_management", template="empty", timeoutTicks=100)
    public static void explorationReceiptRoundtripDropsCoordinates(GameTestHelper helper) {
        CompoundTag hostile = new CompoundTag(); ListTag list = new ListTag();
        list.add(StringTag.valueOf("minecraft:monument")); list.add(StringTag.valueOf("minecraft:mansion"));
        list.add(StringTag.valueOf("minecraft:mineshaft")); list.add(StringTag.valueOf("minecraft:fortress"));
        list.add(StringTag.valueOf("minecraft:village_plains")); hostile.put("structures", list);
        hostile.putLong("oldCoordinates", 123456789); hostile.putString("dimension", "minecraft:overworld");
        CompoundTag saved = ExplorationDiscoveries.load(hostile).export();
        helper.assertTrue(saved.getAllKeys().equals(Set.of("structures")), "Export must contain only structure identifiers");
        helper.assertTrue(ExplorationDiscoveries.readIds(saved).equals(ExplorationDiscoveries.ALLOWED), "All four receipts must survive native NBT roundtrip");
        ExplorationDiscoveries data = ExplorationDiscoveries.load(new CompoundTag());
        data.recordEntry("minecraft:village_plains"); data.recordEntry("arbitrary:injected");
        helper.assertTrue(ExplorationDiscoveries.readIds(data.export()).isEmpty(), "Unapproved structure entries must fail closed");
        helper.succeed();
    }
    @GameTest(templateNamespace="better_world_management", template="empty", timeoutTicks=100)
    public static void explorationCalendarReadPreservesSavedState(GameTestHelper helper) throws Exception {
        var server = helper.getLevel().getServer();
        var calendar = CalendarSavedData.get(server).calendar();
        var before = calendar.state();
        String forecast = ExplorationNative.forecast(server);
        var after = calendar.state();
        helper.assertTrue(before.equals(after), "Forecast must not tick, draw, force, reorder or mutate the saved calendar");
        helper.assertTrue(!forecast.isBlank(), "Native calendar forecast must identify the saved event/date");
        helper.succeed();
    }
    @GameTest(templateNamespace="better_world_management", template="empty", timeoutTicks=100)
    public static void explorationUsesOnlyNativeSurfaceSampleRegistry(GameTestHelper helper) throws Exception {
        var samples = ExplorationNative.surfaceSamples();
        helper.assertTrue(!samples.isEmpty(), "Pinned native sample registry must be available and nonempty");
        for (var block : samples) {
            var key = ForgeRegistries.BLOCKS.getKey(block);
            helper.assertTrue(key != null && key.getNamespace().equals("better_ore_geology")
                    && key.getPath().startsWith("surface_sample_"), "Survey must never include hidden native ore blocks");
        }
        helper.succeed();
    }
    @GameTest(templateNamespace="better_world_management", template="empty", timeoutTicks=100)
    public static void explorationFontEmptyWhitelistDoesNotLoadChunks(GameTestHelper helper) throws Exception {
        var level = helper.getLevel(); int before = level.getChunkSource().getLoadedChunksCount();
        var offer = ExplorationNative.fontOffer(level, helper.absolutePos(net.minecraft.core.BlockPos.ZERO), Items.EMERALD, Set.of());
        helper.assertTrue(offer == null, "Owner API must refuse destinations outside the supplied native market whitelist");
        helper.assertTrue(before == level.getChunkSource().getLoadedChunksCount(), "Font query must not request chunks");
        helper.succeed();
    }
    @GameTest(templateNamespace="better_world_management", template="empty", timeoutTicks=100)
    public static void explorationClipReadsAreHardBudgetedAndNeverLoadChunks(GameTestHelper helper) {
        var level = helper.getLevel(); int loadedBefore = level.getChunkSource().getLoadedChunksCount();
        var unloaded = new net.minecraft.core.BlockPos(29_000_000, 80, 29_000_000);
        helper.assertTrue(level.getChunkSource().getChunkNow(unloaded.getX() >> 4, unloaded.getZ() >> 4) == null,
                "Fixture requires a genuinely absent chunk");
        var view = new LoadedSurveyView(level, 1);
        view.getBlockState(unloaded);
        helper.assertTrue(view.unknown && view.reads == 1, "Unknown state must fail closed and consume one read");
        view.getFluidState(unloaded);
        helper.assertTrue(view.exhausted && view.reads == 1, "Collision/neighbor reads cannot exceed the shared budget");
        helper.assertTrue(loadedBefore == level.getChunkSource().getLoadedChunksCount(), "Survey view must never load absent chunks");
        helper.succeed();
    }
    @GameTest(templateNamespace="better_world_management", template="empty", timeoutTicks=100)
    public static void explorationTrueChunkBoundaryIsInclusiveAndOverflowSafe(GameTestHelper helper) {
        helper.assertTrue(BoundedStructureSearch.within(-100, -100, -164, -36), "64 chunk edge must be admitted");
        helper.assertTrue(!BoundedStructureSearch.within(-100, -100, -165, -36), "65 chunks must be refused");
        helper.assertTrue(!BoundedStructureSearch.within(Integer.MIN_VALUE, 0, Integer.MAX_VALUE, 0), "Coordinate subtraction must not overflow");
        helper.succeed();
    }
}
