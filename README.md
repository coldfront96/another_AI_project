# EmergentCiv

A NeoForge 1.21 Minecraft mod: an emergent civilization simulation where AI-driven
agents will eventually inhabit and learn this world.

This is a multi-phase project. **Phase 1 (current) is world/gameplay systems only —
no AI integration yet.**

## Phase 1 scope

- Standard NeoForge 1.21 mod project (Gradle, `mdk`-style conventions).
- `SettlerEntity` (`com.coldfront96.emergentciv.entity.SettlerEntity`), a custom
  `PathfinderMob` with an attached `NeedsComponent` (hunger, energy, social — each a
  decaying float in `[0, 100]`).
- No artificial safety walls (see `docs/DESIGN.md`): there is deliberately no coded
  permission gate on what settlers may interact with. `ResourceKind` describes what
  settlers know to *seek* (wood, stone, berries), but the only thing deciding an
  attempt's outcome is vanilla's own mechanics (correct tool required to get a drop,
  etc.). Failed attempts are logged as training signal.
- A day/night-driven need decay tick handler (`NeedsDecayHandler`), which applies
  daily decay to every `SettlerEntity` in a level.
- Scripted (not LLM-driven) survival goals: `GatherResourceGoal` gathers/consumes a
  nearby resource to restore the associated need, with `SimpleWanderGoal` as the
  explore-for-more fallback once a need crosses its threshold.
- A settler-proximity social tick (`SocialProximityHandler`) and a JSONL state
  snapshot logger (`StateSnapshotLogger`) that captures per-settler traces for the
  future training pipeline.

## Project layout

```
src/main/java/com/coldfront96/emergentciv/
  EmergentCivMod.java           - mod entry point, registration wiring
  registry/ModEntities.java     - entity type + attribute registration
  entity/SettlerEntity.java     - Settler entity
  entity/component/NeedsComponent.java - hunger/energy/social needs
  gate/ResourceKind.java        - seekable resources + vanilla-consequence harvest
  goal/GatherResourceGoal.java  - scripted gather/consume survival goal
  goal/SimpleWanderGoal.java    - scripted explore/wander fallback goal
  event/NeedsDecayHandler.java  - day/night-driven need decay
  event/SocialProximityHandler.java - settler-proximity social restore tick
  event/StateSnapshotHandler.java   - snapshot logging cadence
  logging/StateSnapshotLogger.java  - per-settler JSONL state traces
  logging/StateSnapshotConfig.java  - snapshot interval / log path config
src/main/resources/
  META-INF/neoforge.mods.toml
```

## Building

Standard Gradle NeoForge MDK workflow:

```
./gradlew build
./gradlew runClient
```

## Roadmap

- **Phase 1 (this repo state):** world/gameplay systems — Settler entity, needs,
  scripted survival goals, social tick, state snapshot logging. No AI integration.
- **Phase 2 (not started):** external agent-mind bridge. Look for `TODO(Phase 2)`
  markers in the code (e.g. `NeedsComponent`, `SettlerEntity`, `SimpleWanderGoal`,
  `NeedsDecayHandler`) for the intended hook points where an external AI process
  will observe settler state and drive decisions.
