package com.bettercontent.betterworldmanagement.worldshaping.inheritance;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class CattleAlleleRangesTest {
    @Test void naturalProviderAllelesAtExactlyTwoWideAutosomalLoci() {
        assertTrue(CattleAlleleRanges.autosomal(94, 244349));
        assertTrue(CattleAlleleRanges.autosomal(95, 244349));
        assertTrue(CattleAlleleRanges.autosomal(94, 999999));
        assertTrue(CattleAlleleRanges.autosomal(95, 999999));
        assertTrue(CattleAlleleRanges.autosomal(93, 100000));
        assertTrue(CattleAlleleRanges.autosomal(96, 100000));
        for (int index = 0; index < 254; index++)
            if (index != 94 && index != 95)
                assertFalse(CattleAlleleRanges.autosomal(index, 100001), "unexpected widened locus " + index);
    }

    @Test void incompleteAndOutOfDomainAllelesRemainRejected() {
        for (int index = 0; index < 254; index++) {
            assertFalse(CattleAlleleRanges.autosomal(index, 0));
            assertFalse(CattleAlleleRanges.autosomal(index, -1));
        }
        assertFalse(CattleAlleleRanges.autosomal(94, 1000000));
        assertFalse(CattleAlleleRanges.autosomal(95, Integer.MAX_VALUE));
        assertFalse(CattleAlleleRanges.autosomal(-1, 1));
        assertFalse(CattleAlleleRanges.autosomal(254, 1));
        assertTrue(CattleAlleleRanges.sexlinked(100000));
        assertFalse(CattleAlleleRanges.sexlinked(0));
        assertFalse(CattleAlleleRanges.sexlinked(100001));
    }
}
