package com.bettercontent.betterworldmanagement;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@OnlyIn(Dist.CLIENT)
public final class PrestigeClient {
    private PrestigeClient() {}
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(PrestigeRegistry.WORLD_CONDENSER_MENU.get(), WorldCondenserScreen::new);
            com.bettercontent.betterworldmanagement.worldshaping.WorldAxiomNetwork.clientReceiver = view -> {
                net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
                if (client.screen instanceof com.bettercontent.betterworldmanagement.worldshaping.client.WorldAxiomScreen screen) screen.update(view);
                else if (client.screen instanceof WorldCondenserScreen) client.setScreen(new com.bettercontent.betterworldmanagement.worldshaping.client.WorldAxiomScreen(view));
            };
        });
    }
}
