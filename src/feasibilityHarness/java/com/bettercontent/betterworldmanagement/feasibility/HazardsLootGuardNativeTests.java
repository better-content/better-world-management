package com.bettercontent.betterworldmanagement.feasibility;

import com.bettercontent.betterchemlibhazards.api.LatentEmissionProfiles;
import com.bettercontent.betterchemlibhazards.sim.LatentRadiationService.RadiationEmissionEvent;
import com.bettercontent.betterworldmanagement.feasibility.observer.HazardsLootPolicyObserver;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/** Native fixture blocks/materialization only; not natural world, human menu, dose, or heat-acceptance proof. */
@GameTestHolder(FeasibilityMod.ID)
@PrefixGameTestTemplate(false)
public final class HazardsLootGuardNativeTests {
    private static final ResourceLocation HOTSTONE = new ResourceLocation("better_ore_geology", "small_ore_chunk_hotstone");
    private static final ResourceLocation NONEMPTY = new ResourceLocation(FeasibilityMod.ID, "hazards_support/nonempty_hotstone");
    private static final ResourceLocation EMPTY = new ResourceLocation(FeasibilityMod.ID, "hazards_support/empty");
    private HazardsLootGuardNativeTests() {}

    @GameTest(templateNamespace = "better_world_management_feasibility", template = "axiom_recipe_arena", timeoutTicks = 1400)
    public static void nativePendingLootAndRealMaterialization(GameTestHelper helper) {
        Session session = new Session(helper);
        try { session.install(); }
        catch (Throwable error) { session.close(); helper.fail("Hazards fixture setup: " + error); }
    }

    private static final class Session {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final List<BlockPos> positions = new ArrayList<>();
        private final List<BlockState> originals = new ArrayList<>();
        private final List<ChestBlockEntity> chests = new ArrayList<>();
        private final List<CompoundTag> pending = new ArrayList<>();
        private final long[] emissions = new long[4];
        private final long[] firstEmission = new long[4];
        private Consumer<RadiationEmissionEvent> radiation;
        private Consumer<TickEvent.ServerTickEvent> lifetime;
        private Consumer<ServerStoppedEvent> stopping;
        private boolean registeredRadiation, registeredLifetime, registeredStopping, observing, closed;
        private int phase, phaseAge, age;
        private long startGameTime, insertedAt;
        private String observationError;
        private Item item;

        Session(GameTestHelper helper) { this.helper = helper; this.level = helper.getLevel(); }

        void install() throws Exception {
            require(level.getServer().isSameThread() && level.getServer().isDedicatedServer(), "Actual dedicated server thread required");
            require(ForgeRegistries.ITEMS.containsKey(HOTSTONE), "Pinned hotstone is not registered");
            item = ForgeRegistries.ITEMS.getValue(HOTSTONE);
            ItemStack sample = new ItemStack(item);
            require(sample.is(TagKey.create(Registries.ITEM, new ResourceLocation("better_ore_geology", "radioactive_forms/uranium/small_chunks"))), "Pinned hotstone tag mismatch");
            var profile = LatentEmissionProfiles.resolve(sample).orElseThrow();
            require(profile.active() && profile.family().equals("uranium")
                    && profile.radiationStrength() == 2.0 && profile.heatStrength() == 0.5, "Pinned one-item fixed profile differs from 2.0/0.5");
            verifyLootResource(NONEMPTY, NONEMPTY_JSON);
            verifyLootResource(EMPTY, EMPTY_JSON);
            for (BlockPos relative : List.of(new BlockPos(1, 2, 1), new BlockPos(3, 2, 1), new BlockPos(1, 2, 3), new BlockPos(3, 2, 3))) {
                BlockPos pos = helper.absolutePos(relative).immutable();
                require(level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null, "Owned fixture position is not already loaded");
                require(level.getBlockState(pos).isAir() && level.getBlockEntity(pos) == null, "Refuse nonempty fixture block position");
                positions.add(pos); originals.add(level.getBlockState(pos));
            }
            startGameTime = level.getGameTime();
            HazardsLootPolicyObserver.begin(level, positions); observing = true;
            radiation = event -> {
                if (event.level() != level) return;
                int index = positions.indexOf(event.pos());
                if (index < 0) return;
                if (!level.getServer().isSameThread()) { observationError = "Off-thread radiation event"; return; }
                if (event.radiationStrength() != 2.0 || event.heatStrength() != 0.5) {
                    observationError = "Actual fixed emission strength differs from 2.0/0.5"; return;
                }
                if (emissions[index] == 0) firstEmission[index] = level.getGameTime();
                if (emissions[index] >= 1_000_000) { observationError = "Emission observation overflow"; return; }
                emissions[index]++;
                if (phase == 0 && index < 3) observationError = "Pending native loot emitted before materialization";
            };
            MinecraftForge.EVENT_BUS.addListener(radiation); registeredRadiation = true;
            lifetime = event -> {
                if (!closed && event.phase == TickEvent.Phase.END && event.getServer() == level.getServer() && ++age >= 1390) {
                    close(); // Independent owned cleanup if the test has already failed/cancelled.
                }
            };
            stopping = event -> { if (event.getServer() == level.getServer()) close(); };
            MinecraftForge.EVENT_BUS.addListener(lifetime); registeredLifetime = true;
            MinecraftForge.EVENT_BUS.addListener(stopping); registeredStopping = true;
            for (BlockPos pos : positions) {
                require(level.setBlock(pos, Blocks.CHEST.defaultBlockState(), 3), "Native chest placement failed");
                require(level.getBlockEntity(pos) instanceof ChestBlockEntity, "No actual native chest BE");
                chests.add((ChestBlockEntity) level.getBlockEntity(pos));
            }
            chests.get(0).setLootTable(NONEMPTY, 0x4a11L);
            chests.get(1).setLootTable(EMPTY, 0x4b22L);
            chests.get(2).setItem(0, new ItemStack(item)); // Genuine real content BEFORE native pending metadata.
            chests.get(2).setLootTable(EMPTY, 0x4c33L);
            for (int index = 0; index < 3; index++) {
                chests.get(index).setChanged(); // Actual native mutation registration, never a scanner call.
                CompoundTag tag = chests.get(index).saveWithoutMetadata();
                require(tag.contains("LootTable"), "Native pending metadata missing");
                pending.add(tag.copy());
            }
            chests.get(3).setItem(0, new ItemStack(item)); // Always-eligible fairness control.
            System.out.println("WLM_HAZARDS_SUPPORT setup=native_four_chests effect=dispatch_only strength=2.0/0.5 game_time=" + startGameTime);
            helper.runAfterDelay(1, this::step);
        }

        void step() {
            if (closed) return;
            try {
                require(observationError == null, "Radiation observation failure: " + observationError);
                require(HazardsLootPolicyObserver.complete(), "Policy observation incomplete/overflow/off-thread");
                require(++phaseAge <= 400, "Native phase lacked actual outcome within 400 ordinary ticks: " + phase);
                for (int index = 0; index < 4; index++) require(level.getBlockEntity(positions.get(index)) == chests.get(index), "Owned native chest replaced");
                if (phase == 0) {
                    for (int index = 0; index < 3; index++) require(chests.get(index).saveWithoutMetadata().equals(pending.get(index)), "Passive scan materialized/changed pending native loot");
                    boolean visited = true;
                    for (int index = 0; index < 3; index++) visited &= HazardsLootPolicyObserver.count(positions.get(index), true, true) > 0
                            && HazardsLootPolicyObserver.count(positions.get(index), false, true) > 0;
                    if (phaseAge >= 80 && level.getGameTime() - startGameTime >= 80 && visited && emissions[3] > 0) {
                        require(emissions[0] == 0 && emissions[1] == 0 && emissions[2] == 0, "Pending positions emitted");
                        System.out.println("WLM_HAZARDS_SUPPORT pending=observed_true_both_native_scanners fairness=actual_fixed_dispatch " + HazardsLootPolicyObserver.summary());
                        phase = 1; phaseAge = 0;
                        // Genuine public native business operation, NOT ordinary human/menu interaction proof.
                        // C receives NO extra insertion/setChanged after this empty fill.
                        chests.get(2).unpackLootTable(null);
                        chests.get(0).unpackLootTable(null);
                        chests.get(1).unpackLootTable(null);
                        for (int index = 0; index < 3; index++) require(!chests.get(index).saveWithoutMetadata().contains("LootTable"), "Native business materialization did not clear loot metadata");
                        require(singleHotstone(chests.get(0)) && singleHotstone(chests.get(2)), "Actual nonempty roll/preexisting real contents mismatch");
                        require(chests.get(1).isEmpty(), "Native empty table invented contents");
                    }
                } else if (phase == 1) {
                    require(emissions[1] == 0 && chests.get(1).isEmpty(), "Empty native roll emitted/invented an item before real insertion");
                    if (phaseAge >= 80 && emissions[0] > 0 && emissions[2] > 0
                            && HazardsLootPolicyObserver.count(positions.get(0), true, false) > 0
                            && HazardsLootPolicyObserver.count(positions.get(2), true, false) > 0) {
                        require(singleHotstone(chests.get(2)), "Real preexisting item lost after empty materialization");
                        System.out.println("WLM_HAZARDS_SUPPORT nonempty_roll=actual_dispatch empty_roll_existing_item=actual_dispatch no_extra_fixture_mutation=true");
                        insertedAt = level.getGameTime();
                        phase = 2; phaseAge = 0;
                        chests.get(1).setItem(0, new ItemStack(item)); // Real native insertion owns setChanged admission.
                    }
                } else if (phaseAge >= 40 && emissions[1] > 0 && firstEmission[1] >= insertedAt) {
                    for (int index = 0; index < 4; index++) require(singleHotstone(chests.get(index)), "Materialized fixed-item inventory changed");
                    require(HazardsLootPolicyObserver.count(positions.get(1), true, false) > 0, "No actual Nuclear eligible policy visit after real insertion");
                    System.out.println("WLM_HAZARDS_SUPPORT PASS scope=native_loot_and_fixed_dispatch not_human_menu=true not_dose_or_heat_acceptance=true game_time="
                            + level.getGameTime() + " " + HazardsLootPolicyObserver.summary());
                    close();
                    helper.succeed();
                    return;
                }
                helper.runAfterDelay(1, this::step);
            } catch (Throwable error) {
                String proof = HazardsLootPolicyObserver.summary();
                try { close(); }
                catch (Throwable cleanup) { error.addSuppressed(cleanup); }
                helper.fail("Hazards native support regression: " + error + " " + proof);
            }
        }

        boolean singleHotstone(ChestBlockEntity chest) {
            int count = 0;
            for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                ItemStack stack = chest.getItem(slot); // ONLY after actual materialization, never pending.
                if (!stack.isEmpty()) {
                    if (!stack.is(item)) return false;
                    count += stack.getCount();
                }
            }
            return count == 1;
        }

        void verifyLootResource(ResourceLocation table, String expected) throws Exception {
            ResourceLocation path = new ResourceLocation(table.getNamespace(), "loot_tables/" + table.getPath() + ".json");
            var resource = level.getServer().getResourceManager().getResource(path).orElseThrow();
            byte[] bytes;
            try (InputStream input = resource.open()) { bytes = input.readNBytes(8193); }
            require(bytes.length <= 8192 && new String(bytes, StandardCharsets.UTF_8).equals(expected), "Actual startup-loaded fixture loot resource differs: " + path);
            // Resource existence + exact bytes disambiguate deliberately empty data from missing-table fallback.
            level.getServer().getLootData().getLootTable(table);
            System.out.println("WLM_HAZARDS_SUPPORT loot_resource=" + path + " sha256=" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        }

        void close() {
            if (closed) return;
            closed = true;
            if (registeredRadiation) MinecraftForge.EVENT_BUS.unregister(radiation);
            if (registeredLifetime) MinecraftForge.EVENT_BUS.unregister(lifetime);
            if (registeredStopping) MinecraftForge.EVENT_BUS.unregister(stopping);
            if (observing) HazardsLootPolicyObserver.end(level);
            // Restore only positions this session actually placed, not other template blocks or another BE.
            for (int index = 0; index < chests.size(); index++) {
                BlockPos pos = positions.get(index);
                if (level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null)
                    throw new IllegalStateException("Owned cleanup chunk no longer resident at " + pos);
                if (level.getBlockEntity(pos) != chests.get(index)) throw new IllegalStateException("Owned cleanup identity changed at " + pos);
                // Remove only our actual BE first so native chest removal cannot roll pending loot or drop fixture items.
                level.removeBlockEntity(pos);
                if (!level.setBlock(pos, originals.get(index), 3)) throw new IllegalStateException("Owned cleanup failed at " + pos);
            }
        }
    }

    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private static final String NONEMPTY_JSON = "{\"type\":\"minecraft:chest\",\"pools\":[{\"rolls\":1,\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"better_ore_geology:small_ore_chunk_hotstone\"}]}]}\n";
    private static final String EMPTY_JSON = "{\"type\":\"minecraft:chest\",\"pools\":[]}\n";
}
