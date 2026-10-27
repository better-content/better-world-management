package com.bettercontent.betterworldmanagement.feasibility;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.joefoxe.hexerei.tileentity.MixingCauldronTile;
import wayoftime.bloodmagic.common.tile.TileAltar;
import wayoftime.bloodmagic.recipe.RecipeBloodAltar;
import wayoftime.bloodmagic.common.item.ItemDaggerOfSacrifice;
import wayoftime.bloodmagic.ConfigManager;
import wayoftime.bloodmagic.impl.BloodMagicAPI;
import wayoftime.bloodmagic.util.helper.PlayerSacrificeHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.common.util.FakePlayer;
import java.util.List;
import java.util.UUID;
import com.mojang.authlib.GameProfile;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.handshake.ClientIntentionPacket;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.network.NetworkConstants;
import net.minecraftforge.network.ConnectionType;
import io.netty.buffer.Unpooled;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/** Nonshipping, loaded-pack native tests. Root registers and runs two separate
 * disposable runtimes with -Dworld_axioms.recipes.mode=enabled|disabled and a
 * matching before-start supervisor projection. No runtime recipe injection. */
@GameTestHolder("better_world_management_feasibility")
@PrefixGameTestTemplate(false)
public final class RecipeAxiomGameTests {
    private RecipeAxiomGameTests() {}
    private static final String PREFIX = "kubejs:world_axioms/";
    private static final String[] RECIPES = {
        "iron_law/refractory_mix", "iron_law/smelting", "iron_law/blasting",
        "eternal_engine/small_water_wheel", "alloy_testament/brass_billet", "alloy_testament/unpack_billet",
        "captive_hearth/firebox", "spirit_awakening/altar",
        "chalk_covenant/white_impure", "patient_alchemist/preparation", "patient_alchemist/moon_dust"
    };
    private static ResourceLocation id(String value) { return new ResourceLocation(value); }
    private static boolean enabled(GameTestHelper helper) {
        String mode = System.getProperty("world_axioms.recipes.mode", "");
        helper.assertTrue(mode.equals("enabled") || mode.equals("disabled"),
                "Set explicit fixture mode enabled|disabled; implicit fixture acceptance forbidden");
        return mode.equals("enabled");
    }
    private static Item item(GameTestHelper helper, String name) {
        helper.assertTrue(ForgeRegistries.ITEMS.containsKey(id(name)), "Missing pinned item " + name);
        return ForgeRegistries.ITEMS.getValue(id(name));
    }

    @GameTest(templateNamespace = "better_world_management", template = "empty", timeoutTicks = 40)
    public static void projectionControlsOnlyAdditiveRecipes(GameTestHelper helper) {
        boolean active = enabled(helper);
        var manager = helper.getLevel().getRecipeManager();
        for (String suffix : RECIPES) {
            helper.assertTrue(manager.byKey(id(PREFIX + suffix)).isPresent() == active,
                    "Loaded projection mismatch for " + suffix);
        }
        for (String ordinary : new String[] { "malum:spirit_altar", "kubejs:bloodmagic/deep_dagger_of_sacrifice",
                "kubejs:fonts_spine/hexerei_cauldron/moon_dust_aether",
                "kubejs:fonts_spine/hexerei_cauldron/occultism_chalk_white_impure",
                "better_industrial_heat:heat_pipe" }) {
            helper.assertTrue(manager.byKey(id(ordinary)).isPresent(), "Ordinary recipe lost: " + ordinary);
        }
        RecipeBloodAltar dagger = bloodRecipe(helper);
        helper.assertTrue(dagger.getMinimumTier() == (active ? 1 : 4), "Only selected fixture may lower exact native dagger tier");
        helper.assertTrue(dagger.getSyphon() == 120000 && dagger.getConsumeRate() == 80 && dagger.getDrainRate() == 80,
                "Effective dagger must retain LP/rates in enabled AND disabled lifetimes");
        System.out.println("WLM_RECIPE_AXIOMS loaded_projection=" + (active ? "enabled" : "disabled") + " PASS");
        helper.succeed();
    }

    @GameTest(templateNamespace = "better_world_management", template = "empty", timeoutTicks = 180)
    public static void nativeBasinConsumesZincAndConservesBrass(GameTestHelper helper) {
        boolean active = enabled(helper);
        var basinId = id("tconstruct:seared_basin");
        helper.assertTrue(ForgeRegistries.BLOCKS.containsKey(basinId), "Pinned native casting basin missing");
        BlockPos relative = new BlockPos(1, 1, 1);
        helper.setBlock(relative, ForgeRegistries.BLOCKS.getValue(basinId));
        var entity = helper.getLevel().getBlockEntity(helper.absolutePos(relative));
        helper.assertTrue(entity instanceof Container, "Native basin must expose actual inventory");
        Container inventory = (Container) entity;
        Item zinc = item(helper, "create:zinc_ingot");
        inventory.setItem(0, new ItemStack(zinc));
        var handler = entity.getCapability(ForgeCapabilities.FLUID_HANDLER).orElseThrow(
                () -> new IllegalStateException("Native basin fluid capability missing"));
        var copperId = id("tconstruct:molten_copper");
        helper.assertTrue(ForgeRegistries.FLUIDS.containsKey(copperId), "Pinned molten copper missing");
        int filled = handler.fill(new FluidStack(ForgeRegistries.FLUIDS.getValue(copperId), 90), IFluidHandler.FluidAction.EXECUTE);
        if (!active) {
            helper.assertTrue(filled == 0, "Disabled fixture unexpectedly accepts zinc/copper brass casting");
            helper.runAfterDelay(100, () -> {
                helper.assertTrue(inventory.getItem(0).is(zinc) && inventory.getItem(0).getCount() == 1,
                        "Disabled fixture consumed zinc");
                helper.assertTrue(inventory.getItem(1).isEmpty(), "Disabled fixture created output");
                helper.succeed();
            });
            return;
        }
        helper.assertTrue(filled == 90, "Enabled native basin must accept exactly 90 mB copper");
        helper.runAfterDelay(100, () -> {
            Item billet = item(helper, "kubejs:brass_billet");
            helper.assertTrue(inventory.getItem(0).isEmpty(), "Zinc cast must be consumed, not reused");
            ItemStack output = inventory.getItem(1);
            helper.assertTrue(output.is(billet) && output.getCount() == 1, "Native basin must produce ONE physical two-ingot billet");
            helper.assertTrue(handler.getFluidInTank(0).isEmpty(), "Copper must be consumed");
            ItemStack physicalBillet = inventory.removeItem(1, 1);
            helper.assertTrue(inventory.getItem(1).isEmpty(), "Actual billet transfer must leave native basin output empty");
            verifyUnpacking(helper, physicalBillet);
            System.out.println("WLM_RECIPE_AXIOMS native_basin_90Cu_90Zn_to_180Brass=PASS");
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "better_world_management", template = "empty", timeoutTicks = 1000)
    public static void refractoryMixCraftThenActualFurnaceAndBlast(GameTestHelper helper) {
        if (!enabled(helper)) {
            helper.assertTrue(helper.getLevel().getRecipeManager().byKey(id(PREFIX + "iron_law/refractory_mix")).isEmpty(),
                    "Disabled refractory recipe present");
            helper.succeed();
            return;
        }
        ItemStack mix = assemble(helper, "iron_law/refractory_mix", 3, 3, new String[] {
                "better_ore_geology:hotstone", "better_ore_geology:black_shale", "minecraft:sand",
                "minecraft:sand", "minecraft:gravel", "minecraft:gravel", "", "", ""
        }, "kubejs:lineage_refractory_mix", 4);
        BlockPos furnacePos = new BlockPos(1, 1, 1), blastPos = new BlockPos(2, 1, 1);
        helper.setBlock(furnacePos, Blocks.FURNACE);
        helper.setBlock(blastPos, Blocks.BLAST_FURNACE);
        Container furnace = (Container) helper.getLevel().getBlockEntity(helper.absolutePos(furnacePos));
        Container blast = (Container) helper.getLevel().getBlockEntity(helper.absolutePos(blastPos));
        furnace.setItem(0, mix.copyWithCount(2));
        blast.setItem(0, mix.copyWithCount(2));
        furnace.setItem(1, new ItemStack(net.minecraft.world.item.Items.COAL));
        blast.setItem(1, new ItemStack(net.minecraft.world.item.Items.COAL));
        helper.runAfterDelay(900, () -> {
            for (Container machine : new Container[] { furnace, blast }) {
                helper.assertTrue(machine.getItem(0).isEmpty(), "Physical refractory input not consumed");
                helper.assertTrue(machine.getItem(2).is(item(helper, "tconstruct:seared_brick")) && machine.getItem(2).getCount() == 2,
                        "Each refractory mix must produce exactly one brick via native furnace/blast ticking");
            }
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "better_world_management", template = "empty", timeoutTicks = 40)
    public static void nativeCraftingWheelFireboxAndSpiritAltar(GameTestHelper helper) {
        if (!enabled(helper)) {
            for (String suffix : new String[] { "eternal_engine/small_water_wheel", "captive_hearth/firebox", "spirit_awakening/altar" })
                helper.assertTrue(helper.getLevel().getRecipeManager().byKey(id(PREFIX + suffix)).isEmpty(), "Disabled counterpart present " + suffix);
            helper.succeed();
            return;
        }
        String p = "minecraft:oak_planks", b = "minecraft:brick", r = "malum:runewood_planks";
        assemble(helper, "eternal_engine/small_water_wheel", 3, 3,
                new String[] { p,p,p,p,"create:andesite_alloy",p,p,p,p }, "create:water_wheel", 1);
        assemble(helper, "captive_hearth/firebox", 3, 3,
                new String[] { b,b,b,b,"minecraft:furnace",b,b,b,b }, "better_industrial_heat:thermal_firebox", 1);
        assemble(helper, "spirit_awakening/altar", 3, 3, new String[] {
                "better_ore_geology:black_shale", "malum:processed_soulstone", "",
                "malum:wicked_spirit", r, "malum:wicked_spirit", r,r,r
        }, "malum:spirit_altar", 1);
        helper.succeed();
    }

    // Separate batches isolate the disposable server's moon time. Root should
    // run these batches serially; no client/gameplay input is synthesized.
    @GameTest(batch = "axiom_cauldron_baseline", templateNamespace = "better_world_management", template = "empty", timeoutTicks = 200)
    public static void actualCauldronBaselineEightInputs(GameTestHelper helper) {
        enabled(helper);
        helper.getLevel().setDayTime(18000L); // full moon at night, native condition
        cauldron(helper, new String[] { "minecraft:redstone", "minecraft:glowstone_dust", "aether:ambrosium_shard", "aether:zanite_gemstone",
                "minecraft:redstone", "minecraft:glowstone_dust", "aether:ambrosium_shard", "aether:zanite_gemstone" },
                // Pinned ordinary route's existing water behavior is retained;
                // scoped adapter must NEVER change this baseline recipe.
                "hexerei:moon_dust", 4, false, true, 1000, 0);
    }

    @GameTest(batch = "axiom_cauldron_patient", templateNamespace = "better_world_management", template = "empty", timeoutTicks = 200)
    public static void actualCauldronPatientPreparationFiveInputs(GameTestHelper helper) {
        boolean active = enabled(helper);
        helper.getLevel().setDayTime(102000L); // new moon DAY; counterpart has no moon gate
        if (active) assemble(helper, "patient_alchemist/preparation", 3, 3, new String[] {
                "minecraft:redstone", "minecraft:redstone", "minecraft:glowstone_dust", "minecraft:glowstone_dust",
                "minecraft:amethyst_shard", "minecraft:amethyst_shard", "", "", ""
        }, "kubejs:amethyst_preparation", 1);
        cauldron(helper, new String[] { "kubejs:amethyst_preparation", "aether:ambrosium_shard", "aether:ambrosium_shard",
                "aether:zanite_gemstone", "aether:zanite_gemstone" }, "hexerei:moon_dust", 4, false, active, 1000);
    }

    @GameTest(batch = "axiom_cauldron_chalk", templateNamespace = "better_world_management", template = "empty", timeoutTicks = 200)
    public static void actualCauldronHeatedWhiteChalk(GameTestHelper helper) {
        cauldron(helper, chalkInputs(), "occultism:chalk_white_impure", 1, true, enabled(helper), 1000);
    }

    @GameTest(batch = "axiom_cauldron_chalk_cold", templateNamespace = "better_world_management", template = "empty", timeoutTicks = 200)
    public static void actualChalkRejectsUnheatedCauldron(GameTestHelper helper) {
        enabled(helper);
        cauldron(helper, chalkInputs(), "occultism:chalk_white_impure", 1, false, false, 1000);
    }

    @GameTest(batch = "axiom_cauldron_low_water", templateNamespace = "better_world_management", template = "empty", timeoutTicks = 200)
    public static void actualChalkRejectsInsufficientWater(GameTestHelper helper) {
        enabled(helper);
        cauldron(helper, chalkInputs(), "occultism:chalk_white_impure", 1, true, false, 249);
    }

    private static String[] chalkInputs() {
        return new String[] { "occultism:burnt_otherstone", "occultism:otherworld_ashes", "better_ore_geology:black_shale",
                "minecraft:amethyst_shard", "malum:sacred_spirit", "malum:wicked_spirit" };
    }

    private static void cauldron(GameTestHelper helper, String[] inputs, String output, int count,
                                 boolean heated, boolean shouldCraft, int water) {
        cauldron(helper, inputs, output, count, heated, shouldCraft, water, 250);
    }

    private static void cauldron(GameTestHelper helper, String[] inputs, String output, int count,
                                 boolean heated, boolean shouldCraft, int water, int expectedConsumed) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos.below(), heated ? Blocks.CAMPFIRE : Blocks.AIR);
        var block = ForgeRegistries.BLOCKS.getValue(id("hexerei:mixing_cauldron"));
        helper.assertTrue(block != null && block != Blocks.AIR, "Pinned Hexerei cauldron missing");
        helper.setBlock(pos, block);
        BlockEntity entity = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        helper.assertTrue(entity instanceof MixingCauldronTile, "Native Hexerei station type missing");
        MixingCauldronTile cauldron = (MixingCauldronTile) entity;
        Container inventory = cauldron;
        IFluidHandler fluid = cauldron;
        helper.assertTrue(fluid.fill(new FluidStack(Fluids.WATER, water), IFluidHandler.FluidAction.EXECUTE) == water,
                "Native cauldron must accept exact fixture water amount");
        for (int slot = 0; slot < inputs.length; slot++) inventory.setItem(slot, new ItemStack(item(helper, inputs[slot])));
        cauldron.craft(); // native public station operation; does not skip delay or inject recipes
        helper.runAfterDelay(150, () -> {
            if (shouldCraft) {
                ItemStack result = inventory.getItem(8);
                helper.assertTrue(result.is(item(helper, output)) && result.getCount() == count, "Native cauldron wrong output/count for " + output);
                for (int slot = 0; slot < 8; slot++) helper.assertTrue(inventory.getItem(slot).isEmpty(), "Cauldron did not consume input slot " + slot);
                // Strict contract oracle, intentionally NOT reduced to checking JSON.
                // Pinned bytecode suggests this may fail: field is threshold-only.
                helper.assertTrue(fluid.getFluidInTank(0).getAmount() == water - expectedConsumed,
                        "Native cauldron wrong actual water consumption; required=" + expectedConsumed + " remaining=" + fluid.getFluidInTank(0).getAmount());
                cauldron.craft();
                helper.assertTrue(fluid.getFluidInTank(0).getAmount() == water - expectedConsumed,
                        "Already-completed/empty-input recraft must never drain water again");
                helper.assertTrue(inventory.getItem(8).getCount() == count, "Empty-input recraft duplicated native output");
            } else {
                helper.assertTrue(inventory.getItem(8).isEmpty(), "Forbidden/disabled cauldron route crafted output");
                helper.assertTrue(fluid.getFluidInTank(0).getAmount() == water, "Rejected craft changed water");
                for (int slot = 0; slot < inputs.length; slot++) helper.assertTrue(inventory.getItem(slot).is(item(helper, inputs[slot]))
                        && inventory.getItem(slot).getCount() == 1, "Rejected craft consumed input " + slot);
            }
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "better_world_management", template = "empty", timeoutTicks = 2200)
    public static void actualBloodAltarTierTwoAndTierOneNegative(GameTestHelper helper) {
        boolean active = enabled(helper);
        if (active) {
            RecipeBloodAltar nativeRecipe = bloodRecipe(helper);
            helper.assertTrue(nativeRecipe.getMinimumTier() == 1, "Native serialized dagger must require Tier II (ordinal1)");
            helper.assertTrue(nativeRecipe.getSyphon() == 120000, "Native serialized dagger must cost120000LP");
            helper.assertTrue(nativeRecipe.getConsumeRate() == 80 && nativeRecipe.getDrainRate() == 80,
                    "Native serialized dagger must retain both80LP rates");
        }
        BlockPos tierTwo = new BlockPos(1, 2, 1), tierOne = new BlockPos(4, 2, 1);
        var altarBlock = ForgeRegistries.BLOCKS.getValue(id("bloodmagic:altar"));
        var runeBlock = ForgeRegistries.BLOCKS.getValue(id("bloodmagic:blankrune"));
        helper.assertTrue(altarBlock != null && runeBlock != null, "Pinned Blood Magic altar/rune missing");
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) if (x != 0 || z != 0)
            helper.setBlock(tierTwo.offset(x, -1, z), runeBlock);
        helper.setBlock(tierTwo, altarBlock);
        helper.setBlock(tierOne, altarBlock);
        BlockEntity twoEntity = helper.getLevel().getBlockEntity(helper.absolutePos(tierTwo));
        BlockEntity oneEntity = helper.getLevel().getBlockEntity(helper.absolutePos(tierOne));
        helper.assertTrue(twoEntity instanceof TileAltar && oneEntity instanceof TileAltar, "Native altar types missing");
        TileAltar two = (TileAltar) twoEntity, one = (TileAltar) oneEntity;
        two.checkTier(); one.checkTier();
        helper.assertTrue(two.getTier() == 2, "Real 8-rune multiblock must be Tier II (public toInt=2)");
        helper.assertTrue(one.getTier() == 1, "Bare negative altar must be Tier I (public toInt=1)");
        Container twoInventory = two, oneInventory = one;
        Item sword = item(helper, "minecraft:diamond_sword");
        twoInventory.setItem(0, new ItemStack(sword)); oneInventory.setItem(0, new ItemStack(sword));
        new BloodOperation(helper, two, one, active).step();
    }

    /** Actual item attack, separate from the LP-feed craft-throughput fixture.
     * The native item rejects Forge FakePlayer and non-server Players. Create a
     * genuine ServerPlayer with a normally activated native Connection/pipeline,
     * then normal player-list registration. No private transport/health writes. */
    @GameTest(batch = "axiom_actual_sacrifice", templateNamespace = "better_world_management", template = "empty", timeoutTicks = 80)
    public static void actualDaggerItemSacrificeAndNoAltarNegative(GameTestHelper helper) {
        enabled(helper); // recipe projection does not replace native dagger behavior
        BlockPos altarPos = new BlockPos(1, 2, 1);
        var altarBlock = ForgeRegistries.BLOCKS.getValue(id("bloodmagic:altar"));
        var runeBlock = ForgeRegistries.BLOCKS.getValue(id("bloodmagic:blankrune"));
        helper.assertTrue(altarBlock != null && runeBlock != null, "Pinned altar/runes required for actual sacrifice");
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) if (x != 0 || z != 0)
            helper.setBlock(altarPos.offset(x, -1, z), runeBlock);
        helper.setBlock(altarPos, altarBlock);
        BlockEntity entity = helper.getLevel().getBlockEntity(helper.absolutePos(altarPos));
        helper.assertTrue(entity instanceof TileAltar, "Actual sacrifice requires a native altar tile");
        TileAltar altar = (TileAltar) entity;
        altar.checkTier();
        helper.assertTrue(altar.getTier() == 2, "Actual dagger sacrifice fixture needs physical Tier II");
        Item nativeItem = item(helper, "bloodmagic:daggerofsacrifice");
        helper.assertTrue(nativeItem instanceof ItemDaggerOfSacrifice, "Registered dagger must be the native attack implementation");
        ItemDaggerOfSacrifice dagger = (ItemDaggerOfSacrifice) nativeItem;
        ItemStack stack = new ItemStack(dagger);
        var server = helper.getLevel().getServer();
        ServerPlayer attacker = new ServerPlayer(server, helper.getLevel(), new GameProfile(UUID.randomUUID(), "AxiomSacrifice"));
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        // The vanilla GameTest mock helper passes an inactive Connection into
        // PlayerList, whose Forge filters dereference its null channel. A normal
        // Netty initializer invokes Connection.channelActive, without private
        // field writes or pretending a fake player is a genuine server actor.
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelInitializer<Channel>() {
            @Override protected void initChannel(Channel nativeChannel) {
                Connection.configureSerialization(nativeChannel.pipeline(), PacketFlow.SERVERBOUND);
                nativeChannel.pipeline().addLast("packet_handler", connection);
            }
        });
        LivingEntity victim = null, negative = null;
        try {
            channel.runPendingTasks();
            channel.checkException();
            helper.assertTrue(connection.channel() == channel && channel.isActive()
                    && channel.pipeline().get("packet_handler") == connection, "Native Connection channelActive/pipeline setup failed");
            // Channel activation/PLAY alone does NOT initialize Forge's login
            // version context. Use its public native entry with an actual packet
            // codec roundtrip, not private channel-attribute writes. This is a
            // synthetic intention fixture, NOT full peer negotiation/client proof.
            connection.setProtocol(ConnectionProtocol.LOGIN);
            FriendlyByteBuf intentionBytes = new FriendlyByteBuf(Unpooled.buffer());
            try {
                new ClientIntentionPacket("localhost", 25565, ConnectionProtocol.LOGIN).write(intentionBytes);
                ClientIntentionPacket intention = new ClientIntentionPacket(intentionBytes);
                helper.assertTrue(NetworkConstants.NETVERSION.equals(intention.getFMLVersion()),
                        "Native intention codec must derive the pinned Forge version");
                NetworkHooks.registerServerLoginChannel(connection, intention);
            } finally {
                intentionBytes.release();
            }
            helper.assertTrue(NetworkHooks.getConnectionType(() -> connection) == ConnectionType.MODDED,
                    "Public native login hook must initialize Forge connection context before admission");
            connection.setProtocol(ConnectionProtocol.PLAY);
            channel.runPendingTasks();
            // Native PlayerList itself constructs ServerGamePacketListenerImpl
            // BEFORE invoking Forge NetworkHooks.sendMCRegistryPackets.
            server.getPlayerList().placeNewPlayer(connection, attacker);
            channel.runPendingTasks();
            channel.checkException();
            helper.assertTrue(server.getPlayerList().getPlayer(attacker.getUUID()) == attacker
                    && attacker.connection != null && connection.getPacketListener() == attacker.connection,
                    "Genuine native ServerPlayer/listener/membership registration failed");
            helper.assertTrue(!(attacker instanceof FakePlayer), "Native dagger explicitly rejects FakePlayer; cannot use that shortcut");
            BlockPos targetPos = altarPos.above();
            attacker.teleportTo(helper.getLevel(), helper.absolutePos(targetPos).getX() + 0.5,
                    helper.absolutePos(targetPos).getY(), helper.absolutePos(targetPos).getZ() + 1.5, 0.0F, 0.0F);
            attacker.setItemInHand(InteractionHand.MAIN_HAND, stack);
            victim = naturalSacrificeTarget(helper, targetPos);
            helper.assertTrue(victim.isAlive() && !victim.isBaby(), "Actual sacrifice victim must be a live naturally initialized adult");
            helper.assertTrue(PlayerSacrificeHelper.getAltar(helper.getLevel(), victim.blockPosition()) == altar,
                    "Native search must select this fixture's physical altar, not another test's tile");
            int before = altar.getCurrentBlood();
            int rawLP = (int) (nativeSacrificePerHealth(victim.getType()) * victim.getHealth());
            int expected = (int) Math.min(altar.getCapacity() - before, rawLP * (1.0F + altar.getSacrificeMultiplier()));
            helper.assertTrue(expected > 0, "Loaded native values/config must permit nonzero sacrifice LP; no guessed fallback");
            // Public native item hook used by normal successful melee hits.
            // Native code itself applies sacrifice damage and death/events.
            dagger.hurtEnemy(stack, victim, attacker);
            int delta = altar.getCurrentBlood() - before;
            helper.assertTrue(delta == expected, "Actual dagger LP delta differs from loaded native table/config/multiplier: expected=" + expected + " actual=" + delta);
            helper.assertTrue(!victim.isAlive() || victim.isDeadOrDying(), "Actual native dagger sacrifice must kill the target");

            helper.setBlock(altarPos, Blocks.AIR);
            negative = naturalSacrificeTarget(helper, targetPos);
            helper.assertTrue(PlayerSacrificeHelper.getAltar(helper.getLevel(), negative.blockPosition()) == null,
                    "No-altar negative must not accidentally see another test's altar");
            float healthBefore = negative.getHealth();
            int lpBeforeRejectedAttack = altar.getCurrentBlood();
            dagger.hurtEnemy(stack, negative, attacker);
            helper.assertTrue(negative.isAlive() && !negative.isDeadOrDying() && negative.getHealth() == healthBefore,
                    "No-altar native dagger hook must not sacrifice or modify target health");
            helper.assertTrue(altar.getCurrentBlood() == lpBeforeRejectedAttack, "No-altar attack must add zero LP");
            System.out.println("WLM_RECIPE_AXIOMS actual_dagger_item_sacrifice_oracles=PASS native_lp_delta=" + delta + " no_altar_negative=PASS");
        } finally {
            if (victim != null) victim.discard();
            if (negative != null) negative.discard();
            attacker.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            try {
                if (server.getPlayerList().getPlayer(attacker.getUUID()) == attacker) server.getPlayerList().remove(attacker);
            } finally {
                attacker.discard();
                channel.finishAndReleaseAll();
            }
        }
        helper.assertTrue(server.getPlayerList().getPlayer(attacker.getUUID()) == null && attacker.isRemoved() && !channel.isOpen(),
                "Actual actor fixture must clean membership, entity and native channel before success");
        helper.succeed();
    }

    private static int nativeSacrificePerHealth(EntityType<?> type) {
        return BloodMagicAPI.INSTANCE.getValueManager().getSacrificial().getOrDefault(
                ForgeRegistries.ENTITY_TYPES.getKey(type), ConfigManager.COMMON.entitySacrificeDefault.get());
    }

    private static LivingEntity naturalSacrificeTarget(GameTestHelper helper, BlockPos relativePos) {
        for (EntityType<? extends Mob> type : List.<EntityType<? extends Mob>>of(EntityType.COW, EntityType.PIG, EntityType.ZOMBIE)) {
            if (nativeSacrificePerHealth(type) <= 0) continue;
            Mob mob = type.create(helper.getLevel());
            helper.assertTrue(mob != null, "Native sacrifice target creation failed");
            BlockPos absolute = helper.absolutePos(relativePos);
            mob.moveTo(absolute.getX() + 0.5, absolute.getY(), absolute.getZ() + 0.5);
            helper.assertTrue(!mob.isBaby(), "Native target initialized as baby; no age/health overrides permitted");
            helper.assertTrue(helper.getLevel().addFreshEntity(mob), "Actual sacrifice victim must be added to native level");
            return mob;
        }
        throw new IllegalStateException("Loaded native sacrifice values disable all allowed natural fixture species");
    }

    /** Craft throughput only: public altar LP entry point, NOT a dagger attack. */
    private static final class BloodOperation {
        final GameTestHelper helper;
        final TileAltar two, one;
        final boolean active;
        int ticks, supplied;
        BloodOperation(GameTestHelper helper, TileAltar two, TileAltar one, boolean active) {
            this.helper = helper; this.two = two; this.one = one; this.active = active;
        }
        void step() {
            int before = two.getCurrentBlood();
            // Native LP acquisition entry point used by sacrifice, no private
            // tier/progress edits. Track accepted LP, not requested overflow.
            two.sacrificialDaggerCall(1000, true);
            supplied += two.getCurrentBlood() - before;
            one.sacrificialDaggerCall(1000, true);
            if (ticks == 0) {
                // Native startCycle returns early with an empty LP tank.
                two.startCycle(); one.startCycle();
                if (active) {
                    helper.assertTrue(two.getLiquidRequired() == 120000, "Native dagger LP requirement must be120000");
                    helper.assertTrue(two.getConsumptionRate() == 80, "Native dagger rate must be80LP/tick");
                }
            }
            Container inventory = two;
            Container negative = one;
            helper.assertTrue(negative.getItem(0).is(item(helper, "minecraft:diamond_sword")), "Tier I must never produce sacrifice dagger");
            helper.assertTrue(one.getProgress() == 0, "Tier I must not advance the Tier II dagger craft");
            if (inventory.getItem(0).is(item(helper, "bloodmagic:daggerofsacrifice"))) {
                helper.assertTrue(active, "Disabled Tier II crafted sacrifice dagger");
                helper.assertTrue(inventory.getItem(0).getCount() == 1, "Expected exactly one native dagger");
                helper.assertTrue(supplied - two.getCurrentBlood() == 120000, "Actual craft must consume exactly120000LP");
                helper.succeed();
                return;
            }
            if (++ticks >= (active ? 2100 : 200)) {
                helper.assertTrue(!active, "Actual Tier II altar never completed native dagger operation");
                helper.assertTrue(inventory.getItem(0).is(item(helper, "minecraft:diamond_sword")), "Disabled altar changed input");
                helper.assertTrue(two.getProgress() == 0, "Disabled altar progressed gated dagger");
                helper.succeed();
                return;
            }
            helper.runAfterDelay(1, this::step);
        }
    }

    private static RecipeBloodAltar bloodRecipe(GameTestHelper helper) {
        Recipe<?> recipe = helper.getLevel().getRecipeManager().byKey(id("kubejs:bloodmagic/deep_dagger_of_sacrifice")).orElseThrow();
        helper.assertTrue(recipe instanceof RecipeBloodAltar, "Effective dagger must be native Blood Magic recipe type");
        return (RecipeBloodAltar) recipe;
    }

    @SuppressWarnings("unchecked")
    private static ItemStack assemble(GameTestHelper helper, String suffix, int width, int height, String[] inputs, String output, int count) {
        AbstractContainerMenu menu = new AbstractContainerMenu(null, -1) {
            @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
            @Override public boolean stillValid(Player player) { return true; }
        };
        CraftingContainer grid = new TransientCraftingContainer(menu, width, height);
        for (int slot = 0; slot < inputs.length; slot++) if (!inputs[slot].isEmpty()) grid.setItem(slot, new ItemStack(item(helper, inputs[slot])));
        Recipe<CraftingContainer> recipe = (Recipe<CraftingContainer>) helper.getLevel().getRecipeManager().byKey(id(PREFIX + suffix)).orElseThrow();
        helper.assertTrue(recipe.matches(grid, helper.getLevel()), "Native crafting recipe rejects exact physical costs: " + suffix);
        ItemStack result = recipe.assemble(grid, helper.getLevel().registryAccess());
        helper.assertTrue(result.is(item(helper, output)) && result.getCount() == count, "Native crafting output mismatch: " + suffix);
        // Every occupied ingredient position is mandatory, not a permission
        // token or a cosmetic cost. Exercise actual native matching negatives.
        for (int slot = 0; slot < inputs.length; slot++) if (!grid.getItem(slot).isEmpty()) {
            ItemStack removed = grid.removeItemNoUpdate(slot);
            helper.assertTrue(!recipe.matches(grid, helper.getLevel()), "Recipe accepted a missing physical ingredient slot " + slot + ": " + suffix);
            grid.setItem(slot, removed);
        }
        // matches/assemble above are diagnostics only. Acceptance requires the
        // real table/menu ResultSlot business operation and physical costs.
        ItemStack[] physicalInputs = new ItemStack[inputs.length];
        for (int slot = 0; slot < inputs.length; slot++) physicalInputs[slot] = inputs[slot].isEmpty()
                ? ItemStack.EMPTY : new ItemStack(item(helper, inputs[slot]));
        return takeNativeCraft(helper, suffix, width, height, physicalInputs, output, count);
    }

    private static ItemStack takeNativeCraft(GameTestHelper helper, String suffix, int width, int height,
                                             ItemStack[] inputs, String output, int count) {
        helper.assertTrue(width <= 3 && height <= 3 && inputs.length == width * height,
                "Native crafting fixture input dimensions invalid");
        BlockPos tablePos = new BlockPos(0, 1, 0);
        BlockPos absolute = helper.absolutePos(tablePos);
        var level = helper.getLevel();
        var previousState = level.getBlockState(absolute);
        helper.assertTrue(level.getBlockEntity(absolute) == null, "Fixture crafting table must not overwrite a block entity");
        helper.setBlock(tablePos, Blocks.CRAFTING_TABLE);
        FakePlayer actor = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "AxiomCraft"));
        try {
            actor.moveTo(absolute.getX() + 0.5, absolute.getY(), absolute.getZ() + 1.5);
            var provider = level.getBlockState(absolute).getMenuProvider(level, absolute);
            helper.assertTrue(provider != null && actor.openMenu(provider).isPresent(), "Physical native table menu must open");
            helper.assertTrue(actor.containerMenu instanceof CraftingMenu, "Physical table must create real CraftingMenu");
            CraftingMenu menu = (CraftingMenu) actor.containerMenu;
            helper.assertTrue(menu.stillValid(actor), "Actual actor must have physical crafting table access");
            helper.assertTrue(actor.getInventory().isEmpty(), "Native crafting actor starts with empty inventory");
            // No private craftSlots/result writes; each public native input slot
            // update runs normal menu recomputation and full-pack recipe hooks.
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                ItemStack cost = inputs[y * width + x];
                helper.assertTrue(cost.isEmpty() || cost.getCount() == 1, "Each occupied native cost position must contain exactly one item");
                helper.assertTrue(cost.isEmpty() || !cost.hasCraftingRemainingItem(),
                        "This perk's explicit costs have no container remainders; unexpected native remainder needs a separate oracle");
                menu.getSlot(1 + y * 3 + x).set(cost);
            }
            var resultSlot = menu.getSlot(0);
            helper.assertTrue(resultSlot instanceof ResultSlot && resultSlot.mayPickup(actor), "Native ResultSlot must permit actual result taking");
            helper.assertTrue(resultSlot.getItem().is(item(helper, output)) && resultSlot.getItem().getCount() == count,
                    "Native table recomputation must produce exact output, never fixture-set output: " + suffix);
            ItemStack taken = resultSlot.remove(count);
            helper.assertTrue(taken.is(item(helper, output)) && taken.getCount() == count, "Native ResultSlot.remove returned wrong actual batch");
            // Public business operation, not clicked()/mouse/keyboard input.
            // Runs native achievements, crafted events, remaining-items hooks,
            // and actual one-item-per-position consumption.
            resultSlot.onTake(actor, taken);
            helper.assertTrue(taken.is(item(helper, output)) && taken.getCount() == count, "Full-pack crafting hooks changed actual taken batch");
            for (int slot = 1; slot <= 9; slot++) helper.assertTrue(menu.getSlot(slot).getItem().isEmpty(),
                    "Native onTake must consume exactly one item per occupied cost and leave no remainder: " + suffix + " slot" + slot);
            helper.assertTrue(resultSlot.getItem().isEmpty(), "Actual consumed grid must clear native result");
            helper.assertTrue(actor.getInventory().add(taken), "Actual taken native output must fit native actor inventory");
            helper.assertTrue(taken.isEmpty(), "Inventory transfer must consume the actual taken stack reference");
            int actualInventoryCount = 0;
            for (int slot = 0; slot < actor.getInventory().getContainerSize(); slot++) {
                ItemStack stack = actor.getInventory().getItem(slot);
                if (stack.is(item(helper, output))) actualInventoryCount += stack.getCount();
            }
            helper.assertTrue(actualInventoryCount == count, "Native craft/inventory hooks duplicated or lost output: " + suffix);
            menu.getSlot(1).set(ItemStack.EMPTY); // native empty-grid recomputation
            helper.assertTrue(resultSlot.getItem().isEmpty() && resultSlot.remove(count).isEmpty(),
                    "Empty native result recheck must not duplicate a craft");
            ItemStack transferred = ItemStack.EMPTY;
            for (int slot = 0; slot < actor.getInventory().getContainerSize(); slot++) if (actor.getInventory().getItem(slot).is(item(helper, output))) {
                transferred = actor.getInventory().removeItem(slot, count);
                break;
            }
            helper.assertTrue(transferred.is(item(helper, output)) && transferred.getCount() == count,
                    "Physical output transfer must take the actual native inventory batch");
            for (int slot = 0; slot < actor.getInventory().getContainerSize(); slot++) helper.assertTrue(!actor.getInventory().getItem(slot).is(item(helper, output)),
                    "Physical result transfer must not leave a duplicate in actor inventory");
            System.out.println("WLM_RECIPE_AXIOMS native_result_slot_take=PASS recipe=" + suffix + " count=" + count);
            return transferred;
        } finally {
            try {
                actor.closeContainer();
                actor.getInventory().clearContent();
                actor.discard();
            } finally {
                level.setBlock(absolute, previousState, 3);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void verifyUnpacking(GameTestHelper helper, ItemStack billet) {
        AbstractContainerMenu menu = new AbstractContainerMenu(null, -1) {
            @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
            @Override public boolean stillValid(Player player) { return true; }
        };
        CraftingContainer crafting = new TransientCraftingContainer(menu, 2, 2);
        crafting.setItem(0, billet.copy()); // diagnostic copy; real batch goes to native ResultSlot below
        Recipe<CraftingContainer> unpack = (Recipe<CraftingContainer>) helper.getLevel().getRecipeManager()
                .byKey(id(PREFIX + "alloy_testament/unpack_billet")).orElseThrow();
        helper.assertTrue(unpack.matches(crafting, helper.getLevel()), "Actual billet must match native unpack recipe");
        ItemStack brass = unpack.assemble(crafting, helper.getLevel().registryAccess());
        helper.assertTrue(brass.is(item(helper, "create:brass_ingot")) && brass.getCount() == 2,
                "Diagnostic native assembly must yield two brass ingots (180 mB)");
        takeNativeCraft(helper, "alloy_testament/unpack_billet", 2, 2,
                new ItemStack[] { billet, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY }, "create:brass_ingot", 2);
    }
}
