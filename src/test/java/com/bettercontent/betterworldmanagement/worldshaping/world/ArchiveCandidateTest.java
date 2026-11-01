package com.bettercontent.betterworldmanagement.worldshaping.world;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashSet;
import org.junit.jupiter.api.Test;

class ArchiveCandidateTest {
    @Test void expandedSearchIsFiniteUniqueAndWithinSpawnBoundary() {
        var seen = new HashSet<String>();
        for (int index = 0; index < 800; index++) {
            int[] position = ArchiveText.candidate(index);
            assertTrue(Math.abs(position[0]) <= 64 && Math.abs(position[1]) <= 64);
            assertTrue(seen.add(position[0] + "," + position[1]), "duplicate candidate " + index);
        }
        assertThrows(IllegalArgumentException.class, () -> ArchiveText.candidate(-1));
        assertThrows(IllegalArgumentException.class, () -> ArchiveText.candidate(800));
    }
}
