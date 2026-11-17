package com.bettercontent.betterworldmanagement;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

final class InitialSpawnNeighborhoodTest {
    @Test void samplesAllNineChunksOnceWithTheExactSpawnInTheMiddle() {
        BlockPos spawn = new BlockPos(128, 72, -1);
        var samples = InitialSpawnNeighborhood.qualify(spawn, point -> point);
        assertEquals(9, samples.size());
        assertEquals(spawn, samples.get(4));
        assertEquals(new BlockPos(120, 72, -24), samples.get(0));
        assertEquals(new BlockPos(152, 72, 8), samples.get(8));
        var chunks = new HashSet<String>();
        samples.forEach(point -> chunks.add(Math.floorDiv(point.getX(), 16) + ","
                + Math.floorDiv(point.getZ(), 16)));
        assertEquals(9, chunks.size(), "one sample in each distinct chunk, including negative coordinates");
    }

    @Test void v4ReceiptRejectsAChangedNeighborOrCenterWithoutAWorldReset() {
        BlockPos spawn = new BlockPos(128, 72, -1);
        var bounds = new InitialSpawnClimate.Bounds(0.4, 1.5);
        var rows = new ArrayList<>(List.of(
                "BC_INITIAL_SPAWN_ATTEMPT_V4", "attempt\trun-1", "target\tworld",
                "number\t1", "seed\t17", "status\tresolved", "provenance\trandom",
                "spawn\t128\t72\t-1", "biome\tminecraft:plains",
                "base_temp_mc\t0.8", "min_temp_mc\t0.4", "max_temp_mc\t1.5"));
        for (int i = 0; i < InitialSpawnNeighborhood.SAMPLE_COUNT; i++) {
            BlockPos point = InitialSpawnNeighborhood.sample(spawn, i);
            rows.add("sample\t" + i + "\t" + point.getX() + "\t" + point.getY()
                    + "\t" + point.getZ() + "\tminecraft:plains\t0.8");
        }
        assertTrue(InitialSpawnCreation.validNeighborhoodReceipt(rows, spawn, bounds));
        String neighbor = rows.get(20);
        rows.set(20, neighbor.replace("sample\t8\t152", "sample\t8\t120"));
        assertFalse(InitialSpawnCreation.validNeighborhoodReceipt(rows, spawn, bounds));
        rows.set(20, neighbor);
        rows.set(16, rows.get(16).replace("minecraft:plains", "minecraft:river"));
        assertFalse(InitialSpawnCreation.validNeighborhoodReceipt(rows, spawn, bounds));
    }

    @Test void oneIneligibleNeighborRejectsTheWholeNeighborhood() {
        BlockPos spawn = new BlockPos(128, 72, -1);
        assertTrue(InitialSpawnNeighborhood.qualify(spawn,
                point -> point.equals(new BlockPos(152, 72, 8)) ? null : point).isEmpty());
        var calls = new AtomicInteger();
        assertTrue(InitialSpawnNeighborhood.qualify(spawn, point -> {
            calls.incrementAndGet();
            return point.equals(spawn) ? null : point;
        }).isEmpty());
        assertEquals(1, calls.get(), "an invalid center must not trigger eight expensive height lookups");
    }
}
