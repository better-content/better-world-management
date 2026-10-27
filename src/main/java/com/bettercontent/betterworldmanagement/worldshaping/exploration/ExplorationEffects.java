package com.bettercontent.betterworldmanagement.worldshaping.exploration;

import com.bettercontent.betterworldmanagement.worldshaping.WorldAxiomService;
import com.mojang.datafixers.util.Pair;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.registries.ForgeRegistries;

/** Server-authoritative exploration. Request NBT selects an ID only; no client coordinates are read. */
public final class ExplorationEffects {
    private static boolean registered;
    private static long surveyTick = Long.MIN_VALUE;
    private static int remainingProbes;
    private static final Map<UUID, Survey> SURVEYS = new HashMap<>();
    private static final Map<UUID, Long> NEXT_SURVEY = new HashMap<>(), NEXT_MAP = new HashMap<>();
    private static final Map<String, Set<String>> FONT_MARKETS = Map.of(
            "renewal", Set.of("aether", "bumblezone"), "control", Set.of("nether"),
            "impact", Set.of("nether"), "work", Set.of("ratlantis"), "endurance", Set.of("ratlantis"),
            "mobility", Set.of("aether"), "robustness", Set.of("bumblezone", "ratlantis"), "tempo", Set.of("bumblezone"));
    private ExplorationEffects() {}
    public static synchronized void register() {
        if (registered) return;
        registered = true;
        MinecraftForge.EVENT_BUS.addListener(ExplorationEffects::tick);
        MinecraftForge.EVENT_BUS.addListener(ExplorationEffects::logout);
        MinecraftForge.EVENT_BUS.addListener(ExplorationEffects::stopped);
    }
    public static CompoundTag discoveries(MinecraftServer server) { return ExplorationDiscoveries.get(server).export(); }
    public static String forecast(MinecraftServer server) {
        if (!WorldAxiomService.has(server, "celestial_almanac")) return "";
        try { return ExplorationNative.forecast(server); }
        catch (LinkageError failure) { return "Moon calendar unavailable"; }
    }
    public static void claim(ServerPlayer player, String id, CompoundTag request) {
        if (!player.server.isSameThread()) throw new IllegalStateException("Exploration claim off server thread");
        if (!WorldAxiomService.has(player.server, id)) return;
        if (id.equals("cartographers_memory")) structureMap(player, "minecraft:" + request.getString("kind"));
        else if (id.equals("font_atlas")) fontMap(player, request.getString("currency").isEmpty() ? "renewal" : request.getString("currency"));
        else if (id.equals("prospectors_eye")) beginSurvey(player);
        else if (id.equals("celestial_almanac")) tell(player, forecast(player.server));
    }
    private static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        if (player.tickCount % 20 == 0) recordEntry(player);
        if (player.isUsingItem() && player.getUseItem().is(Items.SPYGLASS)
                && WorldAxiomService.has(player.server, "prospectors_eye")) beginSurvey(player);
        Survey survey = SURVEYS.get(player.getUUID());
        if (survey != null) {
            if (!player.serverLevel().dimension().equals(survey.dimension)
                    || player.blockPosition().distSqr(survey.origin) > 16
                    || !player.isUsingItem() || !player.getUseItem().is(Items.SPYGLASS)
                    || !WorldAxiomService.has(player.server, "prospectors_eye")) SURVEYS.remove(player.getUUID());
            else {
                long now = player.server.overworld().getGameTime();
                if (surveyTick != now) { surveyTick = now; remainingProbes = 2048; }
                boolean complete = survey.advance(player, remainingProbes);
                remainingProbes -= survey.lastProbes;
                if (complete) SURVEYS.remove(player.getUUID());
            }
        }
    }
    static void recordEntry(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        // A structure reference or a nearby bounding box is not entry: require a containing native piece.
        if (!level.hasChunkAt(player.blockPosition())) return;
        Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        for (String id : ExplorationDiscoveries.ALLOWED) {
            Structure structure = registry.get(new ResourceLocation(id));
            if (structure != null && level.structureManager().getStructureWithPieceAt(player.blockPosition(), structure).isValid())
                ExplorationDiscoveries.get(player.server).recordEntry(id);
        }
    }
    private static boolean mapAdmission(ServerPlayer player) {
        long now = player.server.overworld().getGameTime();
        if (now < NEXT_MAP.getOrDefault(player.getUUID(), Long.MIN_VALUE)) return false;
        NEXT_MAP.put(player.getUUID(), now + 200);
        if (player.getInventory().getFreeSlot() < 0) { tell(player, "A free inventory slot is required."); return false; }
        return true;
    }
    private static void structureMap(ServerPlayer player, String id) {
        Set<String> inherited = ExplorationDiscoveries.readIds(WorldAxiomService.configuration(player.server, "cartographers_memory"));
        if (!inherited.contains(id) || !ExplorationDiscoveries.ALLOWED.contains(id)) { tell(player, "No inherited discovery for this structure."); return; }
        if (id.equals("minecraft:fortress") ? player.level().dimension() != Level.NETHER : player.level().dimension() != Level.OVERWORLD) {
            tell(player, "Visit the structure's native dimension first."); return;
        }
        if (!has(player, Items.PAPER, 8) || !has(player, Items.COMPASS, 1) || !mapAdmission(player)) return;
        ServerLevel level = player.serverLevel();
        Holder<Structure> structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE)
                .getHolderOrThrow(ResourceKey.create(Registries.STRUCTURE, new ResourceLocation(id)));
        Pair<BlockPos, Holder<Structure>> found;
        try { found = BoundedStructureSearch.find(level, structure, player.blockPosition()); }
        catch (IllegalStateException unsupported) { tell(player, unsupported.getMessage() + "; no materials consumed."); return; }
        if (found == null) { tell(player, "No matching structure within 64 chunks. No materials consumed."); return; }
        ItemStack map = MapItem.create(level, found.getFirst().getX(), found.getFirst().getZ(), (byte) 2, true, true);
        MapItemSavedData.addTargetDecoration(map, found.getFirst(), "+", MapDecoration.Type.TARGET_X);
        map.setHoverName(Component.literal("Inherited survey: " + id));
        deliver(player, map, new ItemStack(Items.PAPER, 8), new ItemStack(Items.COMPASS));
    }
    private static void fontMap(ServerPlayer player, String currency) {
        Set<String> allowed = FONT_MARKETS.get(currency);
        if (allowed == null) { tell(player, "Select an authored spirit market currency."); return; }
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("better_spirit_commerce", currency + "_spirit"));
        if (item == null || item == Items.AIR || !has(player, item, 8) || !mapAdmission(player)) return;
        try {
            MerchantOffer offer = ExplorationNative.fontOffer(player.serverLevel(), player.blockPosition(), item, allowed);
            if (offer == null) { tell(player, "No indexed natural Font available. No payment consumed."); return; }
            // Use the owner's authored price and map verbatim, never synthesize a destination.
            deliver(player, offer.getResult().copy(), offer.getBaseCostA(), offer.getCostB());
        } catch (LinkageError failure) { tell(player, "Font atlas API unavailable; no payment consumed."); }
    }
    private static boolean has(ServerPlayer player, Item item, int count) {
        return player.getInventory().items.stream().filter(s -> s.is(item) && !s.hasTag()).mapToInt(ItemStack::getCount).sum() >= count;
    }
    private static void take(ServerPlayer player, ItemStack price) {
        int remaining = price.getCount();
        for (ItemStack stack : player.getInventory().items) if (stack.is(price.getItem()) && !stack.hasTag()) {
            int n = Math.min(remaining, stack.getCount()); stack.shrink(n); remaining -= n;
            if (remaining == 0) break;
        }
    }
    private static void deliver(ServerPlayer player, ItemStack result, ItemStack a, ItemStack b) {
        if (!has(player, a.getItem(), a.getCount()) || (!b.isEmpty() && !has(player, b.getItem(), b.getCount()))) return;
        // Everything runs on the server thread. Admission reserved an empty slot; do not drop failure outputs.
        if (!player.getInventory().add(result)) return;
        take(player, a); if (!b.isEmpty()) take(player, b);
        player.getInventory().setChanged(); player.inventoryMenu.broadcastChanges();
    }
    private static void beginSurvey(ServerPlayer player) {
        long now = player.server.overworld().getGameTime(); UUID id = player.getUUID();
        if (SURVEYS.containsKey(id) || now < NEXT_SURVEY.getOrDefault(id, Long.MIN_VALUE)) return;
        if (!player.isUsingItem() || !player.getUseItem().is(Items.SPYGLASS)) return;
        NEXT_SURVEY.put(id, now + 40);
        try { SURVEYS.put(id, new Survey(player, ExplorationNative.surfaceSamples())); }
        catch (LinkageError failure) { tell(player, "Native surface sample registry unavailable."); }
    }
    private static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID(); SURVEYS.remove(id); NEXT_SURVEY.remove(id); NEXT_MAP.remove(id);
    }
    private static void stopped(ServerStoppedEvent event) { SURVEYS.clear(); NEXT_SURVEY.clear(); NEXT_MAP.clear(); surveyTick = Long.MIN_VALUE; remainingProbes = 0; }
    private static void tell(ServerPlayer player, String text) { player.sendSystemMessage(Component.literal(text)); }
    private static final class Survey {
        final BlockPos origin; final ResourceKey<Level> dimension; final Set<Block> samples;
        int cursor, matches, lastProbes; final List<String> visible = new ArrayList<>();
        Survey(ServerPlayer player, Set<Block> samples) { origin = player.blockPosition(); dimension = player.level().dimension(); this.samples = samples; }
        boolean advance(ServerPlayer player, int budget) {
            ServerLevel level = player.serverLevel(); lastProbes = 0;
            while (cursor < 33 * 33 * 9 && lastProbes < budget) {
                lastProbes++;
                int n = cursor++, dx = n % 33 - 16, dz = n / 33 % 33 - 16, dy = n / (33 * 33) - 4;
                if (dx * dx + dz * dz > 256) continue;
                BlockPos pos = origin.offset(dx, dy, dz);
                if (level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null) continue;
                Block sample = level.getBlockState(pos).getBlock();
                if (!samples.contains(sample)) continue;
                // Native collision clipping, but all shape/neighbour reads go through a loaded-only budgeted view.
                LoadedSurveyView view = new LoadedSurveyView(level, budget - lastProbes);
                BlockHitResult hit = view.clip(new ClipContext(player.getEyePosition(), Vec3.atCenterOf(pos), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
                lastProbes += view.reads;
                if (view.exhausted) { cursor--; return false; } // retry this candidate next tick, never reveal on exhaustion
                if (view.unknown || (hit.getType() != HitResult.Type.MISS && !hit.getBlockPos().equals(pos))) continue;
                matches++;
                if (visible.size() < 16) visible.add(sample.getName().getString() + " at " + pos.toShortString());
            }
            if (cursor < 33 * 33 * 9) return false;
            tell(player, "Surface survey: " + matches + " visible native samples (loaded chunks only). " + String.join("; ", visible));
            return true;
        }
    }
}
