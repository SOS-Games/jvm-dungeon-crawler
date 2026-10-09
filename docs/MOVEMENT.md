# Barony-like movement for jvm-dungeon-crawler: Barony's model vs. Veloren's, and what to build

*Research only. No repo was changed, nothing was committed and no PRs were opened.*

Sources read:
- `TurningWheel/Barony` @ `962a5ce` (2026-02-20 10:09 PM ET). **License: BSD 2-Clause** (`LICENSE.txt`, "Copyright (c) 2013-2020, Turning Wheel LLC"). That covers the code only; the README says you still need a purchased copy for the game data.
- `veloren/veloren` master @ `ebfff94` (2026-10-06 6:56 PM ET), GPL-3.0.
- `SOS-Games/jvm-dungeon-crawler` @ `6093f3a` (up to date), plus the earlier write-ups in `/workspace`: `VELOREN_ANIMATION.md`, `NAV_COLLISION_PORT.md`, `BARONY_ENVIRONMENT.md` and `DUNGEON_GEN_PORT.md`.

Units: *tile* means one Barony map tile (16 Barony units). *m* and *block* mean one Veloren block, which is 1 libGDX unit (the convention set in `NAV_COLLISION_PORT.md`). One generator tile is 3×3 blocks (`BARONY_ENVIRONMENT.md`).

---

## 0. TL;DR

1. **Barony's movement is a tiny 2-D model.** It runs a fixed 50 Hz tick. Each tick it adds `0.045·speedFactor` (half that sideways, a quarter when backpedalling), multiplies velocity by `0.75`, and calls `clipMove` against the tile grid as an axis-separated AABB. `speedFactor = min((DEX·0.4 + 8.5)·weightRatio(STR, weight), 12.5)`. Sneaking or shield-defending halves the speed. There's **no jumping and no physical Z**. Levitation is a flag that lowers the body 1 unit and lets you cross pits, water and lava. Swimming is a tile flag that scales velocity by about 0.75. In first person the local player's own body is hidden, and a separate HUD arm and weapon model is drawn in camera space with its own 60° projection and `glDepthRange(0, 0.1)`. Head bob is a triangle wave on camera Z, and strafing adds a small yaw tilt to the HUD arm.
2. **Veloren's movement is a full character-state machine** (`CharacterState`, 48 variants) on top of a voxel physics step. Ground movement uses accel 100 m/s² for humanoids and friction `0.85^(60·dt)`, which settles at **about 9.5 m/s**. That's 3.2 tiles/s at 3 m per tile, close to **Barony's base of about 3.6 tiles/s (max 5.3)**. Air control is 2.5 m/s². A jump is 10 m/s against gravity 25, so it peaks at about 2 m. You step up 1 block automatically. Climbing, wall-running, swimming, gliding and rolling each have their own state. A roll lasts 0.05 + 0.36 + 0.125 s, costs 10 energy, and makes you immune to melee, beams and explosions but not projectiles. Attack states move you at 0.7× efficiency, blocking at 0.4×, sneaking at 0.4×. Veloren does have a real first-person camera mode. It keeps the **whole skeleton animated**, puts the camera at the head bone, and **stops meshing the head, chest, belt, back, pants and shoulders**. Hands and feet stay visible, and the weapon only appears while wielding or attacking. Veloren has **no first-person viewmodel and no arm bones** (humanoid hands float).
3. **Recommendation:**
   - **Controller:** a Java port of a *small subset* of Veloren's states with Veloren's tuning numbers: Idle/Wield (+sneak), Jump (flag), Roll, Melee/Ranged with stage sections, Block, Stunned, and Swim from a physics flag. Run it at a **fixed 60 Hz** on the planned Bullet kinematic capsule, and add Barony's stat-to-speed mapping, sneak/defend halving, quick turn and pit/levitation rules on top. Don't port Veloren's ability, skill, buff or poise systems.
   - **Camera:** first person with Barony-style head bob and camera-Z smoothing. Other players and monsters use Veloren's full-body animations in third person.
   - **Your own hands:** a **Barony-style viewmodel pass** using Veloren hand and weapon `.vox` meshes, with procedural viewmodel animation timed off the same Veloren stage clock, so what you see locally matches what others see. Keep a Veloren third-person / "true first person" camera as a debug toggle, which costs almost nothing once the skeleton runs.
   - **Effort:** about **9–13 dev-days** for a playable first-person controller with jump, sneak, roll, a basic melee swing and a viewmodel. That's on top of the collision work (`NAV_COLLISION_PORT.md` slices 1–3) and the skeleton work (`VELOREN_ANIMATION.md` P0–P5). The humanoid player body adds **3–5 days** (the repo can't load `humanoid_*` figures yet) and **4–6 days** for the character animation subset.
4. **Licensing:** Veloren is GPL-3.0, so ports go into your GPL-3.0 repo with attribution. **Barony is BSD-2-Clause, not GPL.** It's GPL-compatible, but any file that ports Barony code must **keep Turning Wheel's copyright notice, the conditions and the disclaimer**, and binaries must reproduce them (put them in a NOTICE file). Barony's game data (models, textures, sounds) is not open, so don't use any of it.

---

## 1. Barony's movement model (BSD-2-Clause code)

All references are `src/` files @ `962a5ce`.

### 1.1 Tick structure
- `TICKS_PER_SECOND 50` (`game.hpp:389`). `actPlayer` (`actplayer.cpp:6144`) runs once per tick. When `usecamerasmoothing` is on, `game.cpp:6612–6620` also calls `handlePlayerCameraBobbing / handlePlayerMovement / handlePlayerCameraUpdate / handlePlayerCameraPosition(true)` every rendered frame, scaled by `refreshRateDelta = TICKS_PER_SECOND / fpsLimit`. Otherwise movement runs in the tick (`actplayer.cpp:11456–11460`).
- Order inside the tick: movement forces, then the client→server `PMOV` packet, then `clipMove(&x,&y, VELX, VELY)` (`actplayer.cpp:11485`), then limb animation driven by the distance actually moved (`dist`).

### 1.2 Speed from stats and encumbrance (`PlayerMovement_t`, `actplayer.cpp:4272–4478`)
- `getMaximumSpeed() = 12.5`, or +1 with EFF_FAST.
- Weight is the sum of inventory weight plus gold weight. It's halved by EFF_FAST and reduced by "lighten load".
- `getWeightRatio(weight, STR)`: `(1000 + 100·STR − weight)/(1000 + 100·STR)`, clamped to 0..1. Under 1000 weight there's a gentler quadratic floor.
- `getSpeedFactor(ratio, DEX) = min((DEX·0.4 + 8.5 − slowPenalty)·ratio, maxSpeed)`, then status multipliers (slow, knockback cap 5, nimbleness, and so on).

### 1.3 Acceleration, drag and directions (`handlePlayerMovement`, `actplayer.cpp:4489–4878`)
```
x_force = right − left                      (× lateralMultiplier, 1.0)
y_force = fwd − back × backpedalMultiplier  (0.25!)
drag    = 0.75 (slippery/grease tiles 0.99, magic grease 0.95, cleats cap 0.85)
speed  *= 1/dragToSpeedFactor[drag]          // keeps top speed similar on ice
defendSpeed = 1 + (defending || sneaking ? 1 : 0)      // → half speed
VEL += y_force·(cos yaw, sin yaw)·0.045·speed/defendSpeed
VEL += x_force·(cos yaw+90°, sin yaw+90°)·0.0225·speed/defendSpeed   // strafe = ½
VEL *= drag^refreshRateDelta;  |VEL| ≤ 5 units/tick
```
- The steady state is `v = 3a` for drag 0.75. With DEX 0 and no load that's 1.15 units/tick = **3.6 tiles/s**. The cap of 12.5 gives **5.3 tiles/s**. You reach 95% of top speed in about 0.2 s (time constant about 0.07 s), so it feels snappy.
- Knockback, dash and strafe impulses are added as decaying velocities (`·0.95` or `·0.8` per tick).
- Players inside each other push apart at 0.075 per tick (no hard collision between players).
- Swimming (`isPlayerSwimming`, `actplayer.cpp:3592`) is a tile lookup in `swimmingtiles` / `lavatiles` when you're not levitating and not wearing water-walking boots. It scales velocity by 0.75 (human/rat) or 0.625 each tick, plus a skeleton penalty.

### 1.4 Collision (`collision.cpp`)
- `clipMove` (`:1738`) tries a full move, then X only, then Y only. That's axis sliding, with no sweeps and no sub-steps (velocity ≤ 5 units per tick, less than a third of a tile).
- `barony_clear` (`:956`) tests the AABB `(x ± sizex, y ± sizey)` against every overlapped tile. **It's blocked** if the `OBSTACLELAYER` (wall) tile is set, or **if there's no floor tile and you aren't levitating** (pits block normal walking; monsters are also blocked by water and lava). After that it checks nearby entities (doors, other actors, and so on) with flags like `PASSABLE`.
- The server checks client moves: the `PMOV` handler (`net.cpp:7074`) re-runs `clipMove` from the last accepted position and sends a correction if the client moved more than 0.025 further than allowed.

### 1.5 Vertical: no jumping, levitation only
- `my->z` is just a render and camera offset. It's −1 when standing, +1.5…4 when asleep depending on race (`actplayer.cpp:9144–9211`), and levitating subtracts 1 (`:9213–9219`). There's **no jump input** in player code.
- Levitating over a pit or water (`:11501–11518`) drains the levitation spell. When it ends over a pit, you fall.

### 1.6 Camera, look and bob
- **Look** (`handlePlayerCameraUpdate`, `:3765`): `yaw += mouseDX·0.01·(mouse_speed/128)`. The optional "smooth mouse" keeps residual rotation decaying by 0.5 per tick. Pitch is clamped to **±60°** (`/player_cam_pitch` = π/3). There's a **quick turn** (180° snap over about 15 ticks, `:3626–3763`).
- **Head bob** (`handlePlayerCameraBobbing`, `:4048–4270`): a triangle wave `BOB += BOBMOVE`, where BOBMOVE moves ±0.025 per tick between ±0.1 (half rate while sneaking or defending, ±0.3 while swimming). It's added to camera Z (`:5016`). A **side bob** tilts toward the strafe direction by up to ±π/32 (±π/64 when backpedalling). It's applied to the HUD arm yaw (`acthudweapon.cpp:352`), not the world camera.
- **Camera Z** (`handlePlayerCameraPosition`, `:4882`): the set point is `my->z·2 − 2.5` (+1 when swimming), with per-race offsets. The camera eases toward it at a rate of `clamp(diff·0.5, 0.3, 2)` per tick, so crouching, levitating and swimming transitions are smoothed.
- World FOV defaults to 60 (`main.cpp:466`). The HUD projection is fixed at 60° (`opengl.cpp:1096`).

### 1.7 First-person arms and weapon
- **Your own body isn't drawn** because of the `GENIUS` flag: an entity isn't drawn while the camera is inside its bounding box (`draw.cpp:2113–2134`; set on every player limb, `actplayer.cpp:7502+`).
- **The viewmodel** is separate entities: `actHudWeapon`, `actHudArm`, `actHudShield` and `actHudAdditional*` (`acthudweapon.cpp`, 5,928 lines). They're flagged `OVERDRAW` and rendered in camera space (`opengl.cpp:1365–1405`): `glDepthRange(0, 0.1)` so they never clip into walls, and the `proj_hud` 60° projection. Positions are hand-tuned offsets: `x = 6 + MOVEX, y = 3 + MOVEY, z = cameraZ·0.5 − playerZ + 7 + MOVEZ`, `pitch = π/8 + PITCH`, minus camera shake.
- **Attacks** are a per-weapon state machine (`HUDWEAPON_CHOP` states 0…23+). Each state eases `MOVEX/Y/Z, YAW, PITCH, ROLL` toward targets with `hudWeaponAnimateVariable` (`:52`), and charge, overcharge, parry, bow reload and so on each get their own states. It's all procedural, with no keyframes and no skeleton.

### 1.8 Third-person animation (other players and monsters)
- A body is a parent entity plus **child limb entities**, each a separate voxel model: torso, right/left leg, right/left arm, weapon, shield, cloak, helmet, mask (`monster.hpp:1106–1115`).
- **Walking** swings legs and arms in pitch by `dist·PLAYERWALKSPEED (0.12)` per tick, bouncing between ±π/4. A footstep sound plays at each extreme (`actplayer.cpp:11965–12160`). So stride speed follows **distance actually moved**, the same idea as Veloren's `acc_vel`.
- **Attacks** set `PLAYER_ATTACK` poses (swing 1/2/3, magic windups, ranged shoot, flail, parry, …) that drive the arm pitch with `limbAnimateToLimit` (`:12196–12600`).
- Each of the 41 monsters (`monster_*.cpp`) has its own `xxxMoveBodyparts(my, stats, dist)` with the same pattern.

**What to take from Barony:** the *feel* numbers (snappy accel, ½ strafe, ¼ backpedal, ½ sneak/defend, bob amplitude, ±60° pitch, quick turn), the stat→speed formula, pits plus levitation, swim tiles, and the viewmodel rendering technique. **What not to take:** the 2-D-only collision, the per-tick fixed constants (convert them to per-second), and the giant HUD state machine (rebuild it data-driven).

---

## 2. Veloren's movement model (GPL-3.0)

### 2.1 Architecture
- `common/src/comp/character_state.rs` has `enum CharacterState` with **48 variants**: Idle, Crawl, Climb, Sit, Dance, Talk, Glide, GlideWield, Stunned, BasicBlock, Equipping, Wielding, Roll, BasicMelee, BasicRanged, Boost, DashMelee, ComboMelee2, Leap*, Charged*, RapidRanged, Throw, Shockwave, Explosion, BasicBeam, auras, Blink, summons, SelfBuff, UseItem, Interact, Wallrun, Skate, Music, FinisherMelee, DiveMelee, RiposteMelee, RapidMelee, Transform, RegrowHead, LeapRanged and Simple.
- Each state is a `CharacterBehavior::behavior(&JoinData, &mut OutputEvents) -> StateUpdate` in `common/src/states/*.rs`, driven by `common/systems/src/character_behavior.rs`. Input comes from `comp/controller.rs` (`move_dir`, `move_z`, `look_dir`, `strafing`, ability inputs).
- Most states are a few calls to shared helpers in `common/src/states/utils.rs`. Idle is the template (`states/idle.rs`):
  ```rust
  handle_orientation(data, &mut update, 1.0, None);
  handle_move(data, &mut update, if self.is_sneaking { 0.4 } else { 1.0 });
  handle_jump(data, output_events, &mut update, 1.0);
  handle_wield(..); handle_climb(..); handle_wallrun(..);
  if input_is_pressed(data, InputKind::Roll) { handle_input(.., InputKind::Roll) }
  ```
- Physics is a separate system (`common/systems/src/phys/mod.rs`, `phys/collision.rs`). States only change `vel`/`ori`; physics adds gravity, collides and applies friction.
- Everything lives in `common`, so **the client runs the same code for its own entity (prediction)** while the server stays authoritative. Animation is purely client-side, derived from the replicated `CharacterState`, pos, vel and ori.

### 2.2 Ground and air movement (`utils.rs`)
| Thing | Code | Value for humanoids |
|---|---|---|
| Ground accel | `Body::base_accel` (`:49`) | **100** m/s² (other bodies 20–150) |
| Friction | `collision.rs:519` `vel *= (1 − fric)^(dt·60)`, `FRIC_GROUND = 0.15` (`consts.rs`) | about 0.0175 per tick at 60 Hz |
| Top speed | `max_speed_approx` (`:215`) | ≈ 9.5–10.3 m/s (9.44 measured with discrete 60 Hz integration) |
| Air accel | `air_accel = base·0.025` (`:204`) | 2.5 m/s², so almost no air control |
| Strafing | `ground_move` (`:482–533`) | when `is_strafing`, speed is scaled by `lerp((dot(fwd,dir)+1)/2, 1, 0.45)`: forward 1.0, sideways 0.725, **backwards 0.45** (`reverse_move_factor`) |
| Section modifiers | `ground_move` | `stats.buildup_move_speed_modifier` / `charge_move_speed_modifier` while in Buildup/Charge |
| Turn rate | `handle_orientation` (`:655`), `base_ori_rate` (`:244`) | 2.65 half-turns/s on the ground, ×0.2 in the air, ×0.4 in liquid; slerped |
| Jump | `handle_jump` (`:1361`) + `LocalEvent::Jump` sets `vel.z = impulse` (`common/state/src/state.rs:901`) | `0.4·GRAVITY` = **10 m/s**, with `GRAVITY = 25`, so apex 2.0 m and about 0.8 s airtime |
| Wall jump | `handle_walljump` (`:1396`) | reflects off the wall, ×1.1, z = 0.7 |
| Swim | `swim_move` (`:784`) + `integrate_forces` liquid drag (`phys/mod.rs:49–120`) | thrust ∝ frontal area, auto-treads water, buoyancy from `HUMAN_DENSITY = 990` |
| Fly | `fly_move` (`:837`) | birds, dragons, ships; not humanoids |

**Facing:** when not strafing and not attacking, the body turns toward `move_dir` (third-person "run where you push"). **In first person, voxygen sets `inputs.strafing = true`** (`voxygen/src/session/mod.rs:1550`), so the body follows `look_dir` and backpedal and strafe penalties apply. That's the Barony-style setup you want.

**Efficiency per state** (the third argument of `handle_move`): Idle/Wield 1.0, sneaking 0.4, BasicMelee/ComboMelee/Ranged/Charged 0.7 (overridable), BasicBlock 0.4, Roll buildup 0.3, DashMelee 0.1, Leap* 0.3, Crawl 0.2, Stunned `movement_speed`.

### 2.3 Block collision with step-up (`phys/collision.rs::box_voxel_collision`, `:21–524`)
- The collider is a **vertical cylinder** treated as an AABB. For `CapsulePrism` (humanoids) the radius is ≤ 0.45 and `z_max` is clamped to 1.2–1.95 (`phys/mod.rs:1041–1053`).
- The move is split into sub-steps of `min(radius/2, height)`, clamped to 0.01–0.3 m (max 100 per tick). Each sub-step resolves the "most colliding" solid block along its minimum-penetration axis (up to 16 attempts).
- **Block hop (auto step-up, `:214–255`):** this triggers on a horizontal push-out when the block intrudes more than 0.1 above the feet, the space above is free, and there's ground under the target. Then `z = block_top`, so you **step up 1 full block** automatically.
- **Ground snap (`:288–319`):** if you were on the ground, aren't rising, and a block exists within 1.1 below, you snap down onto it. Walking down stairs therefore doesn't make you "fall".
- `on_wall` comes from four probe AABBs (`:326–396`), and it's what feeds Climb and Wallrun. Liquid depth comes from overlapping liquid blocks.
- Friction applies only when `on_ground` (×0.1 in liquid) or when climbing a wall.
- Entity-vs-entity uses capsule/cylinder push-apart (`resolve_e2e_collision`, `:669`), not hard blocking.

### 2.4 Climb, glide, roll, stamina
- **Climb** (`states/climb.rs`): entered when in the air and pushing into `on_wall` with energy > 1 (`handle_climb`, `utils.rs:1033`). It cancels gravity and moves along the wall at `5²` accel. Energy drains at `|lateral, up·15|` per second. Only `Body::Humanoid` can climb.
- **Wallrun** (`states/wallrun.rs`) and **Glide** (`glide.rs`, which needs a glider; the default glider `vroom.vox` is CC-BY-NC-SA, see §6) mostly matter in open terrain.
- **Roll** (`states/roll.rs`, defaults `comp/ability.rs:1467–1500`): buildup 0.05 s (move ×0.3), movement 0.36 s with `ForcedMovement::Forward(3.3075·((1 − t/T)/2 + 0.25))`, recover 0.125 s (move 1.0, can be cut short by jumping at ×1.5). Energy cost is `10 + 100·remaining` when it cancels another ability. Rolling makes you **immune to melee, beams, air shockwaves, explosions and arcs, but not projectiles or ground shockwaves**, removes Burning, and halves the collider height (`calc_z_limit`, `phys/mod.rs:122`). Simulated from a run, a roll covers about 7.2 m in 0.54 s against about 5.1 m running, so the point is mostly the i-frames, not distance.
- **Energy** (`comp/energy.rs`, `systems/src/stats.rs:14–15`): the regen rate starts at 0 and ramps by `1.0/s²` (2.5 while sitting) up to 10/s, and resets when energy is spent.

### 2.5 Camera and first person (`voxygen/src/scene/camera.rs`, `scene/mod.rs`, `figure/cache.rs`, `figure/load.rs`)
- `CameraMode::{FirstPerson, ThirdPerson (default, dist 10), Freefly}`. Near plane 0.0625, default FOV 1.1 rad (63°). Orientation is smoothed at `LERP_ORI_RATE = 15`. In first person the focus lerps 2× faster. Pitch is clamped to ±90°.
- **Eye position in first person** (`scene/mod.rs:608–627`): `computed_skeleton.head · (0, 0, 0.6)` in world space. The camera **rides the animated head bone**, so run, roll and attack animations move the view. Running on the ground adds `sin(t·17)·0.05` (`:686–691`). Outside first person it uses `eye_height = height·0.8` (×0.6 sneaking, ×0.7 sitting, ×0.3 crawling).
- **Hiding the figure** (`figure/cache.rs:221–297`, `figure/load.rs:515–604`): in first person `CharacterCacheKey.third_person = None`, so **head, chest, belt, back, pants and both shoulders aren't meshed**. **Hands** are kept, except while in a ranged state (to aim bows). **Feet**, lantern and glider are kept. **Tools** are meshed only while `is_wield()` or `is_attack()`.
- So Veloren's first person is "body awareness": the real third-person animations, seen from inside the head. There's **no viewmodel, no separate FOV, no depth hack, and no arm bones**. The humanoid skeleton is head, chest, belt, back, shorts, hand_l/r, foot_l/r, shoulder_l/r, glider, main, second, lantern, hold (`voxygen/anim/src/character/mod.rs:80`), and the hands float.

### 2.6 Humanoid animations available (`voxygen/anim/src/character/`, 14.8k lines)
- **Locomotion, chosen by `(on_ground, moving, in_liquid, riding, skating)`** (`figure/mod.rs:1517–1640`): `stand` (145 lines), `run` (340), `jump` (171), `swim` (213), `mount` (163).
- **State-specific:** `roll` (338), `sneak` / `sneakwield` / `sneakequip`, `wield` (631), `equip`, `climb`, `wallrun`, `gliding` / `glidewield`, `stunned`, `staggered`, `sit`, `sleep`, `crawl`, `dance`, `talk`, `collect`, `consume`, `throw`, `music`, `steer`, `boost`, `pet`, `idle`.
- **Combat:** `basic.rs` (4,193 lines, `BasicAction`, about 75 ability ids) and `multi.rs` (3,874 lines, `MultiAction` combos, about 28 ids). They're keyed by ability-id strings such as `common.abilities.sword.*` (sword 28, hammer 26, axe 25, bow 14, staff 6, shield 2, pick/shovel). Each one reads `StageSection` (Buildup/Charge/Movement/Action/Recover) plus progress.
- These are the third-person animations other players and monsters will show. Monsters use their own bodies (`biped_small`, `biped_large`, quadrupeds, …), which the viewer already loads as static poses.

---

## 3. Comparison

| Aspect | Barony | Veloren | Pick for the crawler |
|---|---|---|---|
| Tick | Fixed 50 Hz (optional per-frame smoothing) | Variable dt (60 Hz typical), dt-corrected friction | **Fixed 60 Hz sim + render interpolation** (deterministic, multiplayer-friendly) |
| Ground feel | v∞ ≈ 3.6–5.3 tiles/s, τ ≈ 0.07 s | v∞ ≈ 9.5 m/s ≈ 3.2 tiles/s, τ ≈ 0.1 s | Veloren numbers; the feel already matches Barony |
| Strafe / backpedal | ½ / ¼ | 0.725 / 0.45 (when strafing) | Make it a config. Barony's ¼ backpedal is a big part of its feel, so use `reverse=0.25…0.45` |
| Speed from stats | DEX + STR / weight formula | `stats.move_speed_modifier` (buffs, gear) | Barony formula → `move_speed_modifier` |
| Sneak / defend | ×½, quieter, bob ½ | sneak 0.4, block 0.4 | 0.5 / 0.4 |
| Vertical | None (levitation = flag) | Gravity 25, jump 10 m/s, auto step 1 block, snap down 1.1 | Gravity + **lower jump (about 6–7 m/s, 0.75–1 m apex)** so you can't bonk 3-block ceilings; 1-block step for stairs |
| Pits / water / lava | Tile flags; pits block unless levitating | Liquid blocks, swim physics | Barony rules on tile flags; simple swim (×0.75, bob, slow sink) |
| Climb / wallrun / glide | No | Yes | **No** (or ladder blocks only); they break dungeon design |
| Dodge | Dash spell / knockback | Roll with i-frames + energy | **Port Roll** (optional; Barony has none, but it fits Veloren's combat animations) |
| Collision | 2-D AABB vs tiles, axis slide | Cylinder AABB vs blocks, step-up, snap | **Bullet kinematic capsule** (planned) with `stepUp = 1.05`, `stepDown = 1.1`; or a Veloren-style voxel AABB port (§4.2) |
| Your own body in 1P | Hidden (GENIUS) + overlay HUD arm/weapon | Body minus torso and head, from the head bone | **Hidden + overlay viewmodel**; optional "Veloren body awareness" toggle |
| Others / monsters | Limb entities, pitch swing ∝ distance | Skeletal procedural anims, `acc_vel` phase | **Veloren skeletons** (from `VELOREN_ANIMATION.md`) |
| Multiplayer | Client sends pos/vel/yaw/pitch at 50 Hz; server re-clips and corrects | Shared `common` sim, client prediction, server authority | Shared Java sim module, see §4.6 |

---

## 4. Recommendations

### 4.1 Port Veloren's states, or build a Barony-style controller?
**Neither in full.** Build a **"Veloren-lite" state machine** in plain Java, with no libGDX in the sim:

```java
sealed interface CharState permits Idle, Wield, Roll, Melee, Ranged, Block, Stunned, Swim, Dead {}
record Idle(boolean sneaking, float timeEntered) implements CharState {}
record Melee(AbilityDef def, Stage stage, float timer) implements CharState {}  // Buildup→Action→Recover
...
final class CharacterSim {                       // 60 Hz, pure function of (state, input, world)
  void tick(Actor a, Input in, float dt) {
     a.state = a.state.behavior(a, in, dt);      // orientation, handle_move(efficiency), jump, transitions
     physics.integrate(a, dt);                   // gravity, capsule move, on_ground/on_wall/in_liquid, friction
  }
}
```
- Keep Veloren's helpers line for line: `handle_move` / `ground_move` (accel 100, efficiency, reverse factor), `handle_orientation` (simplified: first person snaps to yaw, AI uses the ori-rate slerp), `handle_jump`, `StageSection` timing, roll forced movement, energy regen.
- Add **Barony layers**: `move_speed_modifier = barony.speedFactor(DEX, STR, weight)/baseline`, ½ for sneak and defend, swim tiles ×0.75, pits block unless levitating, quick turn (180° over 0.3 s).
- **Why not the full port:** 48 states plus abilities loaded from RON, skills, buffs, poise and combos are thousands of lines tied to Veloren's ECS. For a dungeon crawler you need about 8 states. Keeping the *state names, stage sections and numbers* compatible still lets you drive Veloren's animations directly (the animation picker only needs `variant`, `stage`, `progress`, `vel`, `ori`, `on_ground`, `in_liquid`).
- **Why not pure Barony:** Barony has no jump, no stage-timed attacks and no 3-D physics, and its per-tick constants would have to be re-derived anyway. Veloren's model already *feels* close to Barony's at your scale (3.2 vs 3.6 tiles/s, similar τ).

### 4.2 Collision backend
- **Default (matches `NAV_COLLISION_PORT.md`):** the gdx-bullet kinematic capsule ported from jvm-mw (`BulletWorld.move/slide/depenetrate`). Configure radius 0.4, height 1.9 (Veloren's clamp range; human male figure about 2.28 m, eye 1.82), `stepUp = 1.05` (= recast `walkableClimb`), `stepDown = 1.1` (Veloren snap), gravity 25, max slope 46°.
  - The controller passes `vel·dt` and reads back `onGround / onWall / onCeiling / hitNormal`.
  - Zero the velocity component into a hit normal, as Veloren does (`collision.rs:261–266`), so you slide along walls without bouncing.
- **Alternative if you drop Bullet:** port `box_voxel_collision` (`collision.rs:21–524`, about 250 lines of relevant code) against your block volume. It's exact for blocks and gives Veloren's block-hop and snap for free, but it doesn't cover non-block props (Veloren sprites have `solid_height`, so props can still be blocks). Estimate 1–1.5 d. Either way, keep the controller backend-agnostic behind `interface Mover { MoveResult move(Actor, Vector3 delta); }`.
- **Entity vs entity:** Barony-style soft push-apart (0.075 per tick) / Veloren `resolve_e2e_collision`, not hard blocking. That's simpler, and crowds don't jam doorways. Monsters get the same capsule params per body size.

### 4.3 Camera
- First person at `eyeHeight = 0.8·bodyHeight` (1.82 m for human male). Use a **Barony-style eased camera Z** (sneak ×0.6 like Veloren; swim +; levitate +), **Barony bob** (triangle ±0.1 at 0.025 per tick → in metres about ±0.04 m, double while swimming), and side tilt ±π/32 applied to the viewmodel only.
- Pitch ±60° (Barony) or ±85°. Default FOV 60–65°. Optional mouse smoothing.
- **Debug and options:** a Veloren third-person orbit (dist 3–10 m, right offset when aiming) is almost free once the player skeleton animates. Barony has no third person, but it's useful for checking animations and maybe as an accessibility option. Avoid Veloren's "camera on the animated head bone" for the *default* first person, because roll and attack head motion makes people sick. If you want it, scale the head-bone offset down (e.g. 25%).

### 4.4 First-person viewmodel: what's achievable with Veloren art
Veloren gives you **hand meshes** (`humanoid_armor_hand_manifest.ron`, left/right per glove), **weapon meshes** (`humanoid_main_weapon_manifest` / `weapon/*`) and their **grip offsets** (your `NpcFigure.Hold`/`Grip` already handles some), but **no arms and no first-person animations**. Options:

| Option | What you see | Effort | Notes |
|---|---|---|---|
| **A. Barony-style overlay (recommended)** | Floating hand(s) + weapon in camera space, Barony-like swings | 3–4 d framework + 0.5–1 d per weapon class | Second pass after the world: clear depth (or `glDepthRange(0, 0.1)`), its own 60° projection, same lighting shader. Animate procedurally, one data table per `(weaponKind, StageSection)` of eased targets (`move xyz, yaw, pitch, roll`), with progress taken from the **same stage timer** as the third-person `BasicAction`, so local and remote timing match. Optionally author a simple forearm/sleeve `.vox` in Veloren style for the Barony "arm" look (GPL, your own asset). |
| B. Veloren "body awareness" | Veloren's real third-person animation from inside the head, torso hidden | ≈1 d once the character skeleton + anims exist | Weapon swings are framed for a third-person camera, so they're often out of view or clip the near plane. Hands vanish while aiming. Good as a toggle, poor as the main view. |
| C. Re-anchored skeleton bones | Run the character anim, take `hand_r`, `main`, `control` relative to `chest`/`head`, re-anchor them under the camera with a fixed offset and scale | 2–3 d + per-weapon tuning | You get Veloren's motion for free, but many anims rotate the whole torso, so you'd need to cancel the chest rotation. Worth trying for a few signature attacks after A works. |
| D. Third person over the shoulder (Veloren default) | Full character | ≈0.5 d | Looks correct immediately but isn't Barony. Keep it as a toggle. |

### 4.5 Combat and movement interplay
- Attack states: `efficiency 0.7` (Veloren) during Buildup/Action/Recover. `buildupMoveSpeedModifier` per weapon (heavy weapons 0.5). Orientation locked to look in first person anyway. Barony itself doesn't slow you while attacking, so use 0.85–1.0 for light weapons to keep the Barony feel.
- Block or defend: 0.4–0.5 move. Parry window from Veloren `basic_block.rs` or Barony `HUDWEAPON_CHOP 21–23`.
- Roll / dodge: Veloren roll on a key (energy 10, i-frames vs melee). It can cancel Buildup/Recover for extra energy (`default_roll` cost formula). Barony players will expect no dodge, so make it a design choice. Collider height halves while rolling (fits under low gaps).
- Knockback / stun: an impulse into `vel` (Barony EFF_KNOCKBACK decay ×0.95 per tick; Veloren `Stunned`/`Staggered` states with move efficiency and animations).
- Sneak: ×0.5 speed, enemy detection radius reduced (Barony), Veloren `sneak` animation for others, camera lowered.

### 4.6 Multiplayer (if planned)
- Keep `CharacterSim` + `Mover` in a headless module (no `Gdx.*`) so a server can run it. Make all randomness seeded.
- The **Barony approach** is cheap: the client simulates, sends `(pos, vel, yaw, pitch, state, stage, timer)` at 20–60 Hz, and the server re-runs `move()` from the last accepted position and sends a correction if the gap is over a tolerance (`net.cpp:7074–7140`). This trusts clients and suits co-op.
- The **Veloren approach** is input-driven: client-side prediction, server authority, reconciliation. It's more work (3–5 d plus netcode), but cheat-resistant.
- Animation never needs to be networked: remote figures derive it from the replicated state + vel + ori (exactly how voxygen does it). Interpolate remote positions about 100 ms behind.
- Library: KryoNet or plain UDP. Out of scope here; estimate **4–8 d** for Barony-style co-op on top of everything else.

---

## 5. Prioritized plan

Prerequisites from earlier reports: `NAV_COLLISION_PORT.md` slices 0–3 (units, block volume, PhysicsWorld, kinematic capsule: **4.5–5.5 d**) and `VELOREN_ANIMATION.md` P0–P5 (bone-local meshes, skeleton, anim loop: **4–5.5 d**). The estimates below come **on top of** those.

| # | Slice | Effort | Key references |
|---|---|---|---|
| M0 | `Input` record (moveDir, look yaw/pitch, jump, sneak, roll, attack, block) + fixed-60 Hz loop with render interpolation | 0.5 d | Barony `game.cpp:6600` (smoothing), Veloren `controller.rs` |
| M1 | `CharacterSim` core: Idle/Wield + sneak, `groundMove` (accel 100, friction, reverse factor, efficiency), air accel, gravity, jump (tunable 6–10 m/s), `onGround`/`onWall`/`inLiquid` from the mover | 1.5–2 d | `utils.rs:455–533, 1361`; `phys/mod.rs:763–797`; `collision.rs:495–523` |
| M2 | First-person camera: eased eye Z, Barony bob + side tilt, ±60° pitch, quick turn, sneak lowering; third-person debug toggle | 1 d | `actplayer.cpp:3765–4270, 4882–5034`; `scene/mod.rs:608–720`; `camera.rs` |
| M3 | Barony rules: stat/weight→speed, defend/sneak halving, slippery tiles, swim tiles, pits + levitation flag, soft actor push-apart | 1–1.5 d | `actplayer.cpp:4272–4878`; `collision.cpp:1103–1135` |
| M4 | Stage-section framework + `Melee` (Buildup/Action/Recover, hit window, move efficiency, orientation lock) + `Block` + `Stunned` | 1.5–2 d | `states/basic_melee.rs`, `basic_block.rs`, `stunned.rs`, `utils.rs` `StageSection` / `tick_attack_or_default` |
| M5 | `Roll` + energy (cost, regen ramp, i-frame filter, collider half height) | 1 d | `states/roll.rs`; `ability.rs:1467`; `energy.rs`; `systems/src/stats.rs:14` |
| M6 | Viewmodel pass (option A): overlay render, hand + weapon `.vox` with grips, idle sway/bob, a data-driven swing table for 1–2 weapon classes, timed from M4 | 3–4 d | Barony `acthudweapon.cpp` (`:52`, `:3700–3760`, CHOP states), `opengl.cpp:1365–1405`; Veloren `humanoid_armor_hand_manifest.ron` + weapon manifests |
| **Playable first-person controller** | | **≈ 9.5–13 d** | |
| H1 | Humanoid figure loading (`humanoid_head` / `color` / `armor_*` manifests, species/body type/hair/beard/eyes palettes, tool grips). The viewer currently only does biped_small/large and animals | 3–5 d | `figure/load.rs` (`HumHeadSpec`, `HumColorSpec`, `mesh_*`), `body/humanoid.rs` |
| H2 | Character skeleton + `stand`, `run`, `jump`, `sneak`, `roll`, `wield`, `swim`, `stunned`, and 2–4 `basic.rs` attack branches for remote players and humanoid NPCs | 4–6 d | `voxygen/anim/src/character/*.rs`, picker `figure/mod.rs:1442–1640` |
| H3 | Option B / C viewmodel experiments (body awareness toggle, re-anchored hands) | 1–3 d | `figure/cache.rs:221–297` |
| N1 | Monster movement on the same sim (AI fills `Input` from Detour paths) + per-body accel / ori-rate tables | 1–1.5 d | `utils.rs:49–293`; `NAV_COLLISION_PORT.md` slices 5–6 |
| MP | Barony-style co-op netcode on the headless sim | 4–8 d | `net.cpp:7074–7140`, `actplayer.cpp:11462–11479` |

Suggested order: M0–M2 (walk around a test dungeon), then M3, M4+M6 together (the swing must feel right in first person), then M5, then H1–H2 when you want visible players or humanoid NPCs, then N1 and MP.

### Tuning starting values (metres, seconds)
```
accel_ground=100  fric_ground=0.15 per 1/60 s  accel_air=2.5   gravity=25
jump_vel=7 (apex ≈1.0 m; Veloren 10 → 2 m)   step_up=1.05  step_down=1.1
reverse_factor=0.25..0.45  strafe_factor=0.5..0.725  sneak=0.5  block=0.4  attack=0.7..1.0
eye=0.8·height  pitch=±60°  fov=60–65°  bob=±0.04 m @ ≈2.5 Hz walking  quick_turn=0.3 s
roll: 0.05/0.36/0.125 s, strength 3.3075, energy 10, regen ramp 1/s² to 10/s
capsule r=0.4 h=1.9   push-apart=3.75 m/s² equivalent
```

---

## 6. Licensing

| Source | License | What you may do | Obligations |
|---|---|---|---|
| **Veloren code** (`common/src/states/*`, `utils.rs`, `phys/*`, `voxygen/anim/*`, camera logic) | GPL-3.0-or-later | Port into your GPL-3.0 repo | Keep GPL. Add a header per ported file (source path + commit `ebfff94`, "Copyright (C) Veloren contributors, modified"), and list it in README/NOTICE (same as the animation and shader ports). |
| **Veloren assets** (hand, weapon and figure `.vox`, manifests) | GPL-3.0 by default (`assets/credits.ron`) | Use | Credit them. **Exclude the CC-BY-NC-SA items**: the glider `vroom.vox` (relevant if you ever port Glide), `sprite/mineral/ingot/*`, `sprite/wood/log/wood.vox`. |
| **Barony code** (`actplayer.cpp`, `collision.cpp`, `acthudweapon.cpp`, `net.cpp`, …) | **BSD 2-Clause** (Turning Wheel LLC, 2013–2020), **not GPL** | Port or adapt into your GPL-3.0 repo. BSD-2 is GPL-compatible. | **Every file containing ported Barony code must keep the BSD copyright notice, the two conditions and the disclaimer**, alongside your GPL header. Binary distributions must reproduce that notice in docs or a NOTICE file. Reusing *ideas and numbers* (tuning constants, formulas) needs no notice, but credit is polite. |
| **Barony game data** (models, textures, sounds, maps) | Not open (you need a purchased copy) | **Don't use** | — |
| ImGui / other Barony third-party libs | MIT / zlib / … | Not needed | — |

Suggested header for a file mixing both sources:
```java
/*
 * jvm-dungeon-crawler — GPL-3.0-or-later.
 * Portions ported from Veloren (https://gitlab.com/veloren/veloren) @ ebfff94,
 *   common/src/states/utils.rs — Copyright (C) Veloren contributors, GPL-3.0-or-later.
 * Portions adapted from Barony (https://github.com/TurningWheel/Barony) @ 962a5ce,
 *   src/actplayer.cpp — Copyright (c) 2013-2020, Turning Wheel LLC.
 *   Redistribution and use in source and binary forms, with or without modification, are
 *   permitted provided that the following conditions are met: [BSD-2-Clause conditions and
 *   disclaimer reproduced verbatim from Barony LICENSE.txt]
 */
```

---

## 7. Things to verify before building
- **Jump vs ceiling height:** with 3-block walls (BARONY_ENVIRONMENT §2), a head at 1.9 m plus a 2 m Veloren jump hits the ceiling. Choose between a lower jump or 4-block ceilings.
- **Recast agent vs capsule:** keep `walkableClimb = stepUp`, `walkableRadius = capsule r`, `walkableHeight ≥ 1.9`. Roll's half-height collider can pass under gaps the navmesh says are blocked, which is fine for players and irrelevant for AI.
- **Humanoid scale:** the figure is about 2.28 m but the collider clamps to ≤1.95 m. Veloren accepts the head clipping into low ceilings, and so should you (or shrink the figure scale ×0.85 for a more Barony-like proportion).
- **Speed at 3 m tiles:** 9.5 m/s crosses a 9×9 m room in under a second. Try 0.7–0.8× `move_speed_modifier` if rooms feel small (or enlarge rooms, as BARONY_ENVIRONMENT suggests).
- **Viewmodel lighting:** the overlay should sample the same light at the camera position (Barony uses the camera tile light) or it'll look pasted on.
