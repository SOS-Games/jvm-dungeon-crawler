# NPC perception and detection for jvm-dungeon-crawler (Barony-style, Veloren art)

Research only. No code was changed in any repo.

Sources read:
* **Barony**, `TurningWheel/Barony` @ `962a5ce` (2026-02-20): `src/actmonster.cpp`, `src/entity.cpp`, `src/collision.cpp`, `src/light.cpp`, `src/actgeneral.cpp`, `src/actbeartrap.cpp`, `src/items.cpp`, `src/game.hpp`.
* **Veloren**, `gitlab.com/veloren/veloren` @ `ebfff94` (2026-10-07): `common/src/comp/agent.rs`, `common/src/combat.rs`, `common/src/consts.rs`, `common/src/ray.rs`, `common/src/terrain/block.rs`, `common/src/cached_spatial_grid.rs`, `common/systems/src/{melee,projectile,beam,shockwave,buff,controller}.rs`, `server/agent/src/{action_nodes,util,consts}.rs`, `server/src/sys/agent/behavior_tree/{mod,interaction}.rs`, `server/src/events/interaction.rs`.
* Prior reports in `/workspace`: `NAV_COLLISION_PORT.md`, `BARONY_ENVIRONMENT.md`, `DUNGEON_GEN_PORT.md`. `MOVEMENT.md` doesn't exist yet. Crawler checked at `6093f3a`.

> **License correction:** Barony's open-source code is **BSD-2-Clause** (`LICENSE.txt`, © Turning Wheel LLC), not GPL-3.0. That's still compatible with your GPL-3.0 crawler. Any ported logic just has to keep the BSD copyright notice in the file header. Barony's game assets are not part of the open-source release. Veloren is GPL-3.0, so credit the source file on anything you port, the same as for the shader and animation ports.

---

## 0. TL;DR

* **Barony** uses a light-as-distance model. Once per second (staggered by UID), each idle monster scans every creature. A target counts as seen if it is within the monster's `sightranges[race]`, inside a ±78.75° cone (spiders ±146°), has a clear 2D grid ray, **and** is either within 2 tiles (`TOUCHRANGE`) or closer than its **effective light value**. That value is the tile lightmap brightness (0–255, read as map units, so 16 per tile) plus observer PER×4, halved for sneaking, pulled toward 32 by the Stealth skill, and capped at 32 for invisibility. Barony has **no general hearing**. Instead it has event-specific alerts: a victim alerts friends it can see, a spotter alerts friends it can see, and noisemakers (decoy box, bell) lure monsters by path. Factions are two race×race boolean matrices plus leader and effect overrides.
* **Veloren** uses sight and sound with an awareness meter. Defaults are sight 40 m (bird_large 250, biped_large 100) and hearing 30 m. The FOV test is `dot(look, dir) > 0.15` (about ±81°). LOS is a voxel ray from eye (0.8×height) to eye, stopping on opaque blocks. Sneaking multiplies sight distance by 0.7, and armour stealth multiplies it by `1/(1+Σstealth)`. Anything within 5 m has a 30% chance per check to be sensed. Awareness rises **+1.75/s** while the target is seen and decays **−0.05/s**, with bands at 0.1/0.3/0.6/1.0. Below "alert", the agent walks to the **last known position**. Sounds are `{kind,pos,vol,time}` and fall off with distance squared, with **no wall occlusion** (there's a TODO about using pathfinding). Alignment is a small enum-pair hostility function. There's **no light-based detection**.
* **Recommendation:** combine them. Use **Barony's light-scaled detection distance** (your block light map makes this nearly free) with **Veloren's awareness meter and last-known-position search**. Add a proper **stimulus bus**, **sound flood-fill over the dungeon tile grid** so walls and doors attenuate sound, a **faction matrix** with Barony-style per-race overrides, and **Amanatides–Woo 3D DDA over the block opacity grid** for line of sight. Bullet `rayTest` is only for projectiles and hitscan. Investigating a noise uses the planned recast4j `NavMeshQuery`. With a 5 Hz staggered perception tick, 200 monsters cost well under **0.5 ms/frame** on one core.
* **Effort:** about **7–10 days** for the full system with debug overlay and tests, or **3.5–5 days** for an MVP (sight + light + factions + awareness + last-known search). This assumes the block volume and light map (BARONY_ENVIRONMENT), navmesh (NAV_COLLISION_PORT) and a moving player (MOVEMENT) exist or are built in parallel.

---

## 1. How Barony does it

Units: 1 tile = 16 map units. `TOUCHRANGE = 32` (2 tiles) and `STRIKERANGE = 24` (`game.hpp:347`). The game runs at 50 ticks/s (`TICKS_PER_SECOND`). The world is 2.5D, so every perception test is done in the XY plane.

### 1.1 Tick budgeting: the "reflex" tick
`actmonster.cpp:2785–2798`: `myReflex` is true when `ticks % 50 == uid % 25`, plus an optional one-shot `monsterExtraReflexTick`. So each monster runs target **acquisition and lost-sight checks once per second**, with the population spread over 25 tick slots. Movement and attacks still run every tick. This is the main reason Barony's O(N²) scan over `map.creatures` stays cheap.

### 1.2 Target acquisition (WAIT state, `actmonster.cpp:5234–5515`)
For each creature `entity != my` that isn't `PASSABLE` or an inert mimic:
1. **Enemy test:** `my->checkEnemy(entity) || entity is my current target || conflict ring`.
2. **Bearing:** `dir = my->yaw − atan2(dy,dx)`, wrapped to ±π.
3. **Effective light:** `light = entity->entityLightAfterReductions(*hitstats, my)` (§1.3). Liches, Shadow and spirit weapons "see invisible" (`light = 1000`), and sentry/spell bots get `+150`.
4. **Range:** `monsterVisionRange = sightranges[myRace]`. Some values: rat 128 (8 tiles), slime 80, troll 32 (!), spider 96, skeleton 192, goblin/human/demon 256 (16 tiles), lich/minotaur 512, devil 1024, shadow 768. Anything beyond that is skipped.
5. **Darkness gate:** if `dist > TOUCHRANGE && dist > light`, the target is **not seen**. The code still traces, but only so a player can roll a 1/100 Stealth skill-up. *Light is literally the detection radius in map units*, and anything within 2 tiles ignores light completely.
6. **Vision cone:** ±7π/16 (±78.75°) normally, ±13π/16 (±146°) for spiders, sentry/spell bots, and when the target is a dummy or hologram.
7. **Line of sight:** `lineTrace(my, x, y, tangent, range, LINETRACE_IGNORE_ENTITIES | friendly-fire flags, false)`, retried at `TOUCHRANGE` without ignoring entities. The target counts as seen only if `hit.entity == entity`. So **other creatures can block sight** at close range, and walls always do.
8. **On spot:** `monsterAcquireAttackTarget(target, MONSTER_STATE_ATTACK)`, play the spot sound (`MONSTER_SPOTSND`), set the player's combat-music "assailant" flag, then **alert friends**. Every other monster for which `checkFriend(my)` holds, that is in WAIT, and that isn't friendly with the target is checked with a lineTrace from the spotter. If the spotter can see it, it gets `monsterAcquireAttackTarget(target, MONSTER_STATE_PATH)`. This is a sight-gated shout, not sound.
9. **Bosses** (minotaur, liches, imp boss, corrupted automaton, hell shadow) skip perception entirely. They pick the closest player with a valid `generatePath` and chase.

### 1.3 Light, sneaking, stealth, invisibility (`entity.cpp:531–676`)
* `entityLight()`: average RGB of `lightmaps[0]` at the entity's tile (0–255). In the "fortress" map ambient is lowered by 32–40. If the tile is shaded (`light.w`), up to 80% is removed.
* `entityLightAfterReductions(myStats, observer)`:
  * `light = max(48, entityLight())`
  * `+ observer PER × 4`, so perceptive monsters see further in the dark
  * sneaking (player holding sneak and not blocking): `× 0.5` (`× 0.725` in the fortress)
  * Stealth proficiency: `light −= max(0, light − 32) × (stealth/100) × effectiveness`, which drives light toward 32 as stealth approaches 100
  * observer blind: `light = 32`. Observer under *Seek Creature*: 16. Observer currently hunting a noisemaker: 16
  * invisible: `light = min(light, 32)`, so invisible targets are only seen when adjacent
  * playing music (`ensemblePlaying`): at least 80 and +48. `EFF_DUSTED`: +48. `EFF_NOISE_VISIBILITY`: +16 × strength (set for 2 s by certain gibs, so noise makes you visible)
* **Lightmap** (`light.cpp lightSphereShadow`): each light walks every tile in a square of its radius, and a Bresenham line from the tile back to the light is blocked by the obstacle layer. It's tile-granular and recomputed when lights change.

### 1.4 Line traces (`collision.cpp:2235`, `lineTrace` / `lineTraceTarget`)
This is a classic **2D grid DDA** (Amanatides–Woo style: `dval0/dval1` are distances to the next x/y boundary, stepping the smaller one) over the tile map. It runs together with `findEntityInLine` for entity hits. The `ground` flag means pits also stop the trace for walking creatures, and flying races (bats, sentry bots, elementals) ignore it. `lineTraceTarget` returns the given target even when it's invisible or passable.

### 1.5 Losing sight and hunting (`actmonster.cpp:6060–6200`, HUNT at 7084)
While attacking, the monster updates `monsterTargetX/Y` (the **last known position**) every tick from the target's live position. On each reflex tick it re-runs the range and light test. If the target is now too far or too dark, it goes to **PATH** (path to the last known position) or **WAIT** if `shouldRetreat`. PATH and HUNT build a tile path with `generatePath(..., GENERATE_PATH_TO_HUNT_MONSTER_TARGET)` and walk it. There's no suspicion meter: detection is binary, and the 1 s reflex is the only built-in hysteresis.

### 1.6 Hearing and noise
Barony has **no general sound propagation**. Instead it has:
* **`alertAlliesOnBeingHit(attacker)`** (`entity.cpp:31454`): called from melee, arrows, thrown items and gibs. Every friend of the victim that is in WAIT and **visible from the victim** (lineTrace up to 1024 units) paths to the attacker. Infighting protection cvars stop outsiders joining fights between allies, and sleeping bats are "disturbed" instead.
* **Noisemakers**: `actDecoyBox` (`actbeartrap.cpp:1303`) and `bellAttractMonsters` (`actgeneral.cpp:5360`). These query `TileEntityList.getEntitiesWithinRadiusAroundEntity(my, decoyBoxRange = 15 tiles)`. Every idle monster that **can path there** (`monsterSetPathToLocation`) becomes the noisemaker's target in HUNT, and `monsterLastDistractedByNoisemaker` is set. When a monster arrives and sees the box, it gets `EFF_DISORIENTED` (3 s if idle, 1 s if fighting) and a 5 s `EFF_DISTRACTED_COOLDOWN`. While it's distracted, the player's effective light against it drops to 16 (§1.3). A newer decoy takes precedence.
* "Hearing" the player otherwise means being hit, being shown the player by a friend, or the player's noise or music effects raising their light value.

### 1.7 Factions and allies (`entity.cpp:18790 checkEnemy`, `19417 checkFriend`; matrices `actmonster.cpp:36, 100`)
* There are two `bool[NUMMONSTERS][NUMMONSTERS]` tables, **`swornenemies`** and **`monsterally`**, indexed by race. Players use the HUMAN row.
* Overrides are checked first: everybody-friendly mode, player vs player never, effects (`EFF_PENANCE`, `EFF_COMMAND`, `EFF_TABOO`), `monsterForceAllegiance`, special NPCs (imp boss, "bram kindly", "johann", inner demon, corrupted automaton), the shopkeeper grudge, and **leaders**. A follower uses its leader's relationship (`leader_uid`), which is how recruited allies and summons fight for the player.
* The conflict ring makes everything hostile to everything.

### 1.8 Reaction to items and objects
* `monsterAddNearbyItemToInventory(stats, rangeToFind, …)` + `monsterWantsItem(item, shouldEquip, replace)` (`entity.cpp:24671/25224`) lets races that use equipment pick up and wield better gear. Items that are still moving (thrown or dropped by others) are skipped, except the monster's own thrown weapons.
* Noisemakers, bells, traps and doors are entities that call into monster state directly, with no shared stimulus system.

### 1.9 What to take from Barony
1. **Light value = detection radius.** It's simple, readable to players, and maps directly to your block light map.
2. **Hard close-range override** (2 tiles), so stealth never makes you invisible when you're adjacent.
3. Per-race **sight range** and **cone width** (spiders and turrets wide).
4. **Spot → alert visible friends** and **hit → alert visible friends**. It's cheap and feels good.
5. **Staggered 1 s reflex.** You can do better at 5 Hz, but the staggering idea is the important part.
6. **Race matrices plus leader inheritance** for factions.
7. Noisemakers as a **lure with a cooldown and disorientation**.

What to avoid: the O(N²) scan over all creatures and the global `hit` side-channel. Neither is needed once you have a spatial hash and a pure ray function.

---

## 2. How Veloren does it

### 2.1 Data (`common/src/comp/agent.rs`)
* `Psyche` (`:254`, defaults `:440`): `sight_dist` 40 m (`BirdLarge` 250, `BipedLarge` 100, Gigas 200), `listen_dist` 30 m, `aggro_dist` 20 m for humanoids (they warn and menace first) and `None` for others (attack as soon as aware), `aggro_range_multiplier`, `flee_health` per species, and `should_stop_pursuing`.
* `search_dist() = max(sight_dist, listen_dist) × aggro_range_multiplier` (`:469`) sets the spatial-grid query radius.
* `Target { target, hostile, selected_at, aggro_on, last_known_pos }`.
* `Awareness { level ∈ [0,1], reached }` with bands `UNAWARE 0`, `LOW 0.1`, `MEDIUM 0.3`, `HIGH 0.6`, `ALERT 1.0`. `reached` latches at 1.0 and clears at 0.
* `Sound { kind, pos, vol, time }`. `SoundKind`: `Unknown, Utterance(kind, body), Movement, Melee, Projectile, Explosion, Beam, Shockwave, Mine, Trap`. `sounds_heard: Vec<Sound>` is forgotten after **180 s**.
* Event inbox: `AgentEvent::{ServerSound, Hurt, Talk, Trade…}`.

### 2.2 Sight (`server/agent/src/action_nodes.rs:2332–2390`, `util.rs:146–180`)
```text
can_see_entity = dist < sight_dist × stealthMul(target)
              && dot(normalize(tgt − me), look_dir) > 0.15          // ≈ ±81° cone (3D)
              && ray(eyeA → eyeB).until(Block::is_opaque) reaches eyeB  // eye = pos.z + 0.8·height·scale
stealthMul    = clamp( 1/(1+Σ armour.stealth) × (sneaking ? 0.7 : 1), 0, 1)   // combat.rs:2247
can_sense_directly_near = dist < 5 m && random(0.3)                 // per check, no LOS needed
detects_other = can_sense_directly_near || can_see_entity
```
`is_opaque` (`block.rs:676`): filled blocks, plus keyholes, key doors and bars sprites. Other sprites are transparent. The voxel ray (`ray.rs:98`) steps from boundary to boundary along the ray with a minimum step of 0.001. That is effectively a DDA.

There is **no lighting term**. A lantern changes nothing about detection.

### 2.3 Target selection (`action_nodes.rs:1034 choose_target`)
* Query `CachedSpatialGrid` (32 m cells, 64 m large cells, rebuilt by physics every tick) with `in_circle_aabr(pos, search_dist)`.
* Filter for valid targets. Item drops count as pickup targets: humanoids want everything, other bodies only consumables, subject to loot ownership. Living, non-invulnerable entities count, and downed allies are flagged "to save".
* Enemy filter `is_enemy`: `!passive_towards && owners' alignments hostile`, plus disguise rules (villagers attack anyone wearing >2 cultist, >3 witch or >4 pirate items). `should_defend`: a friendly entity of the same species (or both humanoid) that was just attacked makes its **attacker** the target with `aggro_on = true`.
* `detects_other` must hold, and the result is the minimum by `(is_attack_target, distance)`.
* Frequency: in idle, `choose_target` runs on a **10% chance per tick**, otherwise sounds are handled. While targeting, it re-targets every `RETARGETING_THRESHOLD_SECONDS = 10`.
* On first acquisition the agent utters Surprised or Angry. Utterances are sounds too (§2.5).

### 2.4 Awareness and memory (`server/src/sys/agent/behavior_tree/mod.rs`)
Target tree order: `update_last_known_pos → untarget_if_dead → update_target_awareness → search_last_known_pos_if_not_alert → hostile tree …`
* `update_last_known_pos` (`:719`): if `detects_other`, store the target's current position.
* `update_target_awareness` (`:789`): can see → `+1.75·dt`. Sensed nearby → `+0.25`. Otherwise `−0.05·dt` (`STD_AWARENESS_DECAY_RATE`). At **Unaware** the target is dropped.
* `search_last_known_pos_if_not_alert` (`:827`): if awareness is **≥ Low and not yet reached Alert**, path to `last_known_pos`. That's the "huh? what was that" walk.
* `target_if_attacked` (`:274`): damage within `DAMAGE_MEMORY_DURATION = 0.25 s` → awareness +1, utter Angry, and retarget to the attacker if they're more dangerous than the current target.
* So from unaware it takes about **0.57 s of continuous sight** to hit Alert, and an alerted agent with no sight stays interested for up to 20 s (1.0 / 0.05) before forgetting.

### 2.5 Hearing (`server/src/events/interaction.rs:486`, `consts.rs:37`)
```text
SOUND_TRAVEL_DIST_PER_VOLUME = 3.0
for each agent (no spatial partitioning; TODO in source):
    d² = |agent − sound.pos|²
    propagated.vol = vol − d² / (vol·3)² · vol            // 0 at d = 3·vol metres
    heard if propagated.vol > 0 && d < listen_dist
    → inbox.push(ServerSound(propagated))
```
The comment there reads *"TODO: Use pathfinding for more dropoff around obstacles"*, so walls don't block sound. Emitted volumes: melee swing **2**, projectile in flight **4** (5% chance per tick), beam, shockwave, trap (buffs), utterance (controller), NPC scream **13**, mining **20**, explosions (scaled by power). `SoundKind::Movement` exists, but nothing emits it in the current code, **so footsteps are not heard**.

`handle_sounds_heard` (`action_nodes.rs:2073`) takes the latest sound. If it's *threatening* (`vol ≥ 10` or a scream) **and within 35 m**: Enemy-aligned agents (and guards) **follow** it to the source unless they're below flee health. Everyone else **flees** from it. Other sounds are ignored, so they idle.

### 2.6 Alignment (`agent.rs:46–110`)
`Alignment = Wild | Enemy | Npc | Tame | Owned(uid) | Passive`, with three pair functions:
* `hostile_towards`: Enemy↔(anything but Enemy/Wild/Passive), and Npc→Enemy.
* `passive_towards`: Enemy↔Enemy, same owner, Npc↔Npc/Tame, Enemy↔Wild, anything→Passive.
* `friendly_towards`: Enemy↔Enemy, same owner, Npc/Tame among themselves.

Owned pets use their owner's alignment (`are_our_owners_hostile`). There's no per-species matrix, and species only matters for "defend my kin".

### 2.7 rtsim
`rtsim` simulates NPCs that are out of loaded range (travel, jobs, factions over time) and pushes high-level activities (`NpcActivity::HuntAnimals`, `Talk` …) into `Agent.rtsim_controller`. It doesn't do moment-to-moment perception. A single dungeon level doesn't need it, but the idea of a coarse **far LOD** for agents nowhere near the player does carry over (§4.7). I only skimmed it.

### 2.8 What to take from Veloren
1. **The awareness meter** with a gain rate tied to visibility and slow decay, plus the latch (`reached`).
2. **Last-known-position search** below Alert.
3. The **eye-height-to-eye-height voxel ray** in 3D (it matters with Veloren-sized creatures, pits and ledges).
4. **Multiplicative stealth** (sneak × armour) on distance.
5. The **close random sense** (5 m, 30%).
6. **Sound as a typed event with volume, and an inbox per agent.**

What to improve: sound occlusion, footsteps, light, and per-species factions.

---

## 3. General techniques (what fits this project)

| Topic | Options | Recommendation here |
|---|---|---|
| **View cone** | dot-product threshold (Veloren 0.15), yaw delta (Barony ±78.75°); separate near/peripheral cones; vertical limit | Two cones per species: *focus* (±50°, full gain) and *peripheral* (±80–100°, 0.35× gain). Within `touchRange` the cone is ignored. Use dot products on precomputed `cos` so there's no `atan2`. |
| **LOS ray** | (a) **Amanatides–Woo DDA over the block grid**; (b) Bullet `rayTest` against chunk BVHs; (c) recast `NavMeshQuery.raycast` (2D walkability, not sight) | **(a)**. It's exact for 1 m blocks, has no JNI, allocates nothing, and is thread-safe on a read-only snapshot. 50 m is ≤150 cell steps, about 0.3–1 µs. Bullet `rayTest` goes through JNI with a callback object, is only safe on the game thread, and sees mesh triangles rather than opacity (glass, bars and webs would block). Keep Bullet for projectiles and hitscan, which need entity shapes. Navmesh raycast is for "can I walk straight there" and steering, not sight. |
| **DDA corner rule** | A ray passing exactly through a corner shared by two diagonal opaque blocks can leak | Treat "both side-neighbours opaque at a simultaneous x/y step" as blocked (check tMaxX≈tMaxY within ε). Unit-test it. |
| **Multi-ray visibility** | 1 ray eye→eye; 2–3 rays (head, chest, feet) for partial cover | 2 rays (eye→target head, eye→target chest). Visible fraction 0/0.5/1 scales gain. |
| **Light-based visibility** | Sample the light map at the target cell; Barony light-as-range; Thief-style "light gem" | Sample the **baked block light** at the target's feet and head cells (max), add **dynamic light** from carried torches or spells, and map that to a 0–1 `illum`. Detection range = `lerp(darkRange, sightRange, illum)`. That's Barony's model in metres. Expose the same `illum` to the HUD as a light gem. |
| **Sound propagation** | Euclidean falloff (Veloren); graph BFS over the tile grid; navmesh path length; portal/room graph | **Bounded BFS (Dijkstra) over the dungeon tile grid** (3 m tiles) from the source cell. Walls block, closed doors add attenuation, open doors cost 0. Cost ≈ path length, so sound bends around corners. A 15-tile-radius event touches ≤ ~700 cells, about 20–50 µs. Cache per (cell, radius) for repeated footsteps. Fall back to the room graph for very loud events. Navmesh path length per listener is too costly for every event, so only use it for the chosen "investigate" target. |
| **Neighbour queries** | Uniform spatial hash; quadtree; Bullet broadphase; Veloren `SpatialGrid` | **Uniform grid over the dungeon bounds**, 4 m cells (≈ 1.33 tiles), flat `int[] head/next` linked lists rebuilt each tick (O(N), zero allocation). The level is bounded, so no hashing is needed. Reuse it for melee, separation and AI. |
| **Perception memory** | Last known position, last heard position, timestamps; per-target records; decay | Small per-agent `MemoryRecord[]` (≤ 8, LRU): `{entityId, lastSeenPos, lastSeenTime, lastHeardPos, lastHeardTime, awareness, hostile, threat}`. |
| **Alert states** | Unaware/Suspicious/Searching/Alert/Combat (Thief, MGS, Dishonored) | `UNAWARE → SUSPICIOUS (≥0.3) → ALERT (1.0, latched) → SEARCHING (lost sight) → UNAWARE`, with Veloren's bands. Show it with Veloren-style utterances and a "?" or "!" icon. |
| **Teams / factions** | Alignment enum pairs (Veloren); race×race matrices (Barony); reputation numbers | A `Faction` byte matrix of `HOSTILE/NEUTRAL/FRIENDLY`, data-driven from JSON, plus **per-species overrides** (Barony `swornenemies`), **leader/owner inheritance**, and **temporary effects** (charm, conflict, fear). |
| **Stimulus system** | Direct calls (Barony); event bus with typed stimuli (Unreal AIPerception, Veloren `SoundEvent`) | `StimulusBus` holding typed `Stimulus` records, drained once per AI tick. Producers are movement, combat, doors, items, traps and spells, so AI code never gets called from gameplay code. |
| **Tick budgeting** | Staggered updates (Barony 1 Hz by UID); probabilistic (Veloren 10%); LOD by distance; ray budget per frame | Fixed **perception tick at 5 Hz** staggered by `id % 12` at 60 fps. **10 Hz** for agents in combat. **1 Hz far LOD** for agents > 40 m from every player or in unexplored rooms. Hard **ray budget** (e.g. 400 rays/frame) with a round-robin queue. |

---

## 4. Recommended design for jvm-dungeon-crawler

### 4.1 Assumptions and dependencies
* World: `BlockVolume` in 32³ chunks, **1 unit = 1 m = 1 block**, GL Y-up for physics and nav (NAV_COLLISION_PORT §3.5). The dungeon tile grid is **3×3 blocks per tile** (BARONY_ENVIRONMENT).
* Light: the flood-fill block light (Veloren glow levels: sconce/candle 16, ember 20, lava 24, −1 per block) baked per block in a `byte[]` alongside the chunk. Dynamic point lights are the 8–20 nearest torches.
* Nav: recast4j `NavMeshQuery`, plus `detour-crowd` later.
* Movement: the player and monsters have a position, velocity, yaw and an "is sneaking" flag (the coming MOVEMENT report).
* The crawler today is a figure viewer (`JvmDcApp`, `NpcFigure`, `ForwardRenderer`, `VoxMesher`). None of this exists yet, so perception is a new package that only depends on interfaces.

### 4.2 Package and classes (`io.github.jvmdc.ai.perception`, pure Java, no GL)
```text
world/
  OpacityGrid          interface: boolean opaque(int x,int y,int z); int sizeX/Y/Z    // impl over BlockVolume
  LightField           interface: float illum(int x,int y,int z)  // baked 0..1 (glow/24) + dynamic
  TileGrid             (from :dungeon-gen) for sound BFS; door states
ai/perception/
  VoxelRay             static boolean los(OpacityGrid g, float ax..az, float bx..bz) — 3D Amanatides–Woo,
                       corner-blocking rule, max steps; also traceFirstOpaque() for debug
  SpatialGrid          uniform grid, int[] cellHead/next, rebuild(Actors), query(circle, IntConsumer)
  PerceptionProfile    record per species (JSON): sightRange, darkRange, touchRange, focusCosine,
                       peripheralCosine, peripheralGain, eyeHeight, hearingThreshold, perception (PER),
                       seesInvisible, wideCone, gainPerSec, decayPerSec, forgetAfterSec, alertsAllies
  StealthModel         static float effectiveRange(Actor target, PerceptionProfile obs, float illum)
  Stimulus             record(type, sourceId, x,y,z, loudness, time, tag)  // SIGHT_PING, SOUND, DAMAGE,
                       ALLY_ALERT, ITEM_SEEN, DOOR, NOISEMAKER
  StimulusBus          ring buffer, post()/drain(); producers: movement, combat, doors, items, spells
  SoundPropagator      bounded Dijkstra over TileGrid (walls ∞, closed door +X dB-equivalent), cached
  FactionTable         byte[F][F] + species overrides + effective faction (leader/owner/charm/conflict)
  MemoryRecord         {id, lastSeenPos, tSeen, lastHeardPos, tHeard, awareness, hostile, threat}
  Perception           per-agent component: profile, MemoryRecord[8], state, currentTargetId, nextTick
  PerceptionSystem     tick(dt): rebuild grid → drain bus → scheduled sight checks → awareness →
                       state transitions → emits AiIntent (Investigate(pos), Engage(id), Flee(pos), Alert(id))
  PerceptionScheduler  staggering, LOD, ray budget
ai/
  AgentBrain           consumes AiIntent; FSM or gdx-ai BT; calls NavMeshQuery to path
debug/
  PerceptionDebugDraw  (render module) cones, rays, awareness bars, memories, sound rings, light heat map
```

### 4.3 Data flow per frame
```text
Gameplay (60 Hz)                         PerceptionSystem (runs every frame, work is staggered)
──────────────────                       ──────────────────────────────────────────────────────
player/monster move ─► footstep stimulus ┐
melee/projectile hit ─► DAMAGE + SOUND   ├─► StimulusBus ─► drain ─► SOUND: SoundPropagator.flood(src, loudness)
door open/close     ─► DOOR (SOUND)      │                              → SpatialGrid listeners in radius
item thrown/lands   ─► SOUND + ITEM      ┘                              → per listener: heard = loud − cost(cell) ≥ threshold
                                                                          → memory.lastHeardPos, awareness += f(loud)
SpatialGrid.rebuild(actors)                               DAMAGE: awareness=1, target=attacker, ALLY_ALERT via LOS
                                                          scheduled agents (≈ N/12 per frame):
                                                             candidates = grid.query(sightRange)
                                                             for each hostile/interesting candidate:
                                                                 cone test → range test (StealthModel, LightField)
                                                                 → VoxelRay.los(eye, head) [, chest]   (budgeted)
                                                                 → visibility v ∈ {0, 0.5, 1} × cone gain × distance falloff
                                                             awareness += gain·v·dt_since_last  |  −decay·dt
                                                             state machine → AiIntent
AgentBrain ◄── AiIntent: Investigate(lastHeard/lastSeen) → NavMeshQuery.findPath → follower / crowd target
                         Engage(id) → chase via path, re-path when target moves > 1.5 m
                         Alert(friends) → post ALLY_ALERT (Barony spot-alert, LOS-gated)
```

### 4.4 The visibility formula (Barony light model in metres + Veloren meter)
```text
d        = |eyeObs − targetChest|
if d ≤ touchRange (2.0 m, ≈ Barony 2 tiles scaled to adjacency)  → visible = 1, skip light and cone, still require LOS
illum    = max(light(targetFeetCell), light(targetHeadCell)) + carriedLight(target)          // 0..1+
range    = lerp(profile.darkRange, profile.sightRange, clamp(illum,0,1))                     // e.g. 4 m … 24 m
range   += profile.perception × 0.5 m                                                        // Barony PER×4 units
range   *= sneaking ? 0.6 : 1.0                                                              // between Barony 0.5 and Veloren 0.7
range    = touch + (range − touch) × (1 − stealthSkill × 0.75)                               // Barony "toward 32"
range    = invisible && !profile.seesInvisible ? touchRange : range
range   *= 1 / (1 + Σ armour.stealth)                                                        // Veloren items
range    = max(range, noisyBonus(target))                                                    // recent loud action
visible  = d ≤ range && coneGain(dot) > 0 && VoxelRay.los(...)
gain     = profile.gainPerSec × coneGain × (1 − d/range)^0.5 × losFraction                  // 1.75/s baseline
awareness = clamp(awareness + gain·Δt − (visible ? 0 : decay·Δt), 0, 1)
```
Suggested defaults, tuned to 3 m tiles. Barony goblin 256 units = 16 tiles = 48 m is too far indoors, so scale by about 0.5:

| Profile | sightRange | darkRange | cone (focus/periph) | notes |
|---|---|---|---|---|
| humanoid (cultist, goblin-likes) | 24 m | 4 m | ±50° / ±85° | alertsAllies, opens doors |
| quadruped_small (rat) | 12 m | 3 m | ±60° / ±100° | hearing ×1.5 |
| quadruped_medium (wolf) | 20 m | 6 m | ±55° / ±95° | hearing ×1.3, "scent" close-sense 6 m |
| arthropod (spider) | 10 m | 6 m | ±146° wide | Barony spider cone |
| bird_medium (bat) | 12 m | 12 m | ±180° | ignores light (echolocation), hearing ×2 |
| undead / lich / shadow | 32 m | 32 m | ±80° | seesInvisible |
| boss | n/a | n/a | n/a | Barony: always tracks nearest reachable player |

The light map makes `illum` almost free: one array read per sample. Use the **same function for the HUD light gem**, so the player sees exactly what the AI uses.

### 4.5 Hearing over the tile grid
* **Loudness scale** (Veloren-like): footstep walk 3, run 6, sneak 0.5, heavy armour +2; door 6; melee swing 4, hit 6; thrown item lands 5; shout / spot bark 10; scream 13; explosion 20+.
* **Propagation:** Dijkstra from the source tile. Cost per tile is 3 m (tile size), diagonals √2×, closed door +9 m-equivalent, portcullis/bars +3 m, solid wall ∞. The flood radius is `loudness × 3 m` (Veloren's `SOUND_TRAVEL_DIST_PER_VOLUME`). A listener hears it if `loudness × (1 − (cost/maxDist)²) × profile.hearing ≥ threshold`. That's Veloren's quadratic falloff applied to **path cost** instead of straight-line distance.
* **Reaction:** heard volume above a "curious" threshold sets `lastHeardPos` (snapped to the source tile centre plus jitter that shrinks as volume grows) and adds awareness `+0.15…0.6`. Above a "threat" threshold (≥ 10, or any combat sound within 15 m) the agent goes straight to **SEARCHING**. Low-flee-health or `Wild` agents **flee** instead (Veloren rule).
* **Investigate:** `AgentBrain` calls `findNearestPoly(lastHeardPos, ext 1.5/2/1.5)`, then `findPath`, and moves at walk speed. On arrival it looks around (turns ±90° over 2 s, which also sweeps the cone) and then returns to post or patrol. Unreachable sounds (no path) are ignored. That's Barony's `monsterSetPathToLocation` check.
* **Noisemakers** (decoy item, bell lever): post `NOISEMAKER` with loudness and lure flag. Listeners path to it. On arrival + LOS they get *disoriented* 1–3 s and a 5 s *distracted cooldown*, and the player's effective range against them is cut to `touchRange` meanwhile (Barony).
* **Cost:** footsteps are the frequent case. Post at most 2 per second per mover, and cache the flood per (tile, radius bucket) for 0.5 s. Only flood if any listener is inside the Euclidean radius (spatial grid check first).

### 4.6 Factions, allies, objects
* `FactionTable` JSON:
  ```json
  { "factions": ["player","cultist","undead","beast","vermin","neutral"],
    "relations": { "cultist": {"player":"H","beast":"N","undead":"H"}, "beast": {"player":"H","vermin":"H"} },
    "speciesOverrides": [ {"a":"wolf","b":"rat","rel":"H"}, {"a":"spider","b":"bat","rel":"H"} ] }
  ```
  `effectiveFaction(actor)` = charm/command effect, else leader/owner's faction (Barony `leader_uid`, Veloren `Owned`), else species default. A conflict effect forces `HOSTILE` to everyone. Relations are not assumed symmetric. Infighting protection (Barony cvar): a neutral bystander never joins a fight between two allies.
* **Ally alerts:** on spot or on being hit, post `ALLY_ALERT(target, pos)`. Friends within 15 m **with LOS from the alerter** (Barony) **or who hear the bark** (sound flood with loudness 10) get awareness 0.6 and `lastSeenPos = target pos`.
* **Monster vs monster:** the same perception pipeline runs for all actors. Candidates are filtered by `FactionTable.hostile(a,b)` before any ray is cast.
* **Objects and environment:** the stimulus types `ITEM_SEEN` (a loot drop in LOS, for species with `pickupPolicy` like Veloren humanoids or Barony `monsterWantsItem`), `DOOR` (a door that was closed is now open: suspicion +0.2 if seen within 10 m), `CORPSE_SEEN` (an ally body in LOS: suspicion to SEARCHING, Thief-style), `LIGHT_CHANGED` (a torch was extinguished in its room: suspicion +0.3). These are cheap because they're event-driven. Producers post when state changes, and only agents that are in range and have LOS on their next scheduled tick react.

### 4.7 Scheduling and performance budget
Assumptions are 60 fps, one core for gameplay, and N = 200 monsters on a level (Barony maps hold about 30–80, so this is generous).

| Work | Frequency | Cost each | Per frame |
|---|---|---|---|
| SpatialGrid rebuild (4 m cells, 150×150-tile map = 450 m → 113² cells) | every frame | ~2 µs + 10 ns/actor | ~4 µs |
| Sight checks: N/12 agents per frame at 5 Hz, ~3 hostile candidates each after faction/range/cone filtering | staggered | cone+range ~50 ns; DDA ≤ 150 steps ≈ 0.5 µs ×2 rays | 17 agents × 3 × ~1.2 µs ≈ **60 µs** |
| Combat agents at 10 Hz | ~20 agents | same | +40 µs |
| Sound floods | ~5 events/frame after throttling | 20–50 µs (cache hits ~1 µs) | ≤ 150 µs worst |
| Memory/awareness update | all agents | ~30 ns | 6 µs |
| **Total** | | | **≈ 0.1–0.3 ms/frame** |

* **Ray budget:** cap at 400 rays/frame, which is far above the expected ~100. Agents over budget slip to the next frame, so the stagger absorbs spikes.
* **LOD:** agents in tiles the player hasn't seen and > 40 m from every player run at 1 Hz and skip ally-alert LOS. Sleeping agents (Barony bats) only react to sound and damage.
* **Threading:** all perception reads are of immutable snapshots (opacity, light, positions), so the sight pass can later move to a `ForkJoinPool` worker and post intents back. Keep it single-threaded until profiling says otherwise. Never touch Bullet from there.
* **Allocation:** reuse `IntArray` buffers and keep positions as primitive floats. Don't allocate `Vector3` in loops (libGDX convention).

### 4.8 Integration with navmesh pathing
* `Investigate(pos)` / `Search(lastSeenPos)` → `NavmeshQuery.find(start, pos)` (the jvm-mw pattern). If there's no polygon within 1.5 m, snap to the nearest floor tile centre. Unreachable → drop the stimulus.
* **Search pattern** after arriving at the last known position: pick 2–4 points by sampling `findRandomPointAroundCircle(lastSeen, 6 m)`, preferring points in the direction the target was last moving (record velocity with `lastSeenPos`). Visit each with a look-around. Give up when awareness decays below 0.3. That's Veloren's decay, so about 14 s from 1.0.
* **Chase:** while visible, the crowd agent's target = live position. Re-path when the target moves > 1.5 m from the last path end. When sight is lost, keep pathing to `lastSeenPos + lastVel × 1 s` (a "predicted" last known position), then switch to search.
* **Doors:** `AgentBrain` asks the door system to open doors along the path (humanoids) or treats closed doors as blocked (beasts), using Detour query filters with area flags (`DOOR_CLOSED`). The door opening posts its own `DOOR` sound, which can wake neighbours. That's a nice emergent Barony moment.
* **Navmesh raycast** (`NavMeshQuery.raycast`) can shortcut "walk straight to target" when the voxel LOS ray and the navmesh ray both pass.

### 4.9 Debug overlay (render module, toggle F8)
* View cones: focus and peripheral fans at eye height, coloured by state (grey unaware, yellow suspicious, orange searching, red alert).
* Sight rays from the last check: green when visible, red at the first opaque block, with the hit cell outlined.
* Awareness bar plus state letter above each head (`BitmapFont` in screen space). A "?" or "!" billboard is what the player sees in-game anyway.
* Memory markers: a ghost capsule at `lastSeenPos` (fading with age) and an ear icon at `lastHeardPos`.
* Sound floods: a heat map of tile costs for the last 1 s of events, plus expanding rings.
* Light gem / heat map: per-block `illum` on the floor (reuse the shader's light attribute), and the effective `range` circle around the player against the selected monster.
* Text panel for the selected agent: profile, factions, last stimuli (ring buffer of 10), and rays used this frame against the budget.
* Use `ShapeRenderer` / an `ImmediateModeRenderer20` line batch after the main pass. This matches the planned navmesh carpet (F6) and collider (F7) overlays.

### 4.10 Test strategy (JUnit 5, no GL, CI-friendly)
1. **VoxelRay**
   * Property test: for 10k random segments in random grids, DDA agrees with dense sampling (step 0.01) except within ε of block boundaries.
   * Corner leak: diagonal opaque pair → blocked. Axis-aligned rays, zero-length rays, and rays starting inside an opaque block are all well-defined.
   * Symmetry: `los(a,b) == los(b,a)` for random cases. Document any asymmetry if you allow it.
2. **StealthModel**: table tests: dark + sneaking → range ≈ darkRange×0.6; lit → sightRange; invisible → touchRange; adjacent always visible with LOS.
3. **Cone**: boundary angles (±focus, ±peripheral, behind), and vertical: a target directly above or below is outside the cone unless `wideCone`.
4. **Awareness**: a fixed-dt simulation reaches Alert in 1/1.75 s ± 1 tick, decays to Unaware in 20 s, and `reached` latches.
5. **SoundPropagator**: open corridor vs closed door vs wall; L-shaped corridor cost = path length; loudness thresholds; cache invalidation on door toggle.
6. **FactionTable**: JSON round-trip, leader inheritance, conflict override, asymmetric relations, infighting protection.
7. **Scenario tests** (deterministic seeds, headless): load a tiny hand-made `TileGrid` + `BlockVolume`.
   * The player sneaks along a dark corridor past a guard → never detected. The same path lit → detected at tick T±2.
   * Throw a noisemaker → guard paths there (mock `PathService` returns a straight path), is disoriented, and returns.
   * Hit one cultist → a visible friend engages and an occluded friend doesn't. Then a bark through an open door does wake the occluded friend.
8. **Scheduler**: over 60 frames every agent gets exactly 5 sight ticks (±1), and the ray budget is never exceeded.
9. **Benchmark** (JMH or a simple timed test, not in CI): 200 agents on a 150×150-tile map, < 0.5 ms/frame average.

### 4.11 Effort estimates

| # | Slice | Effort | Depends on |
|---|---|---|---|
| 1 | `OpacityGrid`/`LightField` adapters, `VoxelRay` (3D DDA + corner rule) + tests | 0.75–1 d | block volume, light bake |
| 2 | `SpatialGrid` + `PerceptionProfile` JSON + `StealthModel` + cone + tests | 0.75–1 d | |
| 3 | `Perception` component, `MemoryRecord`, awareness meter, state machine, `PerceptionSystem` + scheduler/LOD/ray budget | 1.5–2 d | movement (player sneaking flag) |
| 4 | `FactionTable` (matrix, overrides, leader/owner, effects) + ally alerts (LOS + bark) | 0.75–1 d | |
| 5 | `AgentBrain` hooks: Investigate/Search/Chase via `NavMeshQuery`, look-around, door handling | 1–1.5 d | navmesh (NAV_COLLISION_PORT phase 5) |
| 6 | `StimulusBus` + `SoundPropagator` (tile Dijkstra, doors, cache) + footstep/combat/door producers | 1–1.5 d | dungeon `TileGrid`, door system |
| 7 | Objects: noisemaker/decoy, item-seen pickup, corpse-seen, light-changed | 0.75–1 d | items/props |
| 8 | Debug overlay (cones, rays, bars, memories, sound heat map, light gem) | 1–1.5 d | renderer |
| 9 | Scenario tests + benchmark + tuning pass on profiles | 0.75–1 d | |
| | **Total** | **≈ 8.25–11.5 d** (call it 8–11) | |

**MVP (slices 1–4 + a minimal 5): about 3.5–5 days.** That gives monsters that see by light, cones and walls, get suspicious, walk to where they last saw you, alert friends, and respect factions. Hearing, objects and the full overlay can follow.

### 4.12 Gotchas
* **Frames:** run DDA in **block coordinates** (Veloren Z-up or GL Y-up, but pick one). The opacity grid is indexed the way the chunk store is. Convert actor eye positions once, and test with an asymmetric map so a swapped axis fails loudly.
* **Half-block offsets:** chunk blocks sit on integer corners (NAV_COLLISION_PORT §3.5). An eye at `x = 10.0` is on a boundary, so add a tiny epsilon toward the ray direction at the start.
* **Thin geometry:** sprites (bars, webs, portcullis, glass) need an opacity flag separate from collision, as Veloren does with `is_opaque`. Doors toggle opacity and nav area flags together.
* **Light units:** the baked light is integer 0–24. Divide by 24 (lava) or 16 (sconce) so a torch-lit tile reads ≈1. Pick one, document it, and drive the HUD gem from the same function.
* **Fairness:** add Barony's guaranteed **touch range** and Veloren's **close random sense** so stealth never makes a player invisible when adjacent, but keep the close sense ≤ 30%/check so a sneak-by stays possible.
* **Licensing:** keep "Ported from Barony (BSD-2-Clause, © Turning Wheel LLC)" or "Ported from Veloren (GPL-3.0) <file>" headers on any file that mirrors their formulas or tables.

---

## 5. Quick reference: constants found

| Item | Barony | Veloren |
|---|---|---|
| Update rate | acquisition 1×/s per monster, staggered by UID (`ticks%50 == uid%25`) | choose_target 10%/tick in idle; retarget every 10 s |
| Sight range | `sightranges[race]` 32–1024 units (16/tile); typical 256 = 16 tiles | 40 m; bird_large 250; biped_large 100 |
| Light | effective light (0–255+) = detection radius; min 48; adjacent (32) always | none |
| Cone | ±78.75° (±146° spiders/turrets) | dot > 0.15 ≈ ±81° |
| LOS | 2D grid DDA `lineTrace`, entities can block | 3D voxel ray eye→eye until `is_opaque` |
| Sneak | light ×0.5 (fortress ×0.725) | sight ×0.7 |
| Stealth stat | light → 32 as Stealth→100 | ×1/(1+Σ armour stealth) |
| Invisibility | light ≤ 32 (2 tiles); some races see invisible | n/a |
| Close sense | always within TOUCHRANGE (2 tiles) | 5 m, 30% per check |
| Memory | `monsterTargetX/Y` last known; PATH/HUNT there | `last_known_pos`; awareness +1.75/s, −0.05/s; bands 0.1/0.3/0.6/1.0 |
| Hearing | none general; ally alert by LOS; decoy 15 tiles by path; bell | vol − d²/(3·vol)²·vol, listen 30 m, no occlusion; threat ≥ 10 or scream within 35 m |
| Factions | `swornenemies` / `monsterally` race matrices + leader + effects | Alignment pair functions + owner + disguises |
| Items | `monsterWantsItem` pickup/equip | humanoids pick up all drops, others consumables |
