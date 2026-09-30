package com.bettercontent.betterworldmanagement;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;

public interface SchematicannonSubstitutionAccess {
    Map<ResourceLocation, ResourceLocation> worldLifecycleManager$substitutions();
    void worldLifecycleManager$setSubstitution(ResourceLocation source, ResourceLocation target);
    void worldLifecycleManager$author(ResourceLocation source, java.util.UUID owner);
    java.util.UUID worldLifecycleManager$author(ResourceLocation source);
    void worldLifecycleManager$clearSubstitution(ResourceLocation source);
    void worldLifecycleManager$clearSubstitutions();
}
