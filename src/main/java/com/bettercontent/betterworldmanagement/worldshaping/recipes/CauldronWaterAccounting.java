package com.bettercontent.betterworldmanagement.worldshaping.recipes;

/** Pure missing-consumption calculation; never creates fluid or repairs overdrain. */
public final class CauldronWaterAccounting {
    private CauldronWaterAccounting() {}

    public static int missingDrain(int before, int after, int required) {
        if (required <= 0 || before < required || after > before || after < before - required) {
            throw new IllegalArgumentException("Unexpected native cauldron water transition: "
                    + before + " -> " + after + "; required=" + required);
        }
        return after - (before - required);
    }
}
