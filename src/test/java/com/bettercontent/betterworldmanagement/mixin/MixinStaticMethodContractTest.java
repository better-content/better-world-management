package com.bettercontent.betterworldmanagement.mixin;

import com.google.gson.JsonParser;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Reads binary resources without loading Mixin classes or inspecting Java members reflectively. */
public final class MixinStaticMethodContractTest {
    @Test void packagedMixinsHaveNoForbiddenStaticHelpers() throws Exception { checkPackaged(); }
    @Test void ordinaryNonprivateStaticHelpersAreRejected() { checkControls(); }
    public static void main(String[] args) throws Exception {
        checkControls(); checkPackaged();
        System.out.println("PASS packaged Mixin static-method contract plus binary negative controls");
    }
    static List<String> forbidden(byte[] bytes) {
        List<String> failures = new ArrayList<>();
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if ((access & Opcodes.ACC_STATIC) == 0 || (access & Opcodes.ACC_PRIVATE) != 0 || name.equals("<clinit>")) return null;
                return new MethodVisitor(Opcodes.ASM9) {
                    boolean accessor;
                    @Override public AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                        if (annotation.equals("Lorg/spongepowered/asm/mixin/gen/Invoker;")
                                || annotation.equals("Lorg/spongepowered/asm/mixin/gen/Accessor;")) accessor = true;
                        return null;
                    }
                    @Override public void visitEnd() { if (!accessor) failures.add(name + descriptor); }
                };
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return failures;
    }
    private static byte[] resource(String name) throws Exception {
        try (InputStream input = MixinStaticMethodContractTest.class.getResourceAsStream("/" + name)) {
            if (input == null) throw new AssertionError("Missing actual packaged resource: " + name);
            return input.readAllBytes();
        }
    }
    private static void checkPackaged() throws Exception {
        int checked = 0;
        for (String config : List.of("better_world_management.mixins.json", "better_world_management.recipes.mixins.json")) {
            var json = JsonParser.parseString(new String(resource(config), StandardCharsets.UTF_8)).getAsJsonObject();
            String base = json.get("package").getAsString().replace('.', '/') + "/";
            for (String section : List.of("mixins", "client", "server")) {
                if (!json.has(section)) continue;
                for (var entry : json.getAsJsonArray(section)) {
                    String path = base + entry.getAsString().replace('.', '/') + ".class";
                    List<String> bad = forbidden(resource(path));
                    if (!bad.isEmpty()) throw new AssertionError("Forbidden non-private static Mixin methods: " + path + " " + bad);
                    checked++;
                }
            }
        }
        if (checked == 0) throw new AssertionError("No actual packaged Mixins checked");
        System.out.println("Checked actual packaged Mixin classes=" + checked);
    }
    private static void checkControls() {
        for (int visibility : new int[] {0, Opcodes.ACC_PUBLIC, Opcodes.ACC_PROTECTED, Opcodes.ACC_PRIVATE}) {
            ClassWriter writer = new ClassWriter(0);
            writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "SyntheticStaticControl", null, "java/lang/Object", null);
            MethodVisitor method = writer.visitMethod(visibility | Opcodes.ACC_STATIC, "helper", "()V", null, null);
            method.visitAnnotation("Lorg/spongepowered/asm/mixin/Unique;", false).visitEnd();
            method.visitCode(); method.visitInsn(Opcodes.RETURN); method.visitMaxs(0, 0); method.visitEnd(); writer.visitEnd();
            if (forbidden(writer.toByteArray()).isEmpty() != (visibility == Opcodes.ACC_PRIVATE))
                throw new AssertionError("Static visibility control failed: " + visibility);
        }
    }
}
