package com.bettercontent.betterworldmanagement.worldshaping.world;

import java.util.List;

/** Pure bounded payload rules shared by placement and tests. */
public final class ArchiveText {
    private ArchiveText() {}
    public static List<String> pages(String text) {
        if (text == null) throw new IllegalArgumentException("Missing archive book");
        List<String> pages = List.of(text.split("\n", -1));
        if (pages.size() > 4 || pages.stream().anyMatch(page -> page.length() > 256))
            throw new IllegalArgumentException("Archive book exceeds four pages of 256 characters");
        return pages;
    }
    public static String plaque(String text) {
        if (text == null || text.length() > 256) throw new IllegalArgumentException("Archive plaque exceeds 256 characters");
        return text;
    }
    /** Probe nearby offsets around each original ring point; still at most 64 blocks from spawn per axis. */
    public static int[] candidate(int index) {
        if (index < 0 || index >= 800) throw new IllegalArgumentException("Archive candidate outside bounded search");
        int ringPoint = index / 25;
        int local = index % 25;
        double angle = (ringPoint % 8) * Math.PI / 4;
        int radius = 14 * (1 + ringPoint / 8);
        return new int[]{(int)Math.round(radius * Math.cos(angle)) + (local % 5 - 2) * 4,
                (int)Math.round(radius * Math.sin(angle)) + (local / 5 - 2) * 4};
    }
}
