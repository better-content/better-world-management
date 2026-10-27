package com.bettercontent.betterworldmanagement.worldshaping.recipes;

import java.util.concurrent.atomic.AtomicBoolean;
import net.joefoxe.hexerei.data.recipes.MixingCauldronRecipe;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Pinned Hexerei0.4.2.3 adapter, ONLY our two recipe IDs. Native item/heat/moon,
 * recipe matching, delay and output run untouched. Correct missing water drain
 * only after independently observed real craft success. Not registered by this
 * slice: root owns mixin JSON and native verification before activation. */
@Pseudo
@Mixin(targets = "net.joefoxe.hexerei.tileentity.MixingCauldronTile", remap = false)
public abstract class WorldAxiomCauldronWaterMixin {
    @Unique private static final Logger worldAxioms$LOGGER = LoggerFactory.getLogger("WorldAxiomCauldronWater");
    @Unique private int worldAxioms$beforeWater = -1;
    @Unique private ItemStack worldAxioms$beforeOutput = ItemStack.EMPTY;
    @Shadow public abstract FluidStack getFluidStack();
    @Shadow public abstract boolean getCrafted();

    @Inject(method = "lambda$craft$8", at = @At("HEAD"), require = 1, remap = false)
    private void worldAxioms$capture(SimpleContainer inputs, AtomicBoolean matched, MixingCauldronRecipe recipe, CallbackInfo ci) {
        worldAxioms$beforeWater = -1;
        String id = recipe.getId().toString();
        if (!id.equals("kubejs:world_axioms/patient_alchemist/moon_dust")
                && !id.equals("kubejs:world_axioms/chalk_covenant/white_impure")) return;
        BlockEntity self = (BlockEntity) (Object) this;
        if (self.getLevel() == null || self.getLevel().isClientSide || getCrafted()) return;
        FluidStack water = getFluidStack();
        if (water.getFluid() != Fluids.WATER || recipe.getLiquid().getFluid() != Fluids.WATER
                || recipe.getLiquidOutput().getFluid() != Fluids.WATER || recipe.getFluidLevelsConsumed() != 250) {
            worldAxioms$LOGGER.error("Refusing unexpected scoped cauldron fluid contract: {}", id);
            return;
        }
        worldAxioms$beforeWater = water.getAmount();
        worldAxioms$beforeOutput = ((Container) self).getItem(8).copy();
    }

    @Inject(method = "lambda$craft$8", at = @At("RETURN"), require = 1, remap = false)
    private void worldAxioms$consumeMissingWater(SimpleContainer inputs, AtomicBoolean matched, MixingCauldronRecipe recipe, CallbackInfo ci) {
        int before = worldAxioms$beforeWater;
        worldAxioms$beforeWater = -1;
        // Both actual native craft flag transition AND exact output delta are
        // required. A match, rejected heat/water, or partial delay cannot drain.
        if (before < 0 || !getCrafted()) return;
        Container inventory = (Container) (Object) this;
        ItemStack output = inventory.getItem(8);
        ItemStack expected = recipe.getOutput();
        if (!ItemStack.isSameItemSameTags(output, expected)
                || (!worldAxioms$beforeOutput.isEmpty() && !ItemStack.isSameItemSameTags(worldAxioms$beforeOutput, expected))
                || output.getCount() != worldAxioms$beforeOutput.getCount() + expected.getCount()) {
            worldAxioms$LOGGER.error("Refusing water adjustment without exact native output transition: {}", recipe.getId());
            return;
        }
        for (int slot = 0; slot < 8; slot++) if (!inventory.getItem(slot).isEmpty()) {
            worldAxioms$LOGGER.error("Refusing water adjustment with unconsumed native input: {} slot{}", recipe.getId(), slot);
            return;
        }
        FluidStack after = getFluidStack();
        if (after.isEmpty() && before == 250) return; // native already consumed exactly all water
        if (after.getFluid() != Fluids.WATER) {
            worldAxioms$LOGGER.error("Refusing unexpected native fluid replacement: {}", recipe.getId());
            return;
        }
        try {
            int missing = CauldronWaterAccounting.missingDrain(before, after.getAmount(), 250);
            if (missing == 0) return; // already corrected upstream: NEVER double-drain
            FluidStack removed = ((IFluidHandler) (Object) this).drain(new FluidStack(Fluids.WATER, missing), IFluidHandler.FluidAction.EXECUTE);
            if (removed.getAmount() != missing || getFluidStack().getAmount() != before - 250) {
                worldAxioms$LOGGER.error("Scoped native water drain mismatch for {}; missing{} actual{} remaining{}",
                        recipe.getId(), missing, removed.getAmount(), getFluidStack().getAmount());
            }
        } catch (IllegalArgumentException unexpectedNativeBehavior) {
            // Fail closed: no water is created to repair an unexpected overdrain.
            worldAxioms$LOGGER.error("Refusing unexpected native water transition for {}", recipe.getId(), unexpectedNativeBehavior);
        }
    }
}
