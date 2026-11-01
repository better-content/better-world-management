package com.bettercontent.betterworldmanagement.worldshaping;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Closed bounded operator-authored values. Native item/entity NBT never enters this channel. */
public final class AxiomConfiguration {
    private AxiomConfiguration() {}
    public static Map<String, String> defaults(String id) {
        return switch (id) {
            case "shaped_horizon" -> Map.of("preset", "ordinary");
            case "turning_year" -> Map.of("season", "early_spring");
            case "deep_compact" -> Map.of("deposit", "ironstone", "band", "shallow");
            case "founders_exchange" -> Map.of("currency", "renewal");
            case "chosen_omen" -> Map.of("event", "thin_veil");
            case "communal_archive" -> Map.of("plaque", "Those who built before us are not forgotten.");
            case "cartographers_memory" -> Map.of("kind", "monument");
            case "font_atlas" -> Map.of("currency", "renewal");
            default -> { AxiomCatalog.node(id); yield Map.of(); }
        };
    }
    public static Map<String, String> validate(String id, Map<String, String> input) {
        AxiomCatalog.node(id);
        if (input.size() > 5) throw new IllegalArgumentException("Too many world-law options");
        TreeMap<String, String> result = new TreeMap<>(defaults(id)); result.putAll(input);
        Set<String> keys = switch (id) {
            case "shaped_horizon" -> Set.of("preset"); case "turning_year" -> Set.of("season");
            case "deep_compact" -> Set.of("deposit", "band"); case "founders_exchange" -> Set.of("currency");
            case "chosen_omen" -> Set.of("event"); case "communal_archive" -> Set.of("plaque", "book1", "book2", "book3", "book4");
            case "cartographers_memory" -> Set.of("kind"); case "font_atlas" -> Set.of("currency"); default -> Set.of();
        };
        if (!keys.containsAll(result.keySet())) throw new IllegalArgumentException("Unknown configuration field for " + id);
        for (var entry : result.entrySet()) if (entry.getValue() == null || entry.getValue().indexOf('\u0000') >= 0 || entry.getValue().length() > 1_027) throw new IllegalArgumentException("Oversized/invalid configuration value");
        switch (id) {
            case "shaped_horizon" -> choice(result, "preset", "ordinary", "large_biomes");
            case "turning_year" -> choice(result, "season", "early_spring", "early_summer", "early_autumn", "early_winter");
            case "deep_compact" -> { choice(result, "deposit", "ironstone", "copper_bloom", "tin_quartz", "brassroot"); choice(result, "band", "shallow", "deep"); }
            case "founders_exchange", "font_atlas" -> choice(result, "currency", "impact", "tempo", "work", "mobility", "endurance", "robustness", "renewal", "control");
            case "chosen_omen" -> choice(result, "event", "thin_veil", "spirit_fair", "wandering_lights", "open_ways");
            case "cartographers_memory" -> choice(result, "kind", "monument", "mansion", "mineshaft", "fortress");
            case "communal_archive" -> {
                if (result.get("plaque").length() > 256) throw new IllegalArgumentException("Archive plaque exceeds 256 characters");
                for (int i = 1; i <= 4; i++) {
                    String[] pages = result.getOrDefault("book" + i, "").split("\\n", -1);
                    if (pages.length > 4) throw new IllegalArgumentException("Archive book exceeds four pages");
                    for (String page : pages) if (page.length() > 256) throw new IllegalArgumentException("Archive page exceeds 256 characters");
                }
            }
            default -> {}
        }
        return Map.copyOf(result);
    }
    private static void choice(Map<String, String> config, String key, String... values) {
        if (!Set.of(values).contains(config.get(key))) throw new IllegalArgumentException("Unsupported " + key);
    }
}
