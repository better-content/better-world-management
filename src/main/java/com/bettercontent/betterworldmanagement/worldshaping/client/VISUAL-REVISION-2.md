# Actual native revision-1 review → shipping UI revision 2

## Actual image inspected before editing

`run-visual-client/screenshots/axiom-revision-1-0f1cc68b-b1f1-48b1-8be6-5ea018fce3f0.png`

Root reports native capture 2026-10-26 23:06:24–23:06:57 UTC. UI owner opened/read that actual Minecraft PNG before this second shipping-source change. Observed: all 24 individual names are readable without major collisions, the stronger title is effective, and budget/details remain legible. However, all four node glyphs in each branch still share almost the same outline. Their small cutter marks are insufficient for fast spatial recognition. This review is presentation-only, not a gameplay/network/claims acceptance statement.

## Revision 2 scope

Only owned `client/**` shipping files changed:

* New **`AxiomSigils.java`** defines exactly 24 canonical-ID-bound engravings. Each is constructed procedurally using bounded Bresenham strokes, outlines/circles and pixels on an immutable **16×16** canvas. No textures/assets, font changes, fake render, input events or prerequisites are introduced.
* **`AxiomEngraving.glyph`** now accepts the actual node ID and emits the sigil's native pixels with `GuiGraphics.fill`. Branch-only family emblem code remains separate and is not used as a node identity.
* **`WorldAxiomScreen`** supplies the same actual node ID for both the atlas/list button glyph and the details glyph. Existing scaling centers the full 16×16 canvas safely inside the existing 24px radial button. Family mineral colors, allocated verdigris, selection outlines, badges, all full-name label packing and all compact geometry are unchanged.

| Branch | Independent sigils in catalog order |
| --- | --- |
| Forge | anvil / toothed engine wheel / stacked billets / flaming masonry hearth |
| Life | horned cattle head / seedling with soil and seed / tiered hive / pointed-ear wolf |
| Atlas | compass rose / Font spiral / diagonal spyglass / star calendar |
| Hearth | open book / crenellated wall / village gate / minted coin |
| Ritual | dagger / floating spirit / chalk and ritual mark / bubbling cauldron |
| World | mountain horizon / autumn leaf / faceted underground ore / crescent and star |

No core, server, config, network, build, fixture data/scene, or launch changes. In particular, this is an actual shipping-source revision following inspection of a real previous capture, not a different data fixture masquerading as iteration.

## Exact source identity / freeze

* `AxiomSigils.java`: `f2949308dff162f2db67c16c30d1eb83f7b88b5b9e0fb7726e603893f3a45f76`
* `AxiomEngraving.java`: `5b689593edc6641bb6dc1e8225e9612f1595c96ff9194a732f2e151e278336f3`
* `WorldAxiomScreen.java`: `f0a314382b088fb546d60d78a2575b70bd1df7d9d200fecdf0649fd1994fe1ed`
* `AxiomLayout.java` is unchanged from revision 1: `fb262efc232c7bb6ad93a2fe370412c7e0e1e4992f0a3079977d9c51ddcf82f4`.

**FROZEN for root's real revision-2 capture.** No subsequent visual source change until that actual PNG is inspected.

## Lightweight checks, not visual acceptance

* Actual production sigil/catalog oracle: **7,181 assertions PASS**. Exactly the 24 current canonical IDs; no old singular-ID aliases; immutable 16×16 masks; nonempty/non-solid art; 24 unique patterns. Minimum pairwise difference is **40 native pixels**, not just one or two variant ticks. Canvas writes outside 0..15 throw immediately.
* The unchanged revision-1 layout checks rerun PASS: **34,560 label assertions / 30 radial viewports**, plus **61,197 viewport/branch geometry assertions**.
* Current changed UI source and harness compile against actual Minecraft/Forge API and actual production source: Java 17 direct compilation PASS. Evidence `/home/dev/.tmp/condenser-ui-r2-javac-20261026T231509Z/`.
* Pure oracle source `/home/dev/.tmp/AxiomSigilsRevisionTwoTest.java`; compiled classes `/home/dev/.tmp/condenser-ui-sigils-r2/`.

No Gradle, Minecraft client, Xvfb, native render, OS input or gameplay input was launched by the UI owner. Pixel-mask distinction is not proof that the sigils read well at native GUI scale: inspect root's actual revision-2 screenshot next.
