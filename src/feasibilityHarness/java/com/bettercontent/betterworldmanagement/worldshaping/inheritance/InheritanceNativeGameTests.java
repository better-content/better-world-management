package com.bettercontent.betterworldmanagement.worldshaping.inheritance;

import com.accbdd.complicated_bees.bees.Chromosome;
import com.accbdd.complicated_bees.bees.GeneticHelper;
import com.accbdd.complicated_bees.registry.ItemsRegistration;
import com.accbdd.complicated_bees.registry.SpeciesRegistration;
import mokiyoki.enhancedanimals.entity.EnhancedCow;
import mokiyoki.enhancedanimals.util.Genes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

/** Non-shipping native fixtures. Root must register this class on RegisterGameTestsEvent. */
@PrefixGameTestTemplate(false)
public final class InheritanceNativeGameTests {
    private InheritanceNativeGameTests() {}

    @GameTest(templateNamespace = "better_world_management", template = "empty", timeoutTicks = 40)
    public static void cattleNativeGenomeSexAndOffspring(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos pos = h.absolutePos(new BlockPos(1, 2, 1));
        var type = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("eanimod:enhanced_cow"));
        h.assertTrue(type != null, "Pinned cattle entity must be loaded");
        EnhancedCow original = (EnhancedCow) type.create(level);
        h.assertTrue(original != null, "Native cattle construction");
        original.moveTo(pos.getX(), pos.getY(), pos.getZ());
        original.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.COMMAND, null, null);
        original.setBirthTime(level, -mokiyoki.enhancedanimals.config.GeneticAnimalsConfig.COMMON.adultAgeCow.get());
        CompoundTag first = CattleInheritance.capture(original);
        CompoundTag second = first.copy();
        second.putUUID("source", UUID.randomUUID());
        second.putBoolean("female", !first.getBoolean("female"));
        EnhancedCow a = CattleInheritance.restore(level, first), b = CattleInheritance.restore(level, second);
        h.assertTrue(CattleInheritance.matches(a, first) && CattleInheritance.matches(b, second), "Native restore preserves diploid genomes and both sexes");
        h.assertTrue(!a.isBaby() && !b.isBaby(), "Restored cattle are adults");
        CompoundTag saved = new CompoundTag();
        a.addAdditionalSaveData(saved);
        h.assertTrue(!saved.getBoolean("Pregnant") && saved.getInt("Gestation") == 0, "Pregnancy must not transfer");
        EnhancedCow reloaded = (EnhancedCow) type.create(level);
        reloaded.readAdditionalSaveData(saved);
        h.assertTrue(CattleInheritance.matches(reloaded, first), "Native NBT save/load preserves genotype and sex");
        for (int i = 0; i < 16; i++) {
            Genes child = new Genes(a.getGenes()).makeChild(a.getOrSetIsFemale(), new Genes(b.getGenes()), b.getOrSetIsFemale(), Genes.Species.COW);
            h.assertTrue(child.isValid() && child.isComplete() && child.getNumberOfAutosomalGenes() == 254, "Native offspring genome complete");
            int[] parent = first.getIntArray("autosomal"), offspring = child.getAutosomalGenes();
            for (int locus = 0; locus < offspring.length; locus++) {
                int pair = locus & ~1;
                h.assertTrue(offspring[locus] == parent[pair] || offspring[locus] == parent[pair + 1], "Offspring allele must originate in nominated parents");
            }
        }
        expectRejected(h, () -> CattleInheritance.capture(EntityType.COW.create(level)), "Vanilla cattle are unsupported, no species fallback");
        CompoundTag malformed = first.copy(); malformed.putIntArray("autosomal", new int[]{1, 1});
        expectRejected(h, () -> CattleInheritance.validate(malformed), "Short genome rejected");
        h.succeed();
    }

    @GameTest(templateNamespace = "better_world_management", template = "empty", timeoutTicks = 40)
    public static void beeNativeDiploidQueenAndOffspring(GameTestHelper h) {
        var level = h.getLevel();
        var species = level.registryAccess().registryOrThrow(SpeciesRegistration.SPECIES_REGISTRY_KEY).get(new ResourceLocation("complicated_bees:common"));
        h.assertTrue(species != null, "Pinned common species must resolve, never fallback");
        ItemStack princess = GeneticHelper.setGenome(new ItemStack(ItemsRegistration.PRINCESS.get()), new Chromosome(species));
        ItemStack drone = GeneticHelper.setGenome(new ItemStack(ItemsRegistration.DRONE.get()), new Chromosome(species));
        princess.getOrCreateTag().putString("untrusted_old_tag", "must not survive");
        CompoundTag payload = new CompoundTag();
        payload.put("princess", BeeInheritance.capture(princess));
        payload.put("drone", BeeInheritance.capture(drone));
        var restored = InheritanceAdapters.items(level, "patient_hive", payload);
        h.assertTrue(restored.size() == 2 && restored.stream().allMatch(s -> s.getCount() == 1), "One princess and one drone in communal escrow projection");
        for (ItemStack stack : restored) {
            h.assertTrue(stack.hasTag() && !stack.getTag().contains("untrusted_old_tag") && !stack.getTag().contains("mate"), "Only newly serialized native genome fields");
            h.assertTrue(BeeInheritance.capture(stack).equals(payload.getCompound(stack.is(ItemsRegistration.PRINCESS.get()) ? "princess" : "drone")), "Diploid chromosomes roundtrip exactly");
        }
        ItemStack queen = GeneticHelper.createQueenFromPrincessAndDrone(restored.get(0), restored.get(1));
        h.assertTrue(queen.is(ItemsRegistration.QUEEN.get()) && queen.getTag().contains(GeneticHelper.MATE), "Restored genomes produce a native mated queen");
        ItemStack child = GeneticHelper.getOffspring(queen, ItemsRegistration.PRINCESS.get(), level, h.absolutePos(new BlockPos(1, 2, 1)));
        BeeInheritance.validate(BeeInheritance.capture(child));
        h.assertTrue(child.is(ItemsRegistration.PRINCESS.get()), "Native offspring remains princess");
        CompoundTag malformed = payload.getCompound("princess").copy();
        malformed.getCompound("primary").putString("arbitrary", "bad");
        expectRejected(h, () -> BeeInheritance.validate(malformed), "Unknown chromosome field rejected");
        CompoundTag unknownSpecies = payload.getCompound("princess").copy();
        unknownSpecies.getCompound("primary").getCompound("complicated_bees:species").putString("data", "complicated_bees:not_registered");
        expectRejected(h, () -> BeeInheritance.validate(unknownSpecies), "No species fallback");
        h.succeed();
    }

    @GameTest(templateNamespace = "better_world_management", template = "empty", timeoutTicks = 40)
    public static void seedsCommunalFourOnlyNoCopiedNbt(GameTestHelper h) {
        CompoundTag seed = new CompoundTag(); seed.putString("item", "minecraft:wheat_seeds");
        var result = InheritanceAdapters.items(h.getLevel(), "seed_covenant", seed);
        h.assertTrue(result.size() == 1 && result.get(0).is(Items.WHEAT_SEEDS) && result.get(0).getCount() == 4 && !result.get(0).hasTag(), "Exactly four clean cultivar seeds, not player inventory grant");
        result.get(0).shrink(1);
        h.assertTrue(InheritanceAdapters.items(h.getLevel(), "seed_covenant", seed).get(0).getCount() == 4, "Pure projection returns independent stacks; root owns claim receipt");
        CompoundTag notSeed = new CompoundTag(); notSeed.putString("item", "minecraft:diamond");
        expectRejected(h, () -> InheritanceAdapters.validate(h.getLevel().getServer(), "seed_covenant", notSeed), "Non-cultivar rejected");
        seed.put("tag", new CompoundTag());
        expectRejected(h, () -> InheritanceAdapters.validate(h.getLevel().getServer(), "seed_covenant", seed), "Arbitrary item NBT rejected");
        h.succeed();
    }

    @GameTest(templateNamespace = "better_world_management", template = "empty", timeoutTicks = 40)
    public static void wolfNativeIdentityAndIdempotentApply(GameTestHelper h) {
        CompoundTag payload = new CompoundTag();
        UUID owner = UUID.randomUUID();
        payload.putUUID("source", UUID.randomUUID()); payload.putUUID("owner", owner);
        payload.putInt("collar", 14); payload.putString("name", "Copper");
        String txn = "native-wolf-" + UUID.randomUUID();
        BlockPos anchor = h.absolutePos(new BlockPos(1, 2, 1));
        InheritanceAdapters.apply(h.getLevel(), anchor, "familiars_name", payload, txn);
        UUID uuid = InheritanceAdapters.restoredUuid(txn, "familiars_name", "wolf");
        Wolf wolf = (Wolf) h.getLevel().getEntity(uuid);
        h.assertTrue(wolf != null && wolf.isTame() && owner.equals(wolf.getOwnerUUID()), "Owner identity restored");
        h.assertTrue(wolf.getCollarColor().getId() == 14 && wolf.getCustomName().getString().equals("Copper") && wolf.isOrderedToSit(), "Collar/name and safe sitting state restored");
        InheritanceAdapters.apply(h.getLevel(), anchor, "familiars_name", payload, txn);
        h.assertTrue(h.getLevel().getEntity(uuid) == wolf, "Replay finds same actual entity");
        CompoundTag saved = new CompoundTag(); wolf.saveWithoutId(saved);
        Wolf loaded = EntityType.WOLF.create(h.getLevel()); loaded.load(saved);
        h.assertTrue(loaded.getUUID().equals(uuid) && owner.equals(loaded.getOwnerUUID()) && loaded.getCustomName().getString().equals("Copper"), "Vanilla native entity NBT roundtrip preserves inherited identity");
        CompoundTag malicious = payload.copy(); malicious.putString("Command", "op anyone");
        expectRejected(h, () -> InheritanceAdapters.validate(h.getLevel().getServer(), "familiars_name", malicious), "Unexpected entity field rejected");
        h.succeed();
    }

    private static void expectRejected(GameTestHelper h, Runnable operation, String reason) {
        try { operation.run(); } catch (IllegalArgumentException expected) { return; }
        h.fail(reason + ": expected strict rejection");
    }
}
