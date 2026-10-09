# Veloren assets for Barony-style traps

Research only. No repo changes were made.

**Sources**
- Veloren `master` @ `ebfff9496e10` (2026-10-07). Sparse checkout at `/workspace/veloren_traps`.
- The `.vox` files I inspected (~170) were fetched as real LFS content into that checkout. `git lfs pull` stalled on the blobless clone, so I pulled them from GitLab `/-/raw/<sha>/<path>` instead, which serves the LFS media.
- Barony @ `962a5ce` (`/workspace/barony`). Its code is BSD-2-Clause; its art and data are not open.
- Iso renders of the key models: `/workspace/traps_renders/sheet1-4.png`.

**Conventions**
- Sprites and object figures are both **11 voxels = 1 block = 1 m**. The object skeleton uses `Mat4::scaling_3d(1/11)` (`voxygen/anim/src/object/mod.rs`).
- Sizes below are .vox `SIZE` in voxels (x × y × z, z up). Blocks ≈ size / 11.
- Your planned grid is 1 Barony tile = 3×3 blocks, with 3–4 block walls. A Barony "tile trap" is therefore a 3×3 patch of 1-block sprites.

**License**
- `assets/credits.ron` says unlisted assets default to **GPL-3.0**.
- The only CC-BY-NC-SA `.vox` entries are `glider/vroom.vox`, `sprite/mineral/ingot/*` and `sprite/wood/log/wood.vox`. **None of the assets below are NC.** Everything recommended here is GPL-3.0 and fine for your GPL repo; credit Veloren.

---

## 1. Barony trap and mechanism inventory

From `src/entity_shared.cpp` `spriteEditorNameStrings` and the `act*.cpp` files.

| Barony entity | Code | Behaviour |
|---|---|---|
| **Spear trap** (the "spike trap") | `actspeartrap.cpp` | Floor tile. Spears shoot up when powered: 50 dmg, out about 60 ticks (≈1.2 s), then retract. Can be sabotaged. |
| **Arrow trap** | `actarrowtrap.cpp` | Hidden inside a wall. Fires when powered. Quiver type sets the arrow (silver, pierce, fire, knockback, crystal, hunting, bone, black-iron, lightweight). After 5 volleys it breaks and drops 2–7 arrows. |
| **Boulder trap** (+ single roll E/S/W/N) | `actboulder.cpp` (`actBoulderTrap*`, `actBoulderTrapHole`) | Boulder drops from a ceiling hole and rolls along a corridor. 80 dmg (50 in some cases). Can be pushed. |
| **Magic trap** (wall) / **Spell trap ceiling** | `actmagictrap.cpp` | Fires a spell when powered: force bolt, magic missile, cold, fireball, lightning, sleep, confuse or slow. Configurable refire rate. |
| **Pressure plate** / **(Latch On)** | `actTrap`, `actTrapPermanent` | Floor plate that powers its wire network. Trigger filters: all, players, monsters, items, boulders, … |
| **Lever** / **Lever with timer** | `mechanisms.cpp` `actSwitch`, `actSwitchWithTimer` | Wall lever with on/off states. |
| **Wire**, **AND gate**, **Signal timer** | `actCircuit`, `actSignalGateAND`, `actSignalTimer` | Hidden logic. No art needed. |
| **Gate** (N-S / E-W, **inverted**) | `actgate.cpp` | Portcullis that rises or falls with power. |
| **Power crystal** | `actpowercrystal.cpp` | Rotating crystal on a base. Sends a power beam the way it faces; some need the unlock spell to start. |
| **Pedestal + orb** | `actpedestal.cpp` | Placing the orb powers the network. |
| **Armed bear trap** (also the player item `TOOL_BEARTRAP`) | `actbeartrap.cpp` | Snaps and holds whoever steps in it. |
| **Summon trap**, **Minotaur spawn trap** | `actsummontrap.cpp` | Invisible. Spawns monsters when triggered. |
| **Wall buster / wall builder** | `actwallbuster.cpp` | Walls that vanish or appear when powered. |
| **Wall lock / wall button** (E/S/W/N) | `actWallLock`, `actWallButton` | Key lock and push button set into a wall. |
| **Teleporter**, **teleport shrine** | `actteleporter.cpp` | Floor teleport. |
| **Pits** | map tiles with no floor | Fall to the next level. Levitation crosses them. |
| **Lava / water** tiles | tile types | Hazards. |
| **Wind tile**, **sound source** | `actWind`, `actSoundSource` | Push zone, ambient sound. |

---

## 2. Veloren trap-relevant systems (how Veloren uses them)

**Floor-touch sprite traps.** These live in `common/systems/src/buff.rs` and check the sprite you are standing on (`physics_state.on_ground`):

| SpriteKind | Effect when stepped on |
|---|---|
| `IronSpike` | Bleeding 1.0 / 4 s |
| `HaniwaTrap` (armed) | Swaps itself to `HaniwaTrapTriggered` with `del_timeout: (4.0, 1.0)`, so it re-arms after about 4±1 s. Also emits `SoundKind::Trap` (AI hearing radius 12) and a `Slash` outcome, and applies Bleeding 5.0 / 3 s. |
| `HaniwaTrapTriggered` | Bleeding 1.0 / 4 s |
| `IceSpike` | Bleeding 15 + Frozen 0.2 / 3 s |
| `EnsnaringWeb` | Ensnared 1.0 / 1 s (a root) |
| `EnsnaringVines` | Ensnared 0.5 |
| `SeaUrchin` | Bleeding 6 s |
| `FireBlock` | Burning 20 |
| `HotSurface` | Burning 10. Invisible: no model. |

**Map placement.**
- `world/src/site/plot/haniwa.rs`:
  - entrance trap ring (`:368`)
  - concentric trap rings in rooms (`:660`)
  - 2×2 trap patches in corridors (`:695`)
  - `IronSpike` floors (`:519`, `:614`)
  - Haniwa sentries (`:585`)
- `IronSpike` also appears in `terracotta_house.rs`, `terracotta_palace.rs`, `terracotta_yard.rs`, `sahagin.rs` and `myrmidon_arena.rs`.

**Collapsing-floor spike pit (Terracotta).** In `terracotta_house.rs:374–397`:
- A 12-block-deep cylinder has `IronSpike` at the bottom and is capped with a `TerracottaBlock` sprite lid.
- The miniboss `TerracottaStatue` (an object entity) stands on a pillar in the middle.
- Its ability `common.abilities.custom.terracotta_statue.trap` is `SpriteSummon { sprite: Empty, summon_distance: (2, 15), del_timeout: (8, 5) }`.
- `common/src/states/sprite_summon.rs` deletes the sprite layer under the caster, so the lid disappears in a 2–15 block ring, players drop onto the spikes, and the floor comes back after about 8±5 s.
- **This is a ready-made Barony pit / trap-door pattern.**

**Turret objects.** These are `Body::Object` entities with an AI agent and a weapon:

| Body | Entity config | Weapon / ability | Where it spawns |
|---|---|---|---|
| `Crossbow` | `assets/common/entity/dungeon/{cultist,dwarven_quarry}/turret.ron` | `npc_weapons.unique.turret` → `abilities/custom/turret/arrows.ron`: BasicRanged, 30 Piercing, 1.2 s buildup, projectile `Object(ArrowTurret)` at speed 130, 10% bleed | `cultist.rs:811`, `dwarven_mine.rs` |
| `Lavathrower` | `dwarven_quarry/lavathrower.ron` | `turret/flamethrower_strong.ron`: BasicBeam, 2.5 s buildup, range 20, 20° cone, Burning. Sends a `FlamethrowerCharge` outcome during buildup (`states/basic_beam.rs:113`). | `dwarven_mine.rs` |
| `Flamethrower` | none | Same weapon as `Lavathrower` | Not spawned by any shipped entity config, but the body, model and weapon mapping all exist |
| `HaniwaSentry` | `haniwa/sentry.ron` | "Haniwa Sentry" → `turret/flamethrower` | Haniwa dungeon |
| `TerracottaStatue` | — | Pit-collapse ability (see above) | Terracotta |
| `GnarlingTotem{Red,Green,White}` | — | `abilities/gnarling/totem/{red,green,white}` | Gnarling forts |
| `SeaLantern` | — | Tidal Totem wave | — |
| `Tornado` / `FieryTornado` | — | — | Particles only (`armor.empty` model) |

- The turrets' animation is in `voxygen/anim/src/object/shoot.rs`. **bone0** (the weapon) follows the entity's yaw toward the target; **bone1** (the mount) is "decorrelated from ori" and stays fixed. The Crossbow also gets a small pitch recoil.
- Pivots are bone0 = (0, 0, 11) and bone1 = (0, 0, 8) voxels.

**Ground-hazard projectiles.** `hazard: true` in `ProjectileConstructor` means the projectile stays on the ground instead of vanishing, and fires when an entity touches it (`common/src/comp/projectile.rs:443`).
- `Object(AdletTrap)`: `abilities/adlet/{tracker,elder}/trap.ron`, 8 or 15 Piercing, lasts 10 s.
- `Object(BorealTrap)`: `boreal_warrior/bow/trap.ron`, 32 Piercing + Frozen, lasts 80 s.
- `Object(Mine)`: `custom/dwarves/flamekeeper/mines.ron`, 6 explosive mines (radius 2, 25 dmg), lasting 10 s.
- **These are the Barony bear trap / landmine.**

**Other mechanics.**
- **Bomb sprite.** `SpriteKind::Bomb` gives a bomb item when collected. When it is destroyed it spawns an explosive with radius 12 and 40 dmg that also damages terrain (`server/src/events/entity_manipulation.rs:2283`).
- **Keyhole → door.** Using a key on a keyhole sprite flood-removes the connected door sprites (`server/src/events/inventory_manip.rs:430`). The pairs are: `Keyhole`→`KeyDoor`, `KeyholeBars`→`DoorBars`, `GlassKeyhole`→`GlassBarrier`, plus the Bone, Haniwa, Sahagin, Terracotta, Vampire and Myrmidon variants. This matches Barony's **wall lock → gate**.
- **Portal.** `Object::Portal` (`server/src/sys/object.rs:87`) swaps the body `Portal` ↔ `PortalActive` while someone is inside `TELEPORTER_RADIUS`. That makes it a **floor plate with off/on art**, and it is also a teleporter (cultist dungeon, `cultist.rs:623`).
- **Missing systems.** Veloren has **no lever, pressure plate, wire/power network, or boulder** system. The keyhole flood-fill is the only "remote actuation" it has.

---

## 3. Mapping: Barony trap → Veloren candidates

Sprite paths are under `assets/voxygen/voxel/`. Offsets come from `sprite_manifest.ron` and `object_manifest.ron`.

### 3.1 Spear trap (floor spikes)

**Best match:** `HaniwaTrap` / `HaniwaTrapTriggered`. It is a true armed/sprung pair with re-arm logic.

| Asset | Size | Notes |
|---|---|---|
| `sprite/furniture/haniwa_door_block.vox` (`HaniwaTrap` armed state) | 11×11×11 (full 1-block cube, blue-grey stone) | Looks like a plain floor block, so it is a **hidden** trap. |
| `sprite/furniture/haniwa_trap_triggered-0.vox`, `-1.vox` (`HaniwaTrapTriggered`, 2 random variations) | 11×11×19 | Stone block plus about 8 voxels (≈0.7 m) of white spikes above the floor. |
| `sprite/misc/iron_spike.vox` (`IronSpike`, always on) | 11×11×11, 135 voxels | Grey caltrop/jack lattice filling the cell. Used for spike pits and floors. |
| `sprite/misc/icespike.vox` (`IceSpike`) | 11×11×23 (≈2 blocks tall) | Cyan ice cone. Ice-themed variant. |

- **States.** Veloren only swaps sprites; there is no motion.
- **Barony-like rise/retract.** Split `haniwa_trap_triggered-*` into the base block (z 0–10) and the spike layer (z 11–18), then translate the spike mesh in Z, about 8 voxels over 0.1 s.
- **Gap.** The armed tile is just a stone cube. If you want Barony's visible holes, a "holes in the plate" floor tile is small custom art (about 11×11×1).

### 3.2 Arrow trap

| Asset | Size | Notes |
|---|---|---|
| Projectiles: `weapon/projectile/simple-arrow.vox` (1×12×3), `turret-arrow.vox` (3×13×3, Veloren's turret arrow), `heavy-arrow.vox` (5×13×5), `clay-arrow.vox` (3×12×3), `snake-arrow.vox` (3×13×3, poison-themed), `multi-arrow.vox` (8×18×10), `dart.vox` (1×11×3) | — | These cover Barony's quiver types, recoloured where needed: fire/crystal/silver via a tint, poison via `snake-arrow`. |
| Visible launcher: `object/crossbow/bone0.vox` (36×31×16, ≈3.3×2.8×1.5 m; wooden ballista) + `object/crossbow/bone1.vox` (18×14×10, mount) | — | **Too big for a wall slot.** Good as a separate "ballista turret" trap. |

- **How Veloren uses it.** Crossbow AI turrets fire `ArrowTurret` at speed 130.
- **Gap.** Barony's arrow trap is an invisible emitter inside a wall. A "slit / murder-hole" wall decal needs about 11×2×11 of custom art, or you can carve 2–3 voxels out of a `dungeon_wall-*` decor sprite (11×1–9×11).

### 3.3 Boulder trap

- **Gap: no boulder model, and no rolling-boulder system.**
- **Closest stand-in:** `weapon/projectile/snowball.vox`. It is a 25×25×25 solid sphere with 8217 voxels, so about 2.3 m across, which nearly fills a 3-block corridor. **Recolour it to rock** with a palette swap and, ideally, add some surface noise.
- **Alternatives:** `object/dagon_bomb.vox` (13³) and `weapon/projectile/ice_bomb.vox` (15³) for smaller boulders; `object/crux.vox` (21³, red spiked sphere) for a "spiked ball" variant.
- **Ceiling hole:** just a missing ceiling block. No art needed.
- **Rubble after the hit:** `sprite/rocks/rock-{0,1,2}.vox` (6×6×4, 9×11×5, 9×9×4).

### 3.4 Magic trap (wall) / spell trap (ceiling)

**Emitters (static or turret bodies):**

| Asset | Size | Notes |
|---|---|---|
| `object/haniwa_sentry/bone.vox` | 2 models: 11×9×14 head + 11×11×6 base; ≈1.4 m clay pot | AI sentry that shoots a flame beam. |
| `object/gnarling_totem_{red,green,white}.vox` | 12×12×19 / 18×18×17 / 16×16×17 | Spell-casting totems. |
| `object/sea_lantern.vox` | 9×9×17 | Stone lantern totem. |
| `sprite/wizard/magical_seal.vox` (`MagicalSeal`) | 25×3×25 | Flat, glowing cyan rune disc, vertical, about 2.3 m. **A good wall glyph that "fires spells".** |
| `sprite/wizard/magical_barrier.vox` (`MagicalBarrier`) | 33×11×33 | Panel with the seal on it. |
| Ceiling: `sprite/mineral/deposit/crystal/ceiling/{blue,green,purple}-{0,1,2}.vox` | 16–22 × 16–25 × 18–21 | Purple-3 is 60 tall. |
| Ceiling: `sprite/cave/ceiling/orb-{0..3}.vox` | 15×15×31–90 | Hanging glowing orbs. |

**Spell projectiles (Barony spell → Veloren model):**

| Barony spell | Veloren model | Size |
|---|---|---|
| Fireball | `fire-bolt-0.vox` / `fire-bolt-1.vox` (Fire material attr) | 6×11×6 / 12³ |
| Cold | `icicle-bolt.vox` | 5×12×5 |
| Cold (alt) | `icicle-spear.vox` | 5×25×5 |
| Lightning | `lightning_bolt.vox` | 9×22×6 |
| Force bolt / magic missile | `laser_beam_small.vox` or `nature-bolt.vox` | 1×9×3 / 6³ |
| Poison (for variants) | `poison_ball.vox` | 8³ |

- **Gap.** Sleep, confuse and slow have no model in either game; use particles. Barony's wall face or vent emitter is not in Veloren, but `magical_seal` covers it.

### 3.5 Pressure plate (+ latch)

- **Gap: no pressure-plate sprite.** Candidates:

| Asset | Size | Notes |
|---|---|---|
| `object/mine.vox` | 11×11 footprint, 4 voxels tall (≈0.36 m) | Dark metal disc with a glowing red centre. Visible "landmine" plate, used by the flamekeeper. One state only. |
| `object/portal.vox` / `object/portal_active.vox` | 66×66×2 each (6×6 blocks) | Floor disc that **swaps between off and on art while occupied**: the active version has about 1000 purple glow voxels. Too large for a 1-block plate; a nice "big rune plate". |
| `sprite/furniture/gear_wheel-0.vox` (`GearWheel0`) | 17×17×3 | Flat bronze gear. Works as a mechanism plate decal. |

- **Custom art:** an up/down stone plate, 11×11×2 and 11×11×1. Trivial.

### 3.6 Lever / lever with timer / wall button

- **Gap: Veloren has no lever, switch or button art in its sprites or objects** (checked every sprite, object and projectile `.vox` name — 1022 files — and every SpriteKind; item and figure models were not searched).
- **Needs custom art:** a wall plate with a 2-state handle, about 5×4×9 voxels, or a handle bone rotated ±45° in code.
- **Decor near mechanisms:** `gear_wheel-0`, `sprite/misc/metal_chain.vox` (11³, hanging chain) and `sprite/misc/rope.vox`.

### 3.7 Wall lock (key) → gate

**Wall lock / door pairs:**

| Lock | Door / barrier |
|---|---|
| `sprite/furniture/keyhole_block.vox` (`Keyhole`, 11³) | `door_block.vox` (`KeyDoor`, 11³, dark iron block) |
| `bone_keyhole_block` | `bone_door_block` |
| `glass_keyhole_block` | `glass_barrier_block` |
| `haniwa_keyhole_block` | `haniwa_door_block` |
| `sprite/bars/keyhole.vox` (`KeyholeBars`) | `sprite/bars/0.vox` (`DoorBars`, single vertical bar, 11³ cell) |

- **How Veloren uses them:** the key removes the connected doors by flood fill. That is already Barony's lock → open behaviour.

### 3.8 Gate (portcullis)

| Asset | Size | Notes |
|---|---|---|
| `sprite/castle/drop_gate_bars-0.vox` (`DropGate`) | 11×11×11 | 3×3-voxel vertical bar plus a full-width horizontal bar at the centre, so it tiles into a lattice. |
| `sprite/castle/drop_gate_bottom-0.vox` (`DropGateBottom`) | 11³ | Bar stub in the top half (z 6–10), used for the bottom row. |
| `sprite/bars/0.vox` (`DoorBars`) | 11³ | Simpler vertical bars. |

- **Veloren status.** `DropGate` and `DropGateBottom` are defined but **not placed by any current worldgen**, and they have no open/closed logic.
- **For Barony's gate:** build a 3-wide × 3–4-high grid mesh from these pieces and translate it in Z. "Inverted gate" is just reversed power logic.

### 3.9 Power crystal and pedestal + orb

**Power crystal:**

| Asset | Size | Notes |
|---|---|---|
| `sprite/mineral/deposit/crystal/floor/{blue,green,purple}-{0,1,2}.vox` (`CrystalLow`) | 17×18×18, 16×16×21, 22×25×20 | Rotate the mesh in code for Barony's turning crystal. |
| `sprite/crystal/glow_ice_crystal_{0..5}.vox` | Same sizes | Glow material. |
| `sprite/misc/floating_diamond.vox` (`DiamondLight`) | 11³, 55 voxels | Glowy floating diamond. |

**Pedestal + orb:** `sprite/misc/crystal_ball.vox` (`CrystalBall`, 11³) is a gold stand with a purple "SwirlyCrystal" orb. **This is almost exactly Barony's pedestal + orb.** For the empty-pedestal state, delete the orb voxels into a second model (small custom edit).

**Gaps:**
- the crystal **base** that Barony's crystal turns on
- a power-beam effect (particles or a line)

### 3.10 Bear trap

| Asset | Size | Notes |
|---|---|---|
| `weapon/projectile/adlet-trap.vox` | 9×5×2 | Bone-white jaw trap. Used by Adlet trackers and elders as a `hazard` projectile that waits on the ground and hits on touch. |
| `weapon/projectile/boreal-trap.vox` | 11×6×4 | Ice jaw trap; adds Frozen. |
| `sprite/misc/ensnaring_web.vox` (`EnsnaringWeb`) | 11³ | Root/immobilise effect without jaws. |
| `sprite/misc/ensnaring_vines.vox` | 10×13×6 | Same idea, partial strength. |

- **Gap.** Only the **open** state exists; a **closed / sprung** jaw model needs custom art. It's small (≈9×5×4); you could fold the jaw halves of `adlet-trap` upward in a voxel editor. Also note these are about 0.8–1.0 m wide, i.e. one block.

### 3.11 Pits, lava, hazard floors

**Spike pit / trap door:** Veloren's Terracotta pattern (§2).
- `misc/terracotta_block-{0,1,2,3,5,6}.vox` (`TerracottaBlock`, 11³, 6 variations) as a removable lid
- `IronSpike` at the bottom
- remove the lid on trigger and restore it after a timeout

**Plain pit:** no art needed (Barony pits are just missing floor).

**Fire floor:**
- `sprite/misc/fireblock.vox` (`FireBlock`): a full 11³ block of Fire-attr voxels (indices 104–110)
- `sprite/ember/1.vox` (`Ember`): 11³ embers
- `HotSurface`: invisible
- Lava itself is a Veloren fluid, so no model is needed.

**Explosive barrel / bomb:** `sprite/bomb/bomb.vox` (`Bomb`, 11×11×13). It chain-explodes when destroyed. Optional Barony-ish addition.

### 3.12 Summon trap, minotaur trap, teleporter, wall buster/builder, wires, AND/timer, wind

| Barony entity | Art needed? | Veloren option |
|---|---|---|
| Summon trap, minotaur spawn trap | None; Barony shows nothing | Optional tell: `magical_seal` decal or `portal_active` |
| Teleporter | — | `object/portal.vox` / `portal_active.vox` (66×66×2, off/on) |
| Wall buster / builder | None; it's just blocks | — |
| Wire, AND gate, signal timer | None; hidden | — |
| Wind tile | — | Particles; Veloren's `Tornado` is particle-only too |
| Sound source / decoy | — | `sprite/misc/bell.vox` (`Bell`, 11×9×10) as a noisy alarm bell |

### 3.13 Veloren traps with no Barony equivalent (optional extras)

| Trap | Model | Size | Behaviour |
|---|---|---|---|
| Flamethrower turret | `object/flamethrower/bone0.vox` + `bone1.vox` | 12×31×16 + 18×14×10 | Grey metal nozzle; 20 m, 20° flame cone after a 2.5 s visible charge |
| Lavathrower | `object/lavathrower/bone0.vox` + `bone1.vox` | 12×31×15 + 18×14×10 | Same as above |
| Ballista turret | `object/crossbow` | See §3.2 | — |
| Haniwa sentry | See §3.4 | — | — |
| Terracotta statue | `object/terracotta_statue.vox` | 42×38×56 | Floor-collapsing guardian |
| Mines | `object/mine.vox` | See §3.5 | Thrown by a caster |

---

## 4. Gap summary (custom art needed)

| Item | Effort | Suggestion |
|---|---|---|
| Lever (2 states) + wall button | Small | Base 5×2×7 plus a handle bone of about 2×2×7, rotated in code |
| Pressure plate up/down | Trivial | 11×11×2 → 11×11×1 stone plate. Or reuse `mine.vox` for a visible plate |
| Arrow-slit wall emitter | Trivial | Cut a slot into a dungeon wall decor sprite |
| Boulder | Small | Rock-recoloured `snowball.vox` (25³) |
| Bear trap closed state | Small | Edit `adlet-trap.vox` |
| Spear trap rise animation | Code only | Split `haniwa_trap_triggered` into base + spikes and translate |
| Power-crystal base + beam | Small | Base plinth; beam in shader or particles |
| Empty pedestal | Trivial | `crystal_ball.vox` minus the orb voxels |
| Portcullis animation | Code only | Mesh from `drop_gate_*` sprites, translated in Z |

**Do NOT use** (CC-BY-NC-SA): `glider/vroom.vox`, `sprite/mineral/ingot/*`, `sprite/wood/log/wood.vox`. None of these are needed for traps.

**Attribution.** All trap assets above are Veloren GPL-3.0. Keep a credits file that names Veloren (gitlab.com/veloren/veloren) and the source paths. Barony code ported for trap logic must keep its BSD-2 header; Barony art is not reusable.
