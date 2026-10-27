package com.bettercontent.betterworldmanagement.harness;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Non-shipping, typed client-only bootstrap. No reflection, login or input-event automation. */
@Mod.EventBusSubscriber(modid = VisualHarnessMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class AxiomVisualClient {
    private AxiomVisualClient() {}
    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        if (Boolean.getBoolean("world_axioms.visualStandalone"))
            event.enqueueWork(AxiomVisualHarness::installStandalone);
    }
}
