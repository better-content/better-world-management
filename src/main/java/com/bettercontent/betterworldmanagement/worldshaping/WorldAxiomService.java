package com.bettercontent.betterworldmanagement.worldshaping;

import com.bettercontent.betterworldmanagement.*;
import com.bettercontent.betterworldmanagement.worldshaping.inheritance.InheritanceAdapters;
import com.bettercontent.betterworldmanagement.worldshaping.exploration.ExplorationEffects;
import com.bettercontent.betterworldmanagement.worldshaping.world.WorldPolicies;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.WeakHashMap;

/** Separate world-law lifecycle: drafts never directly enable effects. Server thread only. */
public final class WorldAxiomService {
    public static final String DRAFT = "world-axiom-draft-v1.json";
    public static final String STAGED = "world-axiom-staged-v1.json";
    public static final String RESET = "world-axiom-reset-v1.json";
    public static final String SNAPSHOT = "data/better_world_management/world-axioms-v1.json";
    private static final Map<MinecraftServer, Optional<AxiomContracts.Build>> ACTIVE = new WeakHashMap<>();
    private WorldAxiomService() {}
    public static Path control(MinecraftServer server, String file) { return PrestigeService.control(server).resolve(file); }
    public static void clear(MinecraftServer server) { ACTIVE.remove(server); }
    public static Optional<AxiomContracts.Build> active(MinecraftServer server) {
        if (!server.isDedicatedServer()) return Optional.empty();
        return ACTIVE.computeIfAbsent(server, WorldAxiomService::loadActive);
    }
    private static Optional<AxiomContracts.Build> loadActive(MinecraftServer server) {
        Path path = server.getWorldPath(LevelResource.ROOT).resolve(SNAPSHOT);
        try {
            Path projection = server.getServerDirectory().toPath().resolve("kubejs/config/world_axioms_runtime.json");
            if (!Files.exists(path)) {
                if (Files.exists(control(server, "successor-request-v5.tsv")))
                    throw new IllegalStateException("Successor is missing its immutable world-law snapshot");
                if (Files.exists(projection)) {
                    if (!Files.isRegularFile(projection) || Files.size(projection) > AxiomContracts.MAX_BYTES)
                        throw new IllegalStateException("Unsafe startup world-law projection");
                    AxiomContracts.validateProjection(Files.readString(projection), null, PrestigeService.lineage(server).lineageId());
                }
                return Optional.empty();
            }
            AxiomContracts.Build build = AxiomContracts.read(path);
            if (!build.phase().equals("committed")) throw new IllegalStateException("World-law snapshot is not committed");
            if (!build.sourceWorld().equals(PrestigeService.worldName(server))) throw new IllegalStateException("World-law world binding mismatch");
            PrestigeContracts.Lineage lineage = PrestigeService.lineage(server);
            if (!lineage.lineageId().equals(build.lineage())) throw new IllegalStateException("World-law lineage mismatch");
            Path successor = control(server, "successor-request-v5.tsv");
            if (Files.exists(successor)) {
                PrestigeContracts.Successor request = PrestigeContracts.readSuccessor(successor);
                if (!request.transactionId().equals(build.transaction()) || request.baseGeneration() != build.baseGeneration()
                    || request.targetGeneration() != build.targetGeneration() || request.successorSeed() != server.overworld().getSeed()
                    || lineage.generation() != build.baseGeneration()
                    || !AxiomContracts.digest(AxiomContracts.read(control(server, RESET))).equals(AxiomContracts.digest(build))) throw new IllegalStateException("World-law successor binding mismatch");
            } else if (lineage.generation() != build.targetGeneration()) throw new IllegalStateException("World-law active generation mismatch");
            if (!Files.isRegularFile(projection) || Files.size(projection) > AxiomContracts.MAX_BYTES) throw new IllegalStateException("Missing startup world-law projection");
            AxiomContracts.validateProjection(Files.readString(projection), build, lineage.lineageId());
            return Optional.of(build);
        } catch (Exception error) { throw new IllegalStateException("World-law startup rejected: " + error.getMessage(), error); }
    }
    public static boolean has(MinecraftServer server, String id) {
        AxiomCatalog.node(id);
        return active(server).map(build -> build.selected().contains(id)).orElse(false);
    }
    public static CompoundTag configuration(MinecraftServer server, String id) {
        AxiomCatalog.node(id);
        return active(server).filter(build -> build.selected().contains(id)).map(build -> configuration(build, id)).orElseGet(CompoundTag::new);
    }
    public static CompoundTag configuration(AxiomContracts.Build build, String id) {
        CompoundTag value = parsePayload(build.payloads().getOrDefault(id, "{}"));
        build.configuration().getOrDefault(id, Map.of()).forEach(value::putString);
        return value;
    }
    private static CompoundTag allConfiguration(AxiomContracts.Build build) {
        CompoundTag result = new CompoundTag(); build.selected().forEach(id -> result.put(id, configuration(build, id))); return result;
    }
    public static AxiomContracts.Build draft(MinecraftServer server) throws IOException {
        PrestigeContracts.Lineage lineage = PrestigeService.lineage(server);
        Path path = control(server, DRAFT);
        AxiomContracts.Build value = Files.exists(path) ? AxiomContracts.read(path) : AxiomContracts.empty(lineage.lineageId(), lineage.generation(), PrestigeService.worldName(server));
        AxiomContracts.identity(value, lineage.lineageId(), lineage.generation(), PrestigeService.worldName(server));
        if (!value.phase().equals("draft")) throw new IllegalStateException("World-law draft phase mismatch");
        return value;
    }
    public static boolean locked(MinecraftServer server) {
        return Files.exists(control(server, "staged-request-v5.tsv")) || Files.exists(control(server, "reset-request-v5.tsv")) || Files.exists(control(server, "successor-request-v5.tsv"));
    }
    public static AxiomView view(ServerPlayer player, String error) throws IOException {
        MinecraftServer server = player.server;
        if (!server.isDedicatedServer()) throw new IllegalStateException("World Axioms require a supervised dedicated server");
        AxiomContracts.Build value = draft(server);
        if (Files.exists(control(server, "staged-request-v5.tsv"))) value = AxiomContracts.read(control(server, STAGED));
        if (Files.exists(control(server, "reset-request-v5.tsv"))) value = AxiomContracts.read(control(server, RESET));
        Optional<AxiomContracts.Build> active = active(server);
        String summary = "Active laws: " + active.map(build -> String.join(", ", new TreeSet<>(build.selected()))).orElse("none")
            + "\n" + ExplorationEffects.forecast(server);
        boolean physicalOperator = player.hasPermissions(4) && player.containerMenu instanceof WorldCondenserMenu menu && !menu.remote();
        return new AxiomView(value.baseGeneration(), value.revision(), physicalOperator, locked(server), value.selected(), allConfiguration(value), error, summary,
            active.map(AxiomContracts.Build::selected).orElse(Set.of()), active.map(WorldAxiomService::allConfiguration).orElseGet(CompoundTag::new));
    }
    public static void requireAccess(ServerPlayer player, boolean mutation) {
        if (!player.server.isDedicatedServer()) throw new IllegalStateException("World Axioms are disabled in integrated servers");
        if (mutation && !player.hasPermissions(4)) throw new IllegalStateException("Only an operator may alter or claim World Axioms");
        if (!(player.containerMenu instanceof WorldCondenserMenu menu) || menu.remote() || !menu.isInOriginalDimension(player)) throw new IllegalStateException("Open the physical World Condenser first");
        BlockPos pos = menu.pos();
        if (player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) > 64
            || !(player.level().getBlockEntity(pos) instanceof WorldCondenserBlockEntity)
            || !WorldCondenserAssembly.valid(player.level(), pos)) throw new IllegalStateException("A complete nearby physical World Condenser is required");
    }
    private static AxiomContracts.Build editable(ServerPlayer player, long expectedRevision) throws IOException {
        requireAccess(player, true);
        if (locked(player.server)) throw new IllegalStateException("World-law selection is locked by the staged succession");
        AxiomContracts.Build build = draft(player.server);
        if (build.revision() != expectedRevision) throw new IllegalStateException("World-law view changed; refreshed instead of applying stale input");
        return build;
    }
    public static void toggle(ServerPlayer player, long revision, String id) throws IOException {
        AxiomCatalog.node(id);
        AxiomContracts.Build old = editable(player, revision);
        Set<String> selected = new TreeSet<>(old.selected());
        Map<String, Map<String, String>> configs = new TreeMap<>(old.configuration());
        Map<String, String> payloads = new TreeMap<>(old.payloads()), nominators = new TreeMap<>(old.nominators());
        if (!selected.remove(id)) { selected.add(id); configs.put(id, AxiomConfiguration.defaults(id)); }
        else { configs.remove(id); payloads.remove(id); nominators.remove(id); }
        saveEdit(player.server, old, selected, configs, payloads, nominators);
    }
    public static void configure(ServerPlayer player, long revision, String id, CompoundTag tag) throws IOException {
        AxiomContracts.Build old = editable(player, revision);
        if (!old.selected().contains(id)) throw new IllegalStateException("Allocate this world law before configuring it");
        Map<String, String> values = new TreeMap<>();
        for (String key : tag.getAllKeys()) {
            if (!tag.contains(key, Tag.TAG_STRING)) throw new IllegalArgumentException("Configuration values must be bounded strings");
            values.put(key, tag.getString(key));
        }
        Map<String, Map<String, String>> configs = new TreeMap<>(old.configuration());
        configs.put(id, AxiomConfiguration.validate(id, values));
        saveEdit(player.server, old, old.selected(), configs, old.payloads(), old.nominators());
    }
    public static void nominate(ServerPlayer player, long revision, String id) throws IOException {
        AxiomContracts.Build old = editable(player, revision);
        if (!old.selected().contains(id) || !AxiomCatalog.inheritance().contains(id)) throw new IllegalStateException("Allocate a supported inheritance law first");
        CompoundTag payload = InheritanceAdapters.capture(player, id, parsePayload(old.payloads().getOrDefault(id, "{}")));
        Map<String, String> payloads = new TreeMap<>(old.payloads()), nominators = new TreeMap<>(old.nominators());
        payloads.put(id, payload.toString()); nominators.put(id, player.getUUID().toString());
        saveEdit(player.server, old, old.selected(), old.configuration(), payloads, nominators);
    }
    private static void saveEdit(MinecraftServer server, AxiomContracts.Build old, Set<String> selected, Map<String, Map<String, String>> configuration, Map<String, String> payloads, Map<String, String> nominators) throws IOException {
        if (old.revision() == Long.MAX_VALUE) throw new IllegalStateException("World-law revision exhausted");
        AxiomContracts.Build next = new AxiomContracts.Build("draft", old.lineage(), old.baseGeneration(), old.revision() + 1, "", old.sourceWorld(), selected, configuration, payloads, nominators);
        AxiomContracts.write(control(server, DRAFT), next);
    }
    public static void stage(MinecraftServer server) throws IOException {
        if (!server.isDedicatedServer()) throw new IllegalStateException("World laws require dedicated succession");
        if (locked(server)) throw new IllegalStateException("Cancel the existing staged succession before restaging world laws");
        AxiomContracts.Build value = draft(server);
        Map<String, String> payloads = new TreeMap<>(value.payloads());
        for (String id : value.selected()) {
            AxiomConfiguration.validate(id, value.configuration().getOrDefault(id, Map.of()));
            if (AxiomCatalog.inheritance().contains(id)) {
                CompoundTag payload = parsePayload(payloads.getOrDefault(id, "{}"));
                String actor = value.nominators().get(id);
                ServerPlayer nominator = actor == null ? null : server.getPlayerList().getPlayer(UUID.fromString(actor));
                if (nominator == null) throw new IllegalStateException("Nominating operator must be online while freezing " + id);
                if (!nominator.hasPermissions(4)) throw new IllegalStateException("Nomination author is no longer an operator");
                InheritanceAdapters.revalidateSources(nominator, id, payload);
            }
        }
        if (value.selected().contains("cartographers_memory")) payloads.put("cartographers_memory", ExplorationEffects.discoveries(server).toString());
        AxiomContracts.Build staged = new AxiomContracts.Build("staged", value.lineage(), value.baseGeneration(), value.revision(), "", value.sourceWorld(), value.selected(), value.configuration(), payloads, value.nominators());
        AxiomContracts.write(control(server, STAGED), staged);
    }
    public static void cancel(MinecraftServer server) throws IOException {
        if (Files.exists(control(server, "reset-request-v5.tsv"))) throw new IllegalStateException("Committed world-law state cannot be discarded");
        Files.deleteIfExists(control(server, STAGED));
    }
    public static void discardDraft(MinecraftServer server) throws IOException {
        cancel(server); Files.deleteIfExists(control(server, DRAFT));
    }
    public static void commit(MinecraftServer server, String transaction) throws IOException {
        AxiomContracts.Build value = AxiomContracts.read(control(server, STAGED));
        PrestigeContracts.Lineage lineage = PrestigeService.lineage(server);
        AxiomContracts.identity(value, lineage.lineageId(), lineage.generation(), PrestigeService.worldName(server));
        if (!value.phase().equals("staged")) throw new IllegalStateException("World-law staged snapshot is invalid");
        for (String id : value.selected()) if (AxiomCatalog.inheritance().contains(id)) InheritanceAdapters.validate(server, id, parsePayload(value.payloads().getOrDefault(id, "{}")));
        AxiomContracts.write(control(server, RESET), value.phase("committed", transaction));
    }
    public static void applyWorld(MinecraftServer server, BlockPos anchor) throws IOException {
        if (active(server).isEmpty()) return;
        AxiomContracts.Build build = active(server).orElseThrow();
        AxiomJournal journal = AxiomJournal.open(server);
        // Always revalidate native receipts and the pinned archive plan: world chunks and
        // separate SavedData files cannot be committed atomically with this JSON journal.
        journal.prepare("world", "", server.overworld().dimension().location().toString(), anchor);
        try {
            WorldPolicies.apply(server.overworld(), anchor, build.transaction());
            if (!server.saveEverything(false, true, true)) throw new IllegalStateException("World-law native save did not complete");
            journal.complete("world");
        } catch (RuntimeException error) {
            server.saveEverything(false, true, true);
            throw error;
        }
    }
    public static void claim(ServerPlayer player, String id, CompoundTag request) throws IOException {
        requireAccess(player, true);
        if (!has(player.server, id)) throw new IllegalStateException("This world law is not active in the current generation");
        if (AxiomCatalog.inheritance().contains(id)) AxiomClaims.claim(player, id);
        else if (Set.of("cartographers_memory", "font_atlas").contains(id)) ExplorationEffects.claim(player, id, request.copy());
        else throw new IllegalArgumentException("This world law has no communal claim");
    }
    public static void writeHealth(MinecraftServer server, boolean healthy, String detail) throws IOException {
        if (!server.isDedicatedServer()) return;
        AxiomContracts.Build build = Files.exists(control(server, RESET)) ? AxiomContracts.read(control(server, RESET)) : active(server).orElse(null);
        if (build == null) return;
        JsonObject tag = new JsonObject(); tag.addProperty("schema", "world_axiom_health_v1");
        tag.addProperty("lineage", build.lineage()); tag.addProperty("transaction", build.transaction());
        tag.addProperty("generation", build.targetGeneration()); tag.addProperty("build_sha256", AxiomContracts.digest(build));
        tag.addProperty("status", healthy ? "healthy" : "failed");
        tag.addProperty("detail", detail == null ? "" : detail.substring(0, Math.min(512, detail.length())));
        AxiomContracts.atomic(control(server, "world-axiom-health-v1.json"), (tag.toString() + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    static CompoundTag parsePayload(String value) {
        if (value.length() > 16_384) throw new IllegalArgumentException("World-law nomination exceeds bounds");
        try { return TagParser.parseTag(value); }
        catch (com.mojang.brigadier.exceptions.CommandSyntaxException error) { throw new IllegalArgumentException("Invalid frozen nomination", error); }
    }
}
