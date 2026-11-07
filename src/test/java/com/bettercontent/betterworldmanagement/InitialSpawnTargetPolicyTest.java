package com.bettercontent.betterworldmanagement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class InitialSpawnTargetPolicyTest {
    @Test void vanillaSingleplayerNamesKeepSpacesAndUnicode() {
        assertTrue(InitialSpawnTargetPolicy.singleplayerFolder("New World"));
        assertTrue(InitialSpawnTargetPolicy.singleplayerFolder("Friends' World (2)"));
        assertTrue(InitialSpawnTargetPolicy.singleplayerFolder("草原の世界"));
    }

    @Test void traversalAndReceiptControlCharactersAreRejected() {
        for (String name : new String[] {"", "  ", ".", "..", "../world", "world/next",
                "world\\next", "world\tother", "world\nother", "world\0next"}) {
            assertFalse(InitialSpawnTargetPolicy.singleplayerFolder(name), name);
        }
    }
}
