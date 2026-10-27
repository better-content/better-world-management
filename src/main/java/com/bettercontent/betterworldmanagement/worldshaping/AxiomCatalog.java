package com.bettercontent.betterworldmanagement.worldshaping;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashMap;

/** Closed, independent, one-point world laws. Deliberately not PrestigePerks/class progression. */
public final class AxiomCatalog {
    public record Node(String id, String label, int branch, String description, String configuration) {}
    private static final List<String> BRANCHES = List.of("Industry", "Living Legacy", "Discovery", "Community", "Arcana", "Worldshaping");
    private static final List<Node> NODES = List.of(
        new Node("iron_law", "Iron Law", 0, "Turn Hotstone, Black Shale, sand and gravel into refractory mix. Fire each portion into a Seared Brick.", ""),
        new Node("eternal_engine", "Eternal Engine", 0, "An alternate small water wheel uses eight planks around Andesite Alloy. Native stress and all other progression gates remain.", ""),
        new Node("alloy_testament", "Alloy Testament", 0, "Cast brass from copper and consumed zinc in a native Tinkers' casting basin. Nothing creates free alloy or reusable zinc.", ""),
        new Node("captive_hearth", "Captive Hearth", 0, "Build the existing Thermal Firebox with eight bricks around a furnace; native heat, pipes and fuel still apply.", ""),
        new Node("ark", "The Ark", 1, "Nominate one adult male and female Genetic Animals cattle pair. Only species, genes and sex survive; healthy new adults await a communal claim.", "nomination"),
        new Node("seed_covenant", "Seed Covenant", 1, "Nominate a supported wheat, rice, cabbage or tomato cultivar. One communal claim yields four planting items, never protected wild forage.", "nomination"),
        new Node("patient_hive", "Patient Hive", 1, "Nominate one Complicated Bees princess and drone. Native validated genomes survive, but queens and mating state do not.", "nomination"),
        new Node("familiars_name", "A Familiar's Name", 1, "Carry an owned vanilla wolf's owner, name and collar into a healthy new companion. No old equipment, wounds or buffs survive.", "nomination"),
        new Node("cartographers_memory", "Cartographer's Memory", 2, "Remember actual entry into a monument, mansion, mineshaft or fortress. Commission a successor map for eight paper and a compass; charge only on success.", "map"),
        new Node("font_atlas", "Font Atlas", 2, "Commission maps to already indexed natural Fonts at their existing authored prices. No extra chunk generation or invented locations.", "font"),
        new Node("prospectors_eye", "Prospector's Eye", 2, "Survey loaded surface samples through a held spyglass within sixteen blocks and four blocks vertically. Bounded work; no underground ore revelation.", ""),
        new Node("celestial_almanac", "Celestial Almanac", 2, "Read the next natural rare celestial night and its saved date without changing the calendar.", ""),
        new Node("communal_archive", "Communal Archive", 3, "Raise one safe nine-by-nine monument near successor spawn. Preserve a short plaque and up to four books, each four short pages.", "archive"),
        new Node("fortified_beginnings", "Fortified Beginnings", 3, "Eligible villages bypass the ordinary wall chance. Native terrain, style, spacing and rejection rules still decide where walls can exist.", ""),
        new Node("settled_arrival", "Settled Arrival", 3, "Require a safe arrival within sixty-four blocks of a genuine village in the selected biome. An unsuitable successor fails health instead of lying.", ""),
        new Node("founders_exchange", "Founders' Exchange", 3, "Choose the first scheduled trader's economy identity. Its date, prices, stock, camp and every later visit remain native.", "currency"),
        new Node("sacrificial_tradition", "Sacrificial Tradition", 4, "Make the native sacrifice dagger at altar tier II, retaining the diamond sword, 120000 LP and effective processing rates.", ""),
        new Node("spirit_awakening", "Spirit Awakening", 4, "An alternate Spirit Altar replaces the initial slate gate with native Wicked Spirits and retains a Black Shale mineral gate.", ""),
        new Node("chalk_covenant", "Chalk Covenant", 4, "Heat a white impure chalk alternative from burnt otherstone, ashes, Black Shale, amethyst, Sacred and Wicked Spirits, and water. Later chalk stays native.", ""),
        new Node("patient_alchemist", "Patient Alchemist", 4, "Prepare redstone, glowstone and two amethyst shards; mix with the original Aether minerals in water for moon dust without waiting for a full moon.", ""),
        new Node("shaped_horizon", "Shaped Horizon", 5, "Choose ordinary or native Large Biomes for the successor before world creation. No custom terrain algorithm or retroactive world edits.", "preset"),
        new Node("turning_year", "Turning Year", 5, "Begin the successor at early Spring, Summer, Autumn or Winter once. Thereafter the native seasons advance normally.", "season"),
        new Node("deep_compact", "Deep Compact", 5, "Move one supported Overworld home/echo deposit family's height band to shallow 0..80 or deep -48..16; preserve counts, budgets and morphology.", "deposit"),
        new Node("chosen_omen", "Chosen Omen", 5, "Choose the first natural celestial event by reordering its initial bag. Preserve the scheduled date and every occurrence; no forced extra night.", "event")
    );
    private static final Map<String, Node> BY_ID;
    static {
        Map<String, Node> values = new LinkedHashMap<>();
        for (Node node : NODES) if (values.put(node.id(), node) != null) throw new ExceptionInInitializerError("Duplicate axiom");
        BY_ID = Map.copyOf(values);
    }
    private AxiomCatalog() {}
    public static List<Node> nodes() { return NODES; }
    public static List<String> branches() { return BRANCHES; }
    public static Node node(String id) {
        Node value = BY_ID.get(id);
        if (value == null) throw new IllegalArgumentException("Unknown World Axiom: " + id);
        return value;
    }
    public static int budget(long baseGeneration) {
        if (baseGeneration < 0 || baseGeneration == Long.MAX_VALUE) throw new IllegalArgumentException("Invalid generation");
        return (int) Math.min(24L, baseGeneration + 1L);
    }
    public static Set<String> inheritance() { return Set.of("ark", "seed_covenant", "patient_hive", "familiars_name"); }
}
