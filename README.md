# World Lifecycle Manager

World Lifecycle Manager is the Better Content Forge 1.20.1 mod for verified dedicated-server world resets, cold archives, rollback, World Condenser control, and the persistent lineage schematic library.

Single-player worlds receive a durable lineage identity and namespaced player-lineage storage so a future successor can inherit collected history. Prestige resets, perks, lineage schematics, World Condenser operation, and lifecycle commands remain disabled on integrated servers; single-player onboarding uses the generation-zero spawn-only policy. Unrelated newly created saves receive distinct lineage identities, while copied saves retain their binding.

Build the World Condenser by placing its interface two blocks above a harvested, unbound Dimensional Font, with open space between. The Font is spent as an expedition anchor when harvested. Players can configure biome preferences and perks and stage a reset at the overhead interface; only a nearby server operator can commit the permanent reset. The staged interface shows a magical pour without consuming or encoding a fluid.

Operators publish lineage schematics directly from the World Condenser's Schematics tab. Save a structure with Create's Schematic and Quill so its `.nbt` file appears under `.minecraft/schematics`, open the tab, select the local file, and click Publish. The server derives the author from the operator's player profile and sanitizes the schematic before publication. Downloads are written back to Create's top-level schematic folder and appear in Create's normal schematic list.

The World Condenser also exposes a world-shaping perk tree. Each upcoming prestige supplies one point. Players may respec before staging, while the staged build is transactionally bound to the successor and only becomes active after verified lineage advancement.

Fresh generation-zero worlds use the configured safe temperate biome list to select their initial shared spawn. The bounded search advances over server ticks, probes biomes nearest-first, requests at most one landing chunk at a time, and persists its pending/resolved/fallback state so onboarding can wait across a restart. If no valid site is found, the vanilla shared spawn is retained. Existing worlds keep their shared spawn.

Successor biome preferences are resolved in their configured order by the same bounded search. A changed request or stopping server cancels the successor cursor. Exhausting the search or reaching its 4,000-tick deadline retains the generated world's shared spawn and publishes an unresolved health result so the supervisor can safely retry or roll back.

Create Schematicannons gain persistent per-cannon material substitutions. Open the normal cannon menu, select a required ordinary block, then click the Fallback ghost slot while carrying the replacement block. The server validates the rule, pauses an active cannon when rules change, preserves compatible block-state properties, and only substitutes when the original is unavailable. Fluid-containing blocks, block entities, multi-item requirements, cycles, and self-substitutions are rejected. Native uses of the fallback material are reserved before substitutions consume it.

## Development

Java 17 is required. The checked-in Gradle wrapper provides the supported build and validation entrypoints:

```sh
./gradlew verifyFast
./gradlew verifyFull
./gradlew verifyFull stageRuntimeJar
```

`verifyFast` runs the JVM tests. `verifyFull` also runs the Forge GameTests. `stageRuntimeJar` copies the reobfuscated production artifact to `build/libs/world-lifecycle-manager-0.1.0.jar`; deploy that canonical staged JAR.

The non-shipping `visualHarness` source set validates the real Create menu. Run the `visualServer` and `visualClient` configurations under Xvfb, then issue `wlmvisual prepare <player>` and `wlmvisual capture <player> <name>` through the dedicated-server console. The server commands create the fixture and open the menu; the client harness only waits for that screen and invokes Minecraft's screenshot API. It does not inject player movement, mouse, keyboard, or gameplay controls.

The matching sibling pack repository is [better-content/better-content-modpack](https://github.com/better-content/better-content-modpack). Its `world-lifecycle-manager-server.sh` supervisor owns archive publication, successor health, retry, rollback, and exactly-once lineage advancement.

## License

World Lifecycle Manager is licensed under the GNU Affero General Public License version 3 or later. See `LICENSE`.


## Identity

The clean-break canonical identity is repository/artifact `world-lifecycle-manager`, mod ID and resource namespace `world_lifecycle_manager`, and Maven group `com.bettercontent`. Legacy `prestige` identifiers and persisted paths are not migrated.
