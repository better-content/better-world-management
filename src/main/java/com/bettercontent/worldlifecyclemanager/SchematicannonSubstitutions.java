package com.bettercontent.worldlifecyclemanager;

import com.simibubi.create.content.schematics.cannon.SchematicannonBlockEntity;
import com.simibubi.create.content.schematics.requirement.ItemRequirement;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SchematicannonSubstitutions {
    public static final int MAX_RULES = 64;
    public static final int MAX_ROWS = 256;

    public record Row(ResourceLocation source, int required, int available, ResourceLocation target,
                      int fallbackAvailable, int fallbackNeeded, int covered, int uncovered) {}
    record FallbackDemand(ResourceLocation source, ResourceLocation target, int shortage) {}
    record FallbackAllocation(int available, int covered) {}

    private SchematicannonSubstitutions() {}

    public static boolean eligible(BlockState state) {
        if (state == null || state.isAir() || !state.getFluidState().isEmpty() || state.hasBlockEntity()) return false;
        if (!(state.getBlock().asItem() instanceof BlockItem)) return false;
        return simpleRequirement(ItemRequirement.of(state, null)) != null;
    }

    public static ItemRequirement.StackRequirement simpleRequirement(ItemRequirement requirement) {
        if (requirement == null || requirement.isEmpty() || requirement.isInvalid()) return null;
        List<ItemRequirement.StackRequirement> required = requirement.getRequiredItems();
        if (required.size() != 1) return null;
        ItemRequirement.StackRequirement stack = required.get(0);
        if (stack.usage != ItemRequirement.ItemUseType.CONSUME || stack.stack.isEmpty()
                || stack.stack.getCount() != 1 || !(stack.stack.getItem() instanceof BlockItem)) return null;
        return stack;
    }

    public static BlockState replacementState(BlockState source, Block target) {
        BlockState result = target.defaultBlockState();
        for (Property<?> sourceProperty : source.getProperties()) {
            Property<?> targetProperty = result.getBlock().getStateDefinition().getProperty(sourceProperty.getName());
            if (targetProperty == null) continue;
            result = copyProperty(source, result, sourceProperty, targetProperty);
        }
        return result;
    }

    private static <S extends Comparable<S>, T extends Comparable<T>> BlockState copyProperty(
            BlockState source, BlockState target, Property<S> sourceProperty, Property<T> targetProperty) {
        String value = sourceProperty.getName(source.getValue(sourceProperty));
        return targetProperty.getValue(value).map(parsed -> target.setValue(targetProperty, parsed)).orElse(target);
    }

    public static boolean available(SchematicannonBlockEntity cannon, ItemRequirement.StackRequirement requirement) {
        return availableCount(cannon, requirement) >= requirement.stack.getCount();
    }

    public static int availableCount(SchematicannonBlockEntity cannon, ItemRequirement.StackRequirement requirement) {
        cannon.findInventories();
        int found = 0;
        for (var optional : cannon.attachedInventories) {
            IItemHandler inventory = optional.orElse(null);
            if (inventory == null) continue;
            for (int slot = 0; slot < inventory.getSlots(); slot++) {
                ItemStack extracted = inventory.extractItem(slot, Integer.MAX_VALUE, true);
                if (requirement.matches(extracted)) found += extracted.getCount();
            }
        }
        return found;
    }

    public static int nativeRequirement(SchematicannonBlockEntity cannon, Item item) {
        return cannon.checklist.required.getOrDefault(item, 0);
    }

    public static List<Row> evaluate(SchematicannonBlockEntity cannon,
                                     Map<ResourceLocation, ResourceLocation> substitutions) {
        Map<Item, Integer> required = new LinkedHashMap<>();
        cannon.checklist.required.forEach((item, count) -> required.put(item, count.intValue()));
        Map<Item, Integer> gathered = new LinkedHashMap<>();
        cannon.checklist.gathered.forEach((item, count) -> gathered.put(item, count.intValue()));
        List<PendingRow> pending = new ArrayList<>();
        required.entrySet().stream().sorted(Comparator.comparing(entry -> BuiltInRegistries.ITEM.getKey(entry.getKey()).toString()))
                .limit(MAX_ROWS).forEach(entry -> {
                    if (!(entry.getKey() instanceof BlockItem sourceItem)) return;
                    BlockState sourceState = sourceItem.getBlock().defaultBlockState();
                    if (!eligible(sourceState)) return;
                    ResourceLocation source = BuiltInRegistries.BLOCK.getKey(sourceItem.getBlock());
                    int need = entry.getValue();
                    int have = gathered.getOrDefault(entry.getKey(), 0);
                    int shortage = Math.max(0, need - have);
                    ResourceLocation target = substitutions.get(source);
                    pending.add(new PendingRow(source, need, have, target, shortage));
                });

        Map<ResourceLocation, Integer> surplus = new LinkedHashMap<>();
        for (PendingRow row : pending) {
            if (row.target == null || surplus.containsKey(row.target)) continue;
            Block targetBlock = BuiltInRegistries.BLOCK.get(row.target);
            Item targetItem = targetBlock.asItem();
            int targetNativeNeed = required.getOrDefault(targetItem, 0);
            surplus.put(row.target, Math.max(0, gathered.getOrDefault(targetItem, 0) - targetNativeNeed));
        }
        List<FallbackDemand> demands = pending.stream().filter(row -> row.target != null)
                .map(row -> new FallbackDemand(row.source, row.target, row.shortage)).toList();
        Map<ResourceLocation, FallbackAllocation> allocated = allocateFallbackCoverage(surplus, demands);
        List<Row> rows = new ArrayList<>();
        for (PendingRow row : pending) {
            FallbackAllocation allocation = allocated.get(row.source);
            int fallbackAvailable = allocation == null ? 0 : allocation.available;
            int covered = allocation == null ? 0 : allocation.covered;
            rows.add(new Row(row.source, row.need, row.have, row.target, fallbackAvailable, row.shortage,
                    covered, row.shortage - covered));
        }
        return List.copyOf(rows);
    }

    /** Allocates each target's native-reserved surplus once, in stable source-ID order. */
    static Map<ResourceLocation, FallbackAllocation> allocateFallbackCoverage(Map<ResourceLocation, Integer> surplus,
                                                                                List<FallbackDemand> demands) {
        Map<ResourceLocation, Integer> remaining = new LinkedHashMap<>(surplus);
        Map<ResourceLocation, FallbackAllocation> result = new LinkedHashMap<>();
        demands.stream().sorted(Comparator.comparing(demand -> demand.source().toString())).forEach(demand -> {
            int available = Math.max(0, remaining.getOrDefault(demand.target(), 0));
            int covered = Math.min(Math.max(0, demand.shortage()), available);
            remaining.put(demand.target(), available - covered);
            result.put(demand.source(), new FallbackAllocation(available, covered));
        });
        return Map.copyOf(result);
    }

    private record PendingRow(ResourceLocation source, int need, int have, ResourceLocation target, int shortage) {}

    public static void validateRule(ResourceLocation sourceId, ResourceLocation targetId,
                                    Map<ResourceLocation, ResourceLocation> existing) {
        if (sourceId == null || targetId == null || sourceId.equals(targetId)) {
            throw new IllegalArgumentException("substitution source and target must be different registered blocks");
        }
        if (!BuiltInRegistries.BLOCK.containsKey(sourceId) || !BuiltInRegistries.BLOCK.containsKey(targetId)) {
            throw new IllegalArgumentException("substitution contains an unregistered block");
        }
        BlockState source = BuiltInRegistries.BLOCK.get(sourceId).defaultBlockState();
        BlockState target = replacementState(source, BuiltInRegistries.BLOCK.get(targetId));
        if (!eligible(source) || !eligible(target)) throw new IllegalArgumentException("substitution requires ordinary single-item blocks");
        ResourceLocation cursor = targetId;
        for (int depth = 0; depth <= MAX_RULES; depth++) {
            if (cursor.equals(sourceId)) throw new IllegalArgumentException("substitution cycles are not allowed");
            cursor = existing.get(cursor);
            if (cursor == null) return;
        }
        throw new IllegalArgumentException("substitution chain exceeds the rule limit");
    }
}
