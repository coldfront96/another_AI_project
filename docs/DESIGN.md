# Design Document v1

## Vision

An emergent civilization simulation: small populations of AI-driven "settlers"
living inside a Minecraft world, starting at stone-age capability, learning and
growing through lived experience rather than pre-loaded knowledge. The goal is
functional ignorance, not literal ignorance — models retain general language/
reasoning ability, but in-character decisions are grounded only in what an agent
has actually observed or been taught in-world, not open-book model knowledge.

Built from the ground up: the base model is used purely as a general reasoning
engine (like an off-the-shelf "language cortex"). Everything that makes an agent
this specific settler — memory, identity, knowledge restriction, teaching,
mortality, inheritance — is custom-built for this project, not borrowed from an
existing agent-simulation framework.

## Architecture (four layers)

- **World layer** (NeoForge 1.21.1 mod, Java) — terrain, entities, needs,
  resources, day/night, mortality, breeding. Owns ground truth game state.
- **Bridge layer** — lightweight local API (socket/HTTP) streaming per-agent
  observations out and accepting actions back in. Same architectural pattern
  as the Hermes Discord-approval bridge already built for Khimaira.
- **Mind layer** (external, Python) — one inference process per active agent.
  Perceive → retrieve memory → reflect → plan → act loop.
- **Training/identity layer** — weekly LoRA bake per agent (personal adapter
  on a shared base model) + a master model that absorbs dead agents' experience.

## World design

- **Mod roster is a world-gen decision**, made early. Terrain features (ore
  veins, structures) lock in permanently the moment a chunk first generates —
  mods added after settlers have already explored an area will not retroactively
  populate it. Decide the eventual mod roster (e.g. Create, others TBD) before
  significant world exploration happens, even though agents won't have the
  capability/gating to meaningfully use advanced content until much later.
- **No artificial safety walls.** Reject hard-coded resource-tier gates (e.g. a
  block on mining iron with a wood pickaxe as a coded permission check). Instead
  rely on vanilla's already-existing natural consequences (wrong tool = no drop,
  raw food = less saturation, poison = harm) as the actual teacher. Failed
  attempts are logged with the same importance as successes — arguably more
  valuable as training signal, since this is where "figuring it out" behavior
  lives.
- **No failsafe world.** Consequences, including permanent ones (explosions,
  death, village-wide loss), are real. This mirrors real-world learning through
  consequence (aviation safety, nuclear safety, etc.) rather than removing
  stakes.
- **Multi-village fault isolation.** Spawn multiple independent settlements
  (2-3 to start) instead of one large population. A catastrophic failure (e.g.
  a modded reactor meltdown) can wipe a village without ending the project.
  Surviving villages continue; the master model can carry forward lessons from
  a wiped village.

## Agent design

- **Needs system:** hunger / energy / social, decaying floats 0-100, drives
  scripted (Phase 1) or LLM-driven (Phase 3+) behavior once a threshold is
  crossed. (Implemented: `NeedsComponent`.)
- **Curiosity is a first-class mechanic, not an emergent hope.** Imitation-
  learned models trend toward safe/average behavior, which suppresses
  exploration. Curiosity needs to be explicitly engineered:
  - Bootstrap training data must include "investigate/try it" as a modeled
    action for unknown objects, not just "correct" actions.
  - A runtime curiosity stat (decays with novel experience, rises with
    boredom) biases the decision layer toward investigation independent of
    what the base policy would otherwise pick.
- **Observational/social learning:** witnessing another settler's outcome
  (death, harm, success) is logged as a high-importance memory event for
  nearby agents. This is the mechanism behind e.g. "watched Bjorn die eating
  red berries → avoid red berries" without needing explicit teaching.
- **Explicit teaching:** an agent "telling" another agent something is an
  in-game action that becomes a logged observation for the listener, which
  then feeds their own next weekly bake. Knowledge does not have to be
  discovered independently to propagate — only via observation or teaching.

## Population: breeding & inheritance

- New agents are not blank-slate. A new agent's starting LoRA adapter is
  initialized as a merge/interpolation of both parents' adapters — a real
  technical analog to inheritance, biased toward reinforced parental traits
  without being identical to either parent.
- **Juvenile period:** new agents get a reduced action set / follow-parent
  behavior for a simulated duration before becoming fully independent
  decision-makers. This is where "learning from birth" genuinely happens,
  applied to new agents born into an already-running world rather than
  needing to be true of every agent from project start.
- **Hard population cap,** enforced in code, not left to agent choice or
  emergent behavior. Per-village and/or global cap. Non-negotiable — prevents
  runaway population growth from overwhelming available compute/VRAM.

## Mortality & the master model

- **Semi-mortality:** death is real and final for that specific agent — no
  player-style respawn. This is a deliberate design choice; it's what makes
  death (and by extension danger, caution, and risk-avoidance behavior)
  actually mean something.
- **Master model absorption:** rather than the agent's experience simply
  vanishing, their data is folded into a persistent "master" model via a real
  training pass (same mechanism as the weekly per-agent LoRA bake, not a raw
  weight merge — weight-averaging divergent adapters risks blurry/incoherent
  interference; retraining on the actual memory-log text produces cleaner
  absorption).
- **Mass-casualty handling:** if N deaths occur within a short window
  (configurable trigger), the batch of deaths is NOT fed to the master in one
  undifferentiated pass. Default approach (tune per-incident, this is not a
  fixed rule):
  - Take full memory histories from a capped subset of the dead (e.g. 5 of 20)
    for deep absorption.
  - For the remainder, extract only the death-moment/immediate-cause context,
    not their full life history, to avoid one large correlated event dominating
    the master's training signal disproportionately to its actual lesson content.
  - Interleave mass-casualty batches with normal living-agent weekly data in
    the same training pass rather than running isolated "death-only" sessions,
    so the lesson stays grounded against everything else the master already
    knows rather than being over-generalized in isolation.
  - Spread absorption of a single large event across multiple weekly training
    cycles rather than one pass.
  - Expected/acceptable outcome: the master model generalizing caution toward
    a hazard class (e.g. "reactors need more testing/caution") is a sign the
    pipeline is working, not a failure mode — mirrors how real institutions
    generalize from real disasters.

## Data / training pipeline

- **State snapshot schema** (flat JSON, JSONL log format): self-state
  (position, facing, needs, health, held item), perception (nearby blocks/
  entities, relative position), world state (time/weather), memory context
  (top-N retrieved), action taken, outcome (deltas, death flag, novelty flag).
  (Implemented: `StateSnapshotLogger`, Phase 1.)
- **Phase 1 data source:** scripted bot self-play (`SimpleWanderGoal` and
  successors) generates real, grounded gameplay traces for free once the mod
  loop is running. Human demonstration (Joel playing/streaming alongside)
  is a second, likely higher-quality data source for judgment-call behaviors.
  Tag traces by source (bot vs. human) for later weighting/filtering.
- **Bootstrap curriculum** (categories to cover before any live model
  integration): locomotion basics, MC-specific perception primitives, needs→
  action mapping, threat response, object interaction primitives, exploration/
  investigation primitives (curiosity seed data), observational/social
  primitives, basic self-preservation heuristics.
- **Bootstrap model:** small transformer trained from scratch (nanoGPT-scale,
  tens-of-millions of parameters) on the curated situation→action dataset.
  Not a general-knowledge model — scoped narrowly to "minimum viable competence
  to not immediately die," per the TinyStories precedent (small models trained
  on narrow, curated data can produce coherent, in-domain behavior even at
  very small parameter counts).

## Voice (later phase)

- Local TTS (Piper primary, XTTS-v2 as a fallback for more expressive voices),
  randomized voice assignment per agent, persistent per agent.
- Local STT (Whisper) for player-to-agent speech.
- Tiered activation: only agents near the player (or in an active conversation)
  run the full voice pipeline; distant agents continue cheap text-only
  simulated dialogue that still feeds memory/relationships normally. Protects
  VRAM budget.

## Phase roadmap

- **Phase 1 (current):** world scaffold, needs system, scripted survival
  behavior, gather/consume loop, settler-proximity social tick, state logging.
  No AI/LLM integration yet.
- **Phase 2:** external agent-mind bridge (read/write hook into
  `NeedsComponent` / `SettlerEntity`, per existing TODO markers).
- **Phase 3:** bootstrap-trained small model wired in via the bridge, replacing
  scripted goals for participating settlers.
- **Phase 4:** weekly LoRA bake, mortality + master-model absorption, breeding/
  inheritance, multi-village fault isolation.
- **Phase 5:** voice layer, player-facing proximity chat.

## Open questions / to decide later

- Exact per-event weight cap / subset-size formula for mass-casualty batching
  (currently case-by-case judgment, not a fixed formula).
- Which future mods gate into which tech tier, and what "unlocks" a tier for
  agents (this depends on what Phase 3+ data shows about learning speed).
- Whether/how surviving villages can learn secondhand from a wiped village's
  incident (warning propagation via a fleeing survivor, vs. only via the
  master model's next bake).
