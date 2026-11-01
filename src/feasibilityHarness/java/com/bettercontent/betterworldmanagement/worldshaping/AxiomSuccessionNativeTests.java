package com.bettercontent.betterworldmanagement.worldshaping;

import com.accbdd.complicated_bees.bees.Chromosome;
import com.accbdd.complicated_bees.bees.GeneticHelper;
import com.accbdd.complicated_bees.registry.ItemsRegistration;
import com.accbdd.complicated_bees.registry.SpeciesRegistration;
import com.bettercontent.betterworldmanagement.*;
import com.bettercontent.betterworldmanagement.mixin.AxiomRegionFileStorageAccess;
import com.bettercontent.betterworldmanagement.worldshaping.world.WorldPolicies;
import com.bettercontent.betterworldmanagement.worldshaping.exploration.ExplorationEffects;
import com.bettercontent.betterworldmanagement.worldshaping.exploration.ExplorationDiscoveries;
import com.bettercontent.betterworldmanagement.worldshaping.inheritance.InheritanceAdapters;
import com.bettercontent.celestial.calendar.CalendarSavedData;
import com.bettercontent.spiritcommerce.trader.WanderingTraderScheduleData;
import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.embedded.EmbeddedChannel;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import mokiyoki.enhancedanimals.entity.EnhancedCow;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.*;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.handshake.ClientIntentionPacket;
import net.minecraftforge.network.NetworkHooks;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import sereneseasons.api.season.Season;
import sereneseasons.api.season.SeasonHelper;
import sereneseasons.season.SeasonHandler;
import sereneseasons.season.SeasonSavedData;
import sereneseasons.season.SeasonTime;

/**
 * REAL supervisor succession campaign, not an in-JVM reset simulation.
 * Select exactly ONE public GameTest method per invocation. No control/snapshot restoration,
 * private writes, time/config changes, forced lifecycle hooks or user-input automation.
 * Root must supply a fresh disposable runtime initialized to synthetic generation 23.
 */
@PrefixGameTestTemplate(false)
public final class AxiomSuccessionNativeTests {
    private static final UUID OPERATOR = UUID.fromString("7ea34167-fd82-4aa1-aecc-1898dc998a24");
    private static final String NAME = "AxiomSuccession";
    private static final String RECEIPT = "world-axiom-succession-fixture-v1.json";
    private static final String PLAQUE = "Across a real archived world, those who built before us are remembered.";
    private static final List<String> CLAIMS = List.of("seed_covenant", "patient_hive", "ark", "familiars_name");
    private static final List<EmbeddedChannel> CHANNELS = new ArrayList<>();
    private AxiomSuccessionNativeTests() {}

    @GameTest(templateNamespace = "better_world_management", template = "empty", timeoutTicks = 1200)
    public static void sourceStageAll24AndRequestRealReset(GameTestHelper helper) {
        guarded(helper, () -> {
            Context c = new Context(helper);
            long generation = PrestigeService.lineage(c.server).generation();
            if (generation == 24) {
                c.loadReceipt(); c.bindSuccessor();
                check(!Files.exists(PrestigeService.control(c.server).resolve("reset-request-v5.tsv"))
                        && !Files.exists(PrestigeService.control(c.server).resolve("successor-request-v5.tsv")), "Source reentry must wait for real supervisor health cleanup");
                c.receipt.addProperty("source_successor_guard", "PASS_NO_RESTAGE");
                c.receipt.addProperty("source_successor_guard_process", process());
                c.write();
                System.out.println("WLM_AXIOM_REAL_SUCCESSION SOURCE_REENTRY_PASS no_restage=true generation=24 transaction=" + c.transaction());
                helper.succeed();
                return;
            }
            check(generation == 23 && PrestigeService.lineage(c.server).totalPrestiges() == 23, "Only explicit synthetic parent23 or completed successor24 is supported");
            check(!Files.exists(c.path), "Source fixture receipt already exists: preserve failed attempt; never restage/reset an existing fixture");
            check(WorldAxiomService.active(c.server).isEmpty(), "Fresh synthetic parent must have no active World Axioms");
            check(WorldAxiomService.draft(c.server).selected().isEmpty(), "Synthetic parent must start with an empty World Axiom draft");
            for (String name : List.of("reset-request-v5.tsv", "successor-request-v5.tsv", "staged-request-v5.tsv"))
                check(!Files.exists(PrestigeService.control(c.server).resolve(name)), "No preexisting committed/staged source controls: " + name);
            c.receipt = new JsonObject();
            c.receipt.addProperty("schema", "world_axiom_real_succession_fixture_v1");
            c.receipt.addProperty("initialization", "SYNTHETIC_PARENT23_NOT_23_REAL_RESETS");
            c.receipt.addProperty("base_generation", 23); c.receipt.addProperty("operator", OPERATOR.toString());
            c.receipt.addProperty("lineage", PrestigeService.lineage(c.server).lineageId());
            c.receipt.addProperty("source_world", PrestigeService.worldName(c.server));
            c.receipt.addProperty("source_seed", c.level.getSeed()); c.receipt.addProperty("source_process", process());
            c.receipt.add("registered_biomes", biomeRegistry(c.level));
            c.receipt.addProperty("source_phase", "preparing"); c.receipt.addProperty("started_utc", Instant.now().toString());
            c.write();
            c.condenser = c.level.getSharedSpawnPos().offset(96, 8, 0).immutable();
            c.buildOwnedArena(); c.actor = c.player(); c.open();
            for (AxiomCatalog.Node node : AxiomCatalog.nodes())
                WorldAxiomService.toggle(c.actor, WorldAxiomService.draft(c.server).revision(), node.id());
            check(WorldAxiomService.draft(c.server).selected().equals(allIds()), "Native toggle route selected all23 independent points");
            c.configure("shaped_horizon", Map.of("preset", "large_biomes"));
            c.configure("turning_year", Map.of("season", "early_summer"));
            c.configure("deep_compact", Map.of("deposit", "ironstone", "band", "shallow"));
            c.configure("chosen_omen", Map.of("event", "open_ways"));
            c.configure("founders_exchange", Map.of("currency", "work"));
            c.configure("cartographers_memory", Map.of("kind", "mineshaft"));
            Map<String, String> books = new TreeMap<>(); books.put("plaque", PLAQUE);
            for (int i = 1; i <= 4; i++) books.put("book" + i, book(i));
            c.configure("communal_archive", books);
            c.nominateNativeSources();
            BlockPos entry = c.loadedNaturalMineshaft();
            if (entry == null) {
                c.receipt.addProperty("natural_mineshaft_entry", "NOT_OBSERVED_NO_ALREADY_LOADED_NATIVE_START");
                c.commitSource();
            } else {
                boolean alreadyEntered = ExplorationDiscoveries.readIds(ExplorationEffects.discoveries(c.server)).contains("minecraft:mineshaft");
                c.actor.moveTo(entry.getX() + .5, entry.getY(), entry.getZ() + .5);
                c.receipt.addProperty("natural_mineshaft_candidate", entry.toShortString());
                // Observe actual normal registered-player ticks; never invoke the discovery event/hook directly.
                helper.runAfterDelay(25, () -> guarded(helper, () -> {
                    CompoundTag discovered = ExplorationEffects.discoveries(c.server);
                    c.receipt.addProperty("natural_mineshaft_entry", alreadyEntered ? "PREEXISTING_NATIVE_ENTRY_NOT_NEW_ENTRY_PROOF"
                            : ExplorationDiscoveries.readIds(discovered).contains("minecraft:mineshaft")
                            ? "OBSERVED_NATIVE_PLAYER_ENTRY" : "NOT_OBSERVED_NORMAL_PLAYER_TICK_DID_NOT_RECORD");
                    c.open(); c.commitSource();
                }));
            }
        });
    }

    /**
     * Narrow source-world bootstrap diagnostic for the eight recipe laws, NOT the all24
     * succession campaign or proof of any recipe effect. The binding must be produced
     * by the actual source Forge process, never a pre-world synthetic snapshot.
     */
    @GameTest(templateNamespace = "better_world_management", template = "empty", timeoutTicks = 1200)
    public static void sourceStageEightRecipesAndRequestRealReset(GameTestHelper helper) {
        guarded(helper, () -> {
            Context c = new Context(helper);
            Path path = c.server.getServerDirectory().toPath().toAbsolutePath().normalize()
                    .resolve("world-axiom-recipe-source-fixture-v1.json");
            nonsymlink(path);
            List<String> recipes = List.of("alloy_testament", "captive_hearth", "chalk_covenant",
                    "eternal_engine", "iron_law", "patient_alchemist", "sacrificial_tradition", "spirit_awakening");
            long generation = PrestigeService.lineage(c.server).generation();
            if (generation == 24) {
                JsonObject source = readJson(path);
                check("committed".equals(source.get("source_phase").getAsString())
                        && "SYNTHETIC_PARENT23_NOT_23_REAL_RESETS".equals(source.get("initialization").getAsString()),
                        "Only the previously committed synthetic-parent23 source may reenter");
                var active = WorldAxiomService.active(c.server).orElseThrow();
                check(active.selected().equals(Set.copyOf(recipes))
                        && active.transaction().equals(source.get("transaction").getAsString())
                        && AxiomContracts.digest(active).equals(source.get("build_sha256").getAsString())
                        && !process().equals(source.get("source_process").getAsString())
                        && c.level.getSeed() != source.get("source_seed").getAsLong(),
                        "Genuine distinct-process successor must bind the frozen eight-recipe build and new world");
                check(!Files.exists(PrestigeService.control(c.server).resolve("reset-request-v5.tsv")),
                        "Successor has no unconsumed reset request");
                // The native shutdown hook requires a post-case region write.
                // Dirty only this GameTest's owned empty-template chunk; do not
                // manufacture any gameplay recipe result or replay source state.
                BlockPos marker = helper.absolutePos(new BlockPos(0, 1, 0));
                BlockState previous = c.level.getBlockState(marker);
                check(c.level.setBlock(marker, previous.is(Blocks.GOLD_BLOCK)
                        ? Blocks.IRON_BLOCK.defaultBlockState() : Blocks.GOLD_BLOCK.defaultBlockState(), 3),
                        "Native successor guard must dirty its own GameTest chunk for normal save proof");
                source.addProperty("successor_guard_process", process());
                source.addProperty("successor_guard_block", marker.asLong());
                source.addProperty("successor_guard_block_id", previous.is(Blocks.GOLD_BLOCK)
                        ? "minecraft:iron_block" : "minecraft:gold_block");
                source.addProperty("successor_guard_utc", Instant.now().toString());
                AxiomContracts.atomic(path, (source + "\n").getBytes(StandardCharsets.UTF_8));
                System.out.println("WLM_AXIOM_RECIPE_SOURCE SUCCESSOR_REENTRY_PASS no_restage=true transaction="
                        + source.get("transaction").getAsString());
                helper.succeed();
                return;
            }
            check(generation == 23 && PrestigeService.lineage(c.server).totalPrestiges() == 23,
                    "Only explicitly initialized synthetic parent23 may stage the recipe source");
            check(!Files.exists(path) && WorldAxiomService.active(c.server).isEmpty()
                    && WorldAxiomService.draft(c.server).selected().isEmpty(),
                    "Fresh source must have no receipt, active build, or selected draft");
            for (String name : List.of("reset-request-v5.tsv", "successor-request-v5.tsv", "staged-request-v5.tsv"))
                check(!Files.exists(PrestigeService.control(c.server).resolve(name)),
                        "Source cannot reuse existing staged or committed controls: " + name);
            c.condenser = c.level.getSharedSpawnPos().offset(96, 8, 0).immutable();
            c.buildOwnedArena(); c.actor = c.player(); c.open();
            for (String id : recipes)
                WorldAxiomService.toggle(c.actor, WorldAxiomService.draft(c.server).revision(), id);
            check(WorldAxiomService.draft(c.server).selected().equals(Set.copyOf(recipes)),
                    "Physical operator selected exactly eight independent recipe laws");
            // Legitimate source biome selection, not a successor-side override:
            // three actual safe temperate preferences reduce seed-dependent
            // landing misses without changing health or landing-search limits.
            PrestigeService.saveDraft(c.actor, List.of("minecraft:plains", "minecraft:forest", "minecraft:meadow"));
            PrestigeService.stage(c.actor, c.condenser);
            check(AxiomContracts.read(WorldAxiomService.control(c.server, WorldAxiomService.STAGED)).selected().equals(Set.copyOf(recipes)),
                    "Production stage froze exactly eight selected recipes");
            String tx = PrestigeService.commit(c.actor, c.condenser);
            var frozen = AxiomContracts.read(WorldAxiomService.control(c.server, WorldAxiomService.RESET));
            check(frozen.selected().equals(Set.copyOf(recipes)) && frozen.transaction().equals(tx),
                    "Production commit froze the same eight selected recipes and transaction");
            Path binding = PrestigeService.worldBindingPath(c.server);
            check(Files.isRegularFile(binding) && !Files.isSymbolicLink(binding),
                    "Production commit must create a real source-world binding");
            JsonObject receipt = new JsonObject();
            receipt.addProperty("schema", "world_axiom_recipe_source_fixture_v1");
            receipt.addProperty("initialization", "SYNTHETIC_PARENT23_NOT_23_REAL_RESETS");
            receipt.addProperty("source_phase", "committed");
            receipt.addProperty("source_process", process());
            receipt.addProperty("source_seed", c.level.getSeed());
            receipt.addProperty("source_world", PrestigeService.worldName(c.server));
            receipt.addProperty("lineage", PrestigeService.lineage(c.server).lineageId());
            receipt.addProperty("transaction", tx);
            receipt.addProperty("build_sha256", AxiomContracts.digest(frozen));
            receipt.addProperty("source_binding", binding.toString());
            receipt.addProperty("committed_utc", Instant.now().toString());
            AxiomContracts.atomic(path, (receipt + "\n").getBytes(StandardCharsets.UTF_8));
            System.out.println("WLM_AXIOM_RECIPE_SOURCE SOURCE_COMMITTED scheduled_real_stop=true transaction=" + tx);
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "better_world_management", template = "empty", timeoutTicks = 1200)
    public static void successorAll24NativeStateClaimsAndRetry(GameTestHelper helper) {
        guarded(helper, () -> {
            Context c = new Context(helper); c.loadReceipt(); c.bindSuccessor();
            c.verifyNativeWorld();
            c.actor = c.player();
            if (c.receipt.has("target_condenser")) c.condenser = BlockPos.of(c.receipt.get("target_condenser").getAsLong());
            else {
                c.condenser = c.level.getSharedSpawnPos().offset(96, 8, 0).immutable();
                c.buildOwnedArena();
                c.receipt.addProperty("target_condenser", c.condenser.asLong());
                c.receipt.addProperty("target_phase", "claiming"); c.write();
            }
            c.open();
            AxiomJournal journal = AxiomJournal.open(c.server);
            boolean pristine = CLAIMS.stream().noneMatch(id -> journal.receipt(id) != null);
            if (pristine) check(c.count(Items.WHEAT_SEEDS) == 0 && c.count(ItemsRegistration.PRINCESS.get()) == 0
                    && c.count(ItemsRegistration.DRONE.get()) == 0, "Old-world nomination stock did not leak into fresh successor inventory");
            for (String id : CLAIMS) {
                if (AxiomJournal.open(c.server).completed(id)) reject(() -> WorldAxiomService.claim(c.actor, id, new CompoundTag()), "completed communal claim " + id);
                else WorldAxiomService.claim(c.actor, id, new CompoundTag());
            }
            // Explicitly load only journaled claim chunks for disk-restart observation; allow normal entity loading to finish.
            for (String id : List.of("ark", "familiars_name")) {
                var receipt = AxiomJournal.open(c.server).receipt(id);
                c.level.getChunk(receipt.anchor());
                if (id.equals("ark")) c.level.getChunk(receipt.anchor().offset(2, 0, 0));
            }
            helper.runAfterDelay(20, () -> guarded(helper, () -> c.verifyClaimsAndFinish()));
        });
    }

    /** Third-process observation only: never re-enters the initial target case or rewrites its receipt. */
    @GameTest(templateNamespace = "better_world_management", template = "empty", timeoutTicks = 1200)
    public static void postclaimRestartPreservesReceiptsAndEntities(GameTestHelper helper) {
        guarded(helper, () -> new RestartObservation(new Context(helper)).start());
    }

    private static String restartHash(Path path) throws Exception {
        nonsymlink(path);
        check(Files.isRegularFile(path) && Files.size(path) <= 32L * 1024 * 1024, "Bounded regular restart input");
        return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
    private static Path restartInput(JsonObject manifest, String pathKey, String hashKey, Path root) throws Exception {
        Path path = Path.of(manifest.get(pathKey).getAsString()).toAbsolutePath().normalize();
        check(path.startsWith(root) && restartHash(path).equals(manifest.get(hashKey).getAsString()), "Restart immutable input hash " + pathKey);
        return path;
    }
    private static final class RestartObservation {
        final Context c;
        final UUID owner = UUID.randomUUID();
        final net.minecraft.server.level.TicketType<UUID> ticket = net.minecraft.server.level.TicketType.create(
                "axioms_postclaim_restart", Comparator.<UUID>naturalOrder(), 200);
        final List<ChunkPos> chunks = new ArrayList<>();
        final List<java.util.concurrent.CompletableFuture<?>> futures = new ArrayList<>();
        final java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "axioms-postclaim-read-request"); thread.setDaemon(true); return thread;
        });
        JsonObject manifest, target, baselineJournal;
        String manifestHash, fixtureHash, journalHash, playerHash;
        Path manifestPath, playerPath, journalPath, targetPath;
        CompoundTag diskPlayer;
        int ticks;
        RestartObservation(Context context) { c = context; }
        void start() throws Exception {
            try {
                Path root = c.server.getServerDirectory().toPath().toAbsolutePath().normalize();
                String supplied = System.getProperty("world_axioms.restartManifestSha256", "");
                check(supplied.matches("[0-9a-f]{64}"), "Explicit actual-target restart manifest SHA required");
                manifestPath = root.resolve("postclaim-restart-input-v1.json");
                manifestHash = restartHash(manifestPath); check(manifestHash.equals(supplied), "Restart manifest digest");
                manifest = readJson(manifestPath);
                check(manifest.get("schema").getAsString().equals("world_axiom_postclaim_restart_input_v1"), "Restart manifest schema");
                targetPath = restartInput(manifest, "target_result_path", "target_result_sha256", root);
                target = readJson(targetPath);
                check(target.get("schema").getAsString().equals("world_axioms_real_succession_launch_v1")
                        && target.get("phase").getAsString().equals("target") && target.get("status").getAsString().equals("PASS")
                        && target.get("phase_accepted").getAsBoolean() && target.get("immutable_inputs_unchanged").getAsBoolean()
                        && target.get("exit_code").getAsInt() == 0 && target.getAsJsonObject("owned_processes_remaining").size() == 0,
                        "Actual accepted clean target evidence required; not a synthetic initializer");
                check(target.get("runtime").getAsString().equals(root.toString())
                        && target.get("materialization_sha256").getAsString().equals(restartHash(root.resolve("runtime-inputs-v1.json"))), "Target belongs to this exact materialization/runtime");
                c.loadReceipt(); c.bindSuccessor();
                check(c.level.getSeed() == c.receipt.get("target_seed").getAsLong(), "Same persisted target world seed, not a replacement world");
                check(c.receipt.equals(target.getAsJsonObject("promotion").getAsJsonObject("fixture")), "Unchanged actual target fixture receipt");
                check(!process().equals(c.receipt.get("target_process").getAsString()), "Genuine third JVM distinct from prior target");
                fixtureHash = restartHash(c.path);
                JsonObject reports = target.getAsJsonObject("native_results");
                check(reports.size() == 1, "Exactly one prior target native report");
                for (var entry : reports.entrySet()) {
                    Path nativePath = Path.of(entry.getKey()).toAbsolutePath().normalize();
                    check(nativePath.startsWith(root) && restartHash(nativePath).equals(entry.getValue().getAsJsonObject().get("sha256").getAsString()), "Prior native report hash");
                    JsonObject nativeResult = readJson(nativePath);
                    check(nativeResult.get("schema").getAsString().equals("world_axioms_native_fixtures_v1")
                            && nativeResult.get("status").getAsString().equals("PASS") && nativeResult.get("actual_dedicated").getAsBoolean()
                            && !nativeResult.get("forge_gametest_enabled").getAsBoolean() && nativeResult.get("world_saved").getAsBoolean()
                            && nativeResult.get("save_mode").getAsString().equals("normal_shutdown")
                            && nativeResult.getAsJsonArray("classes").size() == 1
                            && nativeResult.getAsJsonArray("classes").get(0).getAsString().equals(AxiomSuccessionNativeTests.class.getName()), "Prior actual native normal save PASS/class");
                    JsonArray cases = nativeResult.getAsJsonArray("cases");
                    check(cases.size() == 1 && cases.get(0).getAsJsonObject().get("name").getAsString().equals("successorall24nativestateclaimsandretry")
                            && cases.get(0).getAsJsonObject().get("passed").getAsBoolean()
                            && cases.get(0).getAsJsonObject().get("completed").getAsBoolean()
                            && !cases.get(0).getAsJsonObject().get("failed").getAsBoolean(), "Exact actual prior native target case");
                    check((nativeResult.get("process_id").getAsLong() + "@" + Instant.parse(nativeResult.get("process_started_at").getAsString()))
                            .equals(c.receipt.get("target_process").getAsString()), "Prior target native process provenance");
                }
                JsonObject diskProof = target.getAsJsonObject("target_native_disk_proof");
                journalPath = c.server.getWorldPath(LevelResource.ROOT).resolve("data/better_world_management/world-axiom-journal-v1.json");
                journalHash = restartHash(journalPath);
                check(journalHash.equals(diskProof.get("journal_sha256").getAsString()), "Same already-released target journal bytes");
                baselineJournal = readJson(journalPath);
                for (String id : CLAIMS) {
                    var r = AxiomJournal.open(c.server).receipt(id);
                    check(r != null && r.state().equals("released") && r.actor().equals(OPERATOR.toString()), "Released BEFORE any retry " + id);
                }
                playerPath = c.server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(OPERATOR + ".dat");
                playerHash = restartHash(playerPath);
                check(playerHash.equals(diskProof.getAsJsonObject("native_nbt_sha256").get(playerPath.toAbsolutePath().toString()).getAsString()), "Same physical target player.dat BEFORE login");
                diskPlayer = NbtIo.readCompressed(playerPath.toFile());
                check(diskPlayer.hasUUID("UUID") && diskPlayer.getUUID("UUID").equals(OPERATOR), "Same persisted operator UUID");
                JsonObject priorEntityFiles = diskProof.getAsJsonObject("entity_regions_sha256");
                check(priorEntityFiles.size() > 0 && priorEntityFiles.size() <= 16, "Bounded actual prior native entity file anchors");
                for (var entry : priorEntityFiles.entrySet()) {
                    Path entityPath = Path.of(entry.getKey()).toAbsolutePath().normalize();
                    check(entityPath.startsWith(c.server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().resolve("entities"))
                            && restartHash(entityPath).equals(entry.getValue().getAsString()), "Same actual saved target entity-region bytes before loading");
                }
                scanEntities();
                c.condenser = BlockPos.of(c.receipt.get("target_condenser").getAsLong());
                chunks.add(new ChunkPos(c.condenser));
                for (ChunkPos chunk : new LinkedHashSet<>(chunks)) {
                    c.level.getChunkSource().addRegionTicket(ticket, chunk, 0, owner);
                    futures.add(java.util.concurrent.CompletableFuture.supplyAsync(() ->
                            c.level.getChunkSource().getChunkFuture(chunk.x, chunk.z, net.minecraft.world.level.chunk.ChunkStatus.FULL, false), executor).thenCompose(value -> value));
                }
                poll();
            } catch (Exception | AssertionError error) { close(); throw error; }
        }
        void close() {
            for (ChunkPos chunk : chunks) c.level.getChunkSource().removeRegionTicket(ticket, chunk, 0, owner);
            executor.shutdownNow();
        }
        void poll() {
            c.helper.runAfterDelay(1, () -> {
                try {
                    check(++ticks < 1000, "Bounded real-tick restart entity loading");
                    if (futures.stream().anyMatch(f -> !f.isDone())) { poll(); return; }
                    for (var future : futures) future.join(); // completed-only; never blocks server tick
                    for (ChunkPos chunk : chunks) {
                        if (c.level.getChunkSource().getChunkNow(chunk.x, chunk.z) == null) { poll(); return; }
                    }
                    if (ticks < 40) { poll(); return; }
                    verify(); close(); c.helper.succeed();
                } catch (Throwable error) { close(); c.helper.fail("Postclaim restart failed: " + error); }
            });
        }
        void scanEntities() throws Exception {
            Path directory = DimensionType.getStorageFolder(c.level.dimension(), c.server.getWorldPath(LevelResource.ROOT)).resolve("entities");
            List<Path> files;
            try (var stream = Files.list(directory)) { files = stream.filter(p -> p.getFileName().toString().matches("r\\.-?\\d+\\.-?\\d+\\.mca")).sorted().toList(); }
            check(!files.isEmpty() && files.size() <= 16, "Bounded complete overworld entity-region inventory");
            Map<UUID, Integer> counts = new HashMap<>();
            for (String id : List.of("ark", "familiars_name")) for (String slot : id.equals("ark") ? List.of("first", "second") : List.of("wolf"))
                counts.put(InheritanceAdapters.restoredUuid(c.transaction(), id, slot), 0);
            try (RegionFileStorage storage = AxiomRegionFileStorageAccess.worldAxioms$open(directory, false)) {
                for (Path file : files) {
                    nonsymlink(file); String[] parts = file.getFileName().toString().split("\\.");
                    int rx = Integer.parseInt(parts[1]), rz = Integer.parseInt(parts[2]);
                    for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) {
                        ChunkPos chunk = new ChunkPos(Math.addExact(Math.multiplyExact(rx, 32), x), Math.addExact(Math.multiplyExact(rz, 32), z));
                        CompoundTag saved = storage.read(chunk); if (saved == null) continue;
                        for (Tag raw : saved.getList("Entities", Tag.TAG_COMPOUND)) {
                            CompoundTag tag = (CompoundTag) raw;
                            if (tag.hasUUID("UUID") && counts.containsKey(tag.getUUID("UUID"))) {
                                counts.compute(tag.getUUID("UUID"), (key, old) -> old + 1); chunks.add(chunk);
                            }
                        }
                    }
                }
            }
            check(counts.values().stream().allMatch(n -> n == 1), "Exactly one each deterministic UUID in complete bounded native overworld entity records");
        }
        void verify() throws Exception {
            c.actor = c.player(); c.open();
            CompoundTag actual = c.actor.saveWithoutId(new CompoundTag());
            check(actual.getList("Inventory", Tag.TAG_COMPOUND).equals(diskPlayer.getList("Inventory", Tag.TAG_COMPOUND)), "Public login restored exact saved inventory/tag/counts");
            CompoundTag markers = c.actor.getPersistentData().getCompound("WorldAxiomClaims").copy();
            check(markers.equals(diskPlayer.getCompound("ForgeData").getCompound("WorldAxiomClaims")), "Public login restored exact saved claim marker tags");
            for (String id : List.of("seed_covenant", "patient_hive")) check(markers.getBoolean(c.transaction() + "/" + id), "Loaded actual player atomic claim marker");
            check(c.count(Items.WHEAT_SEEDS) == 4 && c.count(ItemsRegistration.PRINCESS.get()) == 1 && c.count(ItemsRegistration.DRONE.get()) == 1, "Exactly one prior item delivery after third-process login");
            for (ItemStack expected : InheritanceAdapters.items(c.level, "patient_hive", WorldAxiomService.configuration(c.server, "patient_hive")))
                check(c.actor.getInventory().items.stream().filter(s -> ItemStack.isSameItemSameTags(s, expected)).mapToInt(ItemStack::getCount).sum() == 1, "Restored full native bee genome NBT once");
            CompoundTag pair = WorldAxiomService.configuration(c.server, "ark");
            for (String slot : List.of("first", "second")) {
                Entity entity = c.level.getEntity(InheritanceAdapters.restoredUuid(c.transaction(), "ark", slot));
                check(entity instanceof EnhancedCow, "Same inherited native cattle UUID loaded");
                EnhancedCow cow = (EnhancedCow) entity; CompoundTag expected = pair.getCompound(slot);
                check(cow.getOrSetIsFemale() == expected.getBoolean("female")
                        && Arrays.equals(cow.getGenes().getSexlinkedGenes(), expected.getIntArray("sexlinked"))
                        && Arrays.equals(cow.getGenes().getAutosomalGenes(), expected.getIntArray("autosomal")), "Persisted cattle sex/full genome identity");
            }
            Entity entity = c.level.getEntity(InheritanceAdapters.restoredUuid(c.transaction(), "familiars_name", "wolf"));
            check(entity instanceof Wolf, "Same inherited wolf UUID loaded"); Wolf wolf = (Wolf) entity;
            check(wolf.isTame() && OPERATOR.equals(wolf.getOwnerUUID()) && wolf.getCollarColor() == DyeColor.CYAN
                    && wolf.hasCustomName() && wolf.getCustomName().getString().equals("Crossing") && wolf.isOrderedToSit(), "Persisted native familiar identity");
            ListTag inventory = actual.getList("Inventory", Tag.TAG_COMPOUND).copy();
            for (String id : CLAIMS) reject(() -> WorldAxiomService.claim(c.actor, id, new CompoundTag()), "Third-process retry denies completed " + id);
            check(c.actor.saveWithoutId(new CompoundTag()).getList("Inventory", Tag.TAG_COMPOUND).equals(inventory), "Retries changed no inventory NBT/counts");
            check(readJson(journalPath).equals(baselineJournal) && restartHash(journalPath).equals(journalHash), "Retry preserves exact released journal identities/anchors");
            check(c.actor.getPersistentData().getCompound("WorldAxiomClaims").equals(markers), "Retries changed no player claim markers");
            check(restartHash(c.path).equals(fixtureHash) && restartHash(manifestPath).equals(manifestHash)
                    && restartHash(targetPath).equals(manifest.get("target_result_sha256").getAsString()), "Prior target evidence and fixture remain immutable");
            scanEntities();
            JsonObject output = new JsonObject(); output.addProperty("schema", "world_axiom_postclaim_restart_observation_v1");
            output.addProperty("process", process()); output.addProperty("prior_target_process", c.receipt.get("target_process").getAsString());
            output.addProperty("manifest_sha256", manifestHash); output.addProperty("prior_player_sha256", playerHash);
            output.addProperty("journal_sha256", journalHash); output.addProperty("transaction", c.transaction());
            output.addProperty("case_observation", "PASS_AWAITING_NORMAL_SHUTDOWN_AND_POSTEXIT_SAVE_VERIFICATION");
            Path outputPath = c.server.getServerDirectory().toPath().resolve("postclaim-restart-observation-v1.json");
            check(!Files.exists(outputPath), "Restart observation is once-only");
            AxiomContracts.atomic(outputPath, (output + "\n").getBytes(StandardCharsets.UTF_8));
        }
    }

    private static final class Context {
        final GameTestHelper helper; final MinecraftServer server; final ServerLevel level; final Path path;
        JsonObject receipt; BlockPos condenser; FakePlayer actor;
        AutoCloseable paidSurveyCleanup;
        Context(GameTestHelper helper) throws IOException {
            this.helper = helper; this.server = helper.getLevel().getServer(); this.level = helper.getLevel();
            check(server.isDedicatedServer() && level == server.overworld(), "Dedicated ordinary overworld required");
            check("true".equals(System.getProperty("better_world_management.fixture.allow_real_succession"))
                    && "23".equals(System.getProperty("better_world_management.fixture.synthetic_parent_generation")), "Explicit REAL-succession/synthetic-parent23 fixture authorization required");
            Path root = server.getServerDirectory().toPath().toAbsolutePath().normalize();
            JsonObject materialization = readJson(root.resolve("runtime-inputs-v1.json"));
            check(materialization.get("status").getAsString().equals("materialized_unaccepted"), "Require explicitly materialized owned lab runtime, not an existing deployment");
            path = root.resolve(RECEIPT); nonsymlink(path);
        }
        void loadReceipt() throws IOException {
            receipt = readJson(path);
            check(receipt.get("schema").getAsString().equals("world_axiom_real_succession_fixture_v1")
                    && receipt.get("initialization").getAsString().equals("SYNTHETIC_PARENT23_NOT_23_REAL_RESETS")
                    && receipt.get("base_generation").getAsLong() == 23 && receipt.get("operator").getAsString().equals(OPERATOR.toString())
                    && receipt.get("source_phase").getAsString().equals("committed"), "Invalid/uncommitted owned source-phase receipt");
        }
        String transaction() { return receipt.get("transaction").getAsString(); }
        void write() throws IOException { AxiomContracts.atomic(path, (receipt + "\n").getBytes(StandardCharsets.UTF_8)); }
        void bindSuccessor() throws IOException {
            var lineage = PrestigeService.lineage(server);
            check(lineage.generation() == 24 && lineage.totalPrestiges() == 24
                    && lineage.lineageId().equals(receipt.get("lineage").getAsString()), "Real supervisor must advance synthetic parent23 exactly once");
            check(!receipt.get("source_process").getAsString().equals(process()), "Same-process manual snapshot replay is not a real succession");
            var build = WorldAxiomService.active(server).orElseThrow();
            check(build.baseGeneration() == 23 && build.targetGeneration() == 24 && build.transaction().equals(transaction())
                    && AxiomContracts.digest(build).equals(receipt.get("build_sha256").getAsString()) && build.selected().equals(allIds()), "Actual active immutable all23 build binds source transaction");
            check(level.getSeed() != receipt.get("source_seed").getAsLong(), "Successor must have a genuinely new native world seed");
            check(receipt.getAsJsonArray("registered_biomes").equals(biomeRegistry(level)),
                    "Native registered biome roster changed between source and successor");
            var fresh = WorldAxiomService.draft(server);
            check(fresh.selected().isEmpty() && fresh.budget() == 23 && fresh.baseGeneration() == 24, "Target draft is fresh/empty with capped budget23");
        }
        FakePlayer player() throws IOException {
            var existing = server.getPlayerList().getPlayer(OPERATOR);
            if (existing != null) {
                check(existing instanceof FakePlayer && existing.getGameProfile().getName().equals(NAME), "Fixture UUID collides with another native player");
                return (FakePlayer) existing;
            }
            GameProfile profile = new GameProfile(OPERATOR, NAME);
            server.getPlayerList().getOps().add(new ServerOpListEntry(profile, 4, false));
            FakePlayer player = new FakePlayer(level, profile);
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            EmbeddedChannel channel = new EmbeddedChannel(new ChannelInitializer<Channel>() {
                @Override protected void initChannel(Channel nativeChannel) { nativeChannel.pipeline().addLast("packet_handler", connection); }
            });
            CHANNELS.add(channel);
            check(connection.channel() == channel && connection.isConnected() && channel.isActive(), "Actual Netty Connection handler owns active channel");
            // Normal public Forge login-channel registration for the allowed native FakePlayer.
            // This declares protocol metadata, NOT a remote-client/authentication proof.
            NetworkHooks.registerServerLoginChannel(connection, new ClientIntentionPacket("localhost", server.getPort(), ConnectionProtocol.LOGIN));
            connection.setProtocol(ConnectionProtocol.PLAY);
            server.getPlayerList().placeNewPlayer(connection, player);
            check(server.getPlayerList().getPlayer(OPERATOR) == player && player.hasPermissions(4), "Actual native registered level4 operator required");
            return player;
        }
        void buildOwnedArena() {
            check(condenser.getY() >= level.getMinBuildHeight() + 3 && condenser.getY() + 4 < level.getMaxBuildHeight(), "Owned fixture platform within build bounds");
            // This is explicit fixture construction, not a claimed natural Font/village/archive.
            for (int x = -10; x <= 10; x++) for (int z = -10; z <= 10; z++) {
                level.setBlock(condenser.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
                for (int y = 0; y <= 3; y++) level.setBlock(condenser.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
            }
            var font = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("better_dimension_fonts:dimensional_font"));
            check(font != null && font != Blocks.AIR, "Actual pinned native Font required");
            BlockState state = font.defaultBlockState();
            for (var property : state.getProperties()) if (property instanceof net.minecraft.world.level.block.state.properties.BooleanProperty bool && bool.getName().equals("bound")) state = state.setValue(bool, false);
            // Give the genuine unbound Font a solid foundation so native block
            // physics cannot remove this owned fixture during player movement.
            check(level.setBlock(condenser.below(3), Blocks.STONE.defaultBlockState(), 3), "Font foundation placed");
            level.setBlock(condenser.below(2), state, 3); level.setBlock(condenser.below(), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(condenser, PrestigeRegistry.WORLD_CONDENSER_INTERFACE.get().defaultBlockState(), 3);
            check(level.getBlockState(condenser.below(3)).isSolidRender(level, condenser.below(3))
                    && WorldCondenserAssembly.valid(level, condenser),
                    "Physical native supported unbound Font + complete Condenser assembly");
        }
        void open() {
            actor.moveTo(condenser.getX() + .5, condenser.getY(), condenser.getZ() + 2.5, 180, 0);
            check(WorldCondenserAssembly.valid(level, condenser), "Physical Condenser must remain complete");
            check(level.getBlockEntity(condenser) instanceof MenuProvider, "Actual block-entity native menu provider");
            check(actor.openMenu((MenuProvider) level.getBlockEntity(condenser)).isPresent(), "Native player opened physical menu");
            check(actor.containerMenu instanceof WorldCondenserMenu menu && !menu.remote() && menu.pos().equals(condenser), "No manually constructed remote/surrogate menu");
        }
        void configure(String id, Map<String, String> values) throws IOException {
            CompoundTag tag = new CompoundTag(); values.forEach(tag::putString);
            WorldAxiomService.configure(actor, WorldAxiomService.draft(server).revision(), id, tag);
        }
        void nominate(String id) throws IOException { WorldAxiomService.nominate(actor, WorldAxiomService.draft(server).revision(), id); }
        void nominateNativeSources() throws IOException {
            actor.getInventory().setItem(0, new ItemStack(Items.WHEAT_SEEDS, 4)); actor.getInventory().selected = 0; nominate("seed_covenant");
            var species = level.registryAccess().registryOrThrow(SpeciesRegistration.SPECIES_REGISTRY_KEY).get(new ResourceLocation("complicated_bees:common"));
            check(species != null, "Native common bee species must exist");
            actor.getInventory().setItem(1, GeneticHelper.setGenome(new ItemStack(ItemsRegistration.PRINCESS.get()), new Chromosome(species)));
            actor.getInventory().selected = 1; nominate("patient_hive");
            actor.getInventory().setItem(2, GeneticHelper.setGenome(new ItemStack(ItemsRegistration.DRONE.get()), new Chromosome(species)));
            actor.getInventory().selected = 2; nominate("patient_hive");
            EnhancedCow female = sourceCow(true, null); lookAndNominate(female, "ark");
            female.moveTo(condenser.getX() + 7, condenser.getY(), condenser.getZ());
            EnhancedCow male = sourceCow(false, female.getGenes().getAutosomalGenes()); lookAndNominate(male, "ark");
            male.moveTo(condenser.getX() + 7, condenser.getY(), condenser.getZ() + 2);
            Wolf wolf = EntityType.WOLF.create(level); check(wolf != null, "Native owned wolf construction");
            wolf.setTame(true); wolf.setOwnerUUID(OPERATOR); wolf.setCustomName(Component.literal("Crossing")); wolf.setCollarColor(DyeColor.CYAN);
            wolf.moveTo(condenser.getX() + .5, condenser.getY(), condenser.getZ() - 1.5);
            check(level.addFreshEntity(wolf), "Actual native source wolf admission"); lookAndNominate(wolf, "familiars_name");
            wolf.setOrderedToSit(true); wolf.moveTo(condenser.getX() - 7, condenser.getY(), condenser.getZ());
            for (String id : CLAIMS) InheritanceAdapters.revalidateSources(actor, id, WorldAxiomService.parsePayload(WorldAxiomService.draft(server).payloads().get(id)));
        }
        EnhancedCow sourceCow(boolean female, int[] differentFrom) {
            var type = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("eanimod:enhanced_cow"));
            check(type != null, "Pinned native genetic cattle required");
            for (int attempt = 0; attempt < 512; attempt++) {
                EnhancedCow cow = (EnhancedCow) type.create(level); check(cow != null, "Native source cattle construction");
                cow.moveTo(condenser.getX() + .5, condenser.getY(), condenser.getZ() - 1.5);
                cow.finalizeSpawn(level, level.getCurrentDifficultyAt(condenser), MobSpawnType.COMMAND, null, null);
                int[] genes = cow.getGenes().getAutosomalGenes();
                if (!cow.isBaby() && cow.getOrSetIsFemale() == female && genes[26] != 1 && genes[27] != 1
                        && (differentFrom == null || !Arrays.equals(genes, differentFrom))) {
                    cow.setPersistenceRequired(); check(level.addFreshEntity(cow), "Native generated adult cattle admission"); return cow;
                }
                cow.discard();
            }
            throw new AssertionError("Could not select native-generated viable adult " + (female ? "female" : "male") + " without age/genome setters");
        }
        void lookAndNominate(Entity entity, String id) throws IOException {
            open(); actor.lookAt(EntityAnchorArgument.Anchor.EYES, entity.getEyePosition()); actor.setOldPosAndRot(); nominate(id);
        }
        BlockPos loadedNaturalMineshaft() {
            var structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(new ResourceLocation("minecraft:mineshaft"));
            if (structure == null) return null;
            int cx = condenser.getX() >> 4, cz = condenser.getZ() >> 4;
            for (int dx = -8; dx <= 8; dx++) for (int dz = -8; dz <= 8; dz++) {
                var chunk = level.getChunkSource().getChunkNow(cx + dx, cz + dz); if (chunk == null) continue;
                var start = chunk.getStartForStructure(structure); if (start == null || !start.isValid()) continue;
                for (var piece : start.getPieces()) {
                    BlockPos point = piece.getBoundingBox().getCenter();
                    var at = level.getChunkSource().getChunkNow(point.getX() >> 4, point.getZ() >> 4);
                    if (at != null && piece.getBoundingBox().isInside(point) && at.getBlockState(point).isAir() && at.getBlockState(point.above()).isAir()) return point;
                }
            }
            return null;
        }
        void commitSource() throws IOException {
            // Source-authored preferences only. The normal bounded safe-biome search
            // must still finish before promotion.
            open(); PrestigeService.saveDraft(actor, List.of("minecraft:plains", "minecraft:forest", "minecraft:meadow"));
            PrestigeService.stage(actor, condenser);
            var staged = AxiomContracts.read(WorldAxiomService.control(server, WorldAxiomService.STAGED));
            check(staged.phase().equals("staged") && staged.selected().equals(allIds()), "Actual source stage froze all23");
            receipt.addProperty("source_phase", "ready_to_commit"); write();
            String tx = PrestigeService.commit(actor, condenser); // Production schedules the real clean stop; no manual reset implementation.
            var committed = AxiomContracts.read(WorldAxiomService.control(server, WorldAxiomService.RESET));
            check(committed.transaction().equals(tx) && committed.selected().equals(allIds()), "Actual production reset and World Axioms share transaction");
            receipt.addProperty("transaction", tx); receipt.addProperty("build_sha256", AxiomContracts.digest(committed));
            receipt.addProperty("source_phase", "committed"); receipt.addProperty("source_committed_utc", Instant.now().toString());
            receipt.addProperty("source_condenser", condenser.asLong()); write();
            System.out.println("WLM_AXIOM_REAL_SUCCESSION SOURCE_PASS scheduled_real_stop=true synthetic_parent_generation=23 transaction=" + tx);
            helper.succeed();
        }
        void verifyNativeWorld() throws IOException {
            check(level.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator noise && noise.stable(NoiseGeneratorSettings.LARGE_BIOMES), "Actual native Large Biomes noise generator, not only server.properties");
            JsonObject runtime = readJson(server.getServerDirectory().toPath().resolve("kubejs/config/world_axioms_runtime.json"));
            check(runtime.get("generation").getAsLong() == 24 && runtime.get("transaction").getAsString().equals(transaction())
                    && runtime.get("build_sha256").getAsString().equals(receipt.get("build_sha256").getAsString())
                    && runtime.getAsJsonArray("selected").size() == 23, "Actual immutable runtime recipe/world projection identity");
            String landedBiome = level.getBiome(level.getSharedSpawnPos()).unwrapKey()
                    .map(key -> key.location().toString()).orElseThrow();
            check(List.of("minecraft:plains", "minecraft:forest", "minecraft:meadow").contains(landedBiome),
                    "Genuine successor spawn must be in an actually selected biome");
            receipt.addProperty("natural_selected_biome", landedBiome);
            receipt.addProperty("natural_selected_spawn", level.getSharedSpawnPos().asLong());
            for (String variant : List.of("home", "echo")) {
                var id = new ResourceLocation("better_ore_geology:ironstone_" + variant);
                var feature = level.registryAccess().registryOrThrow(Registries.PLACED_FEATURE).get(id);
                check(feature != null, "Actual registered deposit override must exist");
                JsonElement encoded = PlacedFeature.DIRECT_CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE, level.registryAccess()), feature)
                        .getOrThrow(false, message -> { throw new IllegalStateException(message); });
                boolean found = false;
                for (JsonElement modifier : encoded.getAsJsonObject().getAsJsonArray("placement")) {
                    JsonObject tag = modifier.getAsJsonObject();
                    if (tag.get("type").getAsString().equals("minecraft:height_range")) {
                        JsonObject height = tag.getAsJsonObject("height");
                        check(height.getAsJsonObject("min_inclusive").get("absolute").getAsInt() == 0
                                && height.getAsJsonObject("max_inclusive").get("absolute").getAsInt() == 80, "Loaded native deposit height band, not just datapack bytes");
                        found = true;
                    }
                }
                check(found, "Native height modifier found");
            }
            check(SeasonHelper.getSeasonState(level).getSubSeason() == Season.SubSeason.EARLY_SUMMER, "Initial native early summer is running naturally");
            var calendarData = CalendarSavedData.get(server); var calendar = calendarData.calendar();
            CompoundTag calendarSaved = calendarData.save(new CompoundTag());
            check(calendarSaved.getString("firstOmenTransaction").equals(transaction()) && calendar.nextEvent().id().equals("open_ways")
                    && calendar.state().serial() == 0 && calendar.state().bag().size() == 3 && calendar.nextDay() >= 8 && calendar.nextDay() <= 12, "Native first omen receipt, untouched initial date and first bag");
            var trader = WanderingTraderScheduleData.get(level); CompoundTag traderSaved = trader.save(new CompoundTag());
            check(traderSaved.getString("FoundersTransaction").equals(transaction()) && trader.nextCurrencyIdentity().id().equals("work")
                    && !traderSaved.getBoolean("FirstVisitCompleted"), "First native trader identity still pending without injected visit/date");
            receipt.addProperty("native_next_omen_day", calendar.nextDay()); receipt.addProperty("native_next_trader_time", trader.nextAttemptGameTime());
            Path world = server.getWorldPath(LevelResource.ROOT);
            CompoundTag savedPolicy = NbtIo.readCompressed(world.resolve("data/better_world_management_axiom_world_v1.dat").toFile()).getCompound("data").getCompound("journal");
            check(savedPolicy.getString("transaction").equals(transaction()), "Actual native disk world-policy journal binds transaction");
            for (String id : List.of("turning_year", "chosen_omen", "founders_exchange", "communal_archive")) check(savedPolicy.getBoolean(id), "Native disk completed world policy " + id);
            CompoundTag diskCalendar = NbtIo.readCompressed(world.resolve("data/better_celestial_events_calendar.dat").toFile()).getCompound("data");
            CompoundTag diskTrader = NbtIo.readCompressed(world.resolve("data/better_spirit_commerce_wandering_trader.dat").toFile()).getCompound("data");
            SeasonSavedData diskSeason = SeasonSavedData.load(NbtIo.readCompressed(world.resolve("data/" + SeasonSavedData.DATA_IDENTIFIER + ".dat").toFile()).getCompound("data"));
            JsonObject savedCalendarState = JsonParser.parseString(diskCalendar.getString("calendar")).getAsJsonObject();
            check(diskCalendar.getString("firstOmenTransaction").equals(transaction()) && savedCalendarState.get("nextEvent").getAsString().equals("OPEN_WAYS")
                    && savedCalendarState.get("serial").getAsLong() == 0 && savedCalendarState.get("nextDay").getAsLong() == calendar.nextDay()
                    && diskTrader.getString("FoundersTransaction").equals(transaction()) && diskTrader.getString("FirstCurrency").equals("work")
                    && !diskTrader.getBoolean("FirstVisitCompleted"), "Native calendar/trader dat files contain actual pending identities/dates, not just in-memory receipts");
            check(new SeasonTime(diskSeason.seasonCycleTicks).getSubSeason() == Season.SubSeason.EARLY_SUMMER
                    && SeasonHandler.getSeasonSavedData(level).seasonCycleTicks >= diskSeason.seasonCycleTicks
                    && savedPolicy.getInt("seasonTarget") == 3 * SeasonTime.ZERO.getSubSeasonDuration(), "Native saved season counter initialized once and subsequently progresses normally");
            BlockPos archive = BlockPos.of(savedPolicy.getLong("archiveOrigin"));
            check(level.getBlockEntity(archive.offset(0, 1, 2)) instanceof ChestBlockEntity, "Actual saved native communal archive chest");
            ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(archive.offset(0, 1, 2));
            int books = 0;
            for (int i = 0; i < chest.getContainerSize(); i++) if (!chest.getItem(i).isEmpty()) {
                ItemStack stack = chest.getItem(i); check(stack.is(Items.WRITTEN_BOOK) && stack.getCount() == 1, "Only four individual archive books");
                int number = ++books; ListTag pages = stack.getOrCreateTag().getList("pages", Tag.TAG_STRING);
                check(pages.size() == 4, "Actual written book four pages");
                String[] expected = book(number).split("\n", -1);
                for (int page = 0; page < 4; page++) check(Component.Serializer.fromJson(pages.getString(page)).getString().equals(expected[page]), "Actual preserved literal page text");
            }
            check(books == 4, "Exactly four saved archive books");
            check(level.getBlockEntity(archive.offset(0, 1, -4)) instanceof SignBlockEntity, "Actual saved archive plaque");
            SignBlockEntity sign = (SignBlockEntity) level.getBlockEntity(archive.offset(0, 1, -4));
            StringBuilder plaque = new StringBuilder(); for (Component line : sign.getFrontText().getMessages(false)) plaque.append(line.getString());
            check(sign.isWaxed() && plaque.toString().equals(PLAQUE), "Actual preserved waxed plaque text");
            check(AxiomJournal.open(server).completed("world"), "Production world effects have durable applied receipt");
            receipt.addProperty("native_archive_origin", archive.asLong()); receipt.addProperty("native_world_observed_process", process()); write();
        }
        void verifyMovingNativeEntityDisk() throws IOException {
            Map<UUID, Integer> counts = new HashMap<>(); Set<ChunkPos> chunks = new HashSet<>();
            for (String id : List.of("ark", "familiars_name")) {
                var anchor = AxiomJournal.open(server).receipt(id).anchor();
                var slots = id.equals("ark") ? List.of("first", "second") : List.of("wolf");
                for (int i = 0; i < slots.size(); i++) {
                    UUID uuid = InheritanceAdapters.restoredUuid(transaction(), id, slots.get(i)); counts.put(uuid, 0);
                    Entity entity = level.getEntity(uuid); check(entity != null, "Native claimed entity still live");
                    chunks.add(new ChunkPos(anchor.offset(i * 2, 0, 0)));
                    ChunkPos actual = entity.chunkPosition();
                    // Released animals may move: inspect current and original native chunks without freezing or teleporting them.
                    for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) chunks.add(new ChunkPos(actual.x + dx, actual.z + dz));
                }
            }
            Path directory = DimensionType.getStorageFolder(level.dimension(), server.getWorldPath(LevelResource.ROOT)).resolve("entities");
            try (RegionFileStorage storage = AxiomRegionFileStorageAccess.worldAxioms$open(directory, false)) {
                for (ChunkPos chunk : chunks) {
                    if (!Files.isRegularFile(directory.resolve("r." + (chunk.x >> 5) + "." + (chunk.z >> 5) + ".mca"))) continue;
                    CompoundTag saved = storage.read(chunk); if (saved == null) continue;
                    for (Tag entry : saved.getList("Entities", Tag.TAG_COMPOUND)) {
                        CompoundTag tag = (CompoundTag) entry;
                        if (tag.hasUUID("UUID") && counts.containsKey(tag.getUUID("UUID"))) counts.compute(tag.getUUID("UUID"), (key, old) -> old + 1);
                    }
                }
            }
            check(counts.values().stream().allMatch(count -> count == 1), "Exactly one each of three deterministic UUIDs in actual native .mca entity records");
        }
        int count(net.minecraft.world.item.Item item) {
            int count = 0; for (ItemStack stack : actor.getInventory().items) if (stack.is(item)) count += stack.getCount(); return count;
        }
        void verifyClaimsAndFinish() throws IOException {
            open(); var build = WorldAxiomService.active(server).orElseThrow();
            check(count(Items.WHEAT_SEEDS) == 4 && count(ItemsRegistration.PRINCESS.get()) == 1 && count(ItemsRegistration.DRONE.get()) == 1, "Exactly one communal cultivar4 and native princess/drone pair");
            var expectedBees = InheritanceAdapters.items(level, "patient_hive", WorldAxiomService.configuration(server, "patient_hive"));
            for (ItemStack expected : expectedBees) {
                int exact = actor.getInventory().items.stream().filter(stack -> ItemStack.isSameItemSameTags(stack, expected)).mapToInt(ItemStack::getCount).sum();
                check(exact == 1, "Full native genome/clean bee item NBT matches frozen nomination once");
            }
            CompoundTag cowPair = WorldAxiomService.configuration(server, "ark");
            for (String slot : List.of("first", "second")) {
                UUID uuid = InheritanceAdapters.restoredUuid(transaction(), "ark", slot); Entity entity = level.getEntity(uuid);
                check(entity instanceof EnhancedCow, "Actual deterministic inherited native cattle loaded"); EnhancedCow cow = (EnhancedCow) entity;
                CompoundTag expected = cowPair.getCompound(slot), saved = new CompoundTag(); cow.addAdditionalSaveData(saved);
                check(cow.getOrSetIsFemale() == expected.getBoolean("female") && !cow.isBaby()
                        && Arrays.equals(cow.getGenes().getSexlinkedGenes(), expected.getIntArray("sexlinked"))
                        && Arrays.equals(cow.getGenes().getAutosomalGenes(), expected.getIntArray("autosomal")), "Actual restored adult sex and full diploid genomes");
                check(!saved.getBoolean("Pregnant") && saved.getInt("Gestation") == 0 && cow.getHealth() == cow.getMaxHealth()
                        && cow.getActiveEffects().isEmpty() && !cow.isNoAi() && !cow.isInvulnerable() && !cow.isNoGravity(), "Fresh native cattle vitals, no gestation/effects or receipt pin retained");
            }
            UUID wolfId = InheritanceAdapters.restoredUuid(transaction(), "familiars_name", "wolf");
            check(level.getEntity(wolfId) instanceof Wolf, "Actual deterministic inherited wolf loaded"); Wolf wolf = (Wolf) level.getEntity(wolfId);
            check(wolf.isTame() && OPERATOR.equals(wolf.getOwnerUUID()) && wolf.getCollarColor() == DyeColor.CYAN
                    && wolf.hasCustomName() && wolf.getCustomName().getString().equals("Crossing") && wolf.isOrderedToSit()
                    && wolf.getHealth() == wolf.getMaxHealth() && wolf.getActiveEffects().isEmpty() && !wolf.isNoAi() && !wolf.isInvulnerable(), "Actual inherited healthy wolf identity across source/target processes");
            for (String id : CLAIMS) {
                var entry = AxiomJournal.open(server).receipt(id);
                check(entry != null && entry.state().equals("released") && entry.actor().equals(OPERATOR.toString()), "Actual communal journal receipt released once to fixed native operator");
                reject(() -> WorldAxiomService.claim(actor, id, new CompoundTag()), "same-profile public API retry " + id);
            }
            check(count(Items.WHEAT_SEEDS) == 4 && count(ItemsRegistration.PRINCESS.get()) == 1 && count(ItemsRegistration.DRONE.get()) == 1, "Public retry granted zero extra items");
            check(server.saveEverything(false, true, true), "Actual native save before disk proof");
            verifyMovingNativeEntityDisk();
            Path playerFile = server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(OPERATOR + ".dat");
            CompoundTag player = NbtIo.readCompressed(playerFile.toFile());
            CompoundTag markers = player.getCompound("ForgeData").getCompound("WorldAxiomClaims");
            check(markers.getBoolean(transaction() + "/seed_covenant") && markers.getBoolean(transaction() + "/patient_hive"), "Physical native player.dat contains atomic item receipts");
            int seeds = 0, princesses = 0, drones = 0;
            for (Tag entry : player.getList("Inventory", Tag.TAG_COMPOUND)) {
                ItemStack stack = ItemStack.of((CompoundTag) entry);
                if (stack.is(Items.WHEAT_SEEDS)) seeds += stack.getCount();
                if (stack.is(ItemsRegistration.PRINCESS.get())) princesses += stack.getCount();
                if (stack.is(ItemsRegistration.DRONE.get())) drones += stack.getCount();
            }
            check(seeds == 4 && princesses == 1 && drones == 1, "SAME physical native player.dat contains exactly one paid delivery");
            receipt.addProperty("target_phase", "claims_and_disk_proof_pass"); receipt.addProperty("target_process", process());
            receipt.addProperty("target_seed", level.getSeed()); receipt.addProperty("completed_utc", Instant.now().toString());
            receipt.addProperty("natural_reproduction", "NOT_RUN_SEPARATE_UNSHORTENED_40_MINUTE_CATTLE_AND_NATIVE_APIARY_CAMPAIGNS_REQUIRED");
            write();
            startPaidSurveyAfterClaims(playerFile);
        }
        void closePaidSurvey() throws Exception {
            AutoCloseable cleanup = paidSurveyCleanup;
            paidSurveyCleanup = null;
            if (cleanup != null) cleanup.close();
        }
        void paidSurveyGuard(Action action) {
            try { action.run(); }
            catch (Throwable error) {
                try { closePaidSurvey(); } catch (Throwable cleanup) { error.addSuppressed(cleanup); }
                System.err.println("WLM_AXIOM_REAL_SUCCESSION PAID_SURVEY_FAIL " + error);
                helper.fail("Target paid survey continuation failed: " + error);
            }
        }
        void startPaidSurveyAfterClaims(Path playerFile) throws IOException {
            // All existing actor, inventory, journal and native disk checks above remain prerequisites.
            check(helper.getTick() >= 0 && helper.getTick() <= 1200 - 240,
                    "Target must retain >=240 ordinary ticks within unchanged1200tick deadline");
            check(actor != null && actor instanceof FakePlayer && actor.getUUID().equals(OPERATOR)
                    && actor.getGameProfile().getName().equals(NAME)
                    && server.getPlayerList().getPlayer(OPERATOR) == actor,
                    "Only the exact owned registered saga FakePlayer may be removed");
            check(actor.connection != null && actor.connection.connection.channel() instanceof EmbeddedChannel,
                    "Owned saga actor must retain its real embedded Connection");
            Connection connection = actor.connection.connection;
            EmbeddedChannel channel = (EmbeddedChannel) connection.channel();
            check(CHANNELS.contains(channel) && channel.isActive() && connection.isConnected(),
                    "Saga player channel must be one actually created by this fixture");
            Path journal = server.getWorldPath(LevelResource.ROOT).resolve("data/better_world_management/world-axiom-journal-v1.json");
            CompoundTag beforePlayer = NbtIo.readCompressed(playerFile.toFile());
            byte[] beforeJournal = Files.readAllBytes(journal);
            // Public removal saves the SAME actor normally; never delete/rewrite player or claim files.
            // All ownership checks precede this operation, and no human/external actor is accepted.
            try { server.getPlayerList().remove(actor); }
            finally {
                try { connection.disconnect(Component.literal("Owned saga claim actor finished native disk checks")); }
                finally { channel.finishAndReleaseAll(); CHANNELS.remove(channel); }
            }
            check(server.getPlayerList().getPlayer(OPERATOR) == null && !channel.isActive(),
                    "Owned saga actor/Connection must be gone before isolated survey admission");
            check(Files.isRegularFile(playerFile), "Normal saga player removal must preserve actual player.dat");
            CompoundTag afterPlayer = NbtIo.readCompressed(playerFile.toFile());
            check(afterPlayer.hasUUID("UUID") && afterPlayer.getUUID("UUID").equals(OPERATOR)
                    && beforePlayer.getList("Inventory", Tag.TAG_COMPOUND).equals(afterPlayer.getList("Inventory", Tag.TAG_COMPOUND))
                    && beforePlayer.getCompound("ForgeData").getCompound("WorldAxiomClaims")
                        .equals(afterPlayer.getCompound("ForgeData").getCompound("WorldAxiomClaims"))
                    && Arrays.equals(beforeJournal, Files.readAllBytes(journal)),
                    "Public removal must preserve claimed inventory/markers and exact released journal");
            byte[] removedPlayerBytes = Files.readAllBytes(playerFile);
            receipt.addProperty("paid_survey_phase", "awaiting_actual_native_observation"); write();
            paidSurveyGuard(() -> {
                check(helper.getTick() <= 1200 - 240, "Paid survey start still has >=240 real ticks remaining");
                paidSurveyCleanup = com.bettercontent.betterworldmanagement.worldshaping.exploration.PaidSurveyNativeChecks.start(
                        helper, condenser.offset(5, 0, 5), () -> paidSurveyGuard(() -> {
                            closePaidSurvey();
                            check(server.getPlayerList().getPlayer(OPERATOR) == null && Files.isRegularFile(playerFile),
                                    "Survey must not substitute the saga claim actor or remove its save");
                            check(Arrays.equals(beforeJournal, Files.readAllBytes(journal))
                                    && Arrays.equals(removedPlayerBytes, Files.readAllBytes(playerFile)),
                                    "Survey must preserve exact removed saga player save and claim journal");
                            receipt.addProperty("paid_survey_phase", "actual_native_positive_occlusion_no_spyglass_forecast_pass");
                            write();
                            System.out.println("WLM_AXIOM_REAL_SUCCESSION TARGET_PASS generation=24 active=24 fresh_draft=0 claims=4 native_player_dat=true native_entity_regions=true public_retry_no_dup=true paid_survey_native=true transaction=" + transaction());
                            helper.succeed();
                        }));
            });
        }
    }

    private static JsonArray biomeRegistry(ServerLevel level) {
        var ids = new TreeSet<String>();
        for (var id : level.registryAccess().registryOrThrow(Registries.BIOME).keySet())
            check(ids.add(id.toString()), "Duplicate native biome registry ID");
        check(ids.size() >= 50 && ids.size() <= 10000, "Unbounded native biome registry roster");
        var result = new JsonArray();
        ids.forEach(result::add);
        return result;
    }

    private static Set<String> allIds() { var ids = new TreeSet<String>(); for (var node : AxiomCatalog.nodes()) ids.add(node.id()); check(ids.size() == 23, "Closed exact23 catalog"); return Set.copyOf(ids); }
    private static String book(int number) { return "Generation23 book " + number + "\nAcross a real archived world\nKeep the native laws honest\n" + "x".repeat(256); }
    private static String process() { return ProcessHandle.current().pid() + "@" + ProcessHandle.current().info().startInstant().orElseThrow(); }
    private static void nonsymlink(Path path) throws IOException { for (Path p = path.toAbsolutePath(); p != null; p = p.getParent()) if (Files.isSymbolicLink(p)) throw new IOException("Fixture refuses symlink: " + p); }
    private static JsonObject readJson(Path path) throws IOException { nonsymlink(path); check(Files.isRegularFile(path) && Files.size(path) <= 32L * 1024 * 1024, "Missing/oversized owned fixture metadata " + path); return JsonParser.parseString(Files.readString(path)).getAsJsonObject(); }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    @FunctionalInterface private interface Action { void run() throws Exception; }
    @FunctionalInterface private interface IoAction { void run() throws IOException; }
    private static void reject(IoAction action, String detail) throws IOException {
        try { action.run(); }
        catch (IllegalStateException expected) {
            check(expected.getMessage() != null && expected.getMessage().contains("already claimed"), "Retry rejected for wrong reason: " + expected);
            return;
        }
        throw new AssertionError("Expected native public API retry rejection: " + detail);
    }
    private static void guarded(GameTestHelper helper, Action action) {
        try { action.run(); }
        catch (Throwable failure) { System.err.println("WLM_AXIOM_REAL_SUCCESSION FAIL " + failure); helper.fail(failure.toString()); }
    }
}
