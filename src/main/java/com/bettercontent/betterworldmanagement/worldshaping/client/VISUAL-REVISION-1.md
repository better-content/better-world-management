# Actual native baseline review → shipping UI revision 1

## Evidence inspected

Read the actual 1920×1080 Minecraft framebuffer:

`run-visual-client/screenshots/axiom-baseline-f8c4427f-916e-49cb-8ba4-666b2f240157.png`

Root reports native capture 2026-10-26 22:55:41–22:56:12 UTC; image inspected directly by UI owner before changing shipping UI. It shows a 960×540 GUI, eight of twelve draft points, all 24 glyph buttons, and Iron Law details. This is native presentation evidence, **not gameplay/claims/network/succession acceptance**.

### Observed problems

* The six branches are visible, but 23 of the 24 individual axiom names are absent. Users must click/hover repeated family glyphs to discover which law they represent.
* The four glyphs in each family are almost identical at this scale. Tiny cutter marks do not compensate for missing names.
* Large unused negative space makes the map look opaque rather than informative; the small header does not establish a strong visual hierarchy.
* Budget and details are legible. Keep their state semantics, role gates and active/draft separation unchanged.

## Revision 1: actual shipping source changes

Only `client/AxiomLayout.java` and `client/WorldAxiomScreen.java` changed. No scene fixture, server, core, build or effect change.

1. All 24 full catalog names are rendered at 0.8 native Minecraft font scale on radial layouts, in high-contrast light ink or allocated verdigris. No catalog-name truncation/ellipsis is applied.
2. Preferred placement follows branch angles: upper-right Life below/outward, lower-right Atlas above/outward, mirrored right-aligned text on left branches, Forge/Hearth alongside their vertical node columns.
3. Labels use real font width, map clamping, and deterministic bounded vertical packing. The solver reserves every node icon, the central budget register, all six branch captions, and previously placed labels. It adds no prerequisite lines/edges. Packing happens only during layout/widget rebuild, not every render frame.
4. Wide header becomes **THE WORLD REMEMBERS**, at 1.35 native font scale, followed by **24 INDEPENDENT AXIOMS / ONE POINT EACH**. Narrow header/list fallback is unchanged.
5. Native node buttons/tooltips, compact branch lists, point allocation, permissions, nomination/configuration and active-lineage claims are unchanged.

Exact source hashes frozen for root's next capture:

* `AxiomLayout.java`: `fb262efc232c7bb6ad93a2fe370412c7e0e1e4992f0a3079977d9c51ddcf82f4`
* `WorldAxiomScreen.java`: `7e4ee2f1f4831e1aa43ccf71e2c0aa4febbc296970f4c256ba278117a9e0fc29`

## Lightweight verification (not a second native capture)

* Actual pure label geometry: **34,560 assertions**, 30 radial viewports from 760×420 through 1920×1080, all 24 labels with conservative 116px widths; no label overlaps another label or node, and every label fits inside the map.
* Existing actual pure layout sweep: **61,197 assertions**, 594 viewport/branch combinations, PASS.
* Actual Minecraft/Forge API direct Java 17 compilation: PASS. Evidence `/home/dev/.tmp/condenser-ui-r1-javac-20261026T230333Z/`.
* Standalone temporary label oracle source: `/home/dev/.tmp/AxiomLabelsRevisionOneTest.java`; compiled classes `/home/dev/.tmp/condenser-ui-labels-r1/`.

No Gradle, client, Xvfb, native render or injected input was launched by the UI owner. **Source is now frozen for root's real revision-1 capture.** Inspect that actual image before a second source revision. Changing fixture scene or filename alone is not a visual design iteration; native revision-1 acceptance is still pending.
