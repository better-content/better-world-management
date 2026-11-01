# Standalone native GUI: runtime-classpath preflight

Audit: 2026-10-26 22:23–22:28 UTC. Read-only inspection of production source, actual compiled method descriptors, pinned JAR `META-INF/mods.toml`, and embedded Jar-in-Jar metadata. No Gradle, native client, Minecraft initialization, or production/build edit was performed. This is **static preflight**, not observed successful startup.

## Smallest concrete candidate: existing runtime, no additional owner mods

Use the existing runtime graph already declared by the WM build:

* Forge 1.20.1 / 47.4.13;
* `com.simibubi.create:create-1.20.1:6.0.8-291:slim`;
* `net.createmod.ponder:Ponder-Forge-1.20.1:1.0.92`;
* `dev.engine-room.flywheel:flywheel-forge-1.20.1:1.0.5` (client runtime, not just API);
* `com.tterrag.registrate:Registrate:MC1.20-1.3.3`;
* `io.github.llamalad7:mixinextras-forge:0.3.6` (existing build dependency; allow normal embedded-library resolution);
* `curse.maven:blood-magic-224791:7956981`;
* `curse.maven:patchouli-306770:7731017`;
* actual WM main and visual-harness outputs.

WM's actual `mods.toml` requires Create and Blood Magic, not the optional world-effect owners. Create requires Ponder and client Flywheel; Blood Magic requires Patchouli. These are already in the build. Ponder/Flywheel/Create/Blood/Patchouli and all owner closure members below use **`javafml`**, not `kotlinforforge`.

### Why compileOnly owners are not demonstrated eager dependencies here

`PrestigeMod` registers `WorldAxiomLifecycle.class`, `AxiomClaims.class`, and `ExplorationEffects.register()`, alongside existing coordinator/Create support. Inspection of their actual compiled `javap -p -s` output shows only Java, Minecraft, Forge and WM-owned types in all declared field and method descriptors. There is **no** geology/font/genetics/bees/seasons event parameter or foreign return type in those registered classes. `WorldAxiomNetwork` likewise has no such declared foreign descriptor.

`ExplorationEffects` static initialization builds ordinary Java maps/sets and its register method installs Forge player tick/logout/server-stop listeners. Typed owner calls are isolated in `ExplorationNative`; its three methods return vanilla/Java types and are reached only by actual server exploration/forecast/claim operations, not screen construction. `WorldPolicies` is not an event-bus registration target in `PrestigeMod`; its typed native operations are used by server-world lifecycle work. Nomination's cattle/bees/cultivar typed adapters are not invoked by screen presentation. Standalone bootstrap creates no player or world.

Thus the claimed specific failure mode “registered event method signatures require all compileOnly owners before the title screen” is not present in the inspected code. This does **not** prove that every possible JVM verifier, mixin, mod resource loader or runtime initialization path will succeed. The real client remains the final startup oracle. In particular, the Hexerei adapter is `@Pseudo` with a string target; absent-target handling is a real Mixin runtime question, not something this audit marks passing.

**Recommended first native candidate:** retain the existing minimal runtime and normal production registration unchanged. Do not add all owners merely because their imports exist in cold server-only method bodies. Diagnose any real linkage failure from its exact class/stack before adding unrelated mods. No skip guards, fixture-flag suppression of production registration, or fake owner classes are proposed.

## Conservative bounded fallback: all optional owners plus their declared mandatory closure

If root deliberately chooses the fuller fallback, the ten compileOnly roots close to **16 additional mods**, not the 275-mod full pack. Add only to a **non-shipping visual-harness runtime configuration**, remapping raw production JARs through ForgeGradle's normal development mapping path. Do not place SRG production JARs directly on an official-named dev classpath; do not promote these to production `implementation`.

| Root | Exact existing notation / source |
| --- | --- |
| Hexerei 0.4.2.3 | `curse.maven:hexerei-548599:6314111` |
| Genetic Animals 0.11.12 (`eanimod`) | `curse.maven:genetic-animals-313213:6134040` |
| Complicated Bees 3.4.6 | `curse.maven:complicated-bees-1100137:8277762` |
| Serene Seasons 9.1.0.2 | `curse.maven:serene-seasons-291874:6398227` |
| Fonts 1.0.0 | existing `worldcondenser.lab:better-dimension-fonts:1.0.0` |
| Geology 0.2.0 | existing `worldcondenser.lab:better-ore-geology:0.2.0` |
| Bumblezone Crops 0.1.0 | existing `worldcondenser.lab:better-bumblezone-crops:0.1.0` |
| Celestial Events 0.1.0 | existing `worldcondenser.lab:better-celestial-events:0.1.0` |
| Spirit Commerce 1.0.1 | existing `worldcondenser.lab:better-spirit-commerce:1.0.1` |
| Lodestone 1.6.4.1 | existing `worldcondenser.lab:lodestone:1.20.1-1.6.4.1` |

Mandatory additions introduced by those roots:

| Mod | Exact pinned source | Reason |
| --- | --- | --- |
| GlitchCore 0.0.1.1 | `curse.maven:glitchcore-955399:5787839` | Serene Seasons requires it. |
| Bumblezone 7.13.4 | `curse.maven:the-bumblezone-362479:8548194` | Crops requires `[7.13.4,7.14)`. Cached JAR actually declares `7.13.4+1.20.1-forge`. |
| Curios | `curse.maven:curios-309927:6418456` | Spirit Commerce and Malum require it; retain the actual pinned manifest version, not literal `${file.jarVersion}` from TOML. |
| Malum 1.6.7 | `curse.maven:malum-484064:6646111` | Spirit Commerce requires it; Malum also requires already-listed Lodestone and Curios. |
| Better Compat Fixes 0.1.9 | frozen lab pack `mods/better-compat-fixes-0.1.9.jar` | Fonts requires `better_compat_fixes >=0.1.8`. |
| Better Gameplay Notices 1.0.0 | frozen lab pack `mods/better-gameplay-notices-1.0.0.jar` | Compat Fixes requires it. |

The last two custom raw JARs are **not** currently among the six `provenance/api` Ivy artifacts. Root must explicitly provide a hash-pinned local/deobf dependency for them if this fallback is chosen; inventing an existing Ivy coordinate would not resolve them. Do not substitute unrelated canonical checkout outputs.

Bumblezone embeds Athena 3.1.2 and MixinExtras 0.4.1 via Jar-in-Jar. Malum embeds MixinExtras 0.3.2. Preserve these normal embedded libraries and Forge's range resolution rather than stripping/repacking the JARs. No root or mandatory closure member declares a Kotlin loader dependency. Scanning Genetic Animals, Bees and Seasons class bytes also found no `kotlin/`, Geckolib, Curios or MC Lib marker in those three JARs; this marker scan is a diagnostic, not a universal bytecode linkage proof. KotlinForForge is therefore **not an established requirement of this candidate**, and should not be added speculatively.

## Exact evidence

Read-only evidence is retained at `/home/dev/.tmp/condenser-visual-closure/`:

* `inventory.json`: parsed actual mod metadata for cached/pinned candidates, including embedded Jar-in-Jar records;
* `conservative-closure.json`: exact 16-member conservative closure with **absolute raw artifact paths, full SHA-256 values, loader type/range, mod records, and mandatory dependency declarations**;
* `registered-signatures.javap.txt`: actual compiled method/field descriptors for the four registration/network classes above.

Pinned examples from that hash receipt:

* Genetics: `2f5cfa5014bec5297e7db08d8ff574e1e891b1c8613c993724c7b597ee9465e4`;
* Bees: `0c9810afbd0ea861062503081cc34c5615071d4e393190c062bfae22bb32a1f8`;
* Seasons: `c4f1b6bcbd2c4fd8c54866cc217f455d2e8262e35ac218f8fb628c79bc76ff8e`;
* Fonts: `a3fddead3aa3d170f0187028056a502967265833c172f09e744de6aa91ff91ef`;
* Compat: `e1e23fe4d8f6c59fb75618f8c81fc7a85bbe53089bb8d11b79bf4c1e258d6614`;
* Notices: `7bd86884e1bf7d79acfb6cd947b52a1ff3e96f2ea5fa386227a25a1eb4c1eb24`.

No client startup, resource reload, title-screen readiness, framebuffer capture, gameplay or native effect is claimed by this audit. Actual first capture and screenshot-driven shipping UI revision remain pending.
