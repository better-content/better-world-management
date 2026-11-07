package com.bettercontent.betterworldmanagement;

import com.momosoftworks.coldsweat.config.ConfigSettings;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.fml.ModList;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Initial shared-spawn climate bounds and its ocean/administrator biome blacklist. */
final class InitialSpawnClimate {
    private static final String BLACKLIST = "better_world_management-initial-spawn-blacklist.txt";

    record Bounds(double min, double max, Set<ResourceLocation> denied) {
        Bounds(double min, double max) { this(min, max, Set.of()); }
        Bounds {
            if (!Double.isFinite(min) || !Double.isFinite(max) || min > max)
                throw new IllegalStateException("Invalid Cold Sweat habitable temperature settings");
            denied = Set.copyOf(denied);
        }
        boolean contains(double base) { return Double.isFinite(base) && base >= min && base <= max; }
        boolean accepts(double base, boolean ocean, boolean listed) {
            return contains(base) && !ocean && !listed;
        }
        boolean accepts(Holder<Biome> biome) {
            double base = baseTemperature(biome);
            return accepts(base, biome.is(BiomeTags.IS_OCEAN),
                    biome.unwrapKey().map(key -> denied.contains(key.location())).orElse(true));
        }
    }

    private InitialSpawnClimate() {}

    static Bounds loadedBounds(MinecraftServer server) {
        if (!ModList.get().isLoaded("cold_sweat"))
            throw new IllegalStateException("Cold Sweat is required for parametric initial-spawn selection");
        return new Bounds(ConfigSettings.MIN_TEMP.get(), ConfigSettings.MAX_TEMP.get(), readBlacklist(server));
    }

    private static Set<ResourceLocation> readBlacklist(MinecraftServer server) {
        Path file = server.getServerDirectory().toPath().toAbsolutePath().normalize()
                .resolve("config").resolve(BLACKLIST);
        if (Files.isSymbolicLink(file)) throw new IllegalStateException("Unsafe initial-spawn biome blacklist");
        if (Files.notExists(file)) return Set.of(); // The ocean tag remains denied without a pack config.
        if (!Files.isRegularFile(file)) throw new IllegalStateException("Initial-spawn biome blacklist is not a file");
        try {
            if (Files.size(file) > 65_536) throw new IllegalStateException("Initial-spawn biome blacklist is too large");
            return parseBlacklist(Files.readAllLines(file, StandardCharsets.UTF_8));
        } catch (IOException error) {
            throw new IllegalStateException("Could not read initial-spawn biome blacklist", error);
        }
    }

    static Set<ResourceLocation> parseBlacklist(List<String> lines) {
        if (lines.size() > 256) throw new IllegalArgumentException("Initial-spawn biome blacklist has too many rows");
        Set<ResourceLocation> denied = new HashSet<>();
        for (String line : lines) {
            String value = line.trim();
            if (value.isEmpty() || value.startsWith("#")) continue;
            ResourceLocation id = ResourceLocation.tryParse(value);
            if (id == null || !denied.add(id))
                throw new IllegalArgumentException("Invalid or duplicate initial-spawn biome blacklist entry: " + value);
        }
        return Set.copyOf(denied);
    }

    static double baseTemperature(Holder<Biome> biome) {
        if (biome == null || biome.unwrapKey().isEmpty())
            throw new IllegalStateException("Initial-spawn biome is not registered");
        return biome.value().getBaseTemperature();
    }
}
