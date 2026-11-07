package com.bettercontent.betterworldmanagement.mixin;

import com.bettercontent.betterworldmanagement.SingleplayerInitialSpawn;
import com.mojang.serialization.Lifecycle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.server.RegistryLayer;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PrimaryLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.util.Optional;

@Mixin(CreateWorldScreen.class)
public abstract class CreateWorldScreenMixin {
    @Inject(method = "createNewWorld", at = @At("HEAD"), cancellable = true)
    private void betterWorldManagement$recordSeedInput(PrimaryLevelData.SpecialWorldProperty property,
            LayeredRegistryAccess<RegistryLayer> registries, Lifecycle lifecycle, CallbackInfo ci) {
        try {
            SingleplayerInitialSpawn.begin((CreateWorldScreen) (Object) this, property, registries, lifecycle);
        } catch (RuntimeException error) {
            com.bettercontent.betterworldmanagement.PrestigeMod.LOGGER.error(
                    "Protected single-player candidate creation failed before world entry", error);
            Minecraft.getInstance().setScreen(new AlertScreen(
                    () -> Minecraft.getInstance().setScreen(new TitleScreen()),
                    Component.literal("World preparation stopped"),
                    Component.literal("Could not create a protected world staging area; no world was entered.")));
            ci.cancel();
        }
    }

    @Inject(method = "createNewWorld", at = @At("RETURN"))
    private void betterWorldManagement$rejectFailedStagingStartup(PrimaryLevelData.SpecialWorldProperty property,
            LayeredRegistryAccess<RegistryLayer> registries, Lifecycle lifecycle, CallbackInfo ci) {
        SingleplayerInitialSpawn.failedToStartServer();
    }

    @Redirect(method = "createNewWorldDirectory", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/worldselection/WorldCreationUiState;getTargetFolder()Ljava/lang/String;"))
    private String betterWorldManagement$useUnpublishedSave(WorldCreationUiState state) {
        return SingleplayerInitialSpawn.stageFolder(state.getTargetFolder());
    }

    @Redirect(method = "createNewWorldDirectory", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Minecraft;getLevelSource()Lnet/minecraft/world/level/storage/LevelStorageSource;"))
    private LevelStorageSource betterWorldManagement$usePrivateStage(Minecraft client) {
        return SingleplayerInitialSpawn.stageSource(client.getLevelSource());
    }

    @Inject(method = "createNewWorldDirectory", at = @At("RETURN"), cancellable = true)
    private void betterWorldManagement$restoreSelectedDataPacks(
            CallbackInfoReturnable<Optional<LevelStorageSource.LevelStorageAccess>> cir) {
        if (cir.getReturnValue().isEmpty()) {
            SingleplayerInitialSpawn.failedBeforeLaunch("Could not create a protected candidate save. No world was entered.");
            return;
        }
        try { SingleplayerInitialSpawn.copiedDataPacks(cir.getReturnValue().get()); }
        catch (IOException error) {
            try { cir.getReturnValue().get().close(); } catch (IOException ignored) { /* retain stage */ }
            SingleplayerInitialSpawn.failedBeforeLaunch("Could not preserve selected datapacks for the retry. Stage retained.");
            cir.setReturnValue(Optional.empty());
        }
    }
}
