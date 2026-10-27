package com.bettercontent.betterworldmanagement.worldshaping;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/** Write-ahead, generation-bound effect receipts. Never treat a prepared effect as completed. */
public final class AxiomJournal {
    public record Receipt(String state, String actor, String dimension, BlockPos anchor) {}
    private final Path path;
    private final String transaction, digest;
    private final Map<String, Receipt> receipts = new TreeMap<>();
    private AxiomJournal(Path path, String transaction, String digest) { this.path = path; this.transaction = transaction; this.digest = digest; }
    public static AxiomJournal open(MinecraftServer server) throws IOException {
        AxiomContracts.Build build = WorldAxiomService.active(server).orElseThrow(() -> new IllegalStateException("No active world laws"));
        AxiomJournal journal = new AxiomJournal(server.getWorldPath(LevelResource.ROOT).resolve("data/better_world_management/world-axiom-journal-v1.json"), build.transaction(), AxiomContracts.digest(build));
        if (!Files.exists(journal.path)) return journal;
        if (Files.size(journal.path) > AxiomContracts.MAX_BYTES) throw new IOException("Oversized world-law application journal");
        JsonObject root = JsonParser.parseString(Files.readString(journal.path)).getAsJsonObject();
        if (!root.keySet().equals(Set.of("schema", "transaction", "build_sha256", "receipts"))
            || !root.get("schema").getAsString().equals("world_axiom_journal_v1")
            || !root.get("transaction").getAsString().equals(journal.transaction)
            || !root.get("build_sha256").getAsString().equals(journal.digest)) throw new IOException("World-law journal identity mismatch");
        for (var entry : root.getAsJsonObject("receipts").entrySet()) {
            String id = entry.getKey();
            if (!id.equals("world") && (!AxiomCatalog.inheritance().contains(id) || !build.selected().contains(id))) throw new IOException("Unexpected world-law receipt");
            JsonObject tag = entry.getValue().getAsJsonObject();
            if (!tag.keySet().equals(Set.of("state", "actor", "dimension", "x", "y", "z"))) throw new IOException("Invalid world-law receipt fields");
            String state = tag.get("state").getAsString(), actor = tag.get("actor").getAsString(), dimension = tag.get("dimension").getAsString();
            if (!Set.of("prepared", "applied", "released").contains(state)) throw new IOException("Unknown world-law receipt state");
            if (!actor.isEmpty()) java.util.UUID.fromString(actor);
            if (net.minecraft.resources.ResourceLocation.tryParse(dimension) == null) throw new IOException("Invalid receipt dimension");
            BlockPos anchor = new BlockPos(tag.get("x").getAsInt(), tag.get("y").getAsInt(), tag.get("z").getAsInt());
            if (Math.abs((long) anchor.getX()) > 30_000_000 || Math.abs((long) anchor.getZ()) > 30_000_000 || Math.abs((long) anchor.getY()) > 4096) throw new IOException("Receipt anchor outside bounds");
            journal.receipts.put(id, new Receipt(state, actor, dimension, anchor));
        }
        return journal;
    }
    public Map<String, Receipt> receipts() { return Map.copyOf(receipts); }
    public Receipt receipt(String id) { return receipts.get(id); }
    public boolean completed(String id) { return receipts.containsKey(id) && !receipts.get(id).state().equals("prepared"); }
    public Receipt prepare(String id, String actor, String dimension, BlockPos anchor) throws IOException {
        Receipt existing = receipts.get(id);
        if (existing != null) return existing;
        Receipt value = new Receipt("prepared", actor, dimension, anchor.immutable()); receipts.put(id, value); save(); return value;
    }
    public void complete(String id) throws IOException { transition(id, "applied"); }
    public void released(String id) throws IOException { transition(id, "released"); }
    private void transition(String id, String state) throws IOException {
        Receipt old = receipts.get(id); if (old == null) throw new IllegalStateException("Effect has no durable prepare record");
        if (old.state().equals("released")) return;
        receipts.put(id, new Receipt(state, old.actor(), old.dimension(), old.anchor())); save();
    }
    private void save() throws IOException {
        JsonObject root = new JsonObject(); root.addProperty("schema", "world_axiom_journal_v1");
        root.addProperty("transaction", transaction); root.addProperty("build_sha256", digest);
        JsonObject entries = new JsonObject();
        receipts.forEach((id, value) -> {
            JsonObject tag = new JsonObject(); tag.addProperty("state", value.state()); tag.addProperty("actor", value.actor()); tag.addProperty("dimension", value.dimension());
            tag.addProperty("x", value.anchor().getX()); tag.addProperty("y", value.anchor().getY()); tag.addProperty("z", value.anchor().getZ()); entries.add(id, tag);
        });
        root.add("receipts", entries);
        AxiomContracts.atomic(path, (new GsonBuilder().setPrettyPrinting().create().toJson(root) + "\n").getBytes(StandardCharsets.UTF_8));
    }
}
