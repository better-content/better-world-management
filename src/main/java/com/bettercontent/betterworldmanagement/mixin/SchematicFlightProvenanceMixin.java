package com.bettercontent.betterworldmanagement.mixin;
import com.bettercontent.betterworldmanagement.SchematicSubstitutionFlight;
import com.simibubi.create.content.schematics.cannon.LaunchedItem;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(value=LaunchedItem.class,remap=false)
public abstract class SchematicFlightProvenanceMixin implements SchematicSubstitutionFlight {
 @Unique private CompoundTag worldLifecycleManager$provenance=new CompoundTag();
 public void worldLifecycleManager$provenance(CompoundTag tag){worldLifecycleManager$provenance=tag.copy();}
 public CompoundTag worldLifecycleManager$provenance(){return worldLifecycleManager$provenance;}
 @Inject(method="serializeNBT",at=@At("RETURN"))private void write(CallbackInfoReturnable<CompoundTag> cir){if(!worldLifecycleManager$provenance.isEmpty())cir.getReturnValue().put("WorldLifecycleSubstitution",worldLifecycleManager$provenance.copy());}
 @Inject(method="readNBT",at=@At("RETURN"))private void read(CompoundTag tag,net.minecraft.core.HolderGetter<net.minecraft.world.level.block.Block> blocks,CallbackInfo ci){worldLifecycleManager$provenance=tag.getCompound("WorldLifecycleSubstitution").copy();}
}
