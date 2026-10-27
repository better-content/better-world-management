package com.bettercontent.betterworldmanagement.worldshaping.inheritance;

import mokiyoki.enhancedanimals.config.GeneticAnimalsConfig;
import mokiyoki.enhancedanimals.entity.EnhancedCow;
import mokiyoki.enhancedanimals.util.Genes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.Arrays;
import java.util.Set;
import static com.bettercontent.betterworldmanagement.worldshaping.inheritance.InheritanceAdapters.*;

/** Loaded only after the pinned eanimod adapter has been selected. No copied entity NBT. */
final class CattleInheritance {
    private static final ResourceLocation COW = new ResourceLocation("eanimod", "enhanced_cow");
    private CattleInheritance() {}

    static CompoundTag capture(Entity entity) {
        require(entity != null && entity.isAlive() && COW.equals(ForgeRegistries.ENTITY_TYPES.getKey(entity.getType())) && entity.getClass() == EnhancedCow.class, "Look at supported living genetic cattle");
        EnhancedCow cow = (EnhancedCow) entity;
        require(!cow.isBaby(), "Ark requires adult cattle");
        CompoundTag out = new CompoundTag();
        out.putUUID("source", cow.getUUID());
        out.putBoolean("female", cow.getOrSetIsFemale());
        out.putIntArray("sexlinked", cow.getGenes().getSexlinkedGenes().clone());
        out.putIntArray("autosomal", cow.getGenes().getAutosomalGenes().clone());
        validate(out);
        return out;
    }

    static void validate(CompoundTag data) {
        keys(data, Set.of("source", "female", "sexlinked", "autosomal"));
        require(data.hasUUID("source"), "Cattle source UUID required");
        typed(data, "female", Tag.TAG_BYTE);
        require(data.getByte("female") == 0 || data.getByte("female") == 1, "Invalid cattle sex");
        typed(data, "sexlinked", Tag.TAG_INT_ARRAY); typed(data, "autosomal", Tag.TAG_INT_ARRAY);
        int[] sex = data.getIntArray("sexlinked"), auto = data.getIntArray("autosomal");
        require(sex.length == 2 && auto.length == 254, "Pinned cattle genome lengths required");
        require(Arrays.stream(sex).allMatch(i -> i > 0 && i <= 100000) && Arrays.stream(auto).allMatch(i -> i > 0 && i <= 100000), "Incomplete or unbounded cattle alleles");
        Genes genes = new Genes(sex, auto);
        require(genes.isValid() && genes.isComplete(), "Native cattle genome invalid");
    }

    static EnhancedCow restore(ServerLevel level, CompoundTag data) {
        validate(data);
        var type = ForgeRegistries.ENTITY_TYPES.getValue(COW);
        require(type != null, "Pinned cattle entity unavailable");
        Entity entity = type.create(level);
        require(entity != null && entity.getClass() == EnhancedCow.class, "Pinned cattle entity mismatch");
        EnhancedCow cow = (EnhancedCow) entity;
        Genes genes = new Genes(data.getIntArray("sexlinked"), data.getIntArray("autosomal"));
        cow.setGenes(genes);
        cow.setBirthTime(level, -GeneticAnimalsConfig.COMMON.adultAgeCow.get());
        // The native setter for sex is protected. A fresh native save skeleton is the public
        // deserialization boundary; it contains NO old-world inventory/effects/pregnancy/NBT.
        CompoundTag fresh = new CompoundTag();
        cow.addAdditionalSaveData(fresh);
        fresh.putBoolean("IsFemale", data.getBoolean("female"));
        cow.readAdditionalSaveData(fresh);
        cow.setGenes(genes);
        cow.setSharedGenes(genes);
        cow.setInitialDefaults();
        cow.setHealth(cow.getMaxHealth());
        require(!cow.isBaby() && cow.getOrSetIsFemale() == data.getBoolean("female"), "Native cattle age/sex restoration failed");
        require(matches(cow, data), "Native cattle genome restoration failed");
        return cow;
    }

    static boolean matches(Entity entity, CompoundTag data) {
        if (entity == null || entity.getClass() != EnhancedCow.class) return false;
        EnhancedCow cow = (EnhancedCow) entity;
        return cow.getOrSetIsFemale() == data.getBoolean("female")
            && Arrays.equals(cow.getGenes().getSexlinkedGenes(), data.getIntArray("sexlinked"))
            && Arrays.equals(cow.getGenes().getAutosomalGenes(), data.getIntArray("autosomal"));
    }
}
