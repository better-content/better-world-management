package com.bettercontent.betterworldmanagement;

import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import static org.junit.jupiter.api.Assertions.*;

/** CLASS-retention inspection only: never loads a Mixin, Minecraft class or world. */
public final class MixinPackageBoundaryTest {
    private static final String OWN = "com/bettercontent/betterworldmanagement/";

    static boolean isMixin(byte[] bytes) {
        boolean[] found = {false};
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                if (descriptor.equals("Lorg/spongepowered/asm/mixin/Mixin;")) found[0] = true;
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return found[0];
    }

    static List<String> violations(List<String> packages, Set<String> declared, Map<String, byte[]> classes) {
        List<String> bad = new ArrayList<>();
        for (String name : declared) {
            if (!classes.containsKey(name) || !isMixin(classes.get(name))) bad.add("missing/non-Mixin declaration: " + name);
        }
        for (var entry : classes.entrySet()) {
            boolean mixin = isMixin(entry.getValue());
            for (String pkg : packages) {
                if (entry.getKey().startsWith(pkg.replace('.', '/') + "/") && !mixin)
                    bad.add("ordinary class in reserved namespace " + pkg + ": " + entry.getKey());
            }
            if (mixin && !declared.contains(entry.getKey())) bad.add("undeclared/stale Mixin: " + entry.getKey());
        }
        return bad;
    }

    static void audit(Path artifact) throws Exception {
        List<String> packages = new ArrayList<>();
        Set<String> declared = new HashSet<>();
        Map<String, byte[]> classes = new HashMap<>();
        try (var jar = new ZipFile(artifact.toFile())) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (entry.getName().startsWith(OWN) && entry.getName().endsWith(".class")) {
                    try (var input = jar.getInputStream(entry)) {
                        classes.put(entry.getName().substring(0, entry.getName().length() - 6), input.readAllBytes());
                    }
                }
                if (!entry.getName().endsWith(".mixins.json")) continue;
                com.google.gson.JsonObject config;
                try (var input = jar.getInputStream(entry)) {
                    config = JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                }
                String pkg = config.get("package").getAsString();
                if (!pkg.startsWith(OWN.replace('/', '.'))) continue;
                packages.add(pkg);
                for (String section : List.of("mixins", "client", "server")) {
                    if (!config.has(section)) continue;
                    for (var name : config.getAsJsonArray(section))
                        declared.add((pkg + "." + name.getAsString()).replace('.', '/'));
                }
            }
        }
        if (packages.isEmpty() || classes.isEmpty()) throw new AssertionError("No actual own configs/classes in " + artifact);
        List<String> bad = violations(packages, declared, classes);
        if (!bad.isEmpty()) throw new AssertionError(artifact + ": " + bad);
        System.out.println("PASS actual binary reserved-package audit: " + artifact + " configs=" + packages.size()
                + " declaredMixins=" + declared.size() + " classes=" + classes.size());
    }

    @Test void actualProducerJarHasNoReservedOrdinaryClassesOrStaleMixins() throws Exception {
        String artifact = System.getProperty("world_axioms.test.jar");
        assertNotNull(artifact, "Use the actual producer's JAR, never a latest-file guess");
        audit(Path.of(artifact));
    }

    private static byte[] synthetic(String name, boolean mixin) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
        if (mixin) writer.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false).visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    @Test void ordinaryHelpersAreRejectedAtRootAndNestedReservedPaths() {
        for (String name : List.of("p/mixin/Helper", "p/mixin/nested/Helper")) {
            assertFalse(violations(List.of("p.mixin"), Set.of(), Map.of(name, synthetic(name, false))).isEmpty());
        }
    }

    @Test void classRetainedMixinAndOutsideBoundaryHelpersAreAccepted() {
        Map<String, byte[]> classes = Map.of("p/mixin/M", synthetic("p/mixin/M", true),
                "p/mixins/Helper", synthetic("p/mixins/Helper", false),
                "p/Helper", synthetic("p/Helper", false));
        assertTrue(violations(List.of("p.mixin"), Set.of("p/mixin/M"), classes).isEmpty());
    }

    @Test void missingDeclarationsAndOldUnregisteredMixinClassesAreRejected() {
        assertFalse(violations(List.of("p.mixin"), Set.of("p/mixin/M"), Map.of()).isEmpty());
        assertFalse(violations(List.of("p.mixin"), Set.of(),
                Map.of("p/OldMixin", synthetic("p/OldMixin", true))).isEmpty());
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) throw new IllegalArgumentException("Explicit completed artifact paths required");
        for (String path : args) audit(Path.of(path));
    }
}
