package com.bettercontent.betterworldmanagement;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/** Accept vanilla's one-component save names without restricting them to dedicated-server IDs. */
final class InitialSpawnTargetPolicy {
    private InitialSpawnTargetPolicy() {}

    static boolean singleplayerFolder(String name) {
        if (name == null || name.isBlank() || name.equals(".") || name.equals("..")
                || name.contains("/") || name.contains("\\")) return false;
        for (int i = 0; i < name.length(); i++) {
            if (Character.isISOControl(name.charAt(i))) return false; // Keep v2 receipts single-line.
        }
        try {
            Path path = Path.of(name);
            return !path.isAbsolute() && path.getNameCount() == 1 && path.getFileName().toString().equals(name);
        } catch (InvalidPathException error) {
            return false;
        }
    }
}
