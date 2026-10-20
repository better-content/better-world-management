# Better World Management

## Playtest logging

`bc.playtest.better_world_management.prestige` distinguishes stop request admission, changed-request
landing-search cancellation and actual health-result publication. Health records may contain the
existing transaction identity and exact-landing/fresh flags, never seed, filesystem payload,
operator names or schematics. A stop request is not archive completion, and published unresolved
health is not an accepted successor. Real health-operation exceptions retain ERROR plus cause.
DEBUG/TRACE share 120/minute (burst 20), 256 keys and 2,048-character records, with 30-second
summaries. Startup INFO reports loaded version/mode/limits and dedicated-only reset scope;
unavailable source identity is `UNKNOWN`. Stop/disconnect clears transient keys without rearming
process allowances. Pure emitter/contracts supplement native lifecycle gates, not supervisor
archive acceptance.

## Scope and authority

This repository owns gameplay successor/archive persistence, not development artifact retention.
Read [local instructions](AGENTS.md) and the
[shared documentation/policy index](../../better-content-modpack/docs/README.md).


World Lifecycle Manager is the Better Content Forge 1.20.1 mod for verified dedicated-server world resets, cold archives, rollback, World Condenser control, and the persistent lineage schematic library.

Single-player worlds receive a durable lineage identity and namespaced player-lineage storage so a future successor can inherit collected history. Prestige resets, perks, lineage schematics, World Condenser operation, and lifecycle commands remain disabled on integrated servers; single-player onboarding uses the generation-zero spawn-only policy. Unrelated newly created saves receive distinct lineage identities, while copied saves retain their binding.

Build the World Condenser by placing its interface two blocks above a harvested, unbound Dimensional Font, with open space between. The Font is spent as an expedition anchor when harvested. Players can configure biome preferences and perks and stage a reset at the overhead interface; only a nearby server operator can commit the permanent reset. The staged interface shows a magical pour without consuming or encoding a fluid.

Operators publish lineage schematics directly from the World Condenser's Schematics tab. Save a structure with Create's Schematic and Quill so its `.nbt` file appears under `.minecraft/schematics`, open the tab, select the local file, and click Publish. The server derives the author from the operator's player profile and sanitizes the schematic before publication. Downloads are written back to Create's top-level schematic folder and appear in Create's normal schematic list.

The World Condenser also exposes a world-shaping perk tree. Each upcoming prestige supplies one point. Players may respec before staging, while the staged build is transactionally bound to the successor and only becomes active after verified lineage advancement.

Fresh generation-zero worlds choose their initial shared spawn before the world accepts players. World creation searches outward through the configured safe temperate biomes for up to 60 seconds per seed. It tries the supplied seed first, then new random seeds until a candidate is found. Generated terrain and the final spawn-feet biome are checked before publishing the shared spawn; a rejected site never falls back to vanilla spawn. The selected biome and search-attempt summary appear in the server log; ordinary production diagnostics omit the seed and exact position. Presets whose Overworld biome source cannot produce a configured biome use their normal spawn behavior. Existing worlds keep their shared spawn.

Successor biome preferences are resolved in their configured order by the same bounded search. A changed request or stopping server cancels the successor cursor. Exhausting the search or reaching its 4,000-tick deadline retains the generated world's shared spawn and publishes an unresolved health result so the supervisor can safely retry or roll back.

Create Schematicannons gain persistent per-cannon material substitutions. Open the normal cannon menu, select a required ordinary block, then click the Fallback ghost slot while carrying the replacement block. The server validates the rule, pauses an active cannon when rules change, preserves compatible block-state properties, and only substitutes when the original is unavailable. Fluid-containing blocks, block entities, multi-item requirements, cycles, and self-substitutions are rejected. Native uses of the fallback material are reserved before substitutions consume it.

## Development

Java 17 is required. The checked-in Gradle wrapper provides the supported build and validation entrypoints:

```sh
./gradlew verifyFast
./gradlew verifyFull
./gradlew verifyFull stageRuntimeJar
```

`verifyFast` runs the JVM tests. `verifyFull` also runs the Forge GameTests. `stageRuntimeJar` copies the reobfuscated production artifact to `build/libs/better-world-management-0.1.0.jar`; deploy that canonical staged JAR.

The non-shipping `visualHarness` source set validates the real Create menu. Run the `visualServer` and `visualClient` configurations under Xvfb, then issue `wlmvisual prepare <player>` and `wlmvisual capture <player> <name>` through the dedicated-server console. The server commands create the fixture and open the menu; the client harness only waits for that screen and invokes Minecraft's screenshot API. It does not inject player movement, mouse, keyboard, or gameplay controls.

The matching sibling pack repository is [better-content/better-content-modpack](https://github.com/better-content/better-content-modpack). Its `better-world-management-server.sh` supervisor owns archive publication, successor health, retry, rollback, and exactly-once lineage advancement.

## License

World Lifecycle Manager is licensed under the GNU Affero General Public License version 3 or later. See `LICENSE`.


## Identity

The clean-break canonical identity is repository/artifact `better-world-management`, mod ID and resource namespace `better_world_management`, and Maven group `com.bettercontent`. Legacy `prestige` identifiers and persisted paths are not migrated.
