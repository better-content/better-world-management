package com.bettercontent.betterworldmanagement.worldshaping;

import org.junit.jupiter.api.Test;

final class AxiomProjectionTest {
    @Test void startupProjectionCannotDisagreeWithImmutableWorldOrEnableAnEmptyWorld() {
        AxiomProjectionPureTest.main(new String[0]);
    }
}
