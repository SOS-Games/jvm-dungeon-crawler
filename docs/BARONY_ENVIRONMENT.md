# A Barony-style 3D dungeon in jvm-dungeon-crawler: Veloren assets vs. Veloren-coloured blocks

*Research only. No repo was changed. Sources: `SOS-Games/jvm-dungeon-crawler` @ `6093f3a` ("animals"; `git pull` was already up to date). `veloren/veloren` master @ `ebfff94` (merged 2026-10-06 6:56 PM ET). Sizes come from parsing the real `.vox` files; Veloren keeps them in Git LFS, so they were fetched through GitLab's raw endpoint. Barony facts come from the Barony wiki and the official workshop/editor guide.*

---

## 0. TL;DR

**Recommendation: go hybrid.** Build walls, floors and ceilings as **1 m blocks on a grid (option B)**, coloured the way Veloren colours its dungeon blocks. Dress them with **Veloren sprite `.vox` props at 1/11 scale (the useful part of option A)**: doors, wall sconces, chests, barrels, bones, webs, spikes, wall decor panels, bars and gates. Do **not** try to build the level from Veloren's structures.

Why:

* **Veloren has no modular dungeon kit.** Its dungeons (cultist, haniwa, sahagin, vampire castle, myrmidon, adlet, gnarling, terracotta) are **Rust code**. Each plot draws CSG primitives (`Primitive` + `Fill` + `Painter` in `world/src/site/generation.rs`) with its own built-in layout. Note that `site2` has been renamed to `world/src/site`, and the old generic `dungeon.rs` is gone. The only prefab dungeon is the **dwarven quarry**: 11 big `.vox` rooms (for example 12×57×23 and 40×39×40) where **1 voxel = 1 block**. So even Veloren's "3D asset" dungeons are block geometry, and none of them can follow your generator's room graph.
* **Veloren terrain has no textures, so option B is how Veloren itself looks.** Every block is one RGB colour. Dungeon walls use `Fill::Brick(Rock, base, range)`, a 2×2×1 staggered brick hash that shifts the colour by `0..range`. Cave walls use biome lerps. The terrain shader then adds a **3×3 hash-noise grid per block face**. Port those three things and you get Veloren's dungeon look without any texture.
* **The scale already lines up.** Sprites are authored on an **11-voxel-per-block** grid (`SPRITE_SCALE = 1/11`). A door is **11×11×33**, so 1 block wide and 3 tall. Humanoid figures use about 1/11.25 block per voxel and stand **≈2.2 blocks** tall, with eyes at **≈1.8 blocks**. Using **one Barony tile = 3×3 blocks, 3–4 blocks tall** gives Barony-like cube corridors that real Veloren doors and figures fit into.
* **The effort is modest, about 8–11 working days (roughly 2 weeks) for a playable dressed dungeon** on top of the generator port and the shader work you already have plans for. Option A done properly (porting Veloren's site painter plus a dungeon plot) is **3–5+ weeks**, and it fights your generator instead of using it.
* **Licence:** code and assets are GPL-3.0 by default. There's no `assets/LICENSE`, and `assets/credits.ron` says "assumed to be GPL3 if not provided". Three model entries are **CC-BY-NC-SA 4.0** and must be excluded: the glider `vroom.vox`, `sprite/mineral/ingot/*`, and `sprite/wood/log/wood.vox`. NC is not GPL-compatible.

---

## 1. What you're aiming for (Barony's model)

* There are three tile layers per cell: **FLOOR**, **WALLS** (one tile high, blocks movement, diggable, and the height torches spawn at) and **CEILING** (decorative, two tiles above the floor, no collision).
* Each face uses a 32×32 px texture (16×16 before Cursed Edition).
* Walls are plain cubes on a grid. Doors, torches, chests, barrels and the like are entities or sprites placed on that grid. Lighting is per-tile light from torches with some flicker.

So Barony is really "**a 2D tile grid extruded into cubes, plus props**". Your `dungeon-generator` already outputs exactly that kind of grid (`logical_grid[y][x]` ∈ {WALL, ROOM_FLOOR, CORRIDOR_FLOOR}, 3×3 rooms on a 150×150 map, 1-tile corridors; see `DUNGEON_GEN_PORT.md`).

---

## 2. Scale

| Thing | Size in Veloren | Source |
|---|---|---|
| Terrain block | 1 m (1 world unit) | `common/src/terrain` |
| Sprite voxel | **1/11 block** | `SPRITE_SCALE = 1/11` (`voxygen/src/scene/terrain/mod.rs:49`), `SCALE = 1/11` (`sprite-vert.glsl:55`) |
| Humanoid voxel | `BASE_HEIGHT·scaler/25` ≈ 1/11.25 block | `voxygen/anim/src/character/mod.rs:145`, `BASE_HEIGHT = 20/9` |
| Humanoid height | 2.22 × scaler (0.73 Danari to 1.18 Orc male) × 0.85–1.1, so human male ≈ **2.3 blocks** | `common/src/comp/body/humanoid.rs` |
| Eye height | `height·0.8` ≈ **1.8 blocks** | `body/mod.rs::eye_height` |
| Door / dark door | **11×11×33**, so **1×1×3 blocks**; wide door 17×11×33 | `sprite/door/door-0.vox` |
| Wall sconce | 5×7×11 (fits in 1 block, mounted on a wall) | `sprite/furniture/sconce_wall-0.vox` |
| Chest | 15×12×12 | `sprite/chests/chest.vox` |
| Barrel, crate | 9×9×11, 11×11×10 | |
| Bookshelf | 11×11×33 | |
| Dungeon wall decor | 11×2×11 to 11×6×11, 10 variants | `DungeonWallDecor` |
| Veloren cultist dungeon room | 30 blocks across, 3 floors | `cultist.rs:43–46` |

**Proposed mapping:** 1 generator tile = **3×3 blocks** on the floor plan, with **walls 3 blocks high (a Barony cube) or 4 (more headroom)**. The ceiling is the next block layer.

* A 1-tile corridor is 3 m wide. A 1 m Veloren door sits in the middle of a doorway with a 1-block jamb on each side, or a `DoorWide` covers 1.5 m.
* Eyes at about 1.8 m in a 3–4 m tall cell give Barony's proportions, since its walls are one tile tall.
* **Texel density comes out close to Barony's.** One 3×3-block wall face shows 3×3 block colours, and each block face gets 3×3 shader noise cells. That's **9×9 colour cells per tile face**, plus brick variation. It looks chunkier than Barony's 32 px, but it's the same kind of look. For finer detail, go to 4×4 blocks per tile, or switch on the brick-normal mode from §4.3.
* 3×3 rooms come out 9×9 m. That's tiny next to Veloren's 30 m rooms, so you'll probably want the generator's room size as a parameter (it's a hard-coded constant now).

Engine units: keep **1 libGDX unit = 1 block = 1 m**. Load sprite and figure meshes with a `1/11` scale matrix. The viewer currently draws figures with 1 unit = 1 voxel, so that becomes a single scale factor.

---

## 3. Option A: build the dungeon from Veloren's 3D assets

### 3.1 What's actually in the repo

| Asset set | Count / size | Dungeon-ready? |
|---|---|---|
| `assets/voxygen/voxel/sprite/**` | **950 `.vox` files** (4.1 MB), 383 `SpriteKind` entries in `sprite_manifest.ron` | **Yes, as props.** All are 1/11 scale, with `offset` and `ori` (8 directions of 45°) given in the manifest |
| `assets/world/structure/**` | 120 `.vox` files: 66 `jungle_ruin` pieces, 11 `dwarven_quarry` rooms, terracotta decor, `natural/*` (ribcage, skull, troll cave, arch…), `spots/*` (camp, igloo, witch hut, shipwreck, temples) | **Mostly no.** They're **1 voxel = 1 block** set pieces with fixed shapes. Only the dwarven quarry is a dungeon, and its rooms are 12×57×23 up to 77×54×72 blocks |
| Site plots (`world/src/site/plot/*.rs`, 36.5k lines) | `cultist.rs` 866 lines, `haniwa.rs` 908, `sahagin.rs` 779, `vampire_castle.rs` 2224, `adlet.rs` 2434, `gnarling.rs` 2232, `dwarven_mine.rs` 467, `troll_cave.rs`, `terracotta_palace.rs`, `myrmidon_arena.rs`… | **Code, not assets.** Each one carves its own layout using `Primitive` CSG and `Fill`s. There's nothing to load |

Dungeon-relevant sprites you can use straight away (manifest name → model, size in sprite voxels):

* **Doors and gates:** `Door` 11×11×33, `DoorDark` 11×11×33, `DoorWide` 17×11×33, `KeyDoor`/`BoneKeyDoor` (11³ blocks), themed key doors (`Vampire/Haniwa/Sahagin/Terracotta/MyrmidonKeyDoor`) plus matching keyholes, `DoorBars` 11³, `DropGate`/`DropGateBottom` (portcullis), `OneWayWall`, `MagicalBarrier`, `GlassBarrier`.
* **Lights:** `WallSconce` 5×7×11, `WallLamp`/`WallLampSmall`/`WallLampWizard`/`WallLampMesa`, `Lantern` (mine lamp 7×7×11), `FireBowlGround` 7×7×6, `Candle`, `Ember`/`FireBlock`, `BonfireMLit`, `StreetLamp`, `DiamondLight`. Their glow levels are in `Block::get_glow`: sconce, wall lamp, fire bowl and candle are **16**, ember is 20, lava 24.
* **Containers and loot:** `Chest`, `DungeonChest0..5` (vines, skull, gold, demon…), `CommonLockedChest`, `Barrel`, `Crate`, `CrateBlock`, `BarrelWoodWater/Coal`, `HaniwaUrn`, `JugClayM`, `SackLeatherM`.
* **Dungeon dressing:** `DungeonWallDecor` (10 wall-panel variants, 2–6 voxels deep, made to sit flush on a wall), `Bones` (5), `IronSpike`, `EnsnaringWeb`, `HaniwaTrap`, `MetalChain`, `SeaDecorChain`, `Grave`/`Gravestone`, `Mold`, `CeilingMushroom`, `GlowMushroom`, `Stones`, `CrystalHigh/Low`, `WoodBarricades`, `Window1..4`, `WitchWindow`.
* **Furniture:** `BookshelfEnd/Middle`, `Ladder`, beds, benches, tables, chairs, `Cauldron`, `Anvil`, `Forge`, `CraftingBench`, and so on.

### 3.2 Why A doesn't work for the level geometry

1. **There are no modular wall, floor or corner tiles.** Nothing like a Barony tileset (wall cube, corner, floor slab, ceiling) exists among the sprites or structures. The closest things are `DungeonWallDecor` panels and `CrateBlock`/`KeyDoor` "block" sprites. Building every wall face out of 11³ sprite cubes would mean about 1,000 voxels per m² of wall: big meshes, and they'd still look like coloured blocks.
2. **The structures don't fit your graph.** The dwarven quarry rooms are fixed, 1-block-resolution set pieces with hand-placed connections (`custom_indices` mark gates, key doors and lanterns). Plugging them into your MST/A* corridors would mean redesigning the generator around their footprints.
3. **Porting the plot code is a large job.** Running a Veloren dungeon would need `Primitive` (union, intersect, without, rotate, translate, ramps, cones, Bezier segments, …), the `Fill` evaluator, `Painter`, `Canvas`, `RandomField`, `StructureBlock`/prefab loading, and one plot file. That's roughly 2k + 0.8–2.4k lines of Rust, plus a block volume and a mesher anyway. It also gives you **Veloren's** layouts (towers, spiral stairs, multi-floor), not yours.
4. **Scale mismatch:** Veloren rooms are 20–40 m with 3+ floors. Barony is small, readable tile rooms.

**What A is good for: props, plus the colour formulas and design vocabulary in the plot code.** Those come back in §5.

---

## 4. Option B: blocks coloured the Veloren way

### 4.1 How Veloren colours dungeon and cave blocks

Every terrain `Block` is `(BlockKind, Rgb<u8>)`. There are no textures anywhere, and only the kind affects behaviour, glow and material. The colour comes from the `Fill` that painted it.

**Brick walls** (`world/src/site/generation.rs:557`, `Fill::Brick(kind, col, range)`):

```text
p   = (pos + (pos.z, pos.z, 0)) / (2, 2, 1)          // 2×2×1 bricks, shifted one block per layer (staggered)
h   = reverse_bits((p.x ^ p.y ^ p.z) as u8)          // cheap hash per brick
col = base + ((h * range) >> 8)                      // same scalar added to R, G and B
```

Other fills include `PlankWall` (RandomField hash over 16×16 cells, used for wood), `Checker`, `Gradient`/`GradientBrick`, and `Sampling` (an arbitrary function).

**Palettes the dungeons actually use** (copy them as material presets):

| Plot | Material | Kind, base RGB, range |
|---|---|---|
| Cultist | rock | `Rock (55,50,60) ±24` (Brick). "Broken rock" is a `Sampling` that picks (60,55,65), (65,60,70), (70,65,75), (75,70,80) or (55,50,60), with about 4% air holes. Floors are scattered with candles (1 in 30) |
| Haniwa | rock | `Rock (96,123,131) ±24`. Broken variants run (82,108,117)…(97,124,134). Grass is `(72,87,22) ±24`. Fire bowls 1 in 200 |
| Vampire castle | brick | `Rock (80,75,85) ±24`. Wood `(71,33,11) ±12`. Roof `GlowingRock (30,37,55)` |
| Sahagin | wood | `Wood (71,33,11) ±12`, plus `Gradient` bricks |
| Citadel | stone | `Rock (100,100,100) ±20` |
| Myrmidon | roof | `Sand (115,32,2) ±12` |
| Adlet | bone / ice / dirt | `Misc (200,160,140)`, `Ice (120,160,255)`, `Earth (55,25,8) ±24` |

**Caves** (`world/src/layer/cave.rs:1197–1300`): walls and ceilings use a nested lerp over biome weights (mushroom, fire, sandy, leafy, icy, crystal), with `col.marble*` noise picking between pairs such as (80,100,150)↔(23,44,88) for mushroom and (100,40,40)↔(100,75,100) for fire. Floors blend (68,62,58)↔(97,95,85). Glowing kinds appear by chance (`GlowingWeakRock`, `GlowingMushroom`). Basalt (50,35,75) and lava (255,65,0) sit below. This is good material for "cave levels" later.

**Shader texture** (`assets/voxygen/shaders/terrain-frag.glsl:284–300`):

```glsl
float noise = hash(vec4(floor(f_chunk_pos * 3.0 - f_norm * 0.5), 0));  // 3×3 cells per block face
const float A = 0.055, W_INV = 1/(1+A), W_2 = W_INV*W_INV, NOISE_FACTOR = 0.015;
vec3 nd  = sqrt(f_col) * W_INV + noise * NOISE_FACTOR;
vec3 col = nd * nd * W_2;            // f_col is linear (srgb_to_linear in greedy_extract_col_light_*)
```

There's an optional `EXPERIMENTAL_BRICKLOREN` mode at lines 74–82 that bevels normals per 2×2 brick and darkens the mortar. It's cheap and worth having as a toggle.

**AO and light are baked per texel** (`voxygen/src/mesh/terrain.rs`). `calc_light` flood-fills sunlight (max 24) and glow from emissive blocks (`get_glow`) through air, one step per block, and the result is packed with AO into the col/light atlas. Point lights are added in the shader (`light.glsl`, 20 lights, falloff `3/(5+d²)`).

### 4.2 Fit with your codebase

* **Mesher:** `VoxMesher` already does face-culled cube meshing with a per-face palette colour and a glow flag. A dungeon mesher is the same loop over a `byte/short[x][y][z]` block volume, split into **chunks of 32×32 blocks in plan** (Veloren's `TerrainChunkSize` is 32×32). Use int indices or chunking to stay under the 16-bit index limit VoxMesher works around today.
* **Greedy meshing:** Veloren merges faces greedily and keeps per-voxel colour, light and AO in a texture atlas. You can skip that at first. A 150×150-tile map at 3×3 blocks per tile is 450×450 blocks. With about 4k floor tiles, that's roughly 36k floor faces, 36k ceiling faces and about 100k wall faces: **about 170k quads in total**, fewer than 200 chunk draw calls, and frustum culling cuts most of them. That's easy for GL 3.3. If you want greedy merging later, per-block colour variation means you'd need Veloren's atlas trick (or colour from a 3D texture lookup by block position), which is more work than it saves at this scale.
* **AO:** use the same 4-neighbour corner AO already planned for `VoxMesher` (VELOREN_SHADERS.md, item 2). Blocks are where it matters most, in wall/floor corners.
* **Collision and navmesh:** the grid itself is the collision. Grid-cell AABB checks are enough for a Barony-style controller, or use merged `btBoxShape` runs. For recast4j, feed it floor and wall triangles from the same mesher. Both libraries are already declared in `core/build.gradle` (see `DUNGEON_GEN_PORT.md` §6 and `NAV_COLLISION_PORT.md`).
* **Generator fit:** direct. Tile → 3×3 column of blocks: `WALL` = solid from floor to ceiling, `ROOM_FLOOR`/`CORRIDOR_FLOOR` = floor block, 3–4 air blocks, then ceiling. The material comes from the region type (room vs corridor) and the level theme.

---

## 5. Hybrid: blocks for the level, Veloren sprites for props (recommended)

### 5.1 Pipeline

1. **Generator → `TileGrid`** (your port), then add a **decoration pass** that marks door sockets, torch slots, chest and furniture spots on the tile graph.
2. **`TileGrid` → `BlockVolume`**, 1 m blocks:
   * Wall blocks get `Fill::Brick` in the level theme's base colour (for example cultist `(55,50,60) ±24`). Mix in 5–10% "broken rock" variants with the `Sampling` approach.
   * Floors use a darker brick or `Checker` in rooms and plain rock in corridors. Ceilings use the wall colour minus about 10.
   * Optional: carve 1-block alcoves and pillars at room corners so it doesn't look perfectly flat. Veloren dungeons do this a lot.
3. **`BlockVolume` → chunk meshes**: face culling, per-vertex AO, and a light attribute from step 5.
4. **Sprites → instanced meshes.** Parse `sprite_manifest.ron` with your existing ANTLR RON converter, mesh each model once through `VoxMesher`, and draw it with `model = T(block + 0.5) · Rz(ori·45°) · S(1/11) · T(offset)`. Use one libGDX `Mesh` per sprite type and per-instance matrices, either a loop of `setUniformMatrix` calls or real instancing. A few hundred props is cheap: the door is about 1,000 faces after culling, a sconce about 300, a chest about 1,750.
   * Doors go into corridor mouths where a corridor meets a room tile. Rotate them by `ori` and swing them open around their hinge.
   * `WallSconce` goes on wall faces next to floor every N tiles (Barony puts torches at wall height). Hang `DungeonWallDecor` panels flush on walls. Scatter `Bones`, `EnsnaringWeb`, `Candle`, `Barrel`, `Crate` and `Chest` by room type.
5. **Lighting:**
   * **Baked:** a BFS flood fill from each emissive sprite or block using Veloren's glow levels (sconce 16, ember 20, lava 24), dropping by 1 per block, stored per vertex. Sunlight is 0 underground, so the dungeon is dark except near torches, which is the Barony feel.
   * **Dynamic:** the 8–20 nearest torches as point lights with a flicker term. That's Veloren's `lights_at`, falloff `3/(5+d²)`, and it's part of the shader port you already have planned.
   * **Emissive voxels:** the glow palette indices. Use **14–16 by Veloren's 0-based indexing**: the sprite manifest header says 13–15 are 0-based, which means MagicaVoxel slots 14–16. Your mesher currently checks raw slots 13–15, the issue already flagged in VELOREN_SHADERS.md. Sconce and candle flames depend on this being right.

### 5.2 Fit with the planned Veloren shader port

Everything lands in the same three items from `VELOREN_SHADERS.md`: linear colour and tonemap, per-vertex AO, and figure lighting with `lights_at` and glow. Terrain adds just two small things: **the 3×3 face-noise term** (a few lines of GLSL) and **one light/glow vertex attribute**. Sprites and figures share the figure shader path. Underground there's no sun or sky, so you can leave out shadows and fog at first. Add fog later as darkness falloff, which Barony also does.

### 5.3 Colour-only variant (a Barony "coarse tile" look)

If you want walls to read as a single big tile per cell, mesh one big cube per generator tile and give the shader `floor(pos * 3)` noise in tile units, plus a brick hash in world blocks. That's cheaper (about 9× fewer faces) but loses Veloren's chunky 1 m bricks. With the 3×3-block mapping above it isn't needed.

---

## 6. Comparison

| | A: Veloren 3D assets | B: Veloren-coloured blocks | **Hybrid (B + sprite props)** |
|---|---|---|---|
| Matches Barony's grid / tile gameplay | Poor (fixed set pieces or code layouts) | Exact | Exact |
| Uses your dungeon-generator output | Hard; would need a redesign | Direct | Direct |
| Looks like Veloren | Yes | Yes (same colours and shader noise) | Yes, plus real Veloren doors, torches and loot |
| Readiness of assets | Props ready; no wall/floor kit; 11 prefab rooms | Nothing to load, just formulas | Props ready (950 sprite .vox files) |
| Performance | Prefab rooms are 8k–60k voxels each; fine, but no culling structure | ~170k quads for a 150×150 map, chunked | Same plus a few hundred instanced props |
| Collision / navmesh | Triangle soup from prefab meshes | Trivial from the grid | Trivial from the grid; props get simple boxes |
| Lighting with the shader port | OK | Natural (flood fill + point lights) | Natural, and sconces are the light sources |
| Licence | GPL-3.0 (see §7) | GPL-3.0 for ported formulas | GPL-3.0; avoid the 3 NC assets |
| Effort | **3–5+ weeks** (port painter/CSG + one plot) or fixed prefabs only | **~4–5 days** | **~8–11 days** in total |

---

## 7. Licensing

* Veloren's `LICENSE` is **GPL-3.0** (README FAQ: "licensed under the GNU GPL v3.0"). The README's `SPDX CC-BY-SA-4.0` tag covers only the banner snippet in the README.
* **There is no `assets/LICENSE`.** `assets/credits.ron` is the source of truth: "*[license] Can be omitted, but assumed to be GPL3 if not provided.*" So every sprite and structure `.vox` is GPL-3.0 unless it's listed there.
* **Exclude these, because they're CC-BY-NC-SA 4.0 and not GPL-compatible:** `voxygen/voxel/glider/vroom.vox`, `voxygen/voxel/sprite/mineral/ingot/*` (10 ingot sprites), `voxygen/voxel/sprite/wood/log/wood.vox`. Some music is also NC, which matters if you ever bring audio over. Add a filter in `VoxCatalog` or the converter based on `credits.ron`.
* Your repo is GPL-3.0, so you can port colour formulas (`Fill::Brick`, the cave lerps, the terrain-frag noise) with a credit comment naming the Veloren source file. The README already acknowledges Veloren. Add a note that the generated dungeon materials and the ported shader code come from Veloren.

---

## 8. Effort (hybrid)

Assumes the generator port (DUNGEON_GEN_PORT.md) and the linear-colour + AO + lighting shader work (VELOREN_SHADERS.md) happen separately.

| Step | Estimate |
|---|---|
| `TileGrid → BlockVolume` with themes (Brick, Sampling broken rock, Checker floors), configurable tile size and height | 0.5–1 d |
| Chunked block mesher (face culling, AO, light attribute, int indices) | 1.5–2 d |
| Terrain shader additions (3×3 face noise, optional brick bevel, glow/light attribute) | 0.5 d |
| First-person controller with grid collision, plus a Barony-style optional step/turn mode | 1 d |
| Sprite manifest loader (RON → JSON is already there), mesh cache, `ori`/`offset`/1/11 transform, instanced draw | 1–1.5 d |
| Decoration pass (doors at room/corridor mouths, sconces, wall decor, loot/clutter by room type) | 1–1.5 d |
| Door open/close (hinge rotation, collision toggle) | 0.5–1 d |
| Torch lighting: flood-fill glow bake plus N nearest flickering point lights | 1.5–2 d |
| Licence filter from `credits.ron`, plus attribution | 0.25 d |
| **Total** | **≈ 8–11 days** (one level theme). Each extra theme (cave lerps, haniwa, vampire) is about 0.5 d |

Later options: cave levels with `cave.rs` colour lerps and noise-carved walls instead of grid walls (2–3 d). Greedy meshing with a colour atlas only if profiling asks for it. Dwarven-quarry rooms as hand-made "special rooms" stamped into the grid (1–2 d, after mapping `custom_indices`).

---

## 9. Gotchas

* **Axes:** Veloren is Z-up. `VoxMesher.corner()` already turns (x,y,z) into (x, z, −y). Use the same mapping for blocks, sprite `offset` and `ori` rotation (about Veloren Z, which is libGDX Y).
* **Sprite offsets are in sprite voxels**, applied before the 1/11 scale and relative to the block corner plus (0.5, 0.5) horizontally. Most are (−5.5, −5.5, 0), which centres the sprite in the block. Wall-mounted sprites like the sconce at (−2.5, −1.5, 0) and wall decor at (−5.5, −1, 0) are offset toward one block edge, so `ori` picks which wall they sit on.
* **Variants:** many manifest entries list several models (`DungeonWallDecor` has 10, `Bones` has 5). Pick one with a position hash, like Veloren does.
* **Glow index off-by-one** (§5.1, step 5).
* **LFS:** Veloren's `.vox` files are Git LFS objects. A `--filter=blob:none` clone without `git lfs` gives 130-byte pointer files. Your `velorenPath` checkout needs `git lfs pull`, which it presumably already has since the figures load.
* **Room size:** 3×3-tile rooms are 9×9 m. Expose `ROOM_WIDTH/HEIGHT` in the port if you want more Barony-sized rooms.
