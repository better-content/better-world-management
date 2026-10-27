package com.bettercontent.betterworldmanagement.worldshaping.client;

import java.util.HashSet;

/** Executable with javac/java only; intentionally needs neither Forge nor a display. */
public final class AxiomLayoutPureTest {
    private static int assertions;
    private static void check(boolean value, String reason) {
        assertions++;
        if (!value) throw new AssertionError(reason);
    }
    public static void main(String[] args) {
        for (int width : new int[]{240, 320, 426, 539, 540, 640, 759, 760, 854, 1280, 1920}) {
            for (int height : new int[]{180, 200, 240, 360, 419, 420, 480, 720, 1080}) {
                for (int branch = 0; branch < 6; branch++) {
                    var l = AxiomLayout.compute(width, height, branch);
                    check(l.frame().right() <= width && l.frame().bottom() <= height, "frame fits");
                    check(l.map().x() >= l.frame().x() && l.map().right() < l.frame().right(), "map horizontal bounds");
                    check(l.map().y() >= l.frame().y() && l.map().bottom() < l.frame().bottom(), "map vertical bounds");
                    check(l.detail().right() <= l.frame().right() && l.detail().bottom() <= l.frame().bottom(), "detail fits");
                    check(l.radial() == (width >= 760 && height >= 420), "radial breakpoint");
                    check(l.nodes().size() == (l.radial() ? 24 : 4), "node count");
                    var seen = new HashSet<Integer>();
                    for (var p : l.nodes()) {
                        var r = p.bounds();
                        check(seen.add(p.index()), "unique node index");
                        check(p.index() >= 0 && p.index() < 24, "node index in catalog");
                        check(r.width() > 0 && r.height() >= 8, "positive readable hit target");
                        check(r.x() >= l.map().x() && r.right() <= l.map().right(), "node horizontal bounds");
                        check(r.y() >= l.map().y() && r.bottom() <= l.map().bottom(), "node vertical bounds");
                        check(r.contains(r.x(),r.y()) && !r.contains(r.right(),r.bottom()), "half-open hit test");
                        if (!l.radial()) check(p.index() / 4 == branch, "compact branch identity");
                        for (var q : l.nodes()) if (q.index() > p.index()) {
                            var s = q.bounds();
                            check(r.right() <= s.x() || s.right() <= r.x() || r.bottom() <= s.y() || s.bottom() <= r.y(), "independent hit targets must not overlap");
                        }
                    }
                    if (!l.stacked()) check(l.map().right() < l.detail().x(), "map/detail separated");
                }
            }
        }
        for (int invalid : new int[]{-1,6}) {
            try { AxiomLayout.compute(854,480,invalid); throw new AssertionError("branch accepted"); }
            catch (IllegalArgumentException expected) { assertions++; }
        }
        try { AxiomLayout.compute(200,150,0); throw new AssertionError("tiny viewport accepted"); }
        catch (IllegalArgumentException expected) { assertions++; }
        System.out.println("AxiomLayoutPureTest PASS: " + assertions + " assertions across 594 viewport/branch combinations");
    }
}
