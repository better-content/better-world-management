package com.bettercontent.worldlifecyclemanager.mixin;
import com.bettercontent.worldlifecyclemanager.SchematicSubstitutionFlight;
import com.simibubi.create.content.schematics.cannon.LaunchedItem;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.registries.BuiltInRegistries;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=LaunchedItem.ForBlockState.class,remap=false)
public abstract class SchematicSubstitutionPlacementMixin {
 @Unique private BlockState worldLifecycleManager$before;
 @Inject(method="place",at=@At("HEAD"))private void before(Level level,CallbackInfo ci){var flight=(LaunchedItem.ForBlockState)(Object)this;worldLifecycleManager$before=level.getBlockState(flight.target);}
 @Inject(method="place",at=@At("RETURN"))private void placed(Level world,CallbackInfo ci){
  if(!(world instanceof ServerLevel level))return;
  var flight=(LaunchedItem.ForBlockState)(Object)this;var provenance=((SchematicSubstitutionFlight)this).worldLifecycleManager$provenance();
  if(!provenance.hasUUID("owner")||!provenance.hasUUID("operation")||flight.state.equals(worldLifecycleManager$before)||!flight.state.equals(level.getBlockState(flight.target)))return;
  if(!BuiltInRegistries.BLOCK.getKey(flight.state.getBlock()).toString().equals(provenance.getString("target")))return;
  net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new com.bettercontent.worldlifecyclemanager.api.event.SchematicSubstitutionEvent(level,provenance.getUUID("owner"),provenance.getUUID("operation"),flight.target,provenance.getString("source"),provenance.getString("target")));
  ((SchematicSubstitutionFlight)this).worldLifecycleManager$provenance(new net.minecraft.nbt.CompoundTag());
 }
}
