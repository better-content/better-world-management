package com.bettercontent.betterworldmanagement.worldshaping;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Immutable, versioned world-law contracts. No IDs are added to the class/Embark graph. */
public final class AxiomContracts {
    public static final String SCHEMA = "world_axioms_v1";
    public static final int MAX_BYTES = 65_536;
    private static final Set<String> FIELDS = Set.of("schema", "phase", "lineage", "base_generation", "revision", "transaction", "source_world", "selected", "configuration", "payloads", "nominators", "sha256");
    public record Build(String phase, String lineage, long baseGeneration, long revision, String transaction,
                        String sourceWorld, Set<String> selected, Map<String, Map<String, String>> configuration,
                        Map<String, String> payloads, Map<String, String> nominators) {
        public Build {
            if (!Set.of("draft", "staged", "committed").contains(phase)) throw new IllegalArgumentException("Invalid world-law phase");
            identifier(lineage);
            if (sourceWorld == null || !sourceWorld.matches("[A-Za-z0-9._-]{1,128}") || sourceWorld.equals(".") || sourceWorld.equals("..")) throw new IllegalArgumentException("Invalid source world");
            AxiomCatalog.budget(baseGeneration);
            if (revision < 0) throw new IllegalArgumentException("Negative world-law revision");
            if (phase.equals("committed")) identifier(transaction);
            else if (!transaction.isEmpty()) throw new IllegalArgumentException("Uncommitted world-law transaction");
            selected = Set.copyOf(selected);
            if (selected.size() > AxiomCatalog.budget(baseGeneration)) throw new IllegalArgumentException("World-law point budget exceeded");
            selected.forEach(AxiomCatalog::node);
            TreeMap<String, Map<String, String>> configs = new TreeMap<>();
            for (var entry : configuration.entrySet()) {
                if (!selected.contains(entry.getKey())) throw new IllegalArgumentException("Configuration for unselected world law");
                configs.put(entry.getKey(), Map.copyOf(AxiomConfiguration.validate(entry.getKey(), entry.getValue())));
            }
            for (String id : selected) configs.computeIfAbsent(id, key -> AxiomConfiguration.validate(key, Map.of()));
            configuration = Map.copyOf(configs);
            payloads = Map.copyOf(payloads); nominators = Map.copyOf(nominators);
            for (var entry : payloads.entrySet()) {
                if (!selected.contains(entry.getKey()) || (!AxiomCatalog.inheritance().contains(entry.getKey()) && !entry.getKey().equals("cartographers_memory"))) throw new IllegalArgumentException("Unexpected nomination payload");
                if (entry.getValue().length() > 16_384) throw new IllegalArgumentException("Nomination payload is too large");
            }
            for (var entry : nominators.entrySet()) {
                if (!payloads.containsKey(entry.getKey()) || !AxiomCatalog.inheritance().contains(entry.getKey())) throw new IllegalArgumentException("Unexpected nominator");
                java.util.UUID.fromString(entry.getValue());
            }
        }
        public int budget() { return AxiomCatalog.budget(baseGeneration); }
        public long targetGeneration() { return baseGeneration + 1; }
        public Build phase(String next, String tx) { return new Build(next, lineage, baseGeneration, revision, tx, sourceWorld, selected, configuration, payloads, nominators); }
    }
    private AxiomContracts() {}
    public static Build empty(String lineage, long generation, String world) {
        return new Build("draft", lineage, generation, 0, "", world, Set.of(), Map.of(), Map.of(), Map.of());
    }
    public static void identity(Build build, String lineage, long generation, String world) {
        if (!build.lineage().equals(lineage) || build.baseGeneration() != generation || !build.sourceWorld().equals(world)) throw new IllegalStateException("Stale world-law identity");
    }
    private static void identifier(String value) {
        if (value == null || !value.matches("[a-zA-Z0-9][a-zA-Z0-9._-]{0,127}")) throw new IllegalArgumentException("Invalid world-law identity");
    }
    private static JsonObject body(Build build) {
        JsonObject root = new JsonObject();
        root.addProperty("schema", SCHEMA); root.addProperty("phase", build.phase());
        root.addProperty("lineage", build.lineage()); root.addProperty("base_generation", build.baseGeneration());
        root.addProperty("revision", build.revision()); root.addProperty("transaction", build.transaction());
        root.addProperty("source_world", build.sourceWorld());
        JsonArray ids = new JsonArray(); new TreeSet<>(build.selected()).forEach(ids::add); root.add("selected", ids);
        JsonObject configuration = new JsonObject();
        new TreeMap<>(build.configuration()).forEach((id, values) -> {
            JsonObject value = new JsonObject(); new TreeMap<>(values).forEach(value::addProperty); configuration.add(id, value);
        });
        root.add("configuration", configuration);
        JsonObject payloads = new JsonObject(); new TreeMap<>(build.payloads()).forEach(payloads::addProperty); root.add("payloads", payloads);
        JsonObject nominators = new JsonObject(); new TreeMap<>(build.nominators()).forEach(nominators::addProperty); root.add("nominators", nominators);
        return root;
    }
    public static String digest(Build build) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body(build).toString().getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    public static byte[] encode(Build build) {
        JsonObject root = body(build); root.addProperty("sha256", digest(build));
        byte[] bytes = (new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root) + "\n").getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) throw new IllegalArgumentException("World-law contract exceeds bounded size");
        return bytes;
    }
    public static Build decode(byte[] bytes) {
        if (bytes.length > MAX_BYTES) throw new IllegalArgumentException("World-law contract exceeds bounded size");
        JsonObject root = strictJson(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
        if (!root.keySet().equals(FIELDS) || !text(root.get("schema")).equals(SCHEMA)) throw new IllegalArgumentException("Unknown/incomplete world-law contract schema");
        Set<String> selected = new TreeSet<>();
        if (root.getAsJsonArray("selected").size() > 24) throw new IllegalArgumentException("Too many world laws");
        for (JsonElement value : root.getAsJsonArray("selected")) if (!selected.add(text(value))) throw new IllegalArgumentException("Duplicate world law");
        Map<String, Map<String, String>> configuration = new LinkedHashMap<>();
        for (var entry : root.getAsJsonObject("configuration").entrySet()) configuration.put(entry.getKey(), strings(entry.getValue().getAsJsonObject()));
        Build value = new Build(text(root.get("phase")), text(root.get("lineage")), number(root.get("base_generation")), number(root.get("revision")), text(root.get("transaction")), text(root.get("source_world")), selected, configuration, strings(root.getAsJsonObject("payloads")), strings(root.getAsJsonObject("nominators")));
        if (!digest(value).equals(text(root.get("sha256")))) throw new IllegalArgumentException("World-law contract checksum mismatch");
        return value;
    }
    private static JsonElement strictJson(String source) {
        try (com.google.gson.stream.JsonReader reader = new com.google.gson.stream.JsonReader(new java.io.StringReader(source))) {
            reader.setLenient(false);
            JsonElement result = readJson(reader, 0);
            if (reader.peek() != com.google.gson.stream.JsonToken.END_DOCUMENT) throw new IllegalArgumentException("Trailing contract data");
            return result;
        } catch (IOException error) { throw new IllegalArgumentException("Malformed world-law JSON", error); }
    }
    private static JsonElement readJson(com.google.gson.stream.JsonReader reader, int depth) throws IOException {
        if (depth > 12) throw new IllegalArgumentException("Contract JSON nesting exceeds bounds");
        switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                JsonObject object = new JsonObject(); reader.beginObject();
                while (reader.hasNext()) {
                    String key = reader.nextName();
                    if (object.has(key)) throw new IllegalArgumentException("Duplicate contract JSON key");
                    if (object.size() >= 64) throw new IllegalArgumentException("Too many contract JSON keys");
                    object.add(key, readJson(reader, depth + 1));
                }
                reader.endObject(); return object;
            }
            case BEGIN_ARRAY -> {
                JsonArray array = new JsonArray(); reader.beginArray();
                while (reader.hasNext()) {
                    if (array.size() >= 256) throw new IllegalArgumentException("Contract array exceeds bounds");
                    array.add(readJson(reader, depth + 1));
                }
                reader.endArray(); return array;
            }
            case STRING -> { return new com.google.gson.JsonPrimitive(reader.nextString()); }
            case NUMBER -> { return new com.google.gson.JsonPrimitive(new ContractNumber(reader.nextString())); }
            case BOOLEAN -> { return new com.google.gson.JsonPrimitive(reader.nextBoolean()); }
            case NULL -> { reader.nextNull(); return com.google.gson.JsonNull.INSTANCE; }
            default -> throw new IllegalArgumentException("Invalid contract JSON token");
        }
    }
    private static final class ContractNumber extends Number {
        private static final long serialVersionUID = 1L;
        private final String source;
        ContractNumber(String source) { new java.math.BigDecimal(source); this.source = source; }
        @Override public int intValue() { return new java.math.BigDecimal(source).intValue(); }
        @Override public long longValue() { return new java.math.BigDecimal(source).longValue(); }
        @Override public float floatValue() { return Float.parseFloat(source); }
        @Override public double doubleValue() { return Double.parseDouble(source); }
        @Override public String toString() { return source; }
    }
    private static long number(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber() || !value.getAsString().matches("0|[1-9][0-9]*")) throw new IllegalArgumentException("Invalid contract counter");
        return Long.parseLong(value.getAsString());
    }
    private static String text(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException("Expected contract string");
        return value.getAsString();
    }
    private static Map<String, String> strings(JsonObject object) {
        if (object.size() > 24) throw new IllegalArgumentException("Too many contract fields");
        Map<String, String> result = new TreeMap<>(); object.entrySet().forEach(entry -> result.put(entry.getKey(), text(entry.getValue()))); return result;
    }
    /** Exact pre-world projection binding; also rejects duplicate keys and noncanonical counters. */
    public static void validateProjection(String source, Build build, String lineage) {
        if (source.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) throw new IllegalArgumentException("Oversized startup projection");
        JsonObject actual = strictJson(source).getAsJsonObject();
        long generation = number(actual.get("generation"));
        JsonObject expected = new JsonObject();
        expected.addProperty("schema", "world_axioms_runtime_v1");
        expected.addProperty("lineage", build == null ? lineage : build.lineage());
        expected.addProperty("generation", build == null ? 0 : build.targetGeneration());
        expected.addProperty("transaction", build == null ? "" : build.transaction());
        expected.add("selected", build == null ? new com.google.gson.JsonArray() : body(build).get("selected"));
        expected.add("configuration", build == null ? new JsonObject() : body(build).get("configuration"));
        expected.addProperty("build_sha256", build == null ? "" : digest(build));
        if (generation != (build == null ? 0 : build.targetGeneration()) || !expected.equals(actual))
            throw new IllegalArgumentException("Startup projection differs from immutable world snapshot");
    }

    public static Build read(Path path) throws IOException {
        if (!Files.isRegularFile(path) || Files.size(path) > MAX_BYTES) throw new IOException("Missing/oversized world-law contract: " + path.getFileName());
        return decode(Files.readAllBytes(path));
    }
    public static void write(Path path, Build build) throws IOException { atomic(path, encode(build)); }
    public static void atomic(Path path, byte[] bytes) throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        Path partial = Files.createTempFile(path.toAbsolutePath().getParent(), path.getFileName().toString(), ".partial");
        try {
            try (FileChannel channel = FileChannel.open(partial, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes); while (buffer.hasRemaining()) channel.write(buffer); channel.force(true);
            }
            Files.move(partial, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            try (FileChannel directory = FileChannel.open(path.toAbsolutePath().getParent(), StandardOpenOption.READ)) { directory.force(true); }
        } finally { Files.deleteIfExists(partial); }
    }
}
