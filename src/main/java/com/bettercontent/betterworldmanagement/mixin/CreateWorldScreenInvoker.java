package com.bettercontent.betterworldmanagement.mixin;

import com.mojang.serialization.Lifecycle;
import com.mojang.datafixers.util.Pair;
import java.nio.file.Path;
import java.util.function.Consumer;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.server.RegistryLayer;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.server.packs.repository.PackRepository;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CreateWorldScreen.class)
public interface CreateWorldScreenInvoker {
    @Accessor("tempDataPackRepository")
    void betterWorldManagement$setPackRepository(PackRepository repository);

    @Invoker("getDataPackSelectionSettings")
    Pair<Path, PackRepository> betterWorldManagement$selectionSettings(WorldDataConfiguration config);

    @Invoker("applyNewPackConfig")
    void betterWorldManagement$reloadPacks(PackRepository repository, WorldDataConfiguration config,
                                           Consumer<WorldDataConfiguration> afterReload);

    @Invoker("createNewWorld")
    void betterWorldManagement$retryWorld(PrimaryLevelData.SpecialWorldProperty property,
                                           LayeredRegistryAccess<RegistryLayer> registries, Lifecycle lifecycle);
}
