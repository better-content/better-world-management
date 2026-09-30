package com.bettercontent.betterworldmanagement;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MixinPackagingTest {
    @Test
    void externalCreateMixinsDoNotDeclareANonexistentRefmap() throws IOException {
        final String config = Files.readString(Path.of(
                "src/main/resources/better_world_management.mixins.json"));
        final String cannonMixin = Files.readString(Path.of(
                "src/main/java/com/bettercontent/betterworldmanagement/mixin/SchematicannonBlockEntityMixin.java"));
        final String printerMixin = Files.readString(Path.of(
                "src/main/java/com/bettercontent/betterworldmanagement/mixin/SchematicPrinterMixin.java"));

        assertFalse(config.contains("refmap"), "remap-disabled Create mixins must not request a refmap");
        assertTrue(cannonMixin.contains("remap = false"));
        assertTrue(printerMixin.contains("remap = false"));
    }
}
