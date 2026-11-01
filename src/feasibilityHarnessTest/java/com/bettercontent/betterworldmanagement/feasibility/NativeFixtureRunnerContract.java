package com.bettercontent.betterworldmanagement.feasibility;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestRegistry;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Standalone pinned-public-registry and binary control, NOT a server/native gameplay test. */
public final class NativeFixtureRunnerContract {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++; if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        JsonObject process = NativeFixtureRunner.currentProcessProvenance();
        check(process.get("process_id").getAsLong() == ProcessHandle.current().pid(), "Provenance PID is the actual current public process, not a caller/environment value");
        check(Instant.parse(process.get("process_started_at").getAsString()).equals(ProcessHandle.current().info().startInstant().orElseThrow()), "Provenance start is the actual public process start Instant");
        List<TestFunction> first = NativeFixtureRunner.registerFunctions(UnprefixedA.class);
        List<TestFunction> second = NativeFixtureRunner.registerFunctions(UnprefixedB.class);
        check(first.size() == 1 && first.get(0).getTestName().equals("alpha"), "First actual native unprefixed registration");
        check(second.size() == 1 && second.get(0).getTestName().equals("beta"), "Second actual native unprefixed registration");
        check(GameTestRegistry.getTestFunctionsForClassName("UnprefixedA").isEmpty(), "Actual pinned class-prefix query reproduces Native03 failure");
        check(GameTestRegistry.getTestFunctionsForClassName("UnprefixedB").isEmpty(), "Case changes cannot restore missing class prefixes");
        check(first.stream().allMatch(GameTestRegistry.getAllTestFunctions()::contains), "Keep actual native registry objects, no substitutes");
        check(first.get(0) != second.get(0), "Native registration identity provenance stays disjoint");
        check(NativeFixtureRunner.deferPendingSuccession("succession", "successorall24nativestateclaimsandretry", true, 20),
                "All-24 successor waits for production health and promotion");
        check(NativeFixtureRunner.deferPendingSuccession("succession", "sourcestageeightrecipesandrequestrealreset", true, 20),
                "Existing eight-recipe successor deferral is preserved");
        check(NativeFixtureRunner.deferPendingSuccession("succession", "sourcestageall24andrequestrealreset", true, 20),
                "All-24 source selector reentry waits for actual successor promotion");
        for (String selector : List.of("postclaimrestartpreservesreceiptsandentities", "wrongselector"))
            check(!NativeFixtureRunner.deferPendingSuccession("succession", selector, true, 20),
                    "Unrelated restart/unknown selector cannot defer: " + selector);
        check(!NativeFixtureRunner.deferPendingSuccession("succession", "successorall24nativestateclaimsandretry", false, 20)
                && !NativeFixtureRunner.deferPendingSuccession("recipes", "successorall24nativestateclaimsandretry", true, 20)
                && !NativeFixtureRunner.deferPendingSuccession("succession", "successorall24nativestateclaimsandretry", true, 6000),
                "Real-succession opt-in/campaign/deadline cannot be bypassed");
        var region = NativeFixtureRunner.preparationRegion(new BlockPos(1_000_000, 128, 1_000_000));
        ChunkPos center = new ChunkPos(new BlockPos(1_000_000, 128, 1_000_000));
        check(region.size() == 25 && new HashSet<>(region).size() == 25, "Hard-bounded unique 25 native preparation chunks");
        check(region.contains(new ChunkPos(center.x - 1, center.z - 1)) && region.contains(new ChunkPos(center.x + 3, center.z + 3)), "Exact native -1..3 extent, not guessed centered extent");
        check(region.stream().allMatch(pos -> pos.x >= center.x - 1 && pos.x <= center.x + 3 && pos.z >= center.z - 1 && pos.z <= center.z + 3), "No out-of-bound preload requests");
        byte[] runner;
        try (var input = NativeFixtureRunnerContract.class.getResourceAsStream("NativeFixtureRunner.class")) {
            if (input == null) throw new AssertionError("Missing actual compiled runner");
            runner = input.readAllBytes();
        }
        List<String> initialization = new ArrayList<>(), finish = new ArrayList<>(), begin = new ArrayList<>(), preparation = new ArrayList<>(), start = new ArrayList<>(), release = new ArrayList<>();
        List<String> wrongThreadCalls = new ArrayList<>(), manifestTelemetry = new ArrayList<>();
        List<String> record = new ArrayList<>(), publication = new ArrayList<>(), shutdown = new ArrayList<>(), beginLambdas = new ArrayList<>();
        new ClassReader(runner).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                List<String> calls = name.equals("failInitialization") ? initialization : name.equals("finish") ? finish : name.equals("begin") ? begin : name.equals("prepareTick") ? preparation : name.equals("startNative") ? start : name.equals("releasePreparation") ? release : name.equals("record") ? record : name.equals("publish") ? publication : name.equals("finalizeShutdownEvidence") ? shutdown : name.startsWith("lambda$begin$") ? beginLambdas : (name.equals("write") || name.startsWith("lambda$write$")) ? manifestTelemetry : new ArrayList<>();
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override public void visitLdcInsn(Object value) { if (value instanceof String) calls.add("literal:" + value); }
                    @Override public void visitFieldInsn(int opcode, String owner, String field, String desc) { if (opcode == Opcodes.PUTFIELD) calls.add("set:" + field); }
                    @Override public void visitMethodInsn(int opcode, String owner, String method, String desc, boolean itf) {
                        calls.add(method);
                        if (method.equals("getChunkFuture") && !name.startsWith("lambda$prepareTick$")) wrongThreadCalls.add(name);
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        check(initialization.contains("literal:FAIL") && initialization.contains("set:worldSaved"), "Initialization writes explicit FAIL/unsaved state");
        check(initialization.indexOf("write") >= 0 && initialization.indexOf("write") < initialization.indexOf("halt"), "Publish initialization failure before normal halt");
        check(!initialization.contains("saveEverything") && !initialization.contains("finish"), "Initialization failure cannot block in synchronous END-tick flush");
        check(!finish.contains("saveEverything") && finish.indexOf("write") >= 0 && finish.indexOf("write") < finish.indexOf("halt"), "Final case verdict publication precedes public stop, with no in-tick SAVE");
        check(finish.contains("literal:AWAITING_SAVE") && finish.contains("literal:normal_shutdown") && finish.contains("literal:world_axioms.stopAfterFixtures") && finish.contains("getBoolean"), "Save pending is explicit and normal halt retains opt-in for succession");
        check(record.contains("isDone") && record.contains("literal:RUNNING") && record.contains("write"), "Case completion immediately attempts real atomic RUNNING publication");
        check(publication.contains("force") && publication.contains("move"), "Per-case and final publication use forced atomic file replacement");
        check(begin.contains("addShutdownHook") && beginLambdas.contains("finalizeShutdownEvidence") && !shutdown.contains("saveEverything") && !shutdown.contains("halt"), "Disk-only evidence hook registered and wired; cannot save or stop a server");
        check(manifestTelemetry.contains("literal:last_case_completed_at") && manifestTelemetry.contains("literal:case_status") && manifestTelemetry.contains("literal:save_mode"), "Persist exact launcher completion and save field contract");
        check(begin.contains("failInitialization") && !begin.contains("getTestFunctionsForClassName"), "Begin uses cached typed native provenance and durable failure path");
        check(begin.contains("literal:process_started_at") && begin.contains("literal:Public current process PID/startInstant unavailable; cannot qualify native provenance"), "Missing public process start identity fails initialization closed");
        check(manifestTelemetry.contains("entrySet") && !manifestTelemetry.contains("currentProcessProvenance"), "Every manifest reuses captured campaign identity rather than caller metadata or repeated process capture");
        check(wrongThreadCalls.isEmpty() && !preparation.contains("getChunkFuture"), "No server-thread blocking public getChunkFuture invocation");
        check(preparation.contains("supplyAsync") && preparation.contains("isDone") && preparation.indexOf("isDone") < preparation.indexOf("join"), "Supported async request dispatch and DONE-only result read");
        check(preparation.contains("getChunkNow") && preparation.contains("getStatus"), "Actual resident FULL readiness checks retained");
        check(preparation.contains("literal:DONE") && preparation.contains("literal:FAILED") && !preparation.contains("literal:FULL"), "Preparation telemetry distinguishes validated DONE from failed FULL readiness");
        check(manifestTelemetry.contains("literal:DONE") && manifestTelemetry.contains("count"), "Manifest completion count derives from DONE telemetry, not a stale partial counter");
        check(start.contains("runTests") && start.indexOf("runTests") < start.indexOf("getForcedChunks") && start.indexOf("getForcedChunks") < start.indexOf("releasePreparation"), "Native framework ownership verified before own ticket release");
        check(release.contains("removeRegionTicket") && release.contains("shutdownNow") && !release.contains("setChunkForced"), "Release own tickets/executor only, never alter native forced flags");
        diskEvidenceControls();
        System.out.println("PASS standalone actual public registry + binary/disk runner assertions=" + checks + "; no GameTest bodies/world/server launched");
    }
    private static JsonObject read(Path path) throws Exception {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
    private static JsonObject candidate(Instant lastCase, String verdict) {
        JsonObject result = new JsonObject();
        result.addProperty("status", "AWAITING_SAVE"); result.addProperty("case_status", verdict);
        result.addProperty("save_mode", "normal_shutdown"); result.addProperty("world_saved", false);
        result.addProperty("last_case_completed_at", lastCase.toString());
        NativeFixtureRunner.currentProcessProvenance().entrySet().forEach(entry -> result.add(entry.getKey(), entry.getValue()));
        result.addProperty("expected", 2); result.addProperty("completed", 2);
        return result;
    }
    private static void diskEvidenceControls() throws Exception {
        Path root = Files.createTempDirectory(Path.of(System.getenv().getOrDefault("TMPDIR", "/home/dev/.tmp")), "native-shutdown-controls-");
        Path results = root.resolve("results.json"), world = root.resolve("world");
        Files.createDirectories(world.resolve("region"));
        Instant last = Instant.now().minusSeconds(10);
        JsonObject running = candidate(last, "PASS"); running.addProperty("status", "RUNNING");
        running.addProperty("completed", 1); running.remove("save_mode"); running.remove("case_status");
        com.google.gson.JsonArray cases = new com.google.gson.JsonArray();
        JsonObject completedCase = new JsonObject(); completedCase.addProperty("name", "actual-control-row");
        completedCase.addProperty("completed", true); completedCase.addProperty("passed", true); cases.add(completedCase);
        running.add("cases", cases);
        NativeFixtureRunner.publish(results, running);
        check(read(results).getAsJsonArray("cases").get(0).getAsJsonObject().get("passed").getAsBoolean(), "Immediate publication retains a completed case row on disk");
        String beforeCrash = Files.readString(results);
        NativeFixtureRunner.finalizeShutdownEvidence(results, world);
        check(Files.readString(results).equals(beforeCrash) && !read(results).get("world_saved").getAsBoolean(), "Crash-before-final preserves truthful RUNNING-with-cases and cannot become PASS");
        NativeFixtureRunner.publish(results, candidate(last, "PASS"));
        NativeFixtureRunner.finalizeShutdownEvidence(results, world);
        check(!read(results).get("world_saved").getAsBoolean() && read(results).has("save_error") && !read(results).has("save_evidence"), "Missing level.dat refuses save acceptance");
        Path level = world.resolve("level.dat"), region = world.resolve("region/r.0.0.mca");
        Files.writeString(level, "SYNTHETIC FILE CONTROL ONLY"); Files.writeString(region, "SYNTHETIC REGION CONTROL ONLY");
        Files.setLastModifiedTime(level, FileTime.from(last.minusSeconds(1)));
        NativeFixtureRunner.publish(results, candidate(last, "PASS"));
        NativeFixtureRunner.finalizeShutdownEvidence(results, world);
        check(!read(results).get("world_saved").getAsBoolean(), "Stale level.dat refuses save acceptance even with newer region");
        Files.setLastModifiedTime(level, FileTime.from(last.plusSeconds(1)));
        Files.setLastModifiedTime(region, FileTime.from(last));
        NativeFixtureRunner.publish(results, candidate(last, "PASS"));
        NativeFixtureRunner.finalizeShutdownEvidence(results, world);
        check(!read(results).get("world_saved").getAsBoolean(), "Region mtime equal to case completion is not strictly after it");
        Files.setLastModifiedTime(region, FileTime.from(last.plusSeconds(2)));
        NativeFixtureRunner.publish(results, candidate(last, "PASS"));
        NativeFixtureRunner.finalizeShutdownEvidence(results, world);
        JsonObject saved = read(results), evidence = saved.getAsJsonObject("save_evidence");
        check(saved.get("world_saved").getAsBoolean() && saved.get("status").getAsString().equals("PASS") && !saved.has("save_error"), "Only post-case level and region evidence upgrades eligible case PASS");
        JsonObject identity = NativeFixtureRunner.currentProcessProvenance();
        check(saved.get("process_id").equals(identity.get("process_id")) && saved.get("process_started_at").equals(identity.get("process_started_at")), "Save hook preserves captured process PID/start provenance unchanged");
        check(evidence.get("region_files").getAsInt() == 1 && evidence.get("level_dat_sha256").getAsString().equals(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(level)))) && Instant.parse(evidence.get("verified_at").getAsString()).isAfter(last), "Exact save evidence fields retain real disk hash/count/time");
        NativeFixtureRunner.publish(results, candidate(last, "FAIL"));
        NativeFixtureRunner.finalizeShutdownEvidence(results, world);
        check(read(results).get("world_saved").getAsBoolean() && read(results).get("status").getAsString().equals("FAIL"), "Real save evidence can never turn a failed case verdict into PASS");
        JsonObject incomplete = candidate(last, "PASS"); incomplete.addProperty("completed", 1);
        NativeFixtureRunner.publish(results, incomplete); NativeFixtureRunner.finalizeShutdownEvidence(results, world);
        check(!read(results).get("world_saved").getAsBoolean(), "Incomplete campaigns cannot qualify on mtimes alone");
        JsonObject future = candidate(Instant.now().plusSeconds(3600), "PASS");
        NativeFixtureRunner.publish(results, future); NativeFixtureRunner.finalizeShutdownEvidence(results, world);
        check(!read(results).get("world_saved").getAsBoolean(), "Future case timestamp refuses ambiguous save evidence");
        JsonObject missingTime = candidate(last, "PASS"); missingTime.remove("last_case_completed_at");
        NativeFixtureRunner.publish(results, missingTime); NativeFixtureRunner.finalizeShutdownEvidence(results, world);
        check(!read(results).get("world_saved").getAsBoolean(), "Missing case timestamp refuses save evidence");
        Files.writeString(region, "");
        NativeFixtureRunner.publish(results, candidate(last, "PASS")); NativeFixtureRunner.finalizeShutdownEvidence(results, world);
        check(!read(results).get("world_saved").getAsBoolean(), "Empty region file is not save evidence");
        System.out.println("Synthetic disk controls retained at " + root + "; not Minecraft save acceptance");
    }
    @PrefixGameTestTemplate(false)
    public static final class UnprefixedA {
        @GameTest(templateNamespace = "minecraft", template = "empty")
        public static void alpha(GameTestHelper helper) { throw new AssertionError("Control body must never run"); }
    }
    @PrefixGameTestTemplate(false)
    public static final class UnprefixedB {
        @GameTest(templateNamespace = "minecraft", template = "empty")
        public static void beta(GameTestHelper helper) { throw new AssertionError("Control body must never run"); }
    }
}
