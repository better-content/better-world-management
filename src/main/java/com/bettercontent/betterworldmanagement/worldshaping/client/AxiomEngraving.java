package com.bettercontent.betterworldmanagement.worldshaping.client;

import net.minecraft.client.gui.GuiGraphics;

/** Pixel-native copperplate ornament; no external fonts, shaders or web surface. */
final class AxiomEngraving {
    static final int INK = 0xff111c20, PLATE = 0xff1b2a2e, COPPER = 0xffbd8157,
            LIGHT = 0xfff0d4a4, MUTED = 0xff99aaa8, VERDIGRIS = 0xff73c4ad;
    static final int[] MINERAL = {0xffdf9b68, 0xffa4c982, 0xff8cbacb, 0xffd6be7b, 0xffbd9fda, 0xff7ac8bd};
    private AxiomEngraving() {}
    static void line(GuiGraphics g, int x, int y, int tx, int ty, int color) {
        int dx = Math.abs(tx - x), sx = x < tx ? 1 : -1;
        int dy = -Math.abs(ty - y), sy = y < ty ? 1 : -1, error = dx + dy;
        while (true) {
            g.fill(x, y, x + 1, y + 1, color);
            if (x == tx && y == ty) break;
            int e = error * 2;
            if (e >= dy) { error += dy; x += sx; }
            if (e <= dx) { error += dx; y += sy; }
        }
    }
    static void ring(GuiGraphics g, int cx, int cy, int radius, int color) {
        int lastX = cx + radius, lastY = cy;
        for (int i = 1; i <= 48; i++) {
            double a = i * Math.PI / 24;
            int x = cx + (int) Math.round(Math.cos(a) * radius), y = cy + (int) Math.round(Math.sin(a) * radius);
            line(g, lastX, lastY, x, y, color); lastX = x; lastY = y;
        }
    }
    static void panel(GuiGraphics g, AxiomLayout.Rect r) {
        g.fill(r.x(), r.y(), r.right(), r.bottom(), INK);
        g.renderOutline(r.x(), r.y(), r.width(), r.height(), COPPER);
        if (r.width() > 20 && r.height() > 20) g.renderOutline(r.x() + 3, r.y() + 3, r.width() - 6, r.height() - 6, 0xff3c4540);
        for (int x : new int[]{r.x() + 6, r.right() - 7}) for (int y : new int[]{r.y() + 6, r.bottom() - 7})
            g.fill(x, y, x + 2, y + 2, LIGHT);
    }
    static void glyph(GuiGraphics g, int cx, int cy, String id, int color) {
        var rows = AxiomSigils.rows(id);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++)
            if (rows.get(y).charAt(x) == '#') g.fill(cx + x - 8, cy + y - 8, cx + x - 7, cy + y - 7, color);
    }

    /** Optional branch-only emblem, never used as an individual node's identity. */
    static void familyGlyph(GuiGraphics g, int cx, int cy, int index, int color) {
        int family = index / 4, variant = index % 4;
        switch (family) {
            case 0 -> { // forged anvil / furnace
                line(g,cx-6,cy-4,cx+6,cy-4,color); line(g,cx-4,cy-3,cx-2,cy+3,color);
                line(g,cx+4,cy-3,cx+2,cy+3,color); line(g,cx-5,cy+5,cx+5,cy+5,color);
                line(g,cx-2,cy+3,cx+2,cy+3,color);
            }
            case 1 -> { // living covenant
                line(g,cx,cy+6,cx,cy-5,color); line(g,cx,cy,cx-5,cy-5,color);
                line(g,cx-5,cy-5,cx-5,cy-1,color); line(g,cx-5,cy-1,cx,cy+2,color);
                line(g,cx,cy-2,cx+5,cy-6,color); line(g,cx+5,cy-6,cx+5,cy-2,color);
                line(g,cx+5,cy-2,cx,cy+1,color);
            }
            case 2 -> { // compass rose
                line(g,cx,cy-7,cx+4,cy+4,color); line(g,cx+4,cy+4,cx-4,cy+4,color);
                line(g,cx-4,cy+4,cx,cy-7,color); line(g,cx-7,cy,cx+7,cy,color);
            }
            case 3 -> { // settlement gateway
                line(g,cx-6,cy+6,cx-6,cy-3,color); line(g,cx+6,cy+6,cx+6,cy-3,color);
                line(g,cx-7,cy-3,cx,cy-7,color); line(g,cx,cy-7,cx+7,cy-3,color);
                line(g,cx-2,cy+6,cx-2,cy,color); line(g,cx+2,cy+6,cx+2,cy,color);
                line(g,cx-2,cy,cx+2,cy,color);
            }
            case 4 -> { // ritual crystal
                line(g,cx,cy-7,cx+5,cy,color); line(g,cx+5,cy,cx,cy+7,color);
                line(g,cx,cy+7,cx-5,cy,color); line(g,cx-5,cy,cx,cy-7,color);
                line(g,cx,cy-7,cx,cy+7,color); line(g,cx-5,cy,cx+5,cy,color);
            }
            default -> { // horizon and celestial arc
                ring(g,cx,cy-2,4,color); line(g,cx-7,cy+4,cx+7,cy+4,color);
                line(g,cx-5,cy+7,cx+5,cy+7,color);
            }
        }
        // Four distinct cutter marks, common across the six mineral families.
        for (int n = 0; n <= variant; n++) g.fill(cx - 5 + n * 3, cy + 10, cx - 4 + n * 3, cy + 11, color);
    }
}
