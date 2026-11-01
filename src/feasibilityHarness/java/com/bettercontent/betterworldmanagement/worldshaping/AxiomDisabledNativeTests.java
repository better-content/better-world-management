package com.bettercontent.betterworldmanagement.worldshaping;

import com.bettercontent.betterworldmanagement.PrestigeRegistry;
import com.bettercontent.betterworldmanagement.WorldCondenserAssembly;
import com.bettercontent.betterworldmanagement.WorldCondenserMenu;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.embedded.EmbeddedChannel;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.handshake.ClientIntentionPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.network.ConnectionType;
import net.minecraftforge.network.NetworkConstants;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.ForgeRegistries;

/** Nonshipping: one exact case per fresh genuinely neutral dedicated process.
 * Actor/menu setup is public fixture arrangement, not remote-client interaction proof.
 * No active snapshot, lineage, cache, native calendar or claim journal is arranged here.
 */
@PrefixGameTestTemplate(false)
public final class AxiomDisabledNativeTests {
    private static final List<String> CLAIMS = List.of("ark", "seed_covenant", "patient_hive", "familiars_name");
    private static final String INACTIVE = "This world law is not active in the current generation";
    private static final String NON_OPERATOR = "Only an operator may alter or claim World Axioms";
    private AxiomDisabledNativeTests() {}

    @GameTest(templateNamespace = "better_world_management", template = "empty", timeoutTicks = 200)
    public static void neutralAll24LawsGatesAndInheritanceClaimsDenied(GameTestHelper helper) throws Exception {
        run(helper, true);
    }

    @GameTest(templateNamespace = "better_world_management", template = "empty", timeoutTicks = 200)
    public static void neutralNonoperatorInheritanceClaimsDenied(GameTestHelper helper) throws Exception {
        run(helper, false);
    }

    /** Diagnostic only: the strict case currently fails before its first claim.
     * Never count this GameTest as a permission or World Axiom acceptance pass.
     */
    @GameTest(templateNamespace = "better_world_management", template = "empty", timeoutTicks = 200)
    public static void neutralAmbientSnapshotStabilityDiagnostic(GameTestHelper helper) throws Exception {
        var server = helper.getLevel().getServer();
        neutral(server);
        Fixture fixture = new Fixture(helper, false);
        try {
            try (fixture) {
                fixture.setup();
                WorldAxiomService.requireAccess(fixture.actor, false);
                check(!fixture.actor.hasPermissions(4), "Diagnostic needs a non-operator");
                Snapshot previous = fixture.snapshot();
                int stable = 0;
                for (int i = 1; i <= 40; i++) {
                    Snapshot next = fixture.snapshot();
                    String changed = changedKeys(previous.player, next.player);
                    fixture.events.add("NO_CLAIM_SNAPSHOT ordinal=" + i + " player_equal="
                            + previous.player.equals(next.player) + " changed_fields=" + changed);
                    if (previous.player.equals(next.player) && previous.inventory.equals(next.inventory)
                            && previous.persistent.equals(next.persistent) && previous.entities.equals(next.entities)
                            && previous.files.keySet().equals(next.files.keySet())) stable++;
                    else stable = 0;
                    if (stable >= 2) break;
                    previous = next;
                }
                fixture.events.add("NO_CLAIM_STABILITY consecutive_equal=" + stable + " diagnostic_only=true");
            }
            fixture.evidence("DIAGNOSTIC_ONLY_RESTORED", null);
        } catch (Exception | Error failure) {
            try { fixture.evidence("DIAGNOSTIC_FAILED", failure.toString()); }
            catch (IOException evidenceError) { failure.addSuppressed(evidenceError); }
            throw failure;
        }
        // An intentional failing native result keeps this observation out of acceptance receipts.
        throw new IllegalStateException("DIAGNOSTIC_ONLY: no denied claim was attempted");
    }

    private static void neutral(MinecraftServer server) {
        check(server.isDedicatedServer(), "Actual dedicated server required");
        check(WorldAxiomService.active(server).isEmpty(), "Must enter with genuinely empty active world laws");
        check(AxiomCatalog.nodes().size() == 23, "Exactly23 authored laws required");
        for (var node : AxiomCatalog.nodes()) {
            check(!WorldAxiomService.has(server, node.id()), "Unexpected active law: " + node.id());
            check(WorldAxiomService.configuration(server, node.id()).isEmpty(), "Inactive configuration leaked: " + node.id());
        }
    }

    private static void run(GameTestHelper helper, boolean operator) throws Exception {
        var server = helper.getLevel().getServer();
        neutral(server);
        check(helper.getLevel() == server.overworld(), "Ordinary overworld required");
        Fixture fixture = new Fixture(helper, operator);
        try {
            try (fixture) {
                fixture.setup();
                neutral(server);
                WorldAxiomService.requireAccess(fixture.actor, false); // physical positive control, also for nonoperator
                if (operator) WorldAxiomService.requireAccess(fixture.actor, true); // cannot hide behind access/permission failure
                check(fixture.actor.hasPermissions(4) == operator, "Actual operator permission must match case");
                // Distinguish state written by a denied claim from instability in the
                // native actor's own consecutive save/snapshot operations.
                fixture.snapshot().sameAs(fixture.snapshot());
                fixture.events.add("BASELINE consecutive native state snapshots identical=true");
                for (String id : CLAIMS) {
                    Snapshot before = fixture.snapshot(); // AFTER legitimate login/menu setup; no ticks before comparison
                    if (operator) {
                        WorldAxiomService.requireAccess(fixture.actor, true);
                        before.sameAs(fixture.snapshot(), "physical access before denial");
                        check(!WorldAxiomService.has(server, id), "Neutral lookup unexpectedly activated " + id);
                        before.sameAs(fixture.snapshot(), "active-law lookup before denial");
                    }
                    String expected = operator ? INACTIVE : NON_OPERATOR;
                    boolean denied = false;
                    try {
                        WorldAxiomService.claim(fixture.actor, id, new CompoundTag());
                    } catch (IllegalStateException error) {
                        check(expected.equals(error.getMessage()), "Wrong denial for " + id + ": " + error.getMessage());
                        denied = true;
                    }
                    check(denied, "Disabled public claim unexpectedly returned: " + id);
                    before.sameAs(fixture.snapshot());
                    neutral(server);
                    fixture.events.add("DENIED " + id + " reason=" + expected + " synchronous_state_unchanged=true");
                }
                fixture.events.add("BODY_PASS_RESTORE_PENDING");
            }
            fixture.evidence("PASS_RESTORED", null); // never published before close succeeds
        } catch (Exception | Error failure) {
            try { fixture.evidence("FAIL_OR_RESTORE_FAIL", failure.toString()); }
            catch (IOException evidenceError) { failure.addSuppressed(evidenceError); }
            throw failure;
        }
        helper.succeed();
    }

    private static final class Fixture implements AutoCloseable {
        final MinecraftServer server;
        final ServerLevel level;
        final boolean operator;
        final UUID uuid = UUID.randomUUID();
        final BlockPos condenser;
        final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        final List<Path> actorFiles = new ArrayList<>();
        final List<String> events = new ArrayList<>();
        final Path evidence;
        ServerPlayer actor;
        EmbeddedChannel channel;
        boolean mutated, admitted, opAdded;

        Fixture(GameTestHelper helper, boolean operator) throws IOException {
            this.server = helper.getLevel().getServer(); this.level = helper.getLevel(); this.operator = operator;
            this.condenser = helper.absolutePos(new BlockPos(2, 3, 2));
            this.evidence = server.getServerDirectory().toPath().resolve("world-axiom-disabled-evidence").resolve(uuid.toString());
            // Only the exact fixture footprint, not a sweeping terrain/entity cleanup.
            remember(condenser); remember(condenser.below()); remember(condenser.below(2));
            for (int x = -1; x <= 1; x++) for (int z = 1; z <= 3; z++)
                for (int y = -1; y <= 1; y++) remember(condenser.offset(x, y, z));
            check(level.getEntities(null, arena()).isEmpty(), "Refuse preexisting entity in exclusive owned arena");
            for (var resource : List.of(LevelResource.PLAYER_DATA_DIR, LevelResource.PLAYER_ADVANCEMENTS_DIR, LevelResource.PLAYER_STATS_DIR)) {
                String extension = resource == LevelResource.PLAYER_DATA_DIR ? ".dat" : ".json";
                actorFiles.add(server.getWorldPath(resource).resolve(uuid + extension));
                if (resource == LevelResource.PLAYER_DATA_DIR) actorFiles.add(server.getWorldPath(resource).resolve(uuid + ".dat_old"));
            }
            for (Path file : actorFiles) check(!Files.exists(file, LinkOption.NOFOLLOW_LINKS), "Refuse preexisting synthetic profile file");
            check(server.getPlayerList().getPlayer(uuid) == null, "Actor UUID already online");
        }
        AABB arena() { return new AABB(condenser.offset(-2, -3, -1), condenser.offset(3, 3, 5)); }
        void remember(BlockPos position) {
            check(level.getBlockEntity(position) == null, "Refuse overwrite of preexisting block entity: " + position);
            blocks.put(position.immutable(), level.getBlockState(position));
        }
        void setup() throws IOException {
            mutated = true;
            for (int x = -1; x <= 1; x++) for (int z = 1; z <= 3; z++) {
                level.setBlock(condenser.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
                for (int y = 0; y <= 1; y++) level.setBlock(condenser.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
            }
            var font = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("better_dimension_fonts:dimensional_font"));
            check(font != null && font != Blocks.AIR, "Actual registered Font required");
            var state = font.defaultBlockState();
            for (var property : state.getProperties())
                if (property instanceof net.minecraft.world.level.block.state.properties.BooleanProperty flag && flag.getName().equals("bound"))
                    state = state.setValue(flag, false);
            level.setBlock(condenser.below(2), state, 3);
            level.setBlock(condenser.below(), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(condenser, PrestigeRegistry.WORLD_CONDENSER_INTERFACE.get().defaultBlockState(), 3);
            check(WorldCondenserAssembly.valid(level, condenser), "Native complete unbound Font/Condenser assembly required");
            GameProfile profile = new GameProfile(uuid, operator ? "AxiomDisabledOp" : "AxiomDisabledNo");
            check(server.getPlayerList().getOps().get(profile) == null, "Never replace an existing operator entry");
            if (operator) { server.getPlayerList().getOps().add(new ServerOpListEntry(profile, 4, false)); opAdded = true; }
            actor = new ServerPlayer(server, level, profile);
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(new ChannelInitializer<Channel>() {
                @Override protected void initChannel(Channel nativeChannel) {
                    Connection.configureSerialization(nativeChannel.pipeline(), PacketFlow.SERVERBOUND);
                    nativeChannel.pipeline().addLast("packet_handler", connection);
                }
            });
            channel.runPendingTasks(); channel.checkException();
            check(channel.isActive(), "Native Connection channel must actually activate");
            connection.setProtocol(ConnectionProtocol.LOGIN);
            FriendlyByteBuf bytes = new FriendlyByteBuf(Unpooled.buffer());
            try {
                new ClientIntentionPacket("localhost", 25565, ConnectionProtocol.LOGIN).write(bytes);
                ClientIntentionPacket intention = new ClientIntentionPacket(bytes);
                check(NetworkConstants.NETVERSION.equals(intention.getFMLVersion()), "Actual Forge intention codec/version required");
                NetworkHooks.registerServerLoginChannel(connection, intention);
            } finally { bytes.release(); }
            check(NetworkHooks.getConnectionType(() -> connection) == ConnectionType.MODDED, "Public Forge login metadata required");
            connection.setProtocol(ConnectionProtocol.PLAY);
            channel.runPendingTasks(); channel.checkException();
            server.getPlayerList().placeNewPlayer(connection, actor);
            admitted = server.getPlayerList().getPlayer(uuid) == actor;
            channel.runPendingTasks(); channel.checkException();
            check(admitted && actor.connection != null && connection.getPacketListener() == actor.connection, "Native PlayerList admission/packet listener required");
            actor.teleportTo(level, condenser.getX() + .5, condenser.getY(), condenser.getZ() + 2.5, 180, 0);
            check(level.getBlockEntity(condenser) instanceof MenuProvider, "Actual Condenser menu provider required");
            MenuProvider provider = (MenuProvider) level.getBlockEntity(condenser);
            check(actor.openMenu(provider).isPresent(), "Public physical menu open required");
            check(actor.containerMenu instanceof WorldCondenserMenu menu && !menu.remote() && menu.pos().equals(condenser)
                    && menu.isInOriginalDimension(actor)
                    // The shipping override is SRG-reobfuscated; compile the inherited
                    // vanilla virtual call through its mapped base owner, not this mod's class.
                    && ((net.minecraft.world.inventory.AbstractContainerMenu) menu).stillValid(actor),
                    "Actual valid nearby physical Condenser menu required");
            events.add("SETUP native_server_player_public_login=true real_physical_menu=true operator=" + operator);
        }
        Snapshot snapshot() throws IOException {
            CompoundTag player = new CompoundTag(); actor.saveWithoutId(player);
            ListTag inventory = actor.getInventory().save(new ListTag());
            Set<String> entities = new TreeSet<>();
            for (var dimension : server.getAllLevels()) for (var entity : dimension.getAllEntities())
                entities.add(dimension.dimension().location() + ":" + entity.getUUID());
            Map<String, byte[]> files = new LinkedHashMap<>();
            Path root = server.getServerDirectory().toPath();
            snapshotTree(files, root.resolve(".better_world_management"));
            snapshotTree(files, server.getWorldPath(LevelResource.ROOT).resolve("data/better_world_management"));
            snapshotTree(files, root.resolve("kubejs/config/world_axioms_runtime.json"));
            // Also detect unexpected actor saves, including creation from prior absence.
            for (Path path : actorFiles) snapshotTree(files, path);
            return new Snapshot(player, inventory, actor.getPersistentData().copy(), entities, files);
        }
        @Override public void close() throws Exception {
            List<Throwable> failures = new ArrayList<>();
            try {
                if (actor != null) {
                    actor.closeContainer();
                    if (server.getPlayerList().getPlayer(uuid) == actor) server.getPlayerList().remove(actor);
                    actor.discard();
                }
            } catch (Throwable error) { failures.add(error); }
            try { if (opAdded) server.getPlayerList().getOps().remove(new GameProfile(uuid, "AxiomDisabledOp")); }
            catch (Throwable error) { failures.add(error); }
            try { if (channel != null) channel.finishAndReleaseAll(); }
            catch (Throwable error) { failures.add(error); }
            if (mutated) {
                var positions = new ArrayList<>(blocks.keySet());
                java.util.Collections.reverse(positions);
                for (BlockPos position : positions) try {
                    level.removeBlockEntity(position); // every original position was BE-free
                    level.setBlock(position, blocks.get(position), 3);
                    check(level.getBlockState(position).equals(blocks.get(position)) && level.getBlockEntity(position) == null,
                            "Owned block restoration failed at " + position);
                } catch (Throwable error) { failures.add(error); }
            }
            for (Path path : actorFiles) try { Files.deleteIfExists(path); }
            catch (Throwable error) { failures.add(error); }
            try {
                check(server.getPlayerList().getPlayer(uuid) == null && level.getEntity(uuid) == null, "Owned actor still present after cleanup");
                neutral(server);
            } catch (Throwable error) { failures.add(error); }
            if (!failures.isEmpty()) {
                IllegalStateException error = new IllegalStateException("Disabled fixture restoration failed");
                failures.forEach(error::addSuppressed); throw error;
            }
            events.add("RESTORED owned_actor_op_entry_channel_blocks_only=true");
        }
        void evidence(String status, String error) throws IOException {
            Files.createDirectories(evidence);
            Files.writeString(evidence.resolve("status.txt"), status + "\n");
            Files.writeString(evidence.resolve("events.log"), Instant.now() + "\n" + String.join("\n", events) + "\n"
                    + (error == null ? "" : error + "\n"));
        }
    }

    private record Snapshot(CompoundTag player, ListTag inventory, CompoundTag persistent, Set<String> entities, Map<String, byte[]> files) {
        void sameAs(Snapshot after) { sameAs(after, "denied claim"); }
        void sameAs(Snapshot after, String operation) {
            // DoubleTag.equals uses floating-point equality: distinct NaN tags are unequal.
            // Compare the complete native serialized NBT instead; exclude no attributes or fields.
            check(sameTag(player, after.player), operation + " changed player NBT fields: " + changedKeys(player, after.player));
            check(sameTag(inventory, after.inventory), operation + " changed inventory slots: " + changedSlots(inventory, after.inventory));
            check(sameTag(persistent, after.persistent), operation + " changed persistent data fields: " + changedKeys(persistent, after.persistent));
            check(entities.equals(after.entities), operation + " spawned/removed an arena entity");
            check(files.keySet().equals(after.files.keySet()), operation + " created/deleted journal/control/player files");
            for (String path : files.keySet()) check(Arrays.equals(files.get(path), after.files.get(path)), operation + " changed file bytes: " + path);
        }
    }
    private static boolean sameTag(Tag left, Tag right) {
        if (left == right) return true;
        if (left == null || right == null || left.getId() != right.getId()) return false;
        return Arrays.equals(encodedTag(left), encodedTag(right));
    }
    private static byte[] encodedTag(Tag tag) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream data = new DataOutputStream(bytes)) {
                data.writeByte(tag.getId());
                tag.write(data); // same native payload emitted to disk; preserves every field and list entry
            }
            return bytes.toByteArray();
        } catch (IOException error) {
            throw new IllegalStateException("Native NBT serialization failed", error);
        }
    }
    private static String changedKeys(CompoundTag before, CompoundTag after) {
        Set<String> keys = new TreeSet<>(before.getAllKeys()); keys.addAll(after.getAllKeys());
        List<String> changed = new ArrayList<>();
        for (String key : keys) {
            var left = before.get(key); var right = after.get(key);
            if (sameTag(left, right)) continue;
            if (left instanceof CompoundTag l && right instanceof CompoundTag r)
                changed.add(key + "/" + changedKeys(l, r));
            else if (left instanceof ListTag l && right instanceof ListTag r)
                changed.add(key + "/" + changedElements(l, r));
            else changed.add(key); // field names only: never print inventory or private NBT values
        }
        return changed.toString();
    }
    private static String changedElements(ListTag before, ListTag after) {
        List<String> changed = new ArrayList<>();
        for (int i = 0; i < Math.max(before.size(), after.size()); i++) {
            if (i >= before.size() || i >= after.size()) { changed.add("index=" + i + " absent"); continue; }
            var left = before.get(i); var right = after.get(i);
            if (sameTag(left, right)) continue;
            if (left instanceof CompoundTag l && right instanceof CompoundTag r)
                changed.add("index=" + i + " keys=" + changedKeys(l, r)
                        + " attribute_names=" + l.getString("Name") + "->" + r.getString("Name"));
            else changed.add("index=" + i); // never print arbitrary tag values
        }
        return changed.toString();
    }
    private static String changedSlots(ListTag before, ListTag after) {
        List<Integer> changed = new ArrayList<>();
        for (int i = 0; i < Math.max(before.size(), after.size()); i++)
            if (i >= before.size() || i >= after.size() || !sameTag(before.get(i), after.get(i))) changed.add(i);
        return changed.toString();
    }
    private static void snapshotTree(Map<String, byte[]> out, Path path) throws IOException {
        check(!Files.isSymbolicLink(path), "Symlink evidence/state path refused: " + path);
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return;
        if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            out.put(path.toAbsolutePath().normalize() + "/", new byte[0]); // detect even empty newly-created journal directories
            try (var children = Files.list(path)) {
                for (Path child : children.sorted().toList()) snapshotTree(out, child);
            }
        } else {
            check(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS), "Nonregular state refused");
            out.put(path.toAbsolutePath().normalize().toString(), Files.readAllBytes(path));
        }
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
