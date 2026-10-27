package com.bettercontent.betterworldmanagement.worldshaping.client;

import java.util.ArrayList;
import java.util.List;

/** Pure screen geometry. No Minecraft/client bootstrap is required to exercise it. */
public final class AxiomLayout {
    public record Rect(int x, int y, int width, int height) {
        public int right() { return x + width; }
        public int bottom() { return y + height; }
        public boolean contains(double px, double py) { return px >= x && py >= y && px < right() && py < bottom(); }
    }
    public record NodePlace(int index, Rect bounds) {}
    public record Layout(Rect frame, Rect map, Rect detail, boolean radial, boolean stacked, List<NodePlace> nodes) {}
    public record LabelPlace(int index, Rect bounds) {}
    private AxiomLayout() {}

    /** Full-name labels: angle-aware preferred positions, then bounded collision-free vertical packing. */
    public static List<LabelPlace> labels(Layout layout, int[] widths, int textHeight) {
        if (!layout.radial()) return List.of();
        if (widths.length != 24 || textHeight < 1) throw new IllegalArgumentException("Invalid label metrics");
        Rect map = layout.map();
        List<Rect> occupied = new ArrayList<>();
        for (var node : layout.nodes()) {
            Rect r = node.bounds(); occupied.add(new Rect(r.x() - 2, r.y() - 2, r.width() + 4, r.height() + 4));
        }
        int cx = map.x() + map.width() / 2, cy = map.y() + map.height() / 2;
        occupied.add(new Rect(cx - 32, cy - 32, 64, 64));
        // Reserve the six existing branch captions as well as every icon and the central register.
        for (int b = 0; b < 6; b++) {
            Rect caption = caption(layout, b, 82, 9);
            occupied.add(new Rect(caption.x() - 1, caption.y() - 1, caption.width() + 2, caption.height() + 2));
        }
        List<LabelPlace> result = new ArrayList<>();
        for (int b = 0; b < 6; b++) for (int n = 3; n >= 0; n--) {
            int index = b * 4 + n;
            Rect icon = layout.nodes().get(index).bounds();
            int w = Math.min(widths[index], map.width() - 4);
            boolean left = b == 4 || b == 5;
            int x = left ? icon.x() - 6 - w : icon.right() + 6;
            x = Math.max(map.x() + 2, Math.min(map.right() - w - 2, x));
            int direction = b == 2 || b == 4 ? -1 : 1;
            int preferredY = b == 0 || b == 3 ? icon.y() + (icon.height() - textHeight) / 2
                    : direction > 0 ? icon.bottom() + 3 : icon.y() - textHeight - 3;
            Rect chosen = null;
            for (int offset = 0; offset < map.height() && chosen == null; offset++) {
                for (int sign : new int[]{direction, -direction}) {
                    Rect candidate = new Rect(x, preferredY + sign * offset, w, textHeight);
                    if (candidate.y() < map.y() + 2 || candidate.bottom() > map.bottom() - 2) continue;
                    boolean clear = true;
                    for (Rect other : occupied) if (overlaps(candidate, other)) { clear = false; break; }
                    if (clear) { chosen = candidate; break; }
                }
            }
            if (chosen == null) throw new IllegalStateException("No full-name label space for node " + index);
            result.add(new LabelPlace(index, chosen));
            occupied.add(new Rect(chosen.x() - 2, chosen.y() - 1, chosen.width() + 4, chosen.height() + 2));
        }
        return List.copyOf(result);
    }
    /** Shared renderer/label-reservation caption geometry; diagonal captions have >=4px icon clearance. */
    public static Rect caption(Layout layout, int branch, int textWidth, int textHeight) {
        if (!layout.radial() || branch < 0 || branch > 5 || textWidth < 1 || textHeight < 1)
            throw new IllegalArgumentException("Invalid radial caption metrics");
        Rect map = layout.map(), tip = layout.nodes().get(branch * 4 + 3).bounds();
        int cx = map.x() + map.width() / 2, cy = map.y() + map.height() / 2;
        double a = Math.atan2(tip.y() + 12 - cy, tip.x() + 12 - cx);
        int ex = tip.x() + 12 + (int) (Math.cos(a) * 19);
        int ey = tip.y() + 12 + (int) (Math.sin(a) * 19);
        int y;
        if (branch == 1 || branch == 5) y = Math.min(ey - 19, tip.y() - textHeight - 4);
        else if (branch == 2 || branch == 4) y = Math.max(ey + 12, tip.bottom() + 4);
        else y = ey + (branch == 0 ? -13 : 6); // Keep vertical Forge/Hearth preferred anchors.
        int w = Math.min(textWidth, map.width());
        int x = Math.max(map.x(), Math.min(map.right() - w, ex - w / 2));
        y = Math.max(map.y(), Math.min(map.bottom() - textHeight, y));
        return new Rect(x, y, w, textHeight);
    }
    public static boolean overlaps(Rect a, Rect b) {
        return a.x() < b.right() && b.x() < a.right() && a.y() < b.bottom() && b.y() < a.bottom();
    }

    public static Layout compute(int width, int height, int branch) {
        if (width < 240 || height < 180) throw new IllegalArgumentException("Viewport below supported GUI minimum");
        if (branch < 0 || branch > 5) throw new IllegalArgumentException("Invalid branch");
        Rect frame = new Rect(8, 8, width - 16, height - 16);
        boolean radial = width >= 760 && height >= 420;
        boolean stacked = width < 540;
        int detailWidth = Math.min(260, Math.max(200, width / 3));
        int contentY = height < 230 ? 60 : 68;
        int contentHeight = height - (height < 230 ? 96 : 110);
        Rect map = new Rect(18, contentY, stacked ? width - 36 : width - detailWidth - 46, contentHeight);
        Rect detail = stacked ? new Rect(18, contentY, width - 36, contentHeight)
                : new Rect(map.right() + 12, contentY, detailWidth, contentHeight);
        List<NodePlace> nodes = new ArrayList<>();
        if (radial) {
            double cx = map.x + map.width / 2.0, cy = map.y + map.height / 2.0;
            double rx = map.width / 2.0 - 30, ry = map.height / 2.0 - 26;
            for (int b = 0; b < 6; b++) {
                double angle = Math.toRadians(-90 + b * 60);
                for (int n = 0; n < 4; n++) {
                    double radius = .37 + n * .20;
                    int x = (int) Math.round(cx + Math.cos(angle) * rx * radius) - 12;
                    int y = (int) Math.round(cy + Math.sin(angle) * ry * radius) - 12;
                    nodes.add(new NodePlace(b * 4 + n, new Rect(x, y, 24, 24)));
                }
            }
        } else {
            int tabsHeight = height < 230 ? 36 : 44;
            int rowHeight = Math.min(34, (map.height - tabsHeight) / 4);
            for (int n = 0; n < 4; n++) nodes.add(new NodePlace(branch * 4 + n,
                    new Rect(map.x, map.y + tabsHeight + n * rowHeight, map.width, rowHeight - 2)));
        }
        return new Layout(frame, map, detail, radial, stacked, List.copyOf(nodes));
    }
}
