package com.bettercontent.betterworldmanagement.worldshaping;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

/** Actual production projection validator, no Minecraft, copied contracts, or stubs. */
public final class AxiomProjectionPureTest {
    private static int checks;
    private static final String LINEAGE = "lineage_projection";
    private static AxiomContracts.Build build() {
        return new AxiomContracts.Build("committed", LINEAGE, 1, 2, "tx_projection", "world_projection",
                Set.of("iron_law", "shaped_horizon"), Map.of("shaped_horizon", Map.of("preset", "large_biomes")), Map.of(), Map.of());
    }
    private static JsonObject runtime(AxiomContracts.Build build) {
        JsonObject result = new JsonObject();
        result.addProperty("schema", "world_axioms_runtime_v1");
        result.addProperty("lineage", LINEAGE);
        result.addProperty("generation", build == null ? 0 : build.targetGeneration());
        result.addProperty("transaction", build == null ? "" : build.transaction());
        JsonObject body = build == null ? null : JsonParser.parseString(new String(AxiomContracts.encode(build), StandardCharsets.UTF_8)).getAsJsonObject();
        result.add("selected", build == null ? new JsonArray() : body.get("selected"));
        result.add("configuration", build == null ? new JsonObject() : body.get("configuration"));
        result.addProperty("build_sha256", build == null ? "" : AxiomContracts.digest(build));
        return result;
    }
    private static void accepts(String json, AxiomContracts.Build build) {
        AxiomContracts.validateProjection(json, build, LINEAGE); checks++;
    }
    private static void rejects(String json, AxiomContracts.Build build) {
        checks++;
        try { AxiomContracts.validateProjection(json, build, LINEAGE); }
        catch (RuntimeException expected) { return; }
        throw new AssertionError("Accepted changed/malformed projection: " + json);
    }
    public static void main(String[] args) {
        checks = 0;
        AxiomContracts.Build build = build();
        accepts(runtime(build).toString(), build);
        accepts(runtime(null).toString(), null);
        // Duplicate identical keys must not be normalized away by Gson.
        String source = runtime(build).toString();
        rejects(source.replace("\"schema\":", "\"schema\":\"world_axioms_runtime_v1\",\"schema\":"), build);
        rejects(source.replace("\"preset\":", "\"preset\":\"ordinary\",\"preset\":"), build);
        for (String number : new String[] { "2.0", "2e0", "\"2\"", "-0", "true", "null", "9223372036854775808" })
            rejects(source.replace("\"generation\":2", "\"generation\":" + number), build);
        for (String field : new String[] { "schema", "lineage", "transaction", "build_sha256" }) {
            JsonObject changed = runtime(build); changed.addProperty(field, "wrong"); rejects(changed.toString(), build);
        }
        for (String field : new String[] { "schema", "lineage", "generation", "transaction", "selected", "configuration", "build_sha256" }) {
            JsonObject changed = runtime(build); changed.remove(field); rejects(changed.toString(), build);
        }
        JsonObject changed = runtime(build); changed.addProperty("extra", false); rejects(changed.toString(), build);
        changed = runtime(build); changed.getAsJsonObject("configuration").getAsJsonObject("shaped_horizon").addProperty("preset", "ordinary"); rejects(changed.toString(), build);
        changed = runtime(build); changed.getAsJsonArray("selected").add("iron_law"); rejects(changed.toString(), build);
        changed = runtime(build); changed.getAsJsonArray("selected").add("class_wayfinder"); rejects(changed.toString(), build);
        changed = runtime(null); changed.getAsJsonArray("selected").add("iron_law"); rejects(changed.toString(), null);
        changed = runtime(null); changed.getAsJsonObject("configuration").add("iron_law", new JsonObject()); rejects(changed.toString(), null);
        changed = runtime(null); changed.addProperty("generation", 1); rejects(changed.toString(), null);
        rejects(" ".repeat(AxiomContracts.MAX_BYTES + 1) + source, build);
        rejects("[]", build);
        rejects("null", build);
        System.out.println("PASS AxiomProjectionPureTest " + checks + " assertions (strict immutable/empty projection binding)");
    }
}
