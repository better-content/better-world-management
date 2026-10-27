package com.bettercontent.betterworldmanagement.worldshaping;

import com.bettercontent.betterworldmanagement.PrestigeContracts;
import com.bettercontent.betterworldmanagement.PrestigeRegistry;
import com.bettercontent.betterworldmanagement.PrestigeService;
import com.bettercontent.betterworldmanagement.WorldCondenserAssembly;
import com.bettercontent.betterworldmanagement.WorldCondenserMenu;
import com.bettercontent.betterworldmanagement.worldshaping.inheritance.InheritanceAdapters;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.embedded.EmbeddedChannel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * ONE sequential production-service fixture: physical JSON + native player.dat replay.
 * Run ONLY in the root-owned disposable dedicated GameTest runtime with no external
 * succession supervisor and no other shared-lineage-mutating GameTests in the batch.
 * Disk state replay here is explicitly NOT a process restart or full succession proof.
 */
@PrefixGameTestTemplate(false)
public final class AxiomLifecycleNativeTests {
    private static final String JOURNAL = "data/better_world_management/world-axiom-journal-v1.json";
    private AxiomLifecycleNativeTests() {}

    @GameTest(templateNamespace = "better_world_management", template = "empty", timeoutTicks = 1200)
    public static void sequentialActualServiceLifecycleAndCommunalClaims(GameTestHelper helper) throws IOException {
        MinecraftServer server = helper.getLevel().getServer();
        check(server.isDedicatedServer(), "Dedicated server is mandatory");
        check(helper.getLevel() == server.overworld(), "Run sequential lifecycle fixture in the ordinary overworld GameTest level");
        // The fixture must not erase a real delivery that is currently pinned by production.
        Optional<AxiomContracts.Build> originalActive = WorldAxiomService.active(server);
        if (originalActive.isPresent()) for (var entry : AxiomJournal.open(server).receipts().entrySet())
            if (Set.of("ark", "familiars_name").contains(entry.getKey()))
                check(entry.getValue().state().equals("released"), "Refuse fixture while existing real entities are pinned");
        try (Fixture f = new Fixture(helper, originalActive)) {
            try {
                f.isolate();
                f.accessAndDrafts();
                f.freezeCommitAndFreshDraft();
                f.nominateAndActivateClaims();
                f.inventoryPendingAtomicPlayerReceiptAndReplay();
                f.unsafeEntityPendingAndDeterministicReplay();
                f.evidence("BODY_PASS_RESTORE_PENDING", "Actual service, physical files, native inventory/player.dat and fixture-state replay");
                f.bodyPassed = true;
            } catch (IOException | RuntimeException | Error failure) {
                try { f.evidence("FAIL", failure.toString()); } catch (IOException evidenceFailure) { failure.addSuppressed(evidenceFailure); }
                throw failure;
            }
        }
        System.out.println("WLM_AXIOM_LIFECYCLE PASS state_restored=true replay=fixture_state_not_process_restart");
        helper.succeed();
    }

    private static final class Fixture implements AutoCloseable {
        final MinecraftServer server;
        final ServerLevel level;
        final BlockPos condenser;
        final Optional<AxiomContracts.Build> originalActive;
        final Map<Path, byte[]> files = new LinkedHashMap<>();
        final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        final Map<BlockPos, CompoundTag> blockEntities = new LinkedHashMap<>();
        final List<FakePlayer> players = new ArrayList<>();
        final List<EmbeddedChannel> channels = new ArrayList<>();
        final List<UUID> entities = new ArrayList<>();
        final List<String> phases = new ArrayList<>();
        final String lineage = "fixture-" + UUID.randomUUID();
        final String transaction = "fixture-" + UUID.randomUUID();
        final Path control, snapshot, journal, projection, lineagePath, evidenceRoot;
        final Set<Path> originalControlFiles;
        FakePlayer operator, otherOperator, nonOperator;
        AxiomContracts.Build committed;
        boolean mutated, bodyPassed;

        Fixture(GameTestHelper h, Optional<AxiomContracts.Build> originalActive) throws IOException {
            this.server = h.getLevel().getServer(); this.level = h.getLevel();
            this.condenser = h.absolutePos(new BlockPos(3, 4, 3)); this.originalActive = originalActive;
            this.control = PrestigeService.control(server);
            this.snapshot = server.getWorldPath(LevelResource.ROOT).resolve(WorldAxiomService.SNAPSHOT);
            this.journal = server.getWorldPath(LevelResource.ROOT).resolve(JOURNAL);
            this.projection = server.getServerDirectory().toPath().resolve("kubejs/config/world_axioms_runtime.json");
            this.lineagePath = PrestigeService.state(server).resolve("lineage-v5.tsv");
            this.evidenceRoot = server.getServerDirectory().toPath().resolve("world-axiom-lifecycle-evidence").resolve(transaction);
            originalControlFiles = treeFiles(control);
            for (Path p : originalControlFiles) remember(p);
            for (Path p : List.of(snapshot, journal, projection, lineagePath, server.getPlayerList().getOps().getFile().toPath(),
                    server.getServerDirectory().toPath().resolve("usercache.json"))) remember(p);
            // Include the entire deterministic entity-claim search region and assembly.
            for (BlockPos p : BlockPos.betweenClosed(condenser.offset(-10, -4, -10), condenser.offset(12, 3, 10))) {
                BlockPos key = p.immutable(); blocks.put(key, level.getBlockState(key));
                BlockEntity be = level.getBlockEntity(key); if (be != null) blockEntities.put(key, be.saveWithFullMetadata());
            }
        }

        void isolate() throws IOException {
            mutated = true;
            for (Path p : originalControlFiles) Files.deleteIfExists(p);
            Files.deleteIfExists(snapshot); Files.deleteIfExists(journal); Files.deleteIfExists(projection);
            WorldAxiomService.clear(server);
            PrestigeContracts.writeLineage(lineagePath, new PrestigeContracts.Lineage(lineage, 0, 0));
            // Native font registry and native Condenser BE; no surrogate block or access bypass.
            for (BlockPos p : blocks.keySet()) level.setBlock(p, Blocks.STONE.defaultBlockState(), 3);
            assemble();
            check(WorldCondenserAssembly.valid(level, condenser), "Real unbound native Font assembly");
            operator = player("AxiomOperator", true); otherOperator = player("AxiomOtherOp", true); nonOperator = player("AxiomNonOp", false);
            check(operator.hasPermissions(4) && otherOperator.hasPermissions(4) && !nonOperator.hasPermissions(4), "Actual native op list grants/denies level4");
            open(operator, false); open(otherOperator, false); open(nonOperator, false);
            // Keep bystanders out of the real entity nomination ray, while physically nearby.
            otherOperator.moveTo(condenser.getX() + 3.5, condenser.getY(), condenser.getZ() + 2.5);
            nonOperator.moveTo(condenser.getX() - 2.5, condenser.getY(), condenser.getZ() + 2.5);
            phase("native_players_and_real_assembly");
        }

        FakePlayer player(String name, boolean op) throws IOException {
            GameProfile profile = new GameProfile(UUID.randomUUID(), name);
            FakePlayer player = new FakePlayer(level, profile); players.add(player);
            for (LevelResource resource : List.of(LevelResource.PLAYER_DATA_DIR, LevelResource.PLAYER_ADVANCEMENTS_DIR, LevelResource.PLAYER_STATS_DIR)) {
                String suffix = resource == LevelResource.PLAYER_DATA_DIR ? ".dat" : ".json";
                remember(server.getWorldPath(resource).resolve(profile.getId() + suffix));
                if (resource == LevelResource.PLAYER_DATA_DIR) remember(server.getWorldPath(resource).resolve(profile.getId() + ".dat_old"));
            }
            if (op) server.getPlayerList().getOps().add(new ServerOpListEntry(profile, 4, false));
            // Use native login registration so stage() resolves the actual nominator by UUID.
            // No manual mutation of PlayerList internals or permission override/subclass is used.
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            EmbeddedChannel channel = new EmbeddedChannel(new ChannelInitializer<Channel>() {
                @Override protected void initChannel(Channel nativeChannel) { nativeChannel.pipeline().addLast("packet_handler", connection); }
            });
            channels.add(channel);
            connection.setProtocol(ConnectionProtocol.PLAY);
            server.getPlayerList().placeNewPlayer(connection, player);
            check(server.getPlayerList().getPlayer(player.getUUID()) == player, "Native player UUID registration");
            player.moveTo(condenser.getX() + .5, condenser.getY(), condenser.getZ() + 2.5, 180, 0);
            return player;
        }

        void assemble() {
            var font = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("better_dimension_fonts:dimensional_font"));
            check(font != null && font != Blocks.AIR, "Pinned native Font must resolve");
            BlockState state = font.defaultBlockState();
            for (var property : state.getProperties()) if (property instanceof net.minecraft.world.level.block.state.properties.BooleanProperty bool && bool.getName().equals("bound")) state = state.setValue(bool, false);
            level.setBlock(condenser.below(2), state, 3); level.setBlock(condenser.below(), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(condenser, PrestigeRegistry.WORLD_CONDENSER_INTERFACE.get().defaultBlockState(), 3);
            level.setBlock(condenser.south(2), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(condenser.south(2).above(), Blocks.AIR.defaultBlockState(), 3);
        }
        void open(FakePlayer player, boolean remote) {
            player.moveTo(condenser.getX() + .5, condenser.getY(), condenser.getZ() + 2.5, 180, 0);
            if (remote) player.containerMenu = new WorldCondenserMenu(77, player.getInventory(), condenser, true, 2);
            else {
                BlockEntity be = level.getBlockEntity(condenser);
                check(be instanceof net.minecraft.world.MenuProvider, "Actual native Condenser menu provider");
                check(player.openMenu((net.minecraft.world.MenuProvider) be).isPresent(), "Native ServerPlayer menu-open succeeded");
                check(player.containerMenu instanceof WorldCondenserMenu menu && !menu.remote() && menu.pos().equals(condenser), "Menu came from the actual native Condenser BE");
            }
        }

        void accessAndDrafts() throws IOException {
            AxiomContracts.Build empty = WorldAxiomService.draft(server);
            check(empty.selected().isEmpty() && empty.revision() == 0 && empty.budget() == 1, "Generation zero starts fresh with one upcoming point");
            reject(() -> WorldAxiomService.toggle(nonOperator, 0, "iron_law"), "nonoperator");
            open(operator, true); reject(() -> WorldAxiomService.toggle(operator, 0, "iron_law"), "remote menu"); open(operator, false);
            operator.moveTo(condenser.getX() + 20, condenser.getY(), condenser.getZ());
            reject(() -> WorldAxiomService.toggle(operator, 0, "iron_law"), "distant physical access"); open(operator, false);
            level.setBlock(condenser.below(), Blocks.STONE.defaultBlockState(), 3);
            reject(() -> WorldAxiomService.toggle(operator, 0, "iron_law"), "broken assembly"); assemble(); open(operator, false);
            check(!Files.exists(WorldAxiomService.control(server, WorldAxiomService.DRAFT)), "Rejected mutations never write a draft");
            WorldAxiomService.toggle(operator, 0, "iron_law");
            byte[] before = Files.readAllBytes(WorldAxiomService.control(server, WorldAxiomService.DRAFT));
            reject(() -> WorldAxiomService.toggle(operator, 0, "eternal_engine"), "stale revision");
            reject(() -> WorldAxiomService.toggle(operator, 1, "eternal_engine"), "point budget");
            check(java.util.Arrays.equals(before, Files.readAllBytes(WorldAxiomService.control(server, WorldAxiomService.DRAFT))), "Stale/budget rejects preserve physical draft bytes");
            check(!WorldAxiomService.has(server, "iron_law"), "Draft does not activate effects");
            phase("actual_access_revision_budget_and_draft_isolation");
        }

        void freezeCommitAndFreshDraft() throws IOException {
            WorldAxiomService.stage(server);
            var staged = AxiomContracts.read(WorldAxiomService.control(server, WorldAxiomService.STAGED));
            check(staged.phase().equals("staged") && staged.selected().equals(Set.of("iron_law")), "Actual immutable physical staged build");
            // The parent succession controller owns this actual typed lock file. This fixture
            // tests Axiom service lock handling, not the external process supervisor.
            Path lock = control.resolve("staged-request-v5.tsv");
            PrestigeContracts.writeStaged(lock, new PrestigeContracts.Staged(lineage, 0, List.of("minecraft:plains"), operator.getUUID().toString(), PrestigeService.worldName(server)));
            reject(() -> WorldAxiomService.toggle(operator, 1, "iron_law"), "stage lock");
            check(WorldAxiomService.view(operator, "").locked(), "Physical lock is visible in actual service view");
            WorldAxiomService.commit(server, transaction);
            check(AxiomContracts.read(WorldAxiomService.control(server, WorldAxiomService.RESET)).phase().equals("committed"), "Actual committed physical build");
            Files.delete(lock);
            Files.delete(WorldAxiomService.control(server, WorldAxiomService.RESET));
            WorldAxiomService.discardDraft(server);
            var fresh = WorldAxiomService.draft(server);
            check(fresh.selected().isEmpty() && fresh.revision() == 0, "Discard starts an empty allocation, not sticky selection");
            PrestigeContracts.writeLineage(lineagePath, new PrestigeContracts.Lineage(lineage, 1, 1));
            check(WorldAxiomService.draft(server).baseGeneration() == 1 && WorldAxiomService.draft(server).selected().isEmpty(), "Successor fresh draft reflects new generation");
            phase("actual_stage_commit_lock_and_fresh_draft");
        }

        void nominateAndActivateClaims() throws IOException {
            WorldAxiomService.toggle(operator, 0, "seed_covenant");
            operator.getInventory().setItem(0, new ItemStack(Items.WHEAT_SEEDS, 4)); operator.getInventory().selected = 0;
            WorldAxiomService.nominate(operator, 1, "seed_covenant");
            WorldAxiomService.toggle(operator, 2, "familiars_name");
            Wolf source = EntityType.WOLF.create(level); check(source != null, "Native wolf exists");
            source.setTame(true); source.setOwnerUUID(operator.getUUID()); source.setCustomName(net.minecraft.network.chat.Component.literal("Receipt"));
            source.moveTo(condenser.getX() + .5, condenser.getY(), condenser.getZ() + 1.0);
            for (BlockPos p : List.of(source.blockPosition(), source.blockPosition().above())) level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
            check(level.addFreshEntity(source), "Native source wolf joined"); entities.add(source.getUUID());
            operator.lookAt(EntityAnchorArgument.Anchor.EYES, source.position().add(0, .4, 0));
            // Establish a consistent native tick baseline for pick(..., partialTick=0).
            operator.setOldPosAndRot();
            WorldAxiomService.nominate(operator, 3, "familiars_name");
            // Both nominations were captured from real server state; stage resolves real online actors.
            WorldAxiomService.stage(server); WorldAxiomService.commit(server, transaction);
            committed = AxiomContracts.read(WorldAxiomService.control(server, WorldAxiomService.RESET));
            check(committed.selected().equals(Set.of("seed_covenant", "familiars_name")), "Actual selected inheritance pair froze");
            AxiomContracts.write(snapshot, committed);
            PrestigeContracts.writeLineage(lineagePath, new PrestigeContracts.Lineage(lineage, 2, 2));
            JsonObject runtime = new JsonObject(); runtime.addProperty("schema", "world_axioms_runtime_v1");
            runtime.addProperty("lineage", lineage); runtime.addProperty("generation", 2); runtime.addProperty("transaction", transaction);
            runtime.addProperty("build_sha256", AxiomContracts.digest(committed)); JsonArray ids = new JsonArray(); committed.selected().stream().sorted().forEach(ids::add); runtime.add("selected", ids);
            AxiomContracts.atomic(projection, (runtime + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            WorldAxiomService.clear(server);
            check(WorldAxiomService.has(server, "seed_covenant") && WorldAxiomService.has(server, "familiars_name"), "Actual active loader binds snapshot, lineage and projection");
            source.discard();
            phase("actual_nomination_freeze_and_validated_active_snapshot");
        }

        void inventoryPendingAtomicPlayerReceiptAndReplay() throws IOException {
            for (int i = 0; i < 36; i++) operator.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
            open(operator, false);
            reject(() -> WorldAxiomService.claim(operator, "seed_covenant", new CompoundTag()), "full inventory");
            check(AxiomJournal.open(server).receipt("seed_covenant") == null && countSeeds(operator) == 0, "Full inventory leaves communal entitlement unconsumed and no dropped/granted seeds");
            AxiomJournal prepared = AxiomJournal.open(server);
            prepared.prepare("seed_covenant", operator.getUUID().toString(), level.dimension().location().toString(), operator.blockPosition());
            byte[] prePlayerSaveJournal = Files.readAllBytes(journal);
            reject(() -> WorldAxiomService.claim(otherOperator, "seed_covenant", new CompoundTag()), "pending claimant reservation");
            operator.getInventory().setItem(0, ItemStack.EMPTY);
            WorldAxiomService.claim(operator, "seed_covenant", new CompoundTag());
            check(countSeeds(operator) == 4 && AxiomJournal.open(server).receipt("seed_covenant").state().equals("released"), "One actual native inventory delivery and released communal receipt");
            Path dat = server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(operator.getUUID() + ".dat");
            check(Files.isRegularFile(dat), "Production claim physically saved native player.dat");
            CompoundTag saved = NbtIo.readCompressed(dat.toFile());
            String token = transaction + "/seed_covenant";
            check(saved.getCompound("ForgeData").getCompound("WorldAxiomClaims").getBoolean(token), "Native player.dat contains the Forge persistent receipt");
            int savedSeeds = 0;
            for (Tag entry : saved.getList("Inventory", Tag.TAG_COMPOUND)) { ItemStack stack = ItemStack.of((CompoundTag) entry); if (stack.is(Items.WHEAT_SEEDS)) savedSeeds += stack.getCount(); }
            check(savedSeeds == 4, "The SAME native player.dat contains exactly four delivered seeds");
            // Arrange the precise crash window: player.dat durable, communal WAL still prepared.
            AxiomContracts.atomic(journal, prePlayerSaveJournal);
            operator.getInventory().clearContent(); operator.getPersistentData().remove("WorldAxiomClaims");
            check(server.getPlayerList().load(operator) != null, "Actual native PlayerList.load reads physical player.dat");
            open(operator, false);
            check(countSeeds(operator) == 4 && operator.getPersistentData().getCompound("WorldAxiomClaims").getBoolean(token), "Native load restores inventory and receipt together");
            WorldAxiomService.clear(server); AxiomClaims.clear(server); AxiomClaims.recover(server);
            WorldAxiomService.claim(operator, "seed_covenant", new CompoundTag());
            check(countSeeds(operator) == 4 && AxiomJournal.open(server).receipt("seed_covenant").state().equals("released"), "Prepared WAL replay recognizes native saved receipt, grants zero duplicates");
            reject(() -> WorldAxiomService.claim(otherOperator, "seed_covenant", new CompoundTag()), "communal once across operators");
            check(countSeeds(otherOperator) == 0, "Second operator receives no duplicate");
            phase("physical_player_dat_and_json_wal_fixture_state_replay");
        }

        void unsafeEntityPendingAndDeterministicReplay() throws IOException {
            // Solid search arena leaves no pair of safe feet/head spaces near the device.
            for (BlockPos p : blocks.keySet()) if (p.getY() >= condenser.getY() - 2 && p.getY() <= condenser.getY() + 2) level.setBlock(p, Blocks.STONE.defaultBlockState(), 3);
            assemble(); open(operator, false);
            reject(() -> WorldAxiomService.claim(operator, "familiars_name", new CompoundTag()), "unsafe entity arena");
            check(AxiomJournal.open(server).receipt("familiars_name") == null, "Unsafe initial claim does not consume entitlement");
            UUID uuid = InheritanceAdapters.restoredUuid(transaction, "familiars_name", "wolf"); entities.add(uuid);
            check(level.getEntity(uuid) == null, "No entity spawned into unsafe terrain");
            BlockPos anchor = condenser.offset(5, 0, 5);
            AxiomJournal pending = AxiomJournal.open(server); pending.prepare("familiars_name", operator.getUUID().toString(), level.dimension().location().toString(), anchor);
            reject(() -> WorldAxiomService.claim(operator, "familiars_name", new CompoundTag()), "prepared blocked entity replay");
            check(!AxiomJournal.open(server).completed("familiars_name") && level.getEntity(uuid) == null, "Blocked replay retains real prepared WAL, not completed");
            byte[] preEntitySaveJournal = Files.readAllBytes(journal);
            for (BlockPos p : BlockPos.betweenClosed(anchor, anchor.offset(3, 2, 1))) level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
            WorldAxiomService.clear(server); AxiomClaims.clear(server); AxiomClaims.recover(server);
            WorldAxiomService.claim(operator, "familiars_name", new CompoundTag());
            Wolf restored = (Wolf) level.getEntity(uuid);
            check(restored != null && restored.isTame() && operator.getUUID().equals(restored.getOwnerUUID()), "Prepared entity replay restores deterministic owned native wolf");
            check(!restored.isNoAi() && !restored.isInvulnerable() && !restored.isNoGravity(), "Durable claim release unpins restored wolf");
            check(AxiomJournal.open(server).receipt("familiars_name").state().equals("released"), "Entity receipt physically released after save");
            // Arrange the other precise crash window: native entity saved, WAL still prepared.
            AxiomContracts.atomic(journal, preEntitySaveJournal);
            WorldAxiomService.clear(server); AxiomClaims.clear(server); AxiomClaims.recover(server);
            check(restored.isNoAi() && restored.isInvulnerable() && restored.isNoGravity(), "Actual recovery pins the already saved pending native entity");
            WorldAxiomService.claim(operator, "familiars_name", new CompoundTag());
            check(level.getEntity(uuid) == restored, "Prepared WAL replay finds the same actual saved entity, not a replacement");
            check(AxiomJournal.open(server).receipt("familiars_name").state().equals("released") && !restored.isNoAi() && !restored.isInvulnerable() && !restored.isNoGravity(), "Interrupted entity replay revalidates native durability then releases once");
            WorldAxiomService.clear(server); AxiomClaims.clear(server); AxiomClaims.recover(server);
            reject(() -> WorldAxiomService.claim(operator, "familiars_name", new CompoundTag()), "completed entity replay");
            check(level.getEntity(uuid) == restored, "Completed replay retains same actual entity object and deterministic UUID");
            long matching = level.getEntitiesOfClass(Wolf.class, restored.getBoundingBox().inflate(24), w -> w.getUUID().equals(uuid)).size();
            check(matching == 1, "No duplicate deterministic inherited entity");
            phase("unsafe_pending_entity_wal_and_no_duplicate_replay");
        }

        void remember(Path path) throws IOException {
            Path p = path.toAbsolutePath().normalize();
            if (Files.isSymbolicLink(p)) throw new IOException("Refuse lifecycle fixture file symlink: " + p);
            if (!files.containsKey(p)) files.put(p, Files.exists(p) ? Files.readAllBytes(p) : null);
        }
        void phase(String name) { phases.add(name); System.out.println("WLM_AXIOM_LIFECYCLE phase=" + name + " PASS"); }
        void evidence(String status, String detail) throws IOException {
            Files.createDirectories(evidenceRoot);
            JsonObject result = new JsonObject(); result.addProperty("status", status); result.addProperty("time", Instant.now().toString());
            result.addProperty("detail", detail); result.addProperty("replay_kind", "fixture_state_not_process_restart");
            JsonArray done = new JsonArray(); phases.forEach(done::add); result.add("passed_phases", done);
            Files.writeString(evidenceRoot.resolve("result.json"), result + "\n");
            for (Path p : List.of(snapshot, journal, projection, WorldAxiomService.control(server, WorldAxiomService.DRAFT), WorldAxiomService.control(server, WorldAxiomService.STAGED), WorldAxiomService.control(server, WorldAxiomService.RESET)))
                if (Files.isRegularFile(p)) Files.copy(p, evidenceRoot.resolve(p.getFileName()), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            for (FakePlayer p : players) {
                Path dat = server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(p.getUUID() + ".dat");
                if (Files.isRegularFile(dat)) Files.copy(dat, evidenceRoot.resolve(p.getUUID() + ".dat"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }

        @Override public void close() throws IOException {
            if (!mutated) return;
            IOException failure = null;
            try {
                for (FakePlayer player : players) {
                    if (server.getPlayerList().getPlayer(player.getUUID()) == player) server.getPlayerList().remove(player);
                    server.getPlayerList().getOps().remove(player.getGameProfile());
                }
                for (EmbeddedChannel channel : channels) channel.finishAndReleaseAll();
                for (UUID uuid : entities) { var entity = level.getEntity(uuid); if (entity != null) entity.discard(); }
                AxiomClaims.clear(server); WorldAxiomService.clear(server); Files.deleteIfExists(snapshot);
                // Restore native block states and BE data while no synthetic active laws are cached.
                for (var entry : blocks.entrySet()) level.setBlock(entry.getKey(), entry.getValue(), 3);
                for (var entry : blockEntities.entrySet()) { BlockEntity be = level.getBlockEntity(entry.getKey()); if (be != null) { be.load(entry.getValue()); be.setChanged(); } }
                if (!server.saveEverything(false, true, true)) throw new IOException("Native fixture arena restoration flush did not complete");
            } catch (RuntimeException | IOException e) { failure = new IOException("Native fixture cleanup failed", e); }
            try {
                for (Path p : treeFiles(control)) if (!originalControlFiles.contains(p)) Files.deleteIfExists(p);
                for (var entry : files.entrySet()) {
                    if (entry.getValue() == null) Files.deleteIfExists(entry.getKey());
                    else { Files.createDirectories(entry.getKey().getParent()); Files.write(entry.getKey(), entry.getValue()); }
                }
                WorldAxiomService.clear(server);
                check(WorldAxiomService.active(server).equals(originalActive), "Root active cache restored semantically from original bytes");
                for (var entry : files.entrySet()) check(entry.getValue() == null ? !Files.exists(entry.getKey()) : java.util.Arrays.equals(entry.getValue(), Files.readAllBytes(entry.getKey())), "Original control/lineage/snapshot/projection/player/ops file bytes restored: " + entry.getKey());
            } catch (RuntimeException | IOException | Error e) {
                if (failure == null) failure = new IOException("Physical fixture-state restoration failed", e); else failure.addSuppressed(e);
            }
            Path resultPath = evidenceRoot.resolve("result.json");
            if (Files.exists(resultPath)) {
                JsonObject result = JsonParser.parseString(Files.readString(resultPath)).getAsJsonObject();
                result.addProperty("state_restored", failure == null);
                if (failure != null) { result.addProperty("status", "RESTORE_FAIL"); result.addProperty("restoration_error", failure.toString()); }
                else if (bodyPassed) result.addProperty("status", "PASS");
                Files.writeString(resultPath, result + "\n");
            }
            if (failure != null) throw failure;
        }
    }

    private static Set<Path> treeFiles(Path root) throws IOException {
        if (!Files.exists(root)) return Set.of();
        try (var paths = Files.walk(root)) {
            var all = paths.toList();
            for (Path p : all) if (Files.isSymbolicLink(p)) throw new IOException("Refuse symlink in lifecycle fixture control state: " + p);
            return all.stream().filter(Files::isRegularFile).map(p -> p.toAbsolutePath().normalize()).collect(java.util.stream.Collectors.toSet());
        }
    }
    private static int countSeeds(ServerPlayer player) {
        int count = 0; for (int i = 0; i < player.getInventory().getContainerSize(); i++) { ItemStack stack = player.getInventory().getItem(i); if (stack.is(Items.WHEAT_SEEDS)) count += stack.getCount(); } return count;
    }
    private static void check(boolean condition, String detail) { if (!condition) throw new AssertionError(detail); }
    @FunctionalInterface private interface IoAction { void run() throws IOException; }
    private static void reject(IoAction action, String reason) throws IOException {
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("Expected production rejection: " + reason);
    }
}
