# ECS for jvm-dungeon-crawler

*Design doc (current). Updated 2026-10-09.*
*Repo: `SOS-Games/jvm-dungeon-crawler`. Inputs: MOVEMENT, DETECTION, COMBAT, NPC_AI, LOOT, TRAPS, NAV_COLLISION_PORT, SOUNDS, UI, BARONY_ENVIRONMENT, DUNGEON_GEN_PORT, VELOREN_ANIMATION, VELOREN_SHADERS, and the Ashley fork research in `/workspace/ECS_ASHLEY_FORK.md` (historical).*

---

## TL;DR

* **ECS = the cut-down Ashley fork** published at [SOS-Games/ashley-fork](https://github.com/SOS-Games/ashley-fork) (`main` @ `262b3ed`). Coordinates: `io.github.sosgames:ashley:1.7.4-sos.1`. Based on upstream Ashley **1.7.4** (`0dfdf52`). **125 tests pass** (upstream suite updated for the new multi-system semantics, plus new ref / order / lint tests).
* **Consumed by the crawler** via a Gradle **composite build** while the fork is still being proven (`includeBuild '../ashley-fork'` + `implementation 'io.github.sosgames:ashley:1.7.4-sos.1'`). Switch to JitPack later if desired.
* **What the fork adds over stock Ashley:** generational `Entity.ref()` / `Engine.resolve(long)` (refs assigned on `addEntity`, FIFO slot reuse, stale pooled refs → `null`); index-ordered iteration for the entity list and every family; multiple systems of the same class; a reflection test that bans `Entity` fields in components (store `long` refs instead).
* **Game scope:** **singleplayer only** (no networking). The player has a party of **non-LLM player-mimic bots** that share the same `Controller` / intent path as the human. They are separate from the primitive recruitable NPCs in NPC_AI. **Saves only at floor transitions** (Barony style). **No replays.** Whole party transfers on stairs. Pending timed effects / event-queue items are **dropped** on floor change.
* **libGDX in `:sim` is acceptable** (gdx core is pure Java and runs headless; verified with Ashley + gdx 1.14 on Java 21).
* **Remaining foundation work** (scheduler, events, prefabs, floor transfer, inspector, party-bot scaffolding): about **4–5.75 days**, minus the fork core which is **done**. Party-bot ECS scaffolding is another **2.25–3.25 days**.

---

## 1. Decisions (resolved)

| # | Decision | Choice |
|---|---|---|
| D1 | ECS implementation | **Ashley fork** at https://github.com/SOS-Games/ashley-fork (`262b3ed`, `io.github.sosgames:ashley:1.7.4-sos.1`) |
| D2 | How the crawler consumes the fork | **Composite build** for now (`includeBuild '../ashley-fork'`); JitPack later |
| D3 | libGDX dependency in sim / game-logic modules | **Acceptable** (headless-safe gdx core) |
| D4 | Multiplayer / networking | **None** — singleplayer only |
| D5 | Save model | **Floor-transition only** (Barony style). Mid-floor state is not saved. |
| D6 | Replays | **No** (dev golden tests may still hash selected state) |
| D7 | Stairs / floor transfer party filter | **Whole party always comes along** (player, party bots, followers, carried items) |
| D8 | Pending timed effects at floor change | **Drop** (TimerWheel / event queues cleared; long-lived state lives in components) |
| D9 | Party bots vs recruitable NPCs | **Separate.** Bots = player-mimic via shared `Controller`. Recruits = primitive NPC_AI brains. |

Historical alternatives (custom ECS, stock Ashley + wrappers, Fleks, Artemis-odb, Dominion) and the pre-decision research live in **§Appendix A** and `/workspace/ECS_ASHLEY_FORK.md`.

---

## 2. Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│  :lwjgl3 / :core  (libGDX client)                               │
│    HumanInput → Controller   render systems   HUD / bubbles     │
└───────────────────────────────┬─────────────────────────────────┘
                                │
┌───────────────────────────────▼─────────────────────────────────┐
│  :sim  (gameplay; may depend on gdx core)                       │
│    components (§3)  systems (§4)  party / dialogue / loot / …   │
│    add-ons: sched · event · prefab · FloorTransfer · debug      │
└───────────────────────────────┬─────────────────────────────────┘
                                │
┌───────────────────────────────▼─────────────────────────────────┐
│  Ashley fork  (composite build → io.github.sosgames:ashley)     │
│    Engine, Entity, Family, EntitySystem, PooledEngine           │
│    + Entity.ref() / Engine.resolve() / EntityRef                │
│    + index-ordered iteration · multi-instance systems           │
└─────────────────────────────────────────────────────────────────┘
         │                              │
         ▼                              ▼
   :dungeon-gen                   world services
   (TileGrid, …)          (Bullet, Nav, SpatialGrid, …)
```

**World services stay outside the ECS** (systems get them through a `Services` registry, not components): `BlockVolume` / `OpacityGrid`, `LightField`, `TileGrid`, `NavWorld` / `PathService`, Bullet `PhysicsWorld`, `SoundPropagator`, `SpatialGrid`, `FactionTable`, data catalogs (items, abilities, roles, personalities, barks), and the `ChatLog` model.

---

## 3. Component catalog

Components are plain Java data classes implementing Ashley's `Component` marker. Complex logic objects (brain rule list, character state machine, attack stages) are **one component holding a normal object**. Cross-entity links are **`long` refs** (`Entity.ref()`), never `Entity` fields — enforced by copying `ComponentEntityFieldTest` into the game's tests.

| Group | Component | Fields (abridged) | Source |
|---|---|---|---|
| **Core** | `Transform` | pos, yaw, pitch; prevPos/prevYaw for render interpolation | MOVEMENT |
| | `Velocity` | vel, impulses (knockback, decaying) | MOVEMENT |
| | `Name` / tag flags | debug name, archetype id, bit tags (Player, Bot, Npc, Item, Trap…) | all |
| | `Lifetime` | despawnAtTick (projectiles, particles, corpses) | COMBAT, TRAPS |
| **Control** | `Controller` *(shared)* | `InputFrame` (moveDir, yaw/pitch delta, jump, sneak, roll, attack press/hold/release, block, use, quickTurn, hotbar slot), `source` enum {HUMAN, BOT, NPC_AI, SCRIPT}, lastFrame for edge detection | MOVEMENT, NPC_AI, scope |
| | `CharacterState` | sealed `CharState` (Idle/Wield/Roll/Melee/Ranged/Block/Stunned/Swim/Dead) + StageSection + timer | MOVEMENT, COMBAT |
| | `PhysicsBody` | capsule radius/height, stepUp/Down, flags onGround/onWall/inLiquid, handle into Bullet world (not owned) | NAV_COLLISION_PORT, MOVEMENT |
| **Body / render** | `Body` | species, body kind, scale; `SkeletonDef` key | VELOREN_ANIMATION |
| | `Skeleton` | Bone[16] current + target, blend state | VELOREN_ANIMATION |
| | `AnimState` | anim id, phase, accumulated distance (`acc_vel`), layer for attacks | VELOREN_ANIMATION |
| | `FigureRender` *(client-only)* | per-bone mesh handles, tint, flash timer | VELOREN_ANIMATION, VELOREN_SHADERS |
| | `Viewmodel` *(client-only, player-controlled)* | FP hand/weapon meshes | MOVEMENT |
| | `LightSource` | colour, radius, flicker | BARONY_ENVIRONMENT, DETECTION |
| **Stats / combat** | `Health`, `Energy`, `Mana`, `Poise` | value/max/regen | COMBAT |
| | `Attributes` | STR/DEX/CON/INT/PER/CHR, level, XP, proficiencies[] | COMBAT |
| | `Buffs` | active buff list (kind, strength, ticks left, source ref) | COMBAT |
| | `AbilitySet` | ability defs from weapon/tool kind | COMBAT |
| | `Projectile` | def, owner ref, hit-once set, gravity | COMBAT |
| | `Hitbox` overrides | head band, resist row | COMBAT |
| **Inventory / loot** | `Inventory` | slots, gold, weight cache | LOOT |
| | `Equipment` | Barony 9 slots (or Veloren slots), item stacks | LOOT |
| | `WorldItem` | `ItemStack` (identified, blessed/cursed, condition, ownerRef, droppedAtTick) | LOOT |
| | `LootTable` | table id, pre-rolled drops (seeded) | LOOT |
| | `Container` | chest theme, locked, mimic flag, opened | LOOT |
| **Perception / AI** | `Perception` | profile, `MemoryRecord[8]`, awareness state, nextThinkTick, bucket | DETECTION |
| | `Faction` | base faction, overrides | DETECTION |
| | `NoiseEmitter` | footstep throttle, last emission tick | DETECTION |
| | `Brain` (NPC) | role, current Mode, `RuleSet` ref, `AgentMemory`, rng, trace ring | NPC_AI |
| | `Leader` / `Follower` | follower: leaderRef, slot, commandInbox; leader: roster | NPC_AI |
| | `Social` | sentiments map, wanted levels | NPC_AI |
| | `PathAgent` | cached corridor, path request handle, stuck timer | NPC_AI, NAV |
| **Party bots** | `PartyMember` | partyId, slot, role (tank/healer/archer/caster/scout), leaderRef (= player), regroup radius, downed | scope |
| | `BotBrain` | player-mimic planner: current goal (Fight/Loot/Avoid/Heal/Follow/Explore/Interact), goal scores, target refs, rng, decision trace | scope |
| | `Personality` | data id → traits (bravery, greed, caution, chattiness, humor, reaction ms mean/jitter, aim error, loot priorities, voice/bark set) | scope |
| | `ReactionQueue` | ring of pending percepts (stimulus, deliverAtTick) | scope, DETECTION |
| | `KnownHazards` | trap cells seen/triggered, danger heat; cleared on floor transfer | TRAPS, scope |
| | `DialogueState` | per-topic cooldown ticks, last line ids, mood | scope, NPC_AI |
| | `ChatBubble` *(client-only)* | text, expireTick, offset | scope, UI |
| **World objects** | `Trap` | kind, armed/sprung, rearmAtTick, trigger filter, wire network id | TRAPS |
| | `Trigger` | volume (AABB / tile), filter | TRAPS |
| | `Door` | open/closed/locked, key id, sound muffling, nav flag | DETECTION, NAV |
| | `Interactable` | verb list (open, pull lever, talk, trade, loot) | NPC_AI, LOOT |
| | `SoundSource` *(client)* | looping SFX id, cooldown | SOUNDS |
| | `Persistent` | marks entities saved across floors (player, party bots, followers, carried items) | D7, NPC_AI |

---

## 4. System catalog, phases, rates and order

One **fixed 60 Hz sim step**. Within it, systems run in a fixed declared order (Ashley priorities in phase bands). Slower systems run in **stagger buckets**: at 5 Hz an entity is processed when `(tick + entity.getIndex()) % 12 == 0`, and at 10 Hz when `% 6 == 0`. Render runs per frame with `alpha = accumulator / step`.

| # | Phase | System | Rate | Notes |
|---|---|---|---|---|
| 0 | PRE | `CommandFlush` / Ashley op flush | every tick | deferred spawns/despawns/component ops; ids already assigned on `addEntity` |
| 1 | INPUT | `HumanInputSystem` | 60 Hz | latched libGDX input → player's `Controller` |
| 2 | SENSE | `SpatialGridSystem` | 60 Hz | rebuild uniform grid |
| 3 | SENSE | `PerceptionSystem` | 5 Hz idle / 10 Hz engaged / 1 Hz far LOD, staggered, ray budget | also posts percepts to bots' `ReactionQueue` |
| 4 | SENSE | `StimulusDrainSystem` | 60 Hz | StimulusBus → SoundPropagator → memories |
| 5 | SENSE | `ReactionDelaySystem` | 60 Hz | releases bot percepts whose `deliverAtTick ≤ now` into `BotBrain` |
| 6 | THINK | `NpcBrainSystem` | 5–10 Hz staggered | NPC_AI rule list → Mode |
| 7 | THINK | `PartyBotBrainSystem` | 10 Hz staggered (5 Hz out of combat) | player-mimic goal selection |
| 8 | THINK | `PathRequestSystem` | budgeted per tick | recast queries |
| 9 | ACT | `NpcMotorSystem` / `BotMotorSystem` | 60 Hz | Mode or goal → `Controller.InputFrame` |
| 10 | ACT | `CharacterSimSystem` | 60 Hz | MOVEMENT `CharacterSim` for **every** entity with a `Controller` |
| 11 | ACT | `PhysicsSystem` | 60 Hz | Bullet capsule sweeps, push-apart |
| 12 | ACT | `ProjectileSystem` | 60 Hz | swept sphere vs capsules and blocks |
| 13 | RESOLVE | `MeleeHitSystem` → `DamageSystem` | 60 Hz | hit frame → DamagePipeline → CombatEvents |
| 14 | RESOLVE | `TrapSystem` / `TriggerSystem` | 60 Hz + timers | TRAPS |
| 15 | RESOLVE | `BuffSystem`, `RegenSystem` | 60 Hz or 4 Hz DoT | COMBAT |
| 16 | RESOLVE | `DeathSystem` | 60 Hz | loot drop, follower revert, XP, bot downed |
| 17 | RESOLVE | `PickupSystem` / `InteractionSystem` | 60 Hz | items, doors, chests, talk |
| 18 | SOCIAL | `DialogueSystem` | 60 Hz (queue-driven) | event → lines → delayed replies → ChatLog + bubbles |
| 19 | SOCIAL | `LeadershipSystem`, `PartySystem` | 1 Hz staggered | formations, regroup, leader death |
| 20 | POST | `LifetimeSystem` | 60 Hz | despawn expired |
| 21 | POST | `EventDispatch` | 60 Hz | deliver to listeners (sound, UI, stats); clear per-tick queues |
| 22 | POST | `HashCheckSystem` *(tests only)* | every N ticks | determinism hash of selected components |
| R1 | RENDER | `AnimationSystem` | per frame | skeleton blend, interpolated transforms |
| R2 | RENDER | `FigureRenderSystem`, `ViewmodelSystem`, `LightUploadSystem` | per frame | ForwardRenderer |
| R3 | RENDER | `AudioSystem` | per frame | SOUNDS |
| R4 | RENDER | `HudSystem`, `ChatBubbleSystem`, `DebugOverlaySystem` | per frame | UI |

Render systems **only read** sim components (plus client-only components). A headless run omits them.

**Not in the pipeline:** `ReplayInputSystem` (D6 — no replays). Bot and human input share the same `Controller` path; bots write frames from `BotMotorSystem`.

---

## 5. Requirements and fork status

| ID | Requirement | Status with Ashley fork |
|---|---|---|
| R1 | Plain-Java components, object refs inside OK | **Supported** (`Component` marker) |
| R2 | Component-set queries (all / one / exclude), cached | **Supported** (`Family` + `getEntitiesFor`) |
| R3 | Stable entity ids with generations; stale → null | **Supported** (`Entity.ref()` / `Engine.resolve`; assigned on `addEntity`; FIFO reuse) |
| R4 | Runtime add/remove of components, deferred visibility | **Supported** (Ashley deferred family ops) |
| R5 | Spawn from JSON archetypes/prefabs with overlays | **Add-on** (`prefab` package) |
| R6 | Pooling without stale-reference hazards | **Supported** (`PooledEngine` + generational refs; never store `Entity` in components) |
| R7 | Typed event queues + tick-delayed delivery | **Add-on** (`event` package). **Not saved** (D5/D8) |
| R8 | Dialogue bus (cooldowns, rate limit, delayed multi-replies) | **Add-on** (`DialogueSystem` over R7) |
| R9 | Shared `Controller` (human / bot / NPC / script) | **Supported** (component + design rule) |
| R10 | Party / teammate model | **Supported** (components + family listener) |
| R11 | Spatial / faction queries | **Add-on** (`SpatialGrid` service; planned anyway) |
| R12 | Fixed 60 Hz step + render interpolation | **Add-on** (`FixedStepLoop`; prev/curr transforms) |
| R13 | Staggered and budgeted systems | **Add-on** (`StaggeredIteratingSystem`) |
| R14 | Deterministic iteration within a run | **Supported** (index-ordered families). Cross-load determinism via identical floor-arrival path (§8), not mid-floor saves |
| R15 | Ordered phases; several instances of one system class | **Supported** (priority bands + fork multi-instance) |
| R16 | Save/load | **Floor-only** (§8): persistent subset + run state. No mid-floor world / events / timers |
| R17 | Floor transitions | **This is the save system** (§8). Whole party (D7) |
| R18 | Replays | **Dropped** (D6). Keep cheap state hash for golden tests only |
| R19 | Headless | **Supported** (gdx core headless; no GL). Bullet behind an interface / stub Mover in tests |
| R20 | Debug inspector | **Add-on** over `getEntities()` / `getComponents()` |
| R21 | Entity listeners / lifecycle hooks | **Supported** (`EntityListener` per family) |
| R22 | Performance: hundreds–thousands of entities; ≤ 0.5 ms ECS overhead | **Supported** (fork churn ~same order as stock) |
| R23 | Client-only components out of saves and sim hash | **Add-on** (marker / codec filter) |

---

## 6. Module and package layout

```
# settings.gradle (crawler)
includeBuild '../ashley-fork'          // composite; clone next to the crawler
include 'core', 'lwjgl3', 'sim', 'dungeon-gen'

# sim (or core) dependencies
implementation 'io.github.sosgames:ashley:1.7.4-sos.1'
// remove any com.badlogicgames.ashley:ashley so classes aren't duplicated

:ashley-fork  (external repo; Apache-2.0)
  com.badlogic.ashley.core      Engine, Entity, EntityRef, Family, PooledEngine, …
  com.badlogic.ashley.systems   IteratingSystem, IntervalSystem, …
  (see fork README / CHANGES.md)

:sim  (gameplay; may use gdx utils)
  io.github.jvmdc.sim.comp        Transform, Velocity, Controller, CharacterState, Health, … (§3)
  io.github.jvmdc.sim.sys         systems from §4
  io.github.jvmdc.sim.sched       FixedStepLoop, Phase priorities, TickClock, StaggeredIteratingSystem, timings
  io.github.jvmdc.sim.event       EventQueue<E>, Events registry, TimerWheel (unsaved; cleared on floor change)
  io.github.jvmdc.sim.prefab      PrefabLibrary (JSON extends + overlays), Spawner
  io.github.jvmdc.sim.io          FloorTransfer (capture / restore), run-state save file
  io.github.jvmdc.sim.move        CharacterSim, Mover
  io.github.jvmdc.sim.combat      …
  io.github.jvmdc.sim.ai          NPC brains + perception
  io.github.jvmdc.sim.party       BotBrain, goals, Personality, KnownHazards, PartySystem
  io.github.jvmdc.sim.dialogue    DialogueEvent, BarkCatalog, DialogueSystem, ChatLog model
  io.github.jvmdc.sim.loot        …
  io.github.jvmdc.sim.world       Traps, Doors, Triggers
  io.github.jvmdc.sim.debug       Inspector model
  io.github.jvmdc.sim.services    Services registry

:core  (libGDX client)
  io.github.jvmdc.client.render   FigureRenderSystem, AnimationSystem, Viewmodel, lights
  io.github.jvmdc.client.input    HumanInputSystem
  io.github.jvmdc.client.ui       HUD, ChatLogView, ChatBubbleSystem, Inspector
  io.github.jvmdc.client.audio    AudioSystem
  io.github.jvmdc.viewer          asset browser (non-ECS) + optional figure-preview world
```

If Bullet natives must stay in `:core`, put `PhysicsWorld` behind an interface in `:sim`. Headless tests use a stub `Mover` or the voxel collision port.

---

## 7. Add-ons (our code on top of the fork)

### 7.1 Scheduler

* `FixedStepLoop`: accumulator, `STEP = 1/60`, max steps, returns `alpha` for render.
* Phase priority bands: `PRE=0…99`, `INPUT=100…`, `SENSE=200…`, `THINK=300…`, `ACT=400…`, `RESOLVE=500…`, `SOCIAL=600…`, `POST=700…`. Render systems live in a separate engine, or the same engine with `setProcessing` toggles / a higher band skipped headless.
* `TickClock` service: current sim tick (no wall-clock in `:sim`).
* `StaggeredIteratingSystem`: process when `(tick + entity.getIndex()) % period == 0`, optional per-tick budget.
* Optional: wrap `update` for per-system timings (inspector).

### 7.2 Events and timers

```java
// Posted during a tick; readable by later systems the same tick; cleared in POST.
public final class EventQueue<E> {
  final ArrayList<E> items = new ArrayList<>();
  public void post(E e) { items.add(e); }
  public List<E> view() { return items; }
}

// Tick-keyed delayed delivery. NOT saved. Cleared (or flushed-then-cleared) on floor change (D8).
public final class TimerWheel {
  final TreeMap<Long, ArrayList<Object>> due = new TreeMap<>();
  public void schedule(long atTick, Object event) { … }
  public void release(long tick, Events ev) { … }
  public void clear() { due.clear(); }
}
```

Anything that must survive a floor change (buff durations, dialogue cooldowns, mood) lives in **components**, not the TimerWheel.

### 7.3 Prefabs

* JSON archetypes with `extends` and **overlays** (base monster + role + loadout; follower overlay; party-bot class + personality).
* `Spawner.spawn(prefabId, overrides, rngSeed)` → entity via `engine.addEntity` (ref valid immediately).
* Upstream Ashley `serialization` branch's tag resolver is a useful reference for component tags; we do not need its full engine serializer.

### 7.4 Shared Controller and party bots

```java
public final class Controller {
  public final InputFrame now = new InputFrame(), prev = new InputFrame();
  public Source source;
}
public enum Source { HUMAN, BOT, NPC_AI, SCRIPT }

public final class PartyMember {
  public int partyId, slot;
  public Role role;
  public long leader;            // Entity.ref() of the player
  public float regroupRadius;
  public boolean downed;
}
public final class ReactionQueue {
  public final ArrayDeque<Percept> pending = new ArrayDeque<>(8);
}
public final class BotBrain {
  public Goal goal; public long goalTarget; public float[] scores;
  public SplittableRandom rng; public final Trace trace = new Trace(32);
}
```

* Only input sources write `Controller`; only `CharacterSimSystem` reads it for movement/combat intent.
* `BotMotorSystem` translates goals into the same `InputFrame` shape the human uses (aim error, reaction delay via `ReactionQueue`, sneak, attack timing).
* Primitive recruits keep `Brain` + `NpcMotorSystem` and do **not** get party-bot crafting/chat depth (see crafting research separately).

### 7.5 Dialogue skeleton

* Event-driven bark system (L4D-style): combat/loot/trap/perception systems post `DialogueEvent`s.
* Global rate limit (draft: ≤1 line / 1.5 s), per-speaker and per-topic cooldowns, max chain depth 2, cancel if speaker dies / is downed.
* Delayed replies via `TimerWheel` (dropped on floor change — fine; in-flight banter is floor-local).
* Output: `ChatLog` model + client `ChatBubble`.

---

## 8. Floor transition = save = load

Because saves happen only on stairs (D5), **arrival always goes through the same capture → restore path** as "Continue" from disk. Mid-floor events and timers are never persisted (D8).

```
onStairs():
  // finish the current tick's deferred ops
  TimerWheel.clear(); EventQueues.clear()          // D8: drop pending timed effects
  snap = FloorTransfer.capture(engine,
           Family.all(Persistent.class).get())     // whole party (D7): player, bots, followers, carried items
                                                   // index order; long refs → local ids;
                                                   // refs to non-persistent targets → NONE
  SaveFile.write(runState, snap)                   // seed, depth, RNG states, roster, global flags, …
  engine.removeAllEntities(); services.reset()     // Bullet, nav, spatial grid, light map
  Floor floor = DungeonGen.generate(runSeed, depth + 1); spawnFloor(floor)
  FloorTransfer.restore(engine, snap, spawnPoint)  // same call used by Continue
```

* Floor-specific party knowledge (`KnownHazards`, marked targets) is cleared.
* Floor-agnostic state (personality, mood, dialogue cooldowns, follower commands, inventories) is carried over.
* Components that hold refs implement remapping (`RefHolder.remap` or codec): `Follower.leader`, `PartyMember.leader`, `Leader.roster`, item `ownerRef`; `BotBrain` targets cleared on transfer.

---

## 9. Determinism rules (within a run / golden tests)

* One master seed. Per-entity `SplittableRandom(seed ^ mix(index, generation))`; RNG state stored in the component and carried in floor snaps where needed.
* Iterate Ashley families (already index-ordered). Never iterate `HashMap` / `HashSet` of entities.
* No wall-clock reads in `:sim`. Time is `tick`.
* Allocator reuse is FIFO (fork). Command / deferred ops flush in record order (Ashley).
* Rebuild collision objects from components on floor restore. Prefer kinematic capsule sweeps (stateless per query).
* `HashCheckSystem` (tests only) hashes Transform, Health, CharacterState, BotBrain goal every N ticks.
* **No `Entity` fields in components** — copy `ComponentEntityFieldTest` into the game test suite.

---

## 10. Effort

### 10.1 Fork core — done

Published at https://github.com/SOS-Games/ashley-fork @ `262b3ed`. Generational refs (assigned on `addEntity`), index-ordered iteration, multi-instance systems, lint test, 125 tests green. **No further fork work required for foundation** unless bugs turn up in play.

### 10.2 Remaining ECS foundation (add-ons + wiring)

| Step | Content | Days |
|---|---|---|
| E1 | Composite-build wiring in the crawler; Services registry; smoke test | 0.25 |
| E2 | *(fork supplies queries/listeners/refs)* | 0 |
| E3 | Scheduler: FixedStepLoop, phase priorities, StaggeredIteratingSystem, timings | 0.5–0.75 |
| E4 | EventQueue + TimerWheel (unsaved; clear on floor change) | 0.5 |
| E5 | JSON prefabs + overlays + spawner | 0.75–1 |
| **MVP (E1–E5)** | | **~2–2.5** |
| E6 | FloorTransfer capture/restore + run-state save file | 0.5–0.75 |
| E7 | Golden headless tests + state hash (no replay) | 0.25–0.5 |
| E8 | Debug inspector | 0.5–0.75 |
| E9 | Docs (`docs/ecs.md` rules) + light benches | 0.25 |
| **Full foundation** | | **~4–5.75** (fork already done; was 4–5.75 including fork core) |

### 10.3 Party-bot ECS scaffolding

| Item | Days |
|---|---|
| `Controller` / `InputFrame` + `HumanInputSystem` refactor (single path for player, bots, NPCs) | 0.5 |
| `PartyMember`, `PartySystem`, `Personality` JSON catalog | 0.5–0.75 |
| `ReactionQueue` + `ReactionDelaySystem` | 0.25–0.5 |
| `DialogueSystem` skeleton + `ChatBubble` client system | 1–1.5 |
| **Subtotal** | **2.25–3.25** |

The actual player-mimic brain (fight, loot, trap avoidance, heal, opportunity scoring, crafting actions) is a separate design task on top of NPC_AI / COMBAT / LOOT / crafting research.

### 10.4 Suggested order

1. E1 + E3–E4 + `Controller` refactor + MOVEMENT fixed loop.
2. E5 prefabs; optional figure-preview world in the viewer.
3. Collision, navmesh, perception, NPC brains as systems.
4. Party, ReactionQueue, Dialogue skeleton.
5. E6–E8 (floor save, golden tests, inspector) before content-heavy work.

---

## 11. Consuming the fork (quick reference)

**Composite build (current choice):**

```groovy
// settings.gradle
includeBuild '../ashley-fork'

// module build.gradle
dependencies {
    implementation 'io.github.sosgames:ashley:1.7.4-sos.1'
}
```

Clone `SOS-Games/ashley-fork` next to the crawler. Remove any `com.badlogicgames.ashley:ashley` dependency.

**Later (JitPack):** tag `v1.7.4-sos.1`, then `com.github.SOS-Games:ashley-fork:v1.7.4-sos.1` from `https://jitpack.io`.

**Using refs:**

```java
public class Follower implements Component {
    public long leader = EntityRef.NONE;   // never Entity
}
follower.leader = leaderEntity.ref();      // valid immediately after addEntity
Entity leader = engine.resolve(follower.leader);  // null if gone
```

---

## Appendix A — Historical alternatives (superseded)

Pre-decision comparison. **Not the chosen path.** Kept so the rationale stays readable. Details and fork-prototype probes: `/workspace/ECS_ASHLEY_FORK.md`.

| | Custom `:ecs` | **Ashley fork (chosen)** | Stock Ashley + wrappers | Fleks 2.15 | Artemis-odb 2.3 |
|---|---|---|---|---|---|
| API | Java 21 | Java (Ashley + fork) | Java | Kotlin-first | Java |
| Activity | ours | fork done; upstream dormant | upstream dormant | active (2026) | last release 2019 |
| Stale-safe refs | build | **built in** | registry convention | EntityRef | @EntityId links |
| Order | id order | **index order** | swap-remove | id bags | bitset by id |
| Multi system / class | yes | **yes** | subclass trick | yes | yes |
| gdx-free sim | yes | no (accepted) | no | yes | yes |
| Foundation estimate (floor-only, no replay) | 5–7 d | **4–5.75 d** (fork done → remaining ~4–5.5) | 4–5.5 d | n/a (Kotlin cost) | maintenance risk |

Network / snapshot / client-prediction requirements from early drafts are **out of scope** (D4).

---

## Appendix B — Research artifacts

* `/workspace/ECS_ASHLEY_FORK.md` — historical fork research, prototype notes, pre-decision re-estimate (see note at top).
* `/workspace/ashley-fork/` — local clone of the published fork (also on GitHub).
* `/workspace/ecs_eval/` — original Ashley eval harness, results, early fork prototype/patch.
