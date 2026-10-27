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
    public static int[] candidate(int index) {
        if (index < 0 || index >= 32) throw new IllegalArgumentException("Archive candidate outside bounded search");
        double angle = (index % 8) * Math.PI / 4;
        int radius = 14 * (1 + index / 8);
        return new int[]{(int)Math.round(radius * Math.cos(angle)), (int)Math.round(radius * Math.sin(angle))};
    }
}
