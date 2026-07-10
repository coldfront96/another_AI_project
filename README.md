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
- A stone-age resource/crafting gate (`StoneAgeResourceGate`): settlers can only
  interact with wood, stone, and berries (see the `emergentciv:stone_age_*` item
  tags); no vanilla tech tree beyond that tier.
- A day/night-driven need decay tick handler (`NeedsDecayHandler`), which applies
  daily decay to every `SettlerEntity` in a level.
- A placeholder AI goal (`SimpleWanderGoal`) — scripted, not LLM-driven — that makes
  settlers wander toward a nearby point once a need crosses its threshold.

## Project layout

```
src/main/java/com/coldfront96/emergentciv/
  EmergentCivMod.java           - mod entry point, registration wiring
  registry/ModEntities.java     - entity type + attribute registration
  entity/SettlerEntity.java     - Settler entity
  entity/component/NeedsComponent.java - hunger/energy/social needs
  gate/StoneAgeResourceGate.java- stone-age resource allow-list
  goal/SimpleWanderGoal.java    - scripted placeholder AI goal
  event/NeedsDecayHandler.java  - day/night-driven need decay
src/main/resources/
  META-INF/neoforge.mods.toml
  data/emergentciv/tags/items/  - stone_age_wood / stone / berries tags
```

## Building

Standard Gradle NeoForge MDK workflow:

```
./gradlew build
./gradlew runClient
```

## Roadmap

- **Phase 1 (this repo state):** world/gameplay systems — Settler entity, needs,
  stone-age gate, scripted wander goal. No AI integration.
- **Phase 2 (not started):** external agent-mind bridge. Look for `TODO(Phase 2)`
  markers in the code (e.g. `NeedsComponent`, `SettlerEntity`, `SimpleWanderGoal`,
  `NeedsDecayHandler`) for the intended hook points where an external AI process
  will observe settler state and drive decisions.
