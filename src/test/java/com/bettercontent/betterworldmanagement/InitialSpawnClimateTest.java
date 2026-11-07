package com.bettercontent.betterworldmanagement;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InitialSpawnClimateTest {
    @Test void includesBothColdSweatHabitableBounds() {
        var bounds = new InitialSpawnClimate.Bounds(0.4, 1.511111111111111);
        assertTrue(bounds.contains(0.4));
        assertTrue(bounds.contains(1.511111111111111));
        assertTrue(bounds.contains(0.8));
        assertFalse(bounds.contains(Math.nextDown(0.4)));
        assertFalse(bounds.contains(Math.nextUp(1.511111111111111)));
    }

    @Test void blacklistsTaggedOceansEvenWhenTheirBaseTemperatureQualifies() {
        var bounds = new InitialSpawnClimate.Bounds(0.4, 1.511111111111111);
        assertTrue(bounds.accepts(0.5, false, false));
        assertFalse(bounds.accepts(0.5, true, false)); // deep frozen and warm oceans both report 0.5
        assertFalse(bounds.accepts(0.8, false, true)); // optional administrator ID blacklist
        assertFalse(bounds.accepts(0.3, false, false));
        assertTrue(bounds.accepts(0.4, false, false));
        assertTrue(bounds.accepts(1.511111111111111, false, false));
    }

    @Test void parsesExtraBiomeBlacklistWithoutChangingOceanTagRule() {
        assertEquals(ResourceLocation.tryParse("minecraft:river"),
                InitialSpawnClimate.parseBlacklist(List.of("# Ocean biomes are always excluded", "", " minecraft:river "))
                        .iterator().next());
        assertThrows(IllegalArgumentException.class,
                () -> InitialSpawnClimate.parseBlacklist(List.of("minecraft:river", "minecraft:river")));
        assertThrows(IllegalArgumentException.class,
                () -> InitialSpawnClimate.parseBlacklist(List.of("not a biome ID")));
    }

    @Test void rejectsInvalidRangesAndNonFiniteBiomes() {
        assertThrows(IllegalStateException.class, () -> new InitialSpawnClimate.Bounds(2, 1));
        assertThrows(IllegalStateException.class, () -> new InitialSpawnClimate.Bounds(Double.NaN, 1));
        assertThrows(IllegalStateException.class, () -> new InitialSpawnClimate.Bounds(0, Double.POSITIVE_INFINITY));
        var bounds = new InitialSpawnClimate.Bounds(0.4, 1.511111111111111);
        assertFalse(bounds.contains(Double.NaN));
        assertFalse(bounds.contains(Double.POSITIVE_INFINITY));
    }
}
