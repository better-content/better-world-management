package com.bettercontent.betterworldmanagement.worldshaping.inheritance;

import com.accbdd.complicated_bees.bees.Chromosome;
import com.accbdd.complicated_bees.bees.GeneticHelper;
import com.accbdd.complicated_bees.bees.Genome;
import com.accbdd.complicated_bees.bees.gene.*;
import com.accbdd.complicated_bees.bees.gene.enums.*;
import com.accbdd.complicated_bees.block.entity.ApiaryBlockEntity;
import com.accbdd.complicated_bees.config.ServerConfig;
import com.accbdd.complicated_bees.item.BeeItem;
import com.accbdd.complicated_bees.registry.BlocksRegistration;
import com.accbdd.complicated_bees.registry.ItemsRegistration;
import com.accbdd.complicated_bees.registry.SpeciesRegistration;
import com.mojang.authlib.GameProfile;
import mokiyoki.enhancedanimals.config.GeneticAnimalsConfig;
import mokiyoki.enhancedanimals.entity.EnhancedCow;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Non-shipping, real-tick reproduction campaign. Select ONE method per disposable invocation.
 * The full-birth method costs at least 40 minutes at the unmodified pinned gestation setting.
 * No lifecycle tick, mating, offspring, age/progress setter, time skip or config setter is called.
 */
@PrefixGameTestTemplate(false)
public final class InheritanceReproductionNativeGameTests {
    private InheritanceReproductionNativeGameTests() {}

    @GameTest(templateNamespace = "better_world_management", template = "empty",
        batch = "reproduction_fertilization_only", timeoutTicks = 1400)
    public static void restoredCattleFeedAndNaturallyFertilizeOnly(GameTestHelper h) {
        cattle(h, false);
    }

    @GameTest(templateNamespace = "better_world_management", template = "empty",
        batch = "reproduction_full_natural_birth", timeoutTicks = 50200)
    public static void restoredCattleFullNaturalGestationAndCalf(GameTestHelper h) {
        cattle(h, true);
    }

    private static void cattle(GameTestHelper h, boolean requireCalf) {
        Arena a = new Arena(h, requireCalf ? "cattle-full-birth" : "cattle-fertilization-only", requireCalf ? 50000 : 1200);
        try {
            check(a.level.isDay(), "Cattle setup requires existing daylight; fixture never changes world time");
            check(GeneticAnimalsConfig.COMMON.gestationDaysCow.get() == 48000, "Pinned unshortened 48000-tick gestation required");
            check(GeneticAnimalsConfig.COMMON.adultAgeCow.get() == 84000, "Pinned adult age required");
            check(!GeneticAnimalsConfig.COMMON.omnigenders.get(), "Pinned gendered breeding config required");
            a.buildPen();
            EnhancedCow femaleSource = sourceCow(a, true), maleSource = sourceCow(a, false);
            check(!Arrays.equals(femaleSource.getGenes().getAutosomalGenes(), maleSource.getGenes().getAutosomalGenes()), "Distinct native parent genomes required");
            femaleSource.moveTo(a.anchor.getX() + .5, a.anchor.getY(), a.anchor.getZ() + .5);
            maleSource.moveTo(a.anchor.getX() + 2.5, a.anchor.getY(), a.anchor.getZ() + .5);
            check(a.level.addFreshEntity(femaleSource) && a.level.addFreshEntity(maleSource), "Native source admission");
            CompoundTag payload = nominate(a.actor, femaleSource, null);
            payload = nominate(a.actor, maleSource, payload);
            InheritanceAdapters.revalidateSources(a.actor, "ark", payload);
            a.write("nominated-pair.snbt", payload.toString());
            femaleSource.discard(); maleSource.discard();
            InheritanceAdapters.apply(a.level, a.anchor, "ark", payload, a.transaction);
            EnhancedCow mother = (EnhancedCow) a.level.getEntity(InheritanceAdapters.restoredUuid(a.transaction, "ark", "first"));
            EnhancedCow father = (EnhancedCow) a.level.getEntity(InheritanceAdapters.restoredUuid(a.transaction, "ark", "second"));
            check(mother != null && father != null && !mother.isNoAi() && !father.isNoAi(), "Restored adults must retain actual AI");
            CompoundTag first = payload.getCompound("first"), second = payload.getCompound("second");
            check(CattleInheritance.matches(mother, first) && CattleInheritance.matches(father, second), "Restored nomination genomes intact");
            check(!nativeCow(mother).getBoolean("Pregnant") && nativeCow(mother).getInt("Gestation") == 0, "No initial pregnancy/progress injected");
            a.snapshots.put("mother", () -> nativeCow(mother));
            a.snapshots.put("father", () -> nativeCow(father));
            feed(a, mother); feed(a, father);
            check(mother.isInLove() && father.isInLove(), "Ordinary native feeding must put both restored adults in love");
            a.event("NATIVE_FEED_IN_LOVE mother=" + mother.getUUID() + " father=" + father.getUUID());
            long[] fertilizedAt = {-1};
            int[] firstGestation = {-1};
            a.poll(() -> {
                check(mother.isAlive() && father.isAlive(), "Parents must survive naturally");
                check(CattleInheritance.matches(mother, first) && CattleInheritance.matches(father, second), "Parent genomes changed during reproduction");
                CompoundTag state = nativeCow(mother);
                if (state.getBoolean("Pregnant")) {
                    CompoundTag mate = state.getCompound("MateGenetics");
                    check(Arrays.equals(mate.getIntArray("SGenes"), second.getIntArray("sexlinked"))
                        && Arrays.equals(mate.getIntArray("AGenes"), second.getIntArray("autosomal")), "Native fertilization must retain nominated father's genome");
                    if (fertilizedAt[0] < 0) {
                        fertilizedAt[0] = h.getTick(); firstGestation[0] = state.getInt("Gestation");
                        a.write("fertilized-mother.snbt", state.toString());
                        a.event("NATIVE_AI_FERTILIZED gestation=" + firstGestation[0]);
                    }
                    if (!requireCalf && state.getInt("Gestation") >= firstGestation[0] + 100) {
                        check(a.cows().size() == 2, "Short diagnostic must not pretend a calf was observed");
                        a.event("CALF_NOT_OBSERVED remainingNaturalGestationTicks=" + (48000 - state.getInt("Gestation")));
                        return "FERTILIZATION_ONLY_PASS_CALF_NOT_OBSERVED";
                    }
                }
                for (EnhancedCow calf : a.cows()) {
                    if (calf == mother || calf == father) continue;
                    check(requireCalf && fertilizedAt[0] >= 0, "Unexpected third cattle, not an observed natural gestation");
                    CompoundTag childState = nativeCow(calf);
                    check(calf.isBaby() && childState.getString("MotherUUID").equals(mother.getUUID().toString()), "Calf must identify restored mother");
                    check(h.getTick() - fertilizedAt[0] >= 47999 - firstGestation[0], "Calf cannot bypass natural gestation ticks");
                    check(!state.getBoolean("Pregnant") && state.getInt("Gestation") == 0, "Native birth must clear pregnancy");
                    verifyCalf(calf, first, second);
                    a.write("native-calf.snbt", childState.toString());
                    a.event("NATIVE_CALF_OBSERVED uuid=" + calf.getUUID() + " naturalTicksSinceFertilization=" + (h.getTick() - fertilizedAt[0]));
                    return "NATIVE_CALF_PASS";
                }
                // Normal husbandry only: no hunger/health setters, repeated native wheat interactions.
                if (h.getTick() % 100 == 0) {
                    if (mother.getHunger() >= 4000) feed(a, mother);
                    if (father.getHunger() >= 4000) feed(a, father);
                    if (h.getTick() % 200 == 0) a.event("CATTLE_OBSERVED pregnant=" + state.getBoolean("Pregnant") + " gestation=" + state.getInt("Gestation"));
                }
                return null;
            });
        } catch (Throwable failure) { a.fail(failure); }
    }

    @GameTest(templateNamespace = "better_world_management", template = "empty",
        batch = "reproduction_native_apiary", timeoutTicks = 4400)
    public static void restoredBeesMateAgeAndProduceOffspringInNativeApiary(GameTestHelper h) {
        Arena a = new Arena(h, "bee-native-apiary", 4200);
        try {
            check(ServerConfig.SERVER_CONFIG.productionCycleLength.get() == 200
                && ServerConfig.SERVER_CONFIG.enviroCycleLength.get() == 200, "Pinned unshortened native cycles required");
            check(EnumLifespan.SHORTEST.value == 15, "Pinned shortest permitted lifespan allele required");
            a.buildPen();
            BlockPos hivePos = a.anchor.offset(1, 0, 0);
            a.level.setBlock(hivePos, BlocksRegistration.APIARY.get().defaultBlockState(), 3);
            check(a.level.getBlockEntity(hivePos) instanceof ApiaryBlockEntity, "Real registered apiary block entity required");
            ApiaryBlockEntity hive = (ApiaryBlockEntity) a.level.getBlockEntity(hivePos);
            // Native allele values are selected BEFORE nomination, not altered after restoration.
            // Flowers are real registered minecraft:flowers blocks inside native common territory.
            for (int dx : new int[]{-1, 1}) for (int dz : new int[]{-1, 1})
                a.level.setBlock(hivePos.offset(dx, 0, dz), Blocks.DANDELION.defaultBlockState(), 3);
            ItemStack princessSource = beeSource(a, hivePos, true), droneSource = beeSource(a, hivePos, false);
            a.actor.setItemInHand(InteractionHand.MAIN_HAND, princessSource);
            CompoundTag payload = InheritanceAdapters.capture(a.actor, "patient_hive", null);
            a.actor.getInventory().setItem(1, princessSource);
            a.actor.setItemInHand(InteractionHand.MAIN_HAND, droneSource);
            payload = InheritanceAdapters.capture(a.actor, "patient_hive", payload);
            InheritanceAdapters.revalidateSources(a.actor, "patient_hive", payload);
            a.write("nominated-bees.snbt", payload.toString());
            List<ItemStack> restored = InheritanceAdapters.items(a.level, "patient_hive", payload);
            CompoundTag princess = payload.getCompound("princess"), drone = payload.getCompound("drone");
            check(BeeInheritance.capture(restored.get(0)).equals(princess) && BeeInheritance.capture(restored.get(1)).equals(drone), "Restored bee genomes must exactly match nomination");
            for (ItemStack bee : restored) check(!bee.getOrCreateTag().contains(GeneticHelper.MATE) && BeeItem.getAge(bee) == 0, "No pre-mating or lifetime progress injected");
            check(hive.getBeeItems().insertItem(0, restored.get(0), false).isEmpty()
                && hive.getBeeItems().insertItem(1, restored.get(1), false).isEmpty(), "Ordinary real apiary input insertion");
            a.snapshots.put("apiary", hive::saveWithoutMetadata);
            boolean[] queenSeen = {false};
            float[] maxAge = {0};
            a.poll(() -> {
                check(a.level.getBlockEntity(hivePos) == hive, "Apiary must remain native and loaded");
                ItemStack queen = hive.getBeeItems().getStackInSlot(0);
                if (queen.is(ItemsRegistration.QUEEN.get())) {
                    check(BeeInheritance.capture(queen).equals(princess), "Native queen must retain restored princess chromosomes");
                    CompoundTag mate = queen.getOrCreateTag().getCompound(GeneticHelper.MATE);
                    check(mate.getCompound(GeneticHelper.CHROMOSOME_A).equals(drone.getCompound("primary"))
                        && mate.getCompound(GeneticHelper.CHROMOSOME_B).equals(drone.getCompound("secondary")), "Apiary must naturally capture restored drone chromosomes");
                    if (!queenSeen[0]) { queenSeen[0] = true; a.event("NATIVE_APIARY_QUEEN_OBSERVED"); a.write("native-queen.snbt", queen.save(new CompoundTag()).toString()); }
                    maxAge[0] = Math.max(maxAge[0], BeeItem.getAge(queen));
                }
                List<ItemStack> output = new ArrayList<>();
                for (int slot = 0; slot < hive.getOutputItems().getSlots(); slot++) output.add(hive.getOutputItems().getStackInSlot(slot).copy());
                for (ItemStack buffered : hive.getOutputBuffer()) output.add(buffered.copy());
                int princesses = 0, drones = 0;
                for (ItemStack child : output) if (child.is(ItemsRegistration.PRINCESS.get()) || child.is(ItemsRegistration.DRONE.get())) {
                    BeeInheritance.validate(BeeInheritance.capture(child));
                    if (child.is(ItemsRegistration.PRINCESS.get())) princesses += child.getCount(); else drones += child.getCount();
                }
                if (princesses >= 1 && drones >= 1) {
                    check(queenSeen[0] && maxAge[0] >= 14, "Must observe native queen aging before offspring");
                    check(hive.getBeeItems().getStackInSlot(0).isEmpty() && hive.getBeeItems().getStackInSlot(1).isEmpty(), "Native lifecycle must consume queen and mating drone");
                    a.event("NATIVE_APIARY_OFFSPRING princesses=" + princesses + " drones=" + drones + " maxObservedQueenAge=" + maxAge[0]);
                    return "NATIVE_APIARY_OFFSPRING_PASS";
                }
                if (h.getTick() % 200 == 0) a.event("APIARY_OBSERVED queenSeen=" + queenSeen[0] + " maxAge=" + maxAge[0] + " errors=" + hive.getErrors() + " productionProgress=" + hive.getCycleProgress());
                return null;
            });
        } catch (Throwable failure) { a.fail(failure); }
    }

    private static ItemStack beeSource(Arena a, BlockPos pos, boolean princess) {
        var species = a.level.registryAccess().registryOrThrow(SpeciesRegistration.SPECIES_REGISTRY_KEY).get(new ResourceLocation("complicated_bees:common"));
        check(species != null, "Pinned registered common species required");
        Chromosome chromosome = new Chromosome(species).copy();
        var genes = chromosome.getGenes();
        genes.put(GeneSpecies.ID, new GeneSpecies(species, true));
        genes.put(GeneLifespan.ID, new GeneLifespan(EnumLifespan.SHORTEST, true));
        genes.put(GeneTemperature.ID, new GeneTemperature(EnumTemperature.getFromPosition(a.level, pos), EnumTolerance.NONE, true));
        genes.put(GeneHumidity.ID, new GeneHumidity(EnumHumidity.getFromPosition(a.level, pos), EnumTolerance.NONE, true));
        genes.put(GeneActiveTime.ID, new GeneActiveTime(EnumActiveTime.NEVER_SLEEPS, true));
        genes.put(new ResourceLocation("complicated_bees:weatherproof"), new GeneBoolean(true, true));
        genes.put(new ResourceLocation("complicated_bees:cave_dwelling"), new GeneBoolean(true, true));
        genes.put(GeneFlower.ID, new GeneFlower(new ResourceLocation("complicated_bees:flower"), true));
        genes.put(GeneFertility.ID, new GeneFertility(princess ? 2 : 3, true));
        return GeneticHelper.setGenome(new ItemStack(princess ? ItemsRegistration.PRINCESS.get() : ItemsRegistration.DRONE.get()), new Genome(chromosome.copy(), chromosome.copy()));
    }

    private static EnhancedCow sourceCow(Arena a, boolean female) {
        var type = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("eanimod:enhanced_cow"));
        check(type != null, "Pinned native cattle entity required");
        // Select native-generated adults/sex/viable alleles; do not rewrite source age or genetics.
        for (int attempt = 0; attempt < 512; attempt++) {
            EnhancedCow cow = (EnhancedCow) type.create(a.level);
            check(cow != null, "Native cattle construction");
            cow.moveTo(a.anchor.getX() + .5, a.anchor.getY(), a.anchor.getZ() + .5);
            cow.finalizeSpawn(a.level, a.level.getCurrentDifficultyAt(a.anchor), MobSpawnType.COMMAND, null, null);
            int[] auto = cow.getGenes().getAutosomalGenes();
            if (!cow.isBaby() && cow.getOrSetIsFemale() == female && auto[26] != 1 && auto[27] != 1) return cow;
            cow.discard();
        }
        throw new IllegalStateException("Could not select a native-generated viable adult of requested sex");
    }

    private static CompoundTag nominate(FakePlayer player, EnhancedCow cow, CompoundTag previous) {
        player.moveTo(cow.getX(), cow.getY(), cow.getZ() + 3);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, cow.getEyePosition());
        player.setOldPosAndRot();
        return InheritanceAdapters.capture(player, "ark", previous);
    }

    private static void feed(Arena a, EnhancedCow cow) {
        ItemStack wheat = new ItemStack(Items.WHEAT, 64);
        check(cow.isBreedingItem(wheat), "Pinned native cow food map must accept wheat for breeding");
        a.actor.moveTo(cow.getX(), cow.getY(), cow.getZ() + 1.5);
        a.actor.setItemInHand(InteractionHand.MAIN_HAND, wheat);
        check(cow.mobInteract(a.actor, InteractionHand.MAIN_HAND).consumesAction(), "Native wheat interaction must consume action");
    }

    private static CompoundTag nativeCow(EnhancedCow cow) {
        CompoundTag state = new CompoundTag(); cow.saveWithoutId(state); return state;
    }

    private static void verifyCalf(EnhancedCow calf, CompoundTag mother, CompoundTag father) {
        check(calf.getGenes().isValid() && calf.getGenes().isComplete(), "Native calf genome complete");
        int[] child = calf.getGenes().getAutosomalGenes(), m = mother.getIntArray("autosomal"), f = father.getIntArray("autosomal");
        check(child.length == 254, "Pinned calf genome length");
        for (int pair = 0; pair < child.length; pair += 2)
            check((from(child[pair], m, pair) && from(child[pair + 1], f, pair))
                || (from(child[pair + 1], m, pair) && from(child[pair], f, pair)), "Calf autosomal locus must inherit one allele from each restored parent: " + pair);
        int[] sex = calf.getGenes().getSexlinkedGenes(), ms = mother.getIntArray("sexlinked"), fs = father.getIntArray("sexlinked");
        check(sex.length == 2, "Pinned calf sexlinked length");
        for (int allele : sex) check(from(allele, ms, 0) || from(allele, fs, 0), "Calf sexlinked allele must originate in restored parents");
    }

    private static boolean from(int allele, int[] genome, int pair) { return allele == genome[pair] || allele == genome[pair + 1]; }
    private static void check(boolean condition, String reason) { if (!condition) throw new IllegalStateException(reason); }
    private interface Observation { String tick(); }

    /** Actual tick scheduler and reversible physical arena, not a substitute lifecycle implementation. */
    private static final class Arena {
        final GameTestHelper h;
        final ServerLevel level;
        final BlockPos origin, anchor;
        final AABB bounds;
        final FakePlayer actor;
        final String transaction = UUID.randomUUID().toString();
        final Path evidence;
        final int deadline;
        final Map<BlockPos, BlockState> original = new LinkedHashMap<>();
        final Map<String, Supplier<CompoundTag>> snapshots = new LinkedHashMap<>();
        boolean finished;

        Arena(GameTestHelper h, String name, int deadline) {
            this.h = h; this.level = h.getLevel(); this.deadline = deadline;
            origin = h.absolutePos(BlockPos.ZERO).offset(-4, 1, -4); anchor = origin.offset(3, 1, 4);
            bounds = new AABB(origin, origin.offset(9, 6, 9));
            evidence = level.getServer().getServerDirectory().toPath().resolve("world-axiom-reproduction-evidence").resolve(name + "-" + transaction);
            actor = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "AxiomRepro"));
            write("status.txt", "RUNNING\n");
            check(level.getEntities((Entity) null, bounds, e -> e.isAlive()).isEmpty(), "Exclusive disposable arena must start entity-free");
            for (BlockPos p : BlockPos.betweenClosed(origin, origin.offset(8, 5, 8))) {
                check(level.getBlockEntity(p) == null, "Refuse to overwrite any pre-existing block entity");
                original.put(p.immutable(), level.getBlockState(p));
            }
            event("START nativeGameTime=" + level.getGameTime() + " nativeDayTime=" + level.getDayTime());
        }

        void buildPen() {
            for (BlockPos p : original.keySet()) {
                int x = p.getX() - origin.getX(), y = p.getY() - origin.getY(), z = p.getZ() - origin.getZ();
                BlockState state = y == 0 ? Blocks.GRASS_BLOCK.defaultBlockState()
                    : y == 5 || x == 0 || x == 8 || z == 0 || z == 8 ? Blocks.GLASS.defaultBlockState() : Blocks.AIR.defaultBlockState();
                if (y == 1 && (x == 0 || x == 8) && (z == 0 || z == 8)) state = Blocks.GLOWSTONE.defaultBlockState();
                level.setBlock(p, state, 3);
            }
        }

        List<EnhancedCow> cows() { return level.getEntitiesOfClass(EnhancedCow.class, bounds); }

        void poll(Observation observation) {
            if (finished) return;
            try {
                String result = observation.tick();
                if (result != null) { finish(result); h.succeed(); return; }
                check(h.getTick() < deadline, "Natural lifecycle deadline reached: " + deadline + " ticks; inspect retained native snapshots");
                h.runAfterDelay(1, () -> poll(observation));
            } catch (Throwable failure) { fail(failure); }
        }

        void fail(Throwable failure) {
            if (finished) throw new IllegalStateException("Fixture already completed", failure);
            String message = failure.getClass().getName() + ": " + failure.getMessage();
            try { event("FAIL " + message); finish("FAIL\n" + message); }
            catch (Throwable cleanup) { failure.addSuppressed(cleanup); }
            h.fail(message + "; evidence=" + evidence);
        }

        void finish(String result) {
            for (var entry : snapshots.entrySet()) write(entry.getKey() + "-before-cleanup.snbt", entry.getValue().get().toString());
            write("status.txt", result + "\nRESTORE_PENDING\n");
            try {
                for (Entity entity : level.getEntities((Entity) null, bounds, e -> !(e instanceof net.minecraft.world.entity.player.Player))) entity.discard();
                for (BlockPos p : original.keySet()) level.removeBlockEntity(p); // Prevent apiary inventory drops during restoration.
                for (var entry : original.entrySet()) level.setBlock(entry.getKey(), entry.getValue(), 18);
                for (var entry : original.entrySet()) check(level.getBlockState(entry.getKey()).equals(entry.getValue()), "Arena restoration mismatch");
                actor.getInventory().clearContent(); actor.discard();
                finished = true;
                write("status.txt", result + "\nRESTORED\n");
            } catch (Throwable failure) {
                finished = true;
                write("status.txt", "RESTORE_FAIL\n" + failure + "\noriginalResult=" + result + "\n");
                throw failure;
            }
        }

        void event(String message) {
            String line = Instant.now() + " tick=" + h.getTick() + " " + message + "\n";
            System.out.print("[AxiomReproduction] " + line);
            try { Files.createDirectories(evidence); Files.writeString(evidence.resolve("events.log"), line, StandardOpenOption.CREATE, StandardOpenOption.APPEND); }
            catch (IOException failure) { throw new IllegalStateException("Evidence write failed", failure); }
        }

        void write(String name, String text) {
            try { Files.createDirectories(evidence); Files.writeString(evidence.resolve(name), text); }
            catch (IOException failure) { throw new IllegalStateException("Evidence write failed", failure); }
        }
    }
}
