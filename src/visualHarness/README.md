# Native World Axiom presentation client (non-shipping)

This harness opens the **actual `WorldAxiomScreen` inside a real Forge Minecraft client** and captures its native framebuffer. It does not simulate a server, paid effects, persistence, payment, claiming, operator interaction, or a successful succession. Every metadata receipt says `presentation_fixture=true` and `gameplay_acceptance=false`.

## Scope and startup

`AxiomVisualClient` is a typed `Dist.CLIENT` MOD-bus subscriber. During client setup it installs `AxiomVisualHarness` **only** when `-Dworld_axioms.visualStandalone=true` is set. The harness waits for ten stable client ticks with a native `TitleScreen`, no resource overlay, a real Minecraft font, and no player/world. It then calls `Minecraft.setScreen(new WorldAxiomScreen(actualAxiomView))`. It does not submit widget presses, mouse/keyboard events, OS input, gameplay input, network requests, or a login.

Without the opt-in property, the existing explicit `AxiomVisualHarness.request(scene,name)` presentation path is unchanged: it still requires an actual player and level. The original Schematicannon visual commands and screenshot path are untouched.

A scene is a **data fixture**, not a design iteration:

* scene 1: mixed eight-of-twelve allocation, operator draft, distinct active lineage;
* scene 2: first-generation one-point allocation;
* scene 3: sealed, non-operator all-24 projection.

## Required root-owned Gradle adjustment

The root owns `build.gradle.kts`. In `visualClient`, branch on `providers.gradleProperty("axiomVisualStandalone").map(String::toBoolean).getOrElse(false)`:

* standalone: **omit** `--quickPlayMultiplayer` entirely and use `--width 1920 --height 1080` (or root-selected dimensions);
* ordinary mode: keep the existing quick-play multiplayer address and existing defaults;
* keep the minimal client profile (Create/Blood Magic/Patchouli and their normal dependencies). Do not add the full pack or Fonts solely to present this screen.

JVM properties below can either be forwarded explicitly by the run configuration or inherited using `JAVA_TOOL_OPTIONS`. `-PaxiomVisualStandalone=true` alone is not a JVM property: it controls the root's Gradle argument branch, while `-Dworld_axioms.visualStandalone=true` opts the actual client in.

| Actual client JVM property | Meaning / default |
| --- | --- |
| `world_axioms.visualStandalone` | Explicit opt-in, default `false`. |
| `world_axioms.visualScene` | Projection fixture `1`, `2` or `3`; default `1`. |
| `world_axioms.visualIteration` | Evidence label such as `baseline`, `revision-1`, `revision-2`; default `baseline` in standalone. |
| `world_axioms.visualNonce` | **Required** unique filename component in standalone; use a fresh UUID. |
| `world_axioms.visualSourceRevision` | Source hash/label recorded for the orchestrator; default `unspecified`. The actual loaded Screen class SHA-256 is independently recorded. |
| `world_axioms.visualExitAfterCapture` | Default `false`. If `true`, standalone client calls `Minecraft.stop` only after PNG decoding, dimension validation, hashing and the completion receipt finish. Never used to exit an ordinary connected client. |

`src/visualHarness/options.txt` already requests `guiScale:2`, normal fonts, windowed display, no first-run accessibility dialog and no tutorial. At 1920×1080, expected GUI dimensions are 960×540, which select the full atlas. Confirm the actual dimensions in the receipt instead of assuming scale worked.

## Exact launch payload for root orchestration

**Do not run this without a newly granted coordinated slot.** This is the inner launch payload, not permission to launch or a replacement for the root's bounded process-group supervisor, shared fail-fast lock, Xvfb/GLX/Mesa setup and cleanup. No native/Gradle process was launched by this harness author.

After the conditional Gradle adjustment above, in the isolated `mod_source/better-world-management` checkout:

```sh
export JAVA_HOME="$HOME/.sdkman/candidates/java/17.0.17-tem"
export TMPDIR="$HOME/.tmp"
nonce="$(python3 -c 'import uuid; print(uuid.uuid4())')"
source_hash="$(sha256sum src/main/java/com/bettercontent/betterworldmanagement/worldshaping/client/WorldAxiomScreen.java | cut -d ' ' -f 1)"
export JAVA_TOOL_OPTIONS="-XX:ActiveProcessorCount=2 -Dworld_axioms.visualStandalone=true -Dworld_axioms.visualScene=1 -Dworld_axioms.visualIteration=baseline -Dworld_axioms.visualNonce=$nonce -Dworld_axioms.visualSourceRevision=$source_hash -Dworld_axioms.visualExitAfterCapture=true"
./gradlew runVisualClient -PaxiomVisualStandalone=true --no-daemon --max-workers=2
```

The root supplies the connected software-rendered display (`DISPLAY`, Mesa/GLX) and captures all stdout/stderr. Do not introduce synthetic input to dismiss screens or make the capture happen. If startup does not reach a ready title screen, retain logs and diagnose the real failure; do not turn the image into a mock or bypass mod loading by replacing the renderer.

## Evidence and completion

In `run-visual-client/screenshots/`, the harness creates:

1. `axiom-<iteration>-<nonce>.png.presentation.json`: immutable requested-capture metadata, including actual GUI/framebuffer sizes and SHA-256 of the loaded `WorldAxiomScreen.class`.
2. `axiom-<iteration>-<nonce>.png`: the PNG written by Minecraft `Screenshot.grab`.
3. `axiom-<iteration>-<nonce>.png.complete.json`: emitted **only after** the screenshot writer's callback, successful full PNG decoding with native `NativeImage`, exact framebuffer dimension agreement, file-size reading and SHA-256 computation.

PNG existence alone is not success. Look for the completion receipt and `AXIOM_VISUAL PNG_VALIDATED` log line. Receipt generation is not a design/interaction acceptance oracle. A validation failure leaves the PNG/request receipt/logs, writes `.failure.txt` if possible, and raises a client-thread failure. Existing evidence is never overwritten. Optional successful exit happens only after the completion receipt has been written.

## Mandatory real review/iteration protocol

1. Capture **baseline** scene 1. Root and UI owner inspect the actual Minecraft image, not a source diagram or fabricated render.
2. Record concrete readability/composition defects. Only then make actual shipping UI source changes, rebuild, and capture **revision-1**, preferably scene 1 again at the same dimensions.
3. Inspect that second image, make another evidence-driven source revision if required, rebuild, capture **revision-2**, and inspect again.

Use fresh nonces, preserve all images/logs and receipts, and compare `screen_class_sha256` values. Changing only the scene, iteration label, configuration or filename is **not** a UI source revision and does not satisfy the three-round design review. Scenes 2/3 and compact GUI-scale captures are additional coverage, not substitutes for those iterations. Do not change the shipping UI before the first actual baseline image is inspected.

Real operator widget interaction, server networking, physical access, effect behavior, claims and real process succession still need their independent native acceptance gates.
