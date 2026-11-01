package com.bettercontent.betterworldmanagement.worldshaping.exploration;

import com.bettercontent.betterworldmanagement.worldshaping.WorldAxiomService;
import com.bettercontent.celestial.calendar.CalendarSavedData;
import com.mojang.authlib.GameProfile;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import java.nio.file.Files;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.handshake.ClientIntentionPacket;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.network.NetworkHooks;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;

/**
 * Nonshipping checks callable ONLY from a real committed active24 target.
 * Observes real server-emitted packets, not an overridden hint or remote-client proof.
 * Caller retains the returned cleanup handle for unrelated case failure/cancellation.
 */
public final class PaidSurveyNativeChecks {
    private PaidSurveyNativeChecks() {}

    /** Creates only its own ordinary ticking native actor; never substitutes for the saga claim actor. */
    public static AutoCloseable start(GameTestHelper helper, BlockPos safeOwnedArenaAnchor, Runnable onSuccess) {
        var level = helper.getLevel(); var server = level.getServer();
        require(server.isSameThread() && server.isDedicatedServer(), "Actual dedicated server thread required");
        var active = WorldAxiomService.active(server).orElseThrow();
        require(active.targetGeneration() == 24 && active.selected().size() == 23
                && WorldAxiomService.has(server, "prospectors_eye") && WorldAxiomService.has(server, "celestial_almanac"),
                "Only actual promoted all23 target can create survey actor");
        require(level.getChunkSource().getChunkNow(safeOwnedArenaAnchor.getX() >> 4, safeOwnedArenaAnchor.getZ() >> 4) != null,
                "Owned arena anchor must already be loaded");
        UUID id = UUID.randomUUID();
        ServerPlayer actor = new ServerPlayer(server, level, new GameProfile(id, "Survey" + id.toString().substring(0, 8)));
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(new ChannelInitializer<Channel>() {
            @Override protected void initChannel(Channel nativeChannel) { nativeChannel.pipeline().addLast("packet_handler", connection); }
        });
        Runnable cleanup = () -> {
            try { if (server.getPlayerList().getPlayer(id) == actor) server.getPlayerList().remove(actor); }
            finally {
                channel.finishAndReleaseAll();
                for (LevelResource resource : List.of(LevelResource.PLAYER_DATA_DIR, LevelResource.PLAYER_STATS_DIR,
                        LevelResource.PLAYER_ADVANCEMENTS_DIR)) {
                    var directory = server.getWorldPath(resource);
                    for (String suffix : List.of(".dat", ".dat_old", ".json")) {
                        try { Files.deleteIfExists(directory.resolve(id + suffix)); }
                        catch (java.io.IOException error) { throw new IllegalStateException("Cannot remove owned survey player file " + id, error); }
                    }
                }
            }
        };
        Session session = null;
        try {
            require(connection.channel() == channel && connection.isConnected(), "Owned real Connection admission failed");
            // Public protocol metadata for an embedded fixture, NOT remote login/authentication evidence.
            NetworkHooks.registerServerLoginChannel(connection, new ClientIntentionPacket("localhost", server.getPort(), ConnectionProtocol.LOGIN));
            connection.setProtocol(ConnectionProtocol.PLAY);
            server.getPlayerList().placeNewPlayer(connection, actor);
            require(actor.serverLevel() == level, "Native login chose another dimension; fixture refuses substitution");
            actor.teleportTo(level, safeOwnedArenaAnchor.getX() + .5, safeOwnedArenaAnchor.getY(),
                    safeOwnedArenaAnchor.getZ() + .5, 0F, 0F);
            session = new Session(helper, actor, onSuccess);
            session.onCleanup = cleanup;
            session.install();
            return session;
        } catch (Throwable error) {
            if (session != null) session.close(); else cleanup.run();
            throw new IllegalStateException("Owned ordinary survey actor setup failed", error);
        }
    }

    /** Needs >=240 ordinary ticks remaining in the caller's UNCHANGED native case deadline. */
    public static AutoCloseable start(GameTestHelper helper, ServerPlayer actor, Runnable onSuccess) {
        Session session = new Session(helper, actor, onSuccess);
        try { session.install(); }
        catch (Throwable error) { session.close(); throw new IllegalStateException("Survey check setup failed", error); }
        return session;
    }

    private static void require(boolean condition, String reason) {
        if (!condition) throw new IllegalStateException(reason);
    }

    private static final class Session implements AutoCloseable {
        final GameTestHelper helper;
        final ServerPlayer actor;
        final ServerLevel level;
        final Runnable onSuccess;
        final EmbeddedChannel channel;
        final String handlerName = "axioms-survey-observer-" + UUID.randomUUID();
        final List<String> messages = new CopyOnWriteArrayList<>();
        final Map<BlockPos, BlockState> originals = new LinkedHashMap<>();
        final ItemStack mainHand, offHand;
        final Pose pose;
        final BlockPos origin, samplePos, wallPos;
        final int initialPlayerTicks;
        Consumer<TickEvent.ServerTickEvent> lifetime;
        Consumer<ServerStoppedEvent> stopping;
        int age, stage, stageAge;
        boolean closed, installedHandler, installedLifetime, installedStopping;
        Runnable onCleanup = () -> {};

        Session(GameTestHelper helper, ServerPlayer actor, Runnable onSuccess) {
            require(actor.server.isSameThread(), "Survey fixture must start on actual server thread");
            require(!(actor instanceof FakePlayer), "Forge FakePlayer.tick is a no-op; an ordinary ticking ServerPlayer is required");
            require(actor.server.getPlayerList().getPlayer(actor.getUUID()) == actor, "Actual registered fixture player required");
            require(actor.server.isDedicatedServer(), "Actual dedicated target required");
            var active = WorldAxiomService.active(actor.server).orElseThrow();
            require(active.targetGeneration() == 24 && active.selected().size() == 23,
                    "Genuine committed active24 target required; helper never seeds activation");
            require(WorldAxiomService.has(actor.server, "prospectors_eye")
                    && WorldAxiomService.has(actor.server, "celestial_almanac"), "Both actual exploration laws required");
            require(!actor.isUsingItem(), "Refuse to disturb an existing use-item session");
            require(actor.connection != null && actor.connection.connection.channel() instanceof EmbeddedChannel,
                    "Only explicitly owned embedded fixture actor is supported, never a human connection");
            this.helper = helper; this.actor = actor; this.level = actor.serverLevel(); this.onSuccess = onSuccess;
            channel = (EmbeddedChannel) actor.connection.connection.channel();
            require(channel.isActive(), "Actual fixture outbound channel must be active");
            origin = actor.blockPosition().immutable();
            samplePos = origin.offset(3, 0, 0); wallPos = origin.offset(1, 1, 0);
            for (ServerPlayer other : level.players()) if (other != actor)
                require(other.blockPosition().distSqr(origin) > 32 * 32, "Other player near survey fixture; refuse world edits");
            require(level.getChunkSource().getChunkNow(samplePos.getX() >> 4, samplePos.getZ() >> 4) != null
                    && level.getChunkSource().getChunkNow(wallPos.getX() >> 4, wallPos.getZ() >> 4) != null,
                    "Fixture positions must already be loaded; never request chunks");
            for (BlockPos pos : List.of(samplePos, wallPos)) {
                require(level.getBlockEntity(pos) == null && level.getBlockState(pos).isAir(),
                        "Fixture sample/occluder positions must be empty, loaded, block-entity-free space");
                originals.put(pos, level.getBlockState(pos));
            }
            mainHand = actor.getMainHandItem().copy(); offHand = actor.getOffhandItem().copy(); pose = actor.getPose();
            initialPlayerTicks = actor.tickCount;
        }

        void install() throws Exception {
            // Same-action state comparison: ordinary later calendar ticks are not attributed to a read.
            var calendar = CalendarSavedData.get(actor.server).calendar();
            var before = calendar.state();
            String nativeForecast = ExplorationNative.forecast(actor.server);
            String forecast = ExplorationEffects.forecast(actor.server);
            var model = WorldAxiomService.view(actor, "");
            require(!nativeForecast.isBlank() && forecast.equals(nativeForecast)
                    && model != null && model.forecast().contains(nativeForecast), "Actual native calendar forecast/model mismatch");
            require(before.equals(calendar.state()), "Forecast/view mutated native saved calendar state");
            var sample = ExplorationNative.surfaceSamples().stream().findFirst().orElseThrow();
            require(sample.defaultBlockState().canSurvive(level, samplePos), "Native surface sample requires genuine support at fixture position");
            require(level.setBlock(samplePos, sample.defaultBlockState(), 3), "Native sample fixture placement failed");
            actor.setPose(Pose.STANDING);
            actor.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            channel.pipeline().addFirst(handlerName, new ChannelOutboundHandlerAdapter() {
                @Override public void write(ChannelHandlerContext context, Object packet, ChannelPromise promise) throws Exception {
                    if (packet instanceof ClientboundSystemChatPacket chat
                            && chat.content().getString().startsWith("Surface survey:")) messages.add(chat.content().getString());
                    // Observe and forward unchanged, never replace or fabricate output.
                    super.write(context, packet, promise);
                }
            });
            installedHandler = true;
            lifetime = event -> {
                if (event.phase == TickEvent.Phase.END && event.getServer() == actor.server && !closed && ++age > 240) {
                    close(); // Independent cleanup even if the native test has already timed out/cancelled.
                }
            };
            stopping = event -> { if (event.getServer() == actor.server) close(); };
            MinecraftForge.EVENT_BUS.addListener(lifetime); installedLifetime = true;
            MinecraftForge.EVENT_BUS.addListener(stopping); installedStopping = true;
            helper.runAfterDelay(1, this::step);
        }

        void step() {
            if (closed) return;
            boolean success = false;
            try {
                require(actor.serverLevel() == level && actor.blockPosition().equals(origin), "Actor left survey arena");
                require(actor.server.getPlayerList().getPlayer(actor.getUUID()) == actor, "Fixture actor disconnected");
                require(WorldAxiomService.has(actor.server, "prospectors_eye")
                        && WorldAxiomService.has(actor.server, "celestial_almanac"), "Actual active laws changed");
                require(++stageAge <= 60, "Survey phase lacked real observable completion within 60 native ticks");
                if (stage == 0 && stageAge >= 45) {
                    require(actor.tickCount >= initialPlayerTicks + 20, "No ordinary ServerPlayer ticks observed");
                    require(!actor.isUsingItem() && messages.isEmpty(), "No-spyglass control emitted a survey hint");
                    messages.clear(); actor.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SPYGLASS));
                    actor.startUsingItem(InteractionHand.MAIN_HAND);
                    require(actor.isUsingItem() && actor.getUseItem().is(Items.SPYGLASS), "Native spyglass use did not start");
                    stage = 1; stageAge = 0;
                } else if (stage == 1 && !messages.isEmpty()) {
                    require(ExplorationNative.surfaceSamples().contains(level.getBlockState(samplePos).getBlock()),
                            "Fixture native surface sample disappeared before positive observation");
                    require(messages.stream().anyMatch(text -> text.matches("Surface survey: [1-9][0-9]* visible native samples.*")
                            && text.contains(samplePos.toShortString())), "No actual positive native sample coordinate in emitted hint");
                    actor.stopUsingItem(); messages.clear(); stage = 2; stageAge = 0;
                } else if (stage == 2 && stageAge >= 45) {
                    require(messages.isEmpty(), "Cancelled/no-use survey emitted a later hint");
                    require(level.setBlock(wallPos, Blocks.STONE.defaultBlockState(), 3), "Native occluder placement failed");
                    messages.clear(); actor.startUsingItem(InteractionHand.MAIN_HAND);
                    require(actor.isUsingItem(), "Second native spyglass use did not start after ordinary cooldown");
                    stage = 3; stageAge = 0;
                } else if (stage == 3 && !messages.isEmpty()) {
                    require(ExplorationNative.surfaceSamples().contains(level.getBlockState(samplePos).getBlock())
                            && level.getBlockState(wallPos).is(Blocks.STONE), "Occlusion fixture changed or sample disappeared");
                    require(messages.stream().noneMatch(text -> text.contains(samplePos.toShortString())),
                            "Occluded sample coordinate leaked into actual survey hint");
                    // Require a real completed survey packet, NOT silence-as-success.
                    require(messages.stream().allMatch(text -> text.startsWith("Surface survey:")), "No real occluded survey completion");
                    success = true;
                }
                if (!success) helper.runAfterDelay(1, this::step);
            } catch (Throwable error) {
                close(); helper.fail("Paid survey native check: " + error.getClass().getSimpleName() + ": " + error.getMessage()); return;
            }
            if (success) {
                close();
                System.out.println("WLM_PAID_SURVEY_NATIVE PASS actual_active24=true positive_coordinate=true occlusion=true no_spyglass=true native_calendar_readonly=true remote_client_proof=false private_probe_counter_proof=false");
                onSuccess.run();
            }
        }

        @Override public void close() {
            if (closed) return;
            closed = true;
            try {
                actor.stopUsingItem();
                actor.setItemInHand(InteractionHand.MAIN_HAND, mainHand.copy());
                actor.setItemInHand(InteractionHand.OFF_HAND, offHand.copy());
                actor.setPose(pose);
                for (var entry : originals.entrySet()) {
                    BlockPos pos = entry.getKey();
                    if (level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null)
                        level.setBlock(pos, entry.getValue(), 3);
                }
            } finally {
                try { if (installedHandler && channel.pipeline().get(handlerName) != null) channel.pipeline().remove(handlerName); }
                finally {
                    try {
                        if (installedLifetime) MinecraftForge.EVENT_BUS.unregister(lifetime);
                        if (installedStopping) MinecraftForge.EVENT_BUS.unregister(stopping);
                    } finally { onCleanup.run(); }
                }
            }
        }
    }
}
