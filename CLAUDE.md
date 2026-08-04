# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

"Turnkey Factory" (mod id `turnkey_factory`, Java package `dev.aurelien.prefab`) is a NeoForge mod for
Minecraft 1.21.1 / NeoForge 21.1.234. It adds three machine blocks that automate terraforming and
construction:

- **Controller** — builds a complete rectangular factory shell (walls, roof, windows, doors, exterior
  decoration) around itself from a ghost preview, pulling materials from linked inventories.
- **Leveler** — flattens a rectangular patch of terrain to a target height (cut + fill).
- **Texturizer** — re-textures natural ground around itself in a fixed mosaic pattern, radiating outward
  from the block.

Optional soft-dependency integration with the **Create** mod (and **Create Deco**) adds themed
decorative blocks (metal girders, window panes, cage lamps, catwalks) when those mods are present, with
graceful fallback to vanilla blocks when they are not.

## Build & run commands

Gradle wrapper only — always use `gradlew`/`gradlew.bat`, never a system Gradle.

```
./gradlew build           # compile + build the mod jar
./gradlew runClient        # launch a dev client with the mod loaded
./gradlew runServer         # launch a dev dedicated server
./gradlew runData           # run NeoForge data generators (writes to src/generated/resources)
./gradlew runGameTestServer # run GameTestServer (no gametests are currently registered in this mod)
```

There is no JUnit/unit test suite in this repo; correctness is verified in-game via `runClient`/
`runGameTestServer`. Before considering a change to build logic (block placement, cost model, terrain
detection) complete, prefer to actually run it in `runClient` when feasible.

Python texture-generation scripts live in `tools/` (`gen_controller_textures.py`,
`gen_leveler_textures.py`, `gen_texturizer_textures.py`, `gen_mod_icon.py`) and share pixel-art helpers
from `tools/pixelart_common.py` (Bayer dithering + deterministic grime speckle over flat palette fills).
Run them with a plain `python tools/<script>.py`; they write directly into
`src/main/resources/assets/turnkey_factory/textures/`.

## Repo-specific notes

- `/com/` at the repo root is **decompiled Create mod classes kept as a local dev reference only** — it
  is git-ignored (see `.gitignore`) and not part of this project's source. Do not edit it or treat it as
  project code.
- `mod_group_id` is `dev.aurelien.prefab`, but the mod id / resource namespace is `turnkey_factory` (set
  in `gradle.properties`). Assets, lang files, and data live under
  `src/main/resources/{assets,data}/turnkey_factory/`.
- Mod metadata (`neoforge.mods.toml`) is generated from `src/main/templates/META-INF/neoforge.mods.toml`
  by the `generateModMetadata` Gradle task, which expands `${...}` placeholders from `gradle.properties`
  (`mod_version`, `neo_version`, etc.). Edit the template, not a generated file.
- Comments and Javadoc in this codebase are written in French; match that convention when adding
  comments to existing files.

## Architecture

### Block / BlockEntity / Menu / Screen per machine

Each of the three machines follows the same NeoForge pattern, split across four classes named after the
machine (e.g. `Controller*`):

- `block/<Name>Block.java` — the `Block` subclass (placement, interaction, block entity ticker).
- `block/<Name>BlockEntity.java` — all state and logic (dimensions, scan/build ticking, persistence via
  `saveAdditional`/`loadAdditional`, and the transient sync payload via `getUpdateTag`). This is where
  almost all of the interesting logic lives.
- `menu/<Name>Menu.java` — the `AbstractContainerMenu` wiring server-side inventory slots to the screen.
- `client/<Name>Screen.java` — the client-side GUI (`AbstractContainerScreen`).

Registries live in `reg/` (`ModBlocks`, `ModItems`, `ModBlockEntities`, `ModMenus`, `ModCreativeTabs`),
all `DeferredRegister`-based and registered from `PrefabMod`'s constructor. `PrefabModClient` registers
client-only concerns (screens) via `@EventBusSubscriber(value = Dist.CLIENT)`.

### Client/server sync via custom payloads

Config changes (dimensions, offset, style, per-machine settings) and actions (start/cancel build) are
sent client→server as `CustomPacketPayload` records under `network/`, one record per action
(`SetDimsPayload`, `SetOffsetPayload`, `BuildActionPayload`, etc.), each with a `STREAM_CODEC` and a
static `handle()`. All payloads are registered together in `PrefabMod#registerPayloads`. Server→client
state (ghost preview data, build progress, material availability) rides back on the block entity's
`getUpdateTag`/`loadAdditional` via `level.sendBlockUpdated`, not a dedicated payload.

### Controller build pipeline

1. `ControllerBlockEntity.buildingMinMax()` / `reservedMinMax()` compute the shell footprint and the
   reserved footprint-plus-margin box (margin defined by `ExteriorDecorator.MARGIN`/`MARGIN_UP`) relative
   to the controller, facing, and per-axis offset.
2. `BuildPlanner.planMap()` classifies every cell in the shell into a `BlockState` (or leaves interior
   cells untouched — the mod builds a shell around an existing space, not a filled volume), then layers
   on roof geometry (flat slab, or `addPitchedRoof`/`addCeilingBeams`/`pitchedSkylights` for a pitched
   roof) and hands off to `ExteriorDecorator.decorate()` for exterior trim (pillars, eaves, lighting).
   `BuildStyles.of(theme)` supplies the `BuildStyle` (per-role block palettes) and `CostModel.costOf()`
   turns any `BlockState` into its material cost.
3. `ControllerBlockEntity` caches the plan (`cachedPlan`/`cachedFree`/`cachedBom`) and invalidates it on
   any config change (`onConfigChanged`). `recomputeCollisions()` diffs the plan against real world state
   to drive the red/green ghost preview and to block/allow starting a build (`BuildStartMode`: NORMAL
   refuses on obstruction, FORCE overwrites, IGNORE removes obstructed cells from the plan and builds
   around them).
4. During a build, `tickBuild()` drains `buildQueue` (ordered by `BuildPlanner.order()` — walls/floor
   first, glass panes/bars last so they connect correctly on placement) at a fixed rate, pulling
   materials from linked inventories via `InventoryNetwork`/`CostModel`, and a missing item for one
   placement no longer blocks the rest of the queue. A build in progress survives a chunk
   reload/restart via `pendingResume`/`resumeBuild()` (the queue itself isn't persisted, only "a build was
   in progress" — the plan is recomputed and already-placed blocks are skipped).

### Linked inventories

`InventoryNetwork.rescan()` does a 6-directional BFS flood-fill from the machine to find connected
`IItemHandler` inventories (chests, etc.); all three machines (Controller, Leveler, Texturizer) use this
same mechanism to source materials, re-scanning periodically rather than only on placement/breakage.

### Terrain safety heuristic

`NaturalTerrain.isNaturalGround()` is the single source of truth for "is this a block the world
generated, safe to bulldoze" vs. "is this something a player built, must be protected." It's a
tag/blockstate heuristic (Minecraft doesn't track placement provenance), used identically by the
Controller's obstruction detection, the Leveler's cut/fill, and the Texturizer's mosaic — so a false
negative there (a modded block not covered by any known tag) affects all three machines.

### Create integration

`compat/CreateCompat.java` has **zero compile-time dependency** on Create — it resolves blocks by
`ResourceLocation` from the runtime registry (`BuiltInRegistries.BLOCK.getOptional(...)`) after checking
`ModList.get().isLoaded("create"/"createdeco")`, falling back to `Blocks.AIR` (treated as "absent, use
vanilla instead") everywhere. Create/Create Deco are declared as `localRuntime` (not `runtimeOnly`) in
`build.gradle`, so they're available in the dev environment for testing this integration but are never
pulled in as a dependency of the published mod.

<!-- code-review-graph MCP tools -->
## MCP Tools: code-review-graph

**IMPORTANT: This project has a knowledge graph. ALWAYS use the
code-review-graph MCP tools BEFORE using Grep/Glob/Read to explore
the codebase.** The graph is faster, cheaper (fewer tokens), and gives
you structural context (callers, dependents, test coverage) that file
scanning cannot.

### When to use graph tools FIRST

- **Exploring code**: `semantic_search_nodes` or `query_graph` instead of Grep
- **Understanding impact**: `get_impact_radius` instead of manually tracing imports
- **Code review**: `detect_changes` + `get_review_context` instead of reading entire files
- **Finding relationships**: `query_graph` with callers_of/callees_of/imports_of/tests_for
- **Architecture questions**: `get_architecture_overview` + `list_communities`

Fall back to Grep/Glob/Read **only** when the graph doesn't cover what you need.

### Key Tools

| Tool | Use when |
| ------ | ---------- |
| `detect_changes` | Reviewing code changes — gives risk-scored analysis |
| `get_review_context` | Need source snippets for review — token-efficient |
| `get_impact_radius` | Understanding blast radius of a change |
| `get_affected_flows` | Finding which execution paths are impacted |
| `query_graph` | Tracing callers, callees, imports, tests, dependencies |
| `semantic_search_nodes` | Finding functions/classes by name or keyword |
| `get_architecture_overview` | Understanding high-level codebase structure |
| `refactor_tool` | Planning renames, finding dead code |

### Workflow

1. The graph auto-updates on file changes (via hooks).
2. Use `detect_changes` for code review.
3. Use `get_affected_flows` to understand impact.
4. Use `query_graph` pattern="tests_for" to check coverage.
