package com.bettercontent.betterworldmanagement.worldshaping;

import java.util.Set;
import net.minecraft.nbt.CompoundTag;

/** Active world laws and the independent successor draft are explicitly different projections. */
public record AxiomView(long generation, long revision, boolean operator, boolean locked,
                        Set<String> selected, CompoundTag configuration, String error, String forecast,
                        Set<String> active, CompoundTag activeConfiguration) {
    public AxiomView {
        selected = Set.copyOf(selected); active = Set.copyOf(active);
        configuration = configuration.copy(); activeConfiguration = activeConfiguration.copy();
    }
    public AxiomView(long generation, long revision, boolean operator, boolean locked, Set<String> selected,
                     CompoundTag configuration, String error, String forecast) {
        this(generation, revision, operator, locked, selected, configuration, error, forecast, Set.of(), new CompoundTag());
    }
    @Override public CompoundTag configuration() { return configuration.copy(); }
    @Override public CompoundTag activeConfiguration() { return activeConfiguration.copy(); }
    public int budget() { return AxiomCatalog.budget(generation); }
}
