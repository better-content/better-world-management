package com.bettercontent.betterworldmanagement.worldshaping;

import org.junit.jupiter.api.Test;

/** Runs the exact standalone production contract qualification in the ordinary test task. */
final class AxiomContractsTest {
    @Test void productionContractsAreClosedBoundedImmutableAndRoundTrip() throws Exception {
        AxiomContractsPureTest.main(new String[0]);
    }
}
