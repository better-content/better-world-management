package com.bettercontent.betterworldmanagement.worldshaping.inheritance;

/** Strict allele ranges for the pinned Genetic Animals 0_11_12 cow genome.
 * That provider generates autosomal loci 94/95 in 1..999999 or as 244349;
 * every other locus retains the original 1..100000 bound. No NBT values are normalized.
 */
final class CattleAlleleRanges {
    private CattleAlleleRanges() {}

    static boolean sexlinked(int allele) {
        return allele > 0 && allele <= 100000;
    }

    static boolean autosomal(int index, int allele) {
        return index >= 0 && index < 254 && allele > 0
                && allele <= (index == 94 || index == 95 ? 999999 : 100000);
    }
}
