package com.bettercontent.betterworldmanagement;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class IncrementalLandingSearchTest {
    @Test void eachAdvanceHonorsItsProbeBudgetAndEventuallyExhausts() {
        var search = new IncrementalLandingSearch(List.of("minecraft:plains"), 10, 20, 4, 4, 20);
        var seen = new ArrayList<IncrementalLandingSearch.Probe>();

        var first = search.advance(3, probe -> { seen.add(probe); return false; });
        assertFalse(first.exhausted());
        assertEquals(3, seen.size());

        var second = search.advance(6, probe -> { seen.add(probe); return false; });
        assertTrue(second.exhausted());
        assertEquals(9, seen.size());
        assertEquals(9, seen.stream().distinct().count());
    }

    @Test void nearerRingsAreVisitedBeforeFartherCoordinates() {
        var search = new IncrementalLandingSearch(List.of("minecraft:plains"), 10, 20, 4, 4, 20);
        var seen = new ArrayList<IncrementalLandingSearch.Probe>();
        search.advance(9, probe -> { seen.add(probe); return false; });

        assertEquals(new IncrementalLandingSearch.Probe("minecraft:plains", 10, 20), seen.get(0));
        assertEquals(List.of(
                new IncrementalLandingSearch.Probe("minecraft:plains", 6, 16),
                new IncrementalLandingSearch.Probe("minecraft:plains", 10, 16),
                new IncrementalLandingSearch.Probe("minecraft:plains", 14, 16)), seen.subList(1, 4));
    }

    @Test void laterPreferenceStartsOnlyAfterEarlierPreferenceIsExhausted() {
        var search = new IncrementalLandingSearch(
                List.of("minecraft:plains", "minecraft:forest"), 0, 0, 4, 4, 20);
        var seen = new ArrayList<IncrementalLandingSearch.Probe>();

        var result = search.advance(20, probe -> {
            seen.add(probe);
            return probe.preference().equals("minecraft:forest");
        });

        assertNotNull(result.candidate());
        assertEquals("minecraft:forest", result.candidate().preference());
        assertEquals(10, seen.size());
        assertTrue(seen.subList(0, 9).stream().allMatch(probe -> probe.preference().equals("minecraft:plains")));
    }

    @Test void cancellationStopsWithoutCallingTheProbe() {
        var search = new IncrementalLandingSearch(List.of("minecraft:plains"), 0, 0, 4, 4, 20);
        search.cancel();

        var result = search.advance(9, probe -> { fail("cancelled search must not probe"); return false; });
        assertTrue(result.cancelled());
        assertFalse(result.exhausted());
        assertNull(result.candidate());
    }

    @Test void totalTickDeadlineExpiresEvenWhenNoProbeCanAdvance() {
        var search = new IncrementalLandingSearch(List.of("minecraft:plains"), 0, 0, 4, 4, 3);

        assertFalse(search.deadlineReached());
        assertFalse(search.deadlineReached());
        assertTrue(search.deadlineReached());
        assertTrue(search.deadlineReached());
        assertTrue(search.advance(1, probe -> fail("expired search must not probe")).cancelled());
    }
}
