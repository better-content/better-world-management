package com.bettercontent.betterworldmanagement;

import org.junit.jupiter.api.Test;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipFile;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MixinPackagingTest {
    @Test
    void externalCreateMixinsRemainRemapDisabledWithTheNativeRefmap() throws IOException {
        final String cannonMixin = Files.readString(Path.of(
                "src/main/java/com/bettercontent/betterworldmanagement/mixin/SchematicannonBlockEntityMixin.java"));
        final String printerMixin = Files.readString(Path.of(
                "src/main/java/com/bettercontent/betterworldmanagement/mixin/SchematicPrinterMixin.java"));

        assertTrue(cannonMixin.contains("remap = false"));
        assertTrue(printerMixin.contains("remap = false"));
    }

    @Test
    void nativeMixinsReferenceExactlyOneActuallyPackagedGeneratedRefmap() throws IOException {
        var source = JsonParser.parseString(Files.readString(Path.of(
                "src/main/resources/better_world_management.mixins.json"))).getAsJsonObject();
        String refmap = source.get("refmap").getAsString();
        assertEquals("better_world_management.refmap.json", refmap);
        String artifact = System.getProperty("world_axioms.test.jar");
        assertNotNull(artifact, "Gradle must supply the actual producer's jar, not a guessed latest artifact");
        try (var jar = new ZipFile(artifact)) {
            assertEquals(1L, jar.stream().filter(entry -> entry.getName().equals(refmap)).count(),
                    "Both missing and duplicate refmaps are packaging failures");
            var generated = JsonParser.parseString(new String(jar.getInputStream(jar.getEntry(refmap)).readAllBytes(),
                    StandardCharsets.UTF_8)).getAsJsonObject();
            var mappings = generated.getAsJsonObject("mappings");
            String prefix = "com/bettercontent/betterworldmanagement/mixin/";
            assertTrue(mappings.has(prefix + "AxiomPlayerListAccess"));
            assertTrue(mappings.has(prefix + "AxiomSavedDataAtomicMixin"));
            assertTrue(mappings.getAsJsonObject(prefix + "AxiomSavedDataAtomicMixin").has("save(Ljava/io/File;)V"));
            assertFalse(mappings.has(prefix + "SchematicannonBlockEntityMixin"), "Create targets remain unmapped");
            assertFalse(mappings.has(prefix + "SchematicPrinterMixin"), "Create targets remain unmapped");
            assertNotNull(jar.getEntry(prefix + "AxiomRegionFileStorageAccess.class"),
                    "Native constructor invoker must be packaged even though constructors need no name mapping");
            var packaged = JsonParser.parseString(new String(jar.getInputStream(jar.getEntry(
                    "better_world_management.mixins.json")).readAllBytes(), StandardCharsets.UTF_8));
            assertEquals(source, packaged, "A stale config must not pass against a fresh source file");
        }
    }
}
