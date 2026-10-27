package com.bettercontent.betterworldmanagement.worldshaping;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Standalone actual-production qualification. No Minecraft bootstrap, contract copies, or stubs. */
public final class AxiomContractsPureTest {
    private static int checks, cases;
    private static final List<String> failures = new ArrayList<>();
    private static final String LINEAGE = "lineage_03", WORLD = "world_03", UUID = "f4f12dee-381c-4817-a802-782170fa344d";
    @FunctionalInterface private interface Checked { void run() throws Exception; }
    private static void check(boolean condition, String reason) {
        checks++;
        if (!condition) throw new AssertionError(reason);
    }
    private static void rejects(Checked operation, String reason) throws Exception {
        checks++;
        try { operation.run(); }
        catch (RuntimeException expected) { return; }
        throw new AssertionError("Accepted " + reason);
    }
    private static void test(String name, Checked operation) {
        cases++;
        try { operation.run(); System.out.println("PASS " + name); }
        catch (Exception | AssertionError failure) {
            failures.add(name + ": " + failure); System.out.println("FAIL " + failures.get(failures.size() - 1));
        }
    }
    private static AxiomContracts.Build build(long generation, Set<String> selected) {
        return new AxiomContracts.Build("draft",LINEAGE,generation,0,"",WORLD,selected,Map.of(),Map.of(),Map.of());
    }
    private static AxiomContracts.Build configured(String id, Map<String,String> config) {
        return new AxiomContracts.Build("draft",LINEAGE,23,1,"",WORLD,Set.of(id),Map.of(id,config),Map.of(),Map.of());
    }
    private static JsonObject json(AxiomContracts.Build build) {
        return JsonParser.parseString(new String(AxiomContracts.encode(build),StandardCharsets.UTF_8)).getAsJsonObject();
    }
    private static byte[] bytes(JsonObject json) { return json.toString().getBytes(StandardCharsets.UTF_8); }
    private static void roundtrip(AxiomContracts.Build value) {
        var decoded = AxiomContracts.decode(AxiomContracts.encode(value));
        check(value.equals(decoded),"lossless exact contract roundtrip");
        check(AxiomContracts.digest(value).matches("[0-9a-f]{64}"),"canonical sha256 format");
    }

    public static void main(String[] args) throws Exception {
        checks = cases = 0; failures.clear();
        test("catalog is exactly six independent four-node branches", () -> {
            List<String> expected = List.of("iron_law","eternal_engine","alloy_testament","captive_hearth","ark","seed_covenant","patient_hive","familiars_name","cartographers_memory","font_atlas","prospectors_eye","celestial_almanac","communal_archive","fortified_beginnings","settled_arrival","founders_exchange","sacrificial_tradition","spirit_awakening","chalk_covenant","patient_alchemist","shaped_horizon","turning_year","deep_compact","chosen_omen");
            check(AxiomCatalog.branches().size() == 6,"six branches");
            check(AxiomCatalog.nodes().stream().map(AxiomCatalog.Node::id).toList().equals(expected),"approved exact ordered 24 IDs");
            Set<String> ids = new HashSet<>(); int[] branches = new int[6];
            for (var node : AxiomCatalog.nodes()) {
                check(ids.add(node.id()),"unique ID"); check(AxiomCatalog.node(node.id()).equals(node),"lookup exact");
                check(!node.label().isBlank() && !node.description().isBlank(),"readable labels/descriptions"); branches[node.branch()]++;
            }
            for (int count : branches) check(count == 4,"four nodes per branch");
            check(AxiomCatalog.inheritance().equals(Set.of("ark","seed_covenant","patient_hive","familiars_name")),"inheritance IDs exact");
            rejects(() -> AxiomCatalog.node("class_wayfinder"),"Embark/class ID as World Axiom");
            rejects(() -> AxiomCatalog.node("made_up"),"unknown ID");
            rejects(() -> AxiomCatalog.nodes().clear(),"mutable catalog");
            rejects(() -> AxiomCatalog.branches().clear(),"mutable branch names");
        });
        for (var node : AxiomCatalog.nodes()) test("one-point independent " + node.id(), () -> {
            var alone = build(0,Set.of(node.id()));
            check(alone.budget() == 1 && alone.selected().size() == 1,"one point at generation zero, no prerequisite");
            roundtrip(alone); roundtrip(configured(node.id(),Map.of()));
        });
        test("budget cap and generation boundaries", () -> {
            for (long g = 0; g < 100; g++) check(AxiomCatalog.budget(g) == Math.min(24,g + 1),"budget at " + g);
            check(AxiomCatalog.budget(Long.MAX_VALUE - 1) == 24,"highest nonoverflow generation");
            check(build(Long.MAX_VALUE - 1,Set.of()).targetGeneration() == Long.MAX_VALUE,"target addition does not wrap");
            for (long g : new long[]{-1,Long.MIN_VALUE,Long.MAX_VALUE}) rejects(() -> AxiomCatalog.budget(g),"invalid/overflow generation " + g);
            rejects(() -> build(0,Set.of("iron_law","chosen_omen")),"two nodes in one-point budget");
            var all = AxiomCatalog.nodes().stream().map(AxiomCatalog.Node::id).collect(java.util.stream.Collectors.toSet());
            roundtrip(build(23,all)); roundtrip(build(1000000,all));
            rejects(() -> build(22,all),"24 nodes in 23-point budget");
        });
        test("deep immutable copies and order-independent digest", () -> {
            Set<String> selected = new HashSet<>(Set.of("deep_compact","ark"));
            Map<String,String> options = new HashMap<>(Map.of("deposit","brassroot","band","deep"));
            Map<String,Map<String,String>> configurations = new HashMap<>(Map.of("deep_compact",options));
            Map<String,String> payloads = new HashMap<>(Map.of("ark","{safe:1}"));
            Map<String,String> nominators = new HashMap<>(Map.of("ark",UUID));
            var frozen = new AxiomContracts.Build("draft",LINEAGE,1,7,"",WORLD,selected,configurations,payloads,nominators);
            String digest = AxiomContracts.digest(frozen);
            selected.clear(); options.clear(); configurations.clear(); payloads.clear(); nominators.clear();
            check(frozen.selected().size() == 2 && frozen.configuration().get("deep_compact").get("band").equals("deep"),"input mutations cannot change frozen build");
            check(digest.equals(AxiomContracts.digest(frozen)),"snapshot digest unchanged");
            rejects(() -> frozen.selected().clear(),"mutable selected output");
            rejects(() -> frozen.configuration().clear(),"mutable config outer output");
            rejects(() -> frozen.configuration().get("deep_compact").clear(),"mutable nested config output");
            rejects(() -> frozen.payloads().clear(),"mutable payload output");
            rejects(() -> frozen.nominators().clear(),"mutable nominator output");
            var reordered = new AxiomContracts.Build("draft",LINEAGE,1,7,"",WORLD,new LinkedHashSet<>(List.of("ark","deep_compact")),Map.of("deep_compact",Map.of("band","deep","deposit","brassroot")),Map.of("ark","{safe:1}"),Map.of("ark",UUID));
            check(digest.equals(AxiomContracts.digest(reordered)),"insertion order not part of checksum"); roundtrip(frozen);
        });
        test("phase snapshots freeze selection and next generation starts empty", () -> {
            var draft = configured("turning_year",Map.of("season","early_winter"));
            var staged = draft.phase("staged",""); var committed = staged.phase("committed","transaction_04");
            check(draft.phase().equals("draft") && staged.phase().equals("staged") && committed.phase().equals("committed"),"separate immutable phases");
            check(draft.selected().equals(committed.selected()) && draft.configuration().equals(committed.configuration()),"frozen stage/commit values preserved");
            check(!AxiomContracts.digest(draft).equals(AxiomContracts.digest(staged)),"phase authenticated");
            check(!AxiomContracts.digest(staged).equals(AxiomContracts.digest(committed)),"transaction authenticated");
            roundtrip(staged); roundtrip(committed);
            var fresh = AxiomContracts.empty(LINEAGE,committed.targetGeneration(),"world_04");
            check(fresh.selected().isEmpty() && fresh.configuration().isEmpty() && fresh.payloads().isEmpty() && fresh.nominators().isEmpty(),"fresh allocation does not inherit paid draft");
            check(fresh.revision() == 0 && fresh.transaction().isEmpty() && fresh.phase().equals("draft"),"fresh metadata");
            check(committed.selected().equals(Set.of("turning_year")),"new allocation cannot edit committed prior law");
            rejects(() -> draft.phase("bogus",""),"unknown phase");
            rejects(() -> draft.phase("staged","transaction"),"transaction before commit");
            rejects(() -> staged.phase("committed",""),"missing committed transaction");
        });
        test("identity rejects stale lineage generation and world", () -> {
            var value = build(3,Set.of()); AxiomContracts.identity(value,LINEAGE,3,WORLD); check(true,"current identity accepted");
            rejects(() -> AxiomContracts.identity(value,"other",3,WORLD),"stale lineage");
            rejects(() -> AxiomContracts.identity(value,LINEAGE,4,WORLD),"stale generation");
            rejects(() -> AxiomContracts.identity(value,LINEAGE,3,"other"),"stale source world");
            for (String id : new String[]{"","../escape","/absolute","a/b","has space","a".repeat(129)}) {
                rejects(() -> AxiomContracts.empty(id,0,WORLD),"invalid lineage " + id);
                rejects(() -> AxiomContracts.empty(LINEAGE,0,id),"invalid world " + id);
                rejects(() -> build(0,Set.of()).phase("committed",id),"invalid transaction " + id);
            }
            AxiomContracts.empty("a".repeat(128),0,"b".repeat(128)); check(true,"identity maximum accepted");
        });
        test("configuration closed keys and defaults", () -> {
            for (var node : AxiomCatalog.nodes()) {
                var defaults = AxiomConfiguration.validate(node.id(),Map.of());
                check(defaults.equals(AxiomConfiguration.defaults(node.id())),"validated defaults exact " + node.id());
                rejects(() -> defaults.put("extra","x"),"mutable validated defaults");
                rejects(() -> AxiomConfiguration.validate(node.id(),Map.of("operator_override","true")),"unknown field " + node.id());
                rejects(() -> configured(node.id(),Map.of("operator_override","true")),"unknown field in build " + node.id());
            }
            check(AxiomConfiguration.defaults("font_atlas").equals(Map.of("currency","renewal")),"native font currency default");
            check(AxiomConfiguration.defaults("deep_compact").equals(Map.of("deposit","ironstone","band","shallow")),"deposit defaults");
            rejects(() -> AxiomConfiguration.validate("not_a_node",Map.of()),"unknown configuration node");
            rejects(() -> new AxiomContracts.Build("draft",LINEAGE,0,0,"",WORLD,Set.of("iron_law"),Map.of("chosen_omen",Map.of()),Map.of(),Map.of()),"configuration for unselected node");
            Map<String,String> six = Map.of("a","1","b","2","c","3","d","4","e","5","f","6");
            rejects(() -> AxiomConfiguration.validate("communal_archive",six),"more than five options");
        });
        test("native configuration enums exact", () -> {
            choices("shaped_horizon","preset",new String[]{"ordinary","large_biomes"});
            choices("turning_year","season",new String[]{"early_spring","early_summer","early_autumn","early_winter"});
            choices("deep_compact","deposit",new String[]{"ironstone","copper_bloom","tin_quartz","brassroot"});
            choices("deep_compact","band",new String[]{"shallow","deep"});
            choices("chosen_omen","event",new String[]{"thin_veil","spirit_fair","wandering_lights","open_ways"});
            choices("cartographers_memory","kind",new String[]{"monument","mansion","mineshaft","fortress"});
            String[] currencies = {"impact","tempo","work","mobility","endurance","robustness","renewal","control"};
            choices("font_atlas","currency",currencies); choices("founders_exchange","currency",currencies);
            rejects(() -> configured("chosen_omen",Map.of("event","none")),"NONE as paid omen");
            rejects(() -> configured("cartographers_memory",Map.of("kind","minecraft:monument")),"namespaced rather than native utility key");
        });
        test("archive exact page plaque and null bounds", () -> {
            String page = "x".repeat(256), book = String.join("\n",page,page,page,page);
            var full = Map.of("plaque",page,"book1",book,"book2",book,"book3",book,"book4",book);
            roundtrip(configured("communal_archive",full));
            check(book.length() == 1027,"four 256-character pages plus separators");
            roundtrip(configured("communal_archive",Map.of("plaque","","book1","\n\n\n")));
            rejects(() -> configured("communal_archive",Map.of("plaque","x".repeat(257))),"257-character plaque");
            for (int i = 1; i <= 4; i++) {
                String key = "book" + i;
                rejects(() -> configured("communal_archive",Map.of(key,"x".repeat(257))),"257-character " + key + " page");
                rejects(() -> configured("communal_archive",Map.of(key,"a\nb\nc\nd\ne")),"five pages " + key);
                rejects(() -> configured("communal_archive",Map.of(key,"\n\n\n\n")),"five empty pages " + key);
            }
            rejects(() -> configured("communal_archive",Map.of("book5","x")),"fifth archive book");
            rejects(() -> configured("communal_archive",Map.of("plaque","a\u0000b")),"NUL in plaque");
            var nullOption = new HashMap<String,String>(); nullOption.put("plaque",null);
            rejects(() -> configured("communal_archive",nullOption),"null archive text");
        });
        test("payload nomination whitelist bounds and immutable metadata", () -> {
            for (String id : AxiomCatalog.inheritance()) {
                var value = new AxiomContracts.Build("draft",LINEAGE,0,0,"",WORLD,Set.of(id),Map.of(),Map.of(id,"x".repeat(16384)),Map.of(id,UUID));
                roundtrip(value);
                rejects(() -> new AxiomContracts.Build("draft",LINEAGE,0,0,"",WORLD,Set.of(id),Map.of(),Map.of(id,"x".repeat(16385)),Map.of()),"oversized payload " + id);
                rejects(() -> new AxiomContracts.Build("draft",LINEAGE,0,0,"",WORLD,Set.of(id),Map.of(),Map.of(id,"{}"),Map.of(id,"not-a-uuid")),"invalid nominator UUID");
            }
            roundtrip(new AxiomContracts.Build("draft",LINEAGE,0,0,"",WORLD,Set.of("cartographers_memory"),Map.of(),Map.of("cartographers_memory","{}"),Map.of()));
            rejects(() -> new AxiomContracts.Build("draft",LINEAGE,0,0,"",WORLD,Set.of("iron_law"),Map.of(),Map.of("iron_law","{}"),Map.of()),"payload on non-nomination node");
            rejects(() -> new AxiomContracts.Build("draft",LINEAGE,0,0,"",WORLD,Set.of("iron_law"),Map.of(),Map.of("ark","{}"),Map.of()),"payload on unselected node");
            rejects(() -> new AxiomContracts.Build("draft",LINEAGE,0,0,"",WORLD,Set.of("ark"),Map.of(),Map.of(),Map.of("ark",UUID)),"nominator without payload");
            rejects(() -> new AxiomContracts.Build("draft",LINEAGE,0,0,"",WORLD,Set.of("cartographers_memory"),Map.of(),Map.of("cartographers_memory","{}"),Map.of("cartographers_memory",UUID)),"nominator on map payload");
        });
        test("independent Python canonical JSON checksum golden", () -> {
            // Computed independently using Python hashlib.sha256(json.dumps(body,separators=(',',':')).encode()).
            String digest = "08d1d748c2abd5c607d817f4cdc009c0cee48e31f7b2b36e2cd7ccb563ee7407";
            var empty = build(0,Set.of());
            check(AxiomContracts.digest(empty).equals(digest),"Java canonical body matches independent Python golden");
            String golden = "{\"schema\":\"world_axioms_v1\",\"phase\":\"draft\",\"lineage\":\"lineage_03\",\"base_generation\":0,\"revision\":0,\"transaction\":\"\",\"source_world\":\"world_03\",\"selected\":[],\"configuration\":{},\"payloads\":{},\"nominators\":{},\"sha256\":\"" + digest + "\"}";
            check(AxiomContracts.decode(golden.getBytes(StandardCharsets.UTF_8)).equals(empty),"independent wire golden accepted");
            JsonObject shuffled = new JsonObject(); JsonObject original = json(empty);
            var keys = new ArrayList<>(original.keySet()); java.util.Collections.reverse(keys);
            for (String key : keys) shuffled.add(key,original.get(key));
            check(AxiomContracts.decode(bytes(shuffled)).equals(empty),"wire key order irrelevant while canonical digest stays stable");
        });
        test("aggregate wire payload bound cannot be bypassed by per-node sizes", () -> {
            var ids = new HashSet<>(AxiomCatalog.inheritance()); ids.add("cartographers_memory");
            Map<String,String> payloads = new HashMap<>(); ids.forEach(id -> payloads.put(id,"x".repeat(16384)));
            var oversized = new AxiomContracts.Build("draft",LINEAGE,23,0,"",WORLD,ids,Map.of(),payloads,Map.of());
            rejects(() -> AxiomContracts.encode(oversized),"aggregate serialized size above 65536 bytes");
        });
        test("strict JSON field schema and checksum", () -> {
            var value = configured("communal_archive",Map.of("plaque","Copper <stone> & snow \u96ea")); roundtrip(value);
            JsonObject extra = json(value); extra.addProperty("admin",true); rejects(() -> AxiomContracts.decode(bytes(extra)),"unknown root field");
            for (String key : new HashSet<>(json(value).keySet())) {
                JsonObject missing = json(value); missing.remove(key); rejects(() -> AxiomContracts.decode(bytes(missing)),"missing field " + key);
            }
            JsonObject schema = json(value); schema.addProperty("schema","world_axioms_v2"); rejects(() -> AxiomContracts.decode(bytes(schema)),"unknown schema");
            JsonObject changed = json(value); changed.getAsJsonObject("configuration").getAsJsonObject("communal_archive").addProperty("plaque","changed");
            rejects(() -> AxiomContracts.decode(bytes(changed)),"tampered configuration checksum");
            JsonObject checksum = json(value); checksum.addProperty("sha256","0".repeat(64)); rejects(() -> AxiomContracts.decode(bytes(checksum)),"wrong checksum");
            JsonObject typed = json(value); typed.addProperty("lineage",5); rejects(() -> AxiomContracts.decode(bytes(typed)),"non-string lineage");
            JsonObject option = json(value); option.getAsJsonObject("configuration").getAsJsonObject("communal_archive").addProperty("plaque",true);
            rejects(() -> AxiomContracts.decode(bytes(option)),"non-string configuration");
            rejects(() -> AxiomContracts.decode("{}".getBytes(StandardCharsets.UTF_8)),"empty contract object");
            rejects(() -> AxiomContracts.decode(new byte[AxiomContracts.MAX_BYTES + 1]),"oversized wire contract");
        });
        test("duplicate and unknown selected IDs fail closed", () -> {
            for (String id : new String[]{"bogus","class_wayfinder","familiar_name","cartographer_memory","prospector_eye"}) {
                rejects(() -> AxiomCatalog.node(id),"noncanonical legacy/unknown catalog ID " + id);
                rejects(() -> build(0,Set.of(id)),"noncanonical legacy/unknown build ID " + id);
                rejects(() -> AxiomConfiguration.validate(id,Map.of()),"noncanonical legacy/unknown configuration ID " + id);
                JsonObject value = json(build(0,Set.of())); value.getAsJsonArray("selected").add(id);
                rejects(() -> AxiomContracts.decode(bytes(value)),"unknown selected ID " + id);
            }
            JsonObject duplicate = json(build(0,Set.of("iron_law"))); duplicate.getAsJsonArray("selected").add("iron_law");
            rejects(() -> AxiomContracts.decode(bytes(duplicate)),"duplicate selected ID");
            JsonObject many = json(build(23,Set.of())); JsonArray ids = many.getAsJsonArray("selected");
            for (int i = 0; i < 25; i++) ids.add("iron_law");
            rejects(() -> AxiomContracts.decode(bytes(many)),"25 wire IDs");
        });
        test("strict numeric counters and overflow", () -> {
            for (String key : List.of("base_generation","revision")) {
                for (String literal : List.of("-1","1.0","1e0","9223372036854775808","true","null","\"1\"")) {
                    JsonObject value = json(build(0,Set.of())); value.add(key,JsonParser.parseString(literal));
                    rejects(() -> AxiomContracts.decode(bytes(value)),"invalid " + key + " " + literal);
                }
            }
            rejects(() -> new AxiomContracts.Build("draft",LINEAGE,0,-1,"",WORLD,Set.of(),Map.of(),Map.of(),Map.of()),"negative revision");
            var maxRevision = new AxiomContracts.Build("draft",LINEAGE,0,Long.MAX_VALUE,"",WORLD,Set.of(),Map.of(),Map.of(),Map.of()); roundtrip(maxRevision);
        });
        test("checksum-valid exponent counters are not normalized to integers", () -> {
            var one = new AxiomContracts.Build("draft",LINEAGE,1,1,"",WORLD,Set.of(),Map.of(),Map.of(),Map.of());
            String good = json(one).toString();
            for (String key : List.of("base_generation","revision")) {
                for (String literal : List.of("1e0","1E0","1e+0","1E-0","1.0","1.00")) {
                    String changed = good.replace("\"" + key + "\":1", "\"" + key + "\":" + literal);
                    check(!changed.equals(good),"raw lexical mutation took effect");
                    rejects(() -> AxiomContracts.decode(changed.getBytes(StandardCharsets.UTF_8)),"checksum-valid noncanonical " + key + "=" + literal);
                }
            }
        });
        test("duplicate JSON root keys are rejected rather than last-value-wins", () -> {
            String good = new String(AxiomContracts.encode(build(0,Set.of())),StandardCharsets.UTF_8);
            String duplicate = good.replaceFirst("\\{","{\"schema\":\"world_axioms_v1\",");
            rejects(() -> AxiomContracts.decode(duplicate.getBytes(StandardCharsets.UTF_8)),"duplicate schema key even when checksum valid");
        });
        test("duplicate JSON nested configuration IDs are rejected", () -> {
            String good = json(configured("shaped_horizon",Map.of("preset","ordinary"))).toString();
            String duplicate = good.replace("\"configuration\":{", "\"configuration\":{\"shaped_horizon\":{\"preset\":\"ordinary\"},");
            check(!duplicate.equals(good),"fixture actually contains duplicate configuration ID");
            rejects(() -> AxiomContracts.decode(duplicate.getBytes(StandardCharsets.UTF_8)),"duplicate configuration node with valid digest");
        });
        test("duplicate JSON nested option keys are rejected", () -> {
            String good = json(configured("shaped_horizon",Map.of("preset","ordinary"))).toString();
            String duplicate = good.replace("\"preset\":\"ordinary\"", "\"preset\":\"large_biomes\",\"preset\":\"ordinary\"");
            check(!duplicate.equals(good),"fixture actually contains duplicate option");
            rejects(() -> AxiomContracts.decode(duplicate.getBytes(StandardCharsets.UTF_8)),"conflicting nested preset key with last-value-valid checksum");
        });
        test("file roundtrip and bounded atomic persistence", () -> {
            Path homeTemp = Path.of(System.getProperty("user.home"),".tmp"); Files.createDirectories(homeTemp);
            Path dir = Files.createTempDirectory(homeTemp,"axiom-contracts-pure-"); Path file = dir.resolve("build.json");
            var first = build(0,Set.of("chosen_omen")); var second = configured("deep_compact",Map.of("band","deep"));
            AxiomContracts.write(file,first); check(AxiomContracts.read(file).equals(first),"first persisted contract");
            AxiomContracts.write(file,second); check(AxiomContracts.read(file).equals(second),"atomic replacement roundtrip");
            try (var entries = Files.list(dir)) { check(entries.count() == 1,"no leaked partial files"); }
            try { AxiomContracts.read(dir.resolve("missing.json")); throw new AssertionError("Missing file accepted"); }
            catch (java.io.IOException expected) { check(true,"missing file fails closed"); }
            Path oversized = dir.resolve("oversized.json"); Files.write(oversized,new byte[AxiomContracts.MAX_BYTES + 1]);
            try { AxiomContracts.read(oversized); throw new AssertionError("Oversized file accepted"); }
            catch (java.io.IOException expected) { check(true,"oversized file fails closed"); }
            // Retain these tiny fixtures for diagnosis instead of deleting failed evidence.
            System.out.println("Evidence directory: " + dir);
        });
        System.out.println("AxiomContractsPureTest: " + cases + " cases, " + checks + " assertions, " + failures.size() + " failures");
        if (!failures.isEmpty()) throw new AssertionError(String.join("\n",failures));
    }
    private static void choices(String id, String key, String[] values) throws Exception {
        for (String value : values) {
            check(AxiomConfiguration.validate(id,Map.of(key,value)).get(key).equals(value),"native choice " + id + "/" + value);
            roundtrip(configured(id,Map.of(key,value)));
        }
        for (String invalid : List.of("", "made_up", values[0].toUpperCase(java.util.Locale.ROOT),"minecraft:" + values[0]))
            rejects(() -> configured(id,Map.of(key,invalid)),"unsupported enum " + id + "/" + invalid);
    }
}
