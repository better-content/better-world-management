package com.bettercontent.betterworldmanagement.worldshaping;

import com.bettercontent.betterworldmanagement.WorldCondenserMenu;
import java.util.Set;
import java.util.TreeSet;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/** Bounded, revisioned server authority. No payload can specify entities, genomes, coordinates or item counts. */
public final class WorldAxiomNetwork {
    private static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder.named(new ResourceLocation("better_world_management", "world_axioms_v1"))
        .networkProtocolVersion(() -> "1").clientAcceptedVersions("1"::equals).serverAcceptedVersions("1"::equals).simpleChannel();
    private static final WeakHashMap<ServerPlayer, Long> LAST = new WeakHashMap<>();
    private static AxiomView lastView;
    public static Consumer<AxiomView> clientReceiver = ignored -> {};
    private WorldAxiomNetwork() {}
    public static void register() {
        CHANNEL.messageBuilder(Request.class, 0, NetworkDirection.PLAY_TO_SERVER).encoder(Request::encode).decoder(Request::decode).consumerMainThread(Request::handle).add();
        CHANNEL.messageBuilder(State.class, 1, NetworkDirection.PLAY_TO_CLIENT).encoder(State::encode).decoder(State::decode).consumerMainThread(State::handle).add();
    }
    private enum Action { REFRESH, TOGGLE, CONFIGURE, NOMINATE, CLAIM }
    public static void refresh() { request(Action.REFRESH, "", new CompoundTag()); }
    public static void toggle(String id) { request(Action.TOGGLE, id, new CompoundTag()); }
    public static void configure(String id, CompoundTag tag) { request(Action.CONFIGURE, id, tag); }
    public static void nominate(String id) { request(Action.NOMINATE, id, new CompoundTag()); }
    public static void claim(String id) { claim(id, new CompoundTag()); }
    public static void claim(String id, CompoundTag tag) { request(Action.CLAIM, id, tag); }
    private static void request(Action action, String id, CompoundTag tag) { CHANNEL.sendToServer(new Request(action, lastView == null ? -1 : lastView.revision(), id, tag.copy())); }
    public static void send(ServerPlayer player, String error) {
        try { CHANNEL.sendTo(new State(WorldAxiomService.view(player, error)), player.connection.connection, NetworkDirection.PLAY_TO_CLIENT); }
        catch (Exception failure) {
            String message = safe(failure);
            CHANNEL.sendTo(new State(new AxiomView(0, 0, false, true, Set.of(), new CompoundTag(), message, "World-law state rejected; no mutation applied.")), player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
        }
    }
    private static String safe(Exception error) { String text = error.getMessage(); if (text == null) text = error.getClass().getSimpleName(); return text.substring(0, Math.min(512, text.length())); }
    private record Request(Action action, long revision, String id, CompoundTag tag) {
        private static void encode(Request value, FriendlyByteBuf out) {
            out.writeEnum(value.action()); out.writeLong(value.revision()); out.writeUtf(value.id(), 64);
            if (value.tag().getAllKeys().size() > 5) throw new IllegalArgumentException("Too many request fields");
            out.writeVarInt(value.tag().getAllKeys().size());
            for (String key : new TreeSet<>(value.tag().getAllKeys())) {
                if (!value.tag().contains(key, Tag.TAG_STRING)) throw new IllegalArgumentException("Request values must be strings");
                out.writeUtf(key, 32); out.writeUtf(value.tag().getString(key), 1027);
            }
        }
        private static Request decode(FriendlyByteBuf in) {
            Action action = in.readEnum(Action.class); long revision = in.readLong(); String id = in.readUtf(64);
            int count = in.readVarInt(); if (count < 0 || count > 5) throw new IllegalArgumentException("Too many request fields");
            CompoundTag tag = new CompoundTag();
            for (int i = 0; i < count; i++) { String key = in.readUtf(32); if (tag.contains(key)) throw new IllegalArgumentException("Duplicate request field"); tag.putString(key, in.readUtf(1027)); }
            return new Request(action, revision, id, tag);
        }
        private static void handle(Request request, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get(); context.setPacketHandled(true); ServerPlayer player = context.getSender();
            if (player == null || !(player.containerMenu instanceof WorldCondenserMenu)) return;
            try {
                long tick = player.server.overworld().getGameTime(); Long previous = LAST.put(player, tick);
                if (previous != null && tick >= previous && tick - previous < 2) return;
                if (request.action() == Action.REFRESH) {
                    if (!player.server.isDedicatedServer()) throw new IllegalStateException("World Axioms require a dedicated server");
                } else {
                    WorldAxiomService.requireAccess(player, true);
                    switch (request.action()) {
                        case TOGGLE -> WorldAxiomService.toggle(player, request.revision(), request.id());
                        case CONFIGURE -> WorldAxiomService.configure(player, request.revision(), request.id(), request.tag());
                        case NOMINATE -> WorldAxiomService.nominate(player, request.revision(), request.id());
                        case CLAIM -> WorldAxiomService.claim(player, request.id(), request.tag());
                        default -> throw new IllegalStateException("Unsupported world-law action");
                    }
                }
                send(player, "");
            } catch (Exception error) { send(player, safe(error)); }
        }
    }
    private record State(AxiomView view) {
        private static void encode(State state, FriendlyByteBuf out) {
            AxiomView view = state.view(); CompoundTag tag = new CompoundTag();
            tag.putLong("generation", view.generation()); tag.putLong("revision", view.revision()); tag.putBoolean("operator", view.operator()); tag.putBoolean("locked", view.locked());
            ListTag selected = new ListTag(); new TreeSet<>(view.selected()).forEach(id -> selected.add(StringTag.valueOf(id))); tag.put("selected", selected);
            ListTag active = new ListTag(); new TreeSet<>(view.active()).forEach(id -> active.add(StringTag.valueOf(id))); tag.put("active", active);
            tag.put("configuration", view.configuration()); tag.put("activeConfiguration", view.activeConfiguration());
            tag.putString("error", view.error()); tag.putString("forecast", view.forecast()); out.writeNbt(tag);
        }
        private static State decode(FriendlyByteBuf in) {
            if (in.readableBytes() > AxiomContracts.MAX_BYTES * 2) throw new IllegalArgumentException("Oversized world-law view");
            CompoundTag tag = in.readNbt(); if (tag == null) throw new IllegalArgumentException("Missing world-law view");
            Set<String> selected = ids(tag.getList("selected", Tag.TAG_STRING)), active = ids(tag.getList("active", Tag.TAG_STRING));
            return new State(new AxiomView(tag.getLong("generation"), tag.getLong("revision"), tag.getBoolean("operator"), tag.getBoolean("locked"), selected,
                tag.getCompound("configuration"), tag.getString("error"), tag.getString("forecast"), active, tag.getCompound("activeConfiguration")));
        }
        private static Set<String> ids(ListTag values) {
            if (values.size() > 24) throw new IllegalArgumentException("Too many view IDs");
            Set<String> ids = new TreeSet<>(); for (int i = 0; i < values.size(); i++) { String id = values.getString(i); AxiomCatalog.node(id); if (!ids.add(id)) throw new IllegalArgumentException("Duplicate view ID"); } return ids;
        }
        private static void handle(State packet, Supplier<NetworkEvent.Context> supplier) {
            supplier.get().setPacketHandled(true); lastView = packet.view(); clientReceiver.accept(packet.view());
        }
    }
}
