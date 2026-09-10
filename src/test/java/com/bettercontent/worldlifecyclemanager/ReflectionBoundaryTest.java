package com.bettercontent.worldlifecyclemanager;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ReflectionBoundaryTest {
    private static final List<String> SOURCE_MARKERS = List.of(
            "java.lang.reflect", "kotlin.reflect", "Class.forName(", ".getMethod(",
            ".getDeclaredMethod(", ".getField(", ".getDeclaredField(", ".getConstructor(",
            ".getDeclaredConstructor(", ".setAccessible(", ".trySetAccessible(",
            "Proxy.newProxyInstance(", "MethodHandles", "VarHandle", "sun.misc.Unsafe",
            "jdk.internal.misc.Unsafe");
    private static final List<String> BINARY_MARKERS = List.of(
            "java/lang/reflect", "kotlin/reflect", "forName", "getMethod", "getDeclaredMethod",
            "getField", "getDeclaredField", "getConstructor", "getDeclaredConstructor",
            "setAccessible", "trySetAccessible", "newProxyInstance", "java/lang/invoke/VarHandle",
            "sun/misc/Unsafe", "jdk/internal/misc/Unsafe");

    @Test void productionSourcesDoNotUseReflection() throws Exception {
        try (var files = Files.walk(Path.of("src"))) {
            for (Path file : files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java") || path.toString().endsWith(".kt"))
                    .filter(path -> !path.getFileName().toString().equals("ReflectionBoundaryTest.java"))
                    .toList()) {
                String source = Files.readString(file);
                for (String marker : SOURCE_MARKERS) assertFalse(source.contains(marker), file + ": " + marker);
            }
        }
    }

    @Test void compiledProductionClassesDoNotReferenceReflection() throws Exception {
        try (var files = Files.walk(Path.of("build/classes"))) {
            for (Path file : files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".class") && !path.toString().contains("/test/"))
                    .toList()) {
                String bytecode = new String(Files.readAllBytes(file), java.nio.charset.StandardCharsets.ISO_8859_1);
                for (String marker : BINARY_MARKERS) assertFalse(bytecode.contains(marker), file + ": " + marker);
            }
        }
    }
}
