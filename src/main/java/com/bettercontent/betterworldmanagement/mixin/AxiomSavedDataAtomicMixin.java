package com.bettercontent.betterworldmanagement.mixin;

import com.bettercontent.betterworldmanagement.worldshaping.AxiomSavedDataWriter;
import com.bettercontent.betterworldmanagement.worldshaping.world.WorldPolicyData;
import com.bettercontent.celestial.calendar.CalendarSavedData;
import com.bettercontent.spiritcommerce.trader.WanderingTraderScheduleData;
import java.io.File;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sereneseasons.season.SeasonSavedData;

/** Atomic durability only for the four world-law receipt owners; every other SavedData keeps vanilla behavior. */
@Mixin(SavedData.class)
public abstract class AxiomSavedDataAtomicMixin {
    @Inject(method = "save(Ljava/io/File;)V", at = @At("HEAD"), cancellable = true)
    private void axioms$saveAtomically(File file, CallbackInfo callback) {
        SavedData data = (SavedData) (Object) this;
        // Do not cancel a clean instance: retain vanilla's own normal no-op path.
        if (!data.isDirty() || !axioms$isScoped(data)) return;
        AxiomSavedDataWriter.write(data, file.toPath());
        callback.cancel();
    }

    @Unique
    private static boolean axioms$isScoped(SavedData data) {
        if (data instanceof WorldPolicyData) return true;
        if (ModList.get().isLoaded("better_celestial_events") && data instanceof CalendarSavedData) return true;
        if (ModList.get().isLoaded("better_spirit_commerce") && data instanceof WanderingTraderScheduleData) return true;
        return ModList.get().isLoaded("sereneseasons") && data instanceof SeasonSavedData
                && data.getClass() == SeasonSavedData.class;
    }

}
