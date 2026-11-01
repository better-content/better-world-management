# Actual native revision-2 review → shipping UI revision 3

## Actual evidence inspected before editing

Opened/read the real Minecraft PNG:

`run-visual-client/screenshots/axiom-revision-2-5d1afff0-2508-468d-a137-4977e8011584.png`

Root reports native revision-2 capture GREEN at 2026-10-26 23:18:48 UTC. All 24 individual sigils are now visibly distinct and all names remain legible. Actual remaining defect: upper diagonal `6 / WORLD` and `2 / LIFE` captions touch/overlap the outer-node top borders; lower diagonal `5 / RITUAL` and `3 / ATLAS` captions touch the outer-node bottom borders. This is a concrete defect seen in the native PNG, not a hypothetical source-layout concern.

## Narrow source correction

Only owned `AxiomLayout.java` and `WorldAxiomScreen.java` changed. Sigil code, icon/color/badge design, node positions, catalog names, header, role/budget state, configuration/network behavior and fixtures are unchanged.

* Added a single shared pure `AxiomLayout.caption` rectangle function. The renderer and full-name solver now consume the same caption position instead of maintaining two slightly different coordinate formulas.
* Upper diagonal Life/World preferred captions move six logical pixels upward; lower diagonal Atlas/Ritual captions move six logical pixels downward.
* Aspect-ratio-safe clearance explicitly places upper caption bottoms at least four logical pixels above the outer node and lower caption tops at least four pixels below. This also handles very wide/shallow viewports where an angle-only displacement is insufficient.
* All caption rectangles are clamped inside the map. Forge/Hearth keep their original preferred vertical anchors; clamping only prevents those preferred coordinates from escaping the map on smaller viewports. Forge is never pulled upward into the header. At the reviewed 960×540 GUI, Forge stays in place and Hearth receives only the minimal bottom-bound clamp.
* Label packing reserves the **new shared caption rectangles**, with a one-pixel margin. Full names are not shortened, and no prerequisite/decorative edges are introduced.

## Actual production checks

* New pure caption oracle: **5,250 assertions PASS** across **35 radial viewports**, all six captions against all 24 node boxes and map bounds. Minimum measured rectangle separation: **4 logical pixels**.
* Current label solver: **34,560 assertions PASS** across 30 radial viewports with conservative 116px full-name widths.
* Existing layout suite: **61,197 assertions PASS** across 594 viewport/branch combinations.
* Actual Minecraft/Forge API direct Java 17 compilation: PASS. Receipt `/home/dev/.tmp/condenser-ui-r3-javac-20261026T233300Z/`.
* Caption oracle `/home/dev/.tmp/AxiomCaptionsRevisionThreeTest.java`; compiled actual geometry and regression oracles `/home/dev/.tmp/condenser-ui-captions-r3/`.

## Exact source identity and freeze

* `AxiomLayout.java`: `eac3a8f985b9b22de43d5636ed5b0b28eeef979fdc25cc56b19b1ac26a93ea18`
* `WorldAxiomScreen.java`: `756149e13fe0af4d9b42616860d58d80c90ff9ba565fdadeabd461371236cce6`
* Unchanged `AxiomSigils.java`: `f2949308dff162f2db67c16c30d1eb83f7b88b5b9e0fb7726e603893f3a45f76`
* Unchanged `AxiomEngraving.java`: `5b689593edc6641bb6dc1e8225e9612f1595c96ff9194a732f2e151e278336f3`

**SOURCE FROZEN FOR ROOT'S ACTUAL REVISION-3 CAPTURE.** No Gradle, native client, framebuffer capture or input automation was launched by the UI owner. This is the third real-image-driven shipping source revision (baseline→R1→R2→pending R3), not three data-fixture scenes. Native revision-3 visual inspection is still pending, and no gameplay/claims/network/succession acceptance is inferred.
