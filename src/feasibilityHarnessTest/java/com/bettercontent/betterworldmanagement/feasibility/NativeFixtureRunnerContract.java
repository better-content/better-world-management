package com.bettercontent.betterworldmanagement.feasibility;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
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
        List<TestFunction> first = NativeFixtureRunner.registerFunctions(UnprefixedA.class);
        List<TestFunction> second = NativeFixtureRunner.registerFunctions(UnprefixedB.class);
        check(first.size() == 1 && first.get(0).getTestName().equals("alpha"), "First actual native unprefixed registration");
        check(second.size() == 1 && second.get(0).getTestName().equals("beta"), "Second actual native unprefixed registration");
        check(GameTestRegistry.getTestFunctionsForClassName("UnprefixedA").isEmpty(), "Actual pinned class-prefix query reproduces Native03 failure");
        check(GameTestRegistry.getTestFunctionsForClassName("UnprefixedB").isEmpty(), "Case changes cannot restore missing class prefixes");
        check(first.stream().allMatch(GameTestRegistry.getAllTestFunctions()::contains), "Keep actual native registry objects, no substitutes");
        check(first.get(0) != second.get(0), "Native registration identity provenance stays disjoint");
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
        List<String> wrongThreadCalls = new ArrayList<>();
        new ClassReader(runner).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                List<String> calls = name.equals("failInitialization") ? initialization : name.equals("finish") ? finish : name.equals("begin") ? begin : name.equals("prepareTick") ? preparation : name.equals("startNative") ? start : name.equals("releasePreparation") ? release : new ArrayList<>();
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
        check(finish.indexOf("saveEverything") >= 0 && finish.indexOf("saveEverything") < finish.indexOf("write"), "Success still requires actual normal SAVE before PASS publication");
        check(begin.contains("failInitialization") && !begin.contains("getTestFunctionsForClassName"), "Begin uses cached typed native provenance and durable failure path");
        check(wrongThreadCalls.isEmpty() && !preparation.contains("getChunkFuture"), "No server-thread blocking public getChunkFuture invocation");
        check(preparation.contains("supplyAsync") && preparation.contains("isDone") && preparation.indexOf("isDone") < preparation.indexOf("join"), "Supported async request dispatch and DONE-only result read");
        check(preparation.contains("getChunkNow") && preparation.contains("getStatus"), "Actual resident FULL readiness checks retained");
        check(start.contains("runTests") && start.indexOf("runTests") < start.indexOf("getForcedChunks") && start.indexOf("getForcedChunks") < start.indexOf("releasePreparation"), "Native framework ownership verified before own ticket release");
        check(release.contains("removeRegionTicket") && release.contains("shutdownNow") && !release.contains("setChunkForced"), "Release own tickets/executor only, never alter native forced flags");
        System.out.println("PASS standalone actual public registry + binary runner assertions=" + checks + "; no GameTest bodies/world/server launched");
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
