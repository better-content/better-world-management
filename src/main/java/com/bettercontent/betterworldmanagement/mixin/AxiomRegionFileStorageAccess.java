package com.bettercontent.betterworldmanagement.mixin;

import java.nio.file.Path;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Read back native persisted entity records after the normal flush, without private-field reflection. */
@Mixin(RegionFileStorage.class)
public interface AxiomRegionFileStorageAccess {
    @Invoker("<init>") static RegionFileStorage worldAxioms$open(Path directory, boolean sync) { throw new AssertionError("Mixin invoker was not applied"); }
}
