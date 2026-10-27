package com.bettercontent.betterworldmanagement.worldshaping.client;

import org.junit.jupiter.api.Test;

/** Includes the dependency-free exhaustive geometry sweep in the ordinary JUnit suite. */
final class AxiomLayoutTest {
    @Test void allSupportedViewportsKeepIndependentNodesInsideTheirPanel() {
        AxiomLayoutPureTest.main(new String[0]);
    }
}
