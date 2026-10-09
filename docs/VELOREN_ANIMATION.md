# Veloren animation system and what jvm-dungeon-crawler needs to adopt it

Research only. No changes were made to `SOS-Games/jvm-dungeon-crawler`.

- Veloren source read: `gitlab.com/veloren/veloren` `master` @ `ebfff94` (committed 2026-10-06 18:56 ET)
- Java repo read: `SOS-Games/jvm-dungeon-crawler` @ `6093f3a` ("animals")
- vek (Veloren's math crate) checked at `github.com/yoanlcq/vek` for exact Transform/lerp semantics

---

## TL;DR

Veloren animation isn't keyframes. Each body type (bird_medium, quadruped_small, character, …) has a **skeleton struct of named bones** (`Transform{position, orientation: quat, scale}`). Each **animation is a pure function** `(dependency tuple, anim_time, &mut rate, SkeletonAttr) -> Skeleton` that writes those bones using sin/cos waves plus per-species offsets (`SkeletonAttr`). Each frame the figure manager **picks an animation from physics/character state**, gets a *target* skeleton, **slerps the current skeleton toward it** with `min(15*dt, 1)`, runs `compute_matrices` (a hard-coded parent chain, e.g. `wing_out_l = chest * wing_in_l * wing_out_l`), and uploads **up to 16 bone matrices** to a uniform. The mesh is **one vertex buffer per figure, each vertex tagged with a 4-bit bone index**. The vertex shader does `bones[idx].bone_mat * pos`.

Your repo **evaluates that once, offline**: `AnimalPose.java` holds Veloren's idle pose already **flattened through the hierarchy into world positions and world 3×3 matrices**. `NpcFigure.build()` then bakes every part into **one static mesh with no bone information**. To animate you need: (1) bone-tagged or per-part meshes in bone-local space, (2) a Skeleton/Bone runtime with Veloren's parent chain, (3) `SkeletonAttr` tables instead of baked poses, (4) ported animation functions, (5) a per-frame time + slerp loop, and (6) bone matrices reaching the shader. A first animated bird_medium (idle/run/fly) in the viewer is about **4–7 working days**.

---

## 1. How Veloren does it

### 1.1 Crate layout: `voxygen/anim` (crate `veloren-voxygen-anim`)

| Path | What it is |
|---|---|
| `voxygen/anim/src/lib.rs` | `Skeleton` trait, `Animation` trait, `Bone = Transform<f32,f32,f32>`, `FigureBoneData`, `MAX_BONE_COUNT = 16`, `make_bone`, the `skeleton_impls!` macro, dylib hot-reload glue |
| `voxygen/anim/src/vek.rs` | Re-exports vek with `repr_c` types and a **column-major** `Mat4` |
| `voxygen/anim/src/util.rs` | Easing helpers: `bounce`, `elastic`, `ease_in_back`, `out_and_in` |
| `voxygen/anim/src/<body>/mod.rs` | One per body: bone list, `compute_matrices_inner` (the hierarchy), `SkeletonAttr` (per-species table), mount helpers |
| `voxygen/anim/src/<body>/<anim>.rs` | One file per animation (`idle.rs`, `run.rs`, `fly.rs`, `alpha.rs`, …) |

Bodies and mesh-bone counts in this commit: character 16 (+4 helper bones), biped_large 16, biped_small 10 (+3 control), bird_large 16, bird_medium **9**, quadruped_small **7**, quadruped_medium 15, quadruped_low 16, theropod 13, dragon 15, fish_medium 7, fish_small 4, golem 12, arthropod 16, crustacean 15, object 2, item 1, ship 4. Animation counts: character 33 files, biped_large 25, bird_large 15, bird_medium 12, quadruped_small 8.

### 1.2 Bone / Transform

```rust
pub type Bone = Transform<f32, f32, f32>;   // position: Vec3, orientation: Quaternion, scale: Vec3
```
- `Mat4::from(Transform)` in vek is `Mat4::from(orientation).scaled_3d(scale).translated_3d(position)`, i.e. **M = T · S · R**. Scales are always uniform in practice, so this equals libGDX's `Matrix4.set(pos, quat, scale)` (T·R·S).
- `Lerp` for `Transform` lerps position and scale and **slerps** orientation (`vek/src/transform.rs`). The slerp flips `to` when `dot < 0` (shortest path) and falls back to normalized lerp when nearly equal.

### 1.3 `skeleton_impls!` and the `Skeleton` trait

```rust
skeleton_impls!(struct BirdMediumSkeleton ComputedBirdMediumSkeleton {
    + head + chest + tail + wing_in_l + wing_in_r + wing_out_l + wing_out_r + leg_l + leg_r
});
```
- `+ bone` is a **mesh bone**, which gets a matrix and an index. Bones without `+` (`torso`, `control`, `control_l`, `mount`, …) are helper transforms used only inside `compute_matrices`. After `::` come non-bone fields (flags such as `holding_lantern` or `detach_right`).
- **The bone index is the declaration order**. It has to match the order the loader meshes parts in (`voxygen/src/scene/figure/load.rs`, the `mesh_*` functions in the same order).
- The macro also generates `Lerp for &Skeleton` (per-bone lerp/slerp; non-bone fields snap to the target) and `set_figure_bone_data` (writes matrices into the `[FigureBoneData; 16]` buffer).

```rust
pub trait Skeleton { type Attr; type Body; type ComputedSkeleton; const BONE_COUNT: usize;
    fn compute_matrices_inner(&self, base_mat: Mat4<f32>, buf: &mut [FigureBoneData; 16], body: Self::Body)
        -> Self::ComputedSkeleton; }
```

**bird_medium hierarchy** (`voxygen/anim/src/bird_medium/mod.rs`):
```rust
let base_mat      = base_mat * Mat4::scaling_3d(SkeletonAttr::from(&body).scaler / 8.0);
let chest_mat     = base_mat  * Mat4::from(self.chest);
let head_mat      = chest_mat * Mat4::from(self.head);
let tail_mat      = chest_mat * Mat4::from(self.tail);
let wing_in_l_mat = chest_mat * Mat4::from(self.wing_in_l);   // same for _r
let wing_out_l_mat= wing_in_l_mat * Mat4::from(self.wing_out_l);
let leg_l_mat     = base_mat  * Mat4::from(self.leg_l);       // legs hang off the ROOT, not chest
```
**quadruped_small**: `chest = base * scaling(scaler/11) * chest`. Head, four legs and tail are all children of chest.

`make_bone(mat)` stores `(mat, mat.map_cols(normalized))`. The second matrix is the bone matrix with columns normalized (scale removed) and is used to rotate normals.

### 1.4 `SkeletonAttr`: per-species numbers

Each body's `mod.rs` has `struct SkeletonAttr` with tuples per bone, plus `scaler` and sometimes `tempo`/`feed`, and `impl From<&Body>` with a big `match (species, body_type)`. For example bird_medium Parrot:
```
chest (0.0, 5.0)  head (1.5, 4.5)  tail (-6.0, 0.0)
wing_in (2.0, 0.5, 0.0)  wing_out (5.0, 3.0, 0.5)  leg (1.5, -1.0, 2.2)  scaler 0.75
```
Tuples are `(y, z)` or `(x, y, z)` in **voxel units, Z-up, +Y forward**. The left side is generated in the animation by negating x (`Vec3::new(-s_a.wing_in.0, …)`).

### 1.5 The `Animation` trait and functions

```rust
pub trait Animation { type Skeleton: Skeleton; type Dependency<'a>;
    fn update_skeleton_inner(skeleton: &Self::Skeleton, dep: Self::Dependency<'_>,
        anim_time: f32, rate: &mut f32, s_a: &<Self::Skeleton as Skeleton>::Attr) -> Self::Skeleton; }
```
- **Pure function**: clone the input skeleton, overwrite bones, return. No keyframes, no state.
- **`Dependency`** is a per-animation tuple. Examples:
  - `bird_medium::IdleAnimation`: `f32` global time, used for a pseudo-random head look that changes every few seconds.
  - `bird_medium::RunAnimation`: `(velocity, orientation, last_ori, avg_vel, acc_vel)`.
  - `bird_medium::FlyAnimation`: `(velocity, orientation, last_ori)`.
  - Attack animations (`AlphaAnimation`, `ShootAnimation`, …): `(Option<StageSection>, time, state_time, ori, last_ori, on_ground)`, and `anim_time` is the **0..1 stage progress** of Buildup/Action/Recover.
- **`anim_time`** is `state.state_time` (seconds since the character-state variant last changed, scaled by `rate`) for locomotion, or stage progress for abilities.
- **`rate: &mut f32`** lets the animation set its own playback speed (`run.rs` sets `*rate = 1.0`). The caller feeds it back into the next frame's `state_time += dt * rate / scale`.
- Typical body (bird_medium `idle.rs`):
  ```rust
  let wave_slow_cos = (anim_time * 4.5).cos();
  next.chest.position = Vec3::new(0.0, s_a.chest.0, s_a.chest.1 + wave_slow_cos * 0.06 + 1.5) * s_a.scaler;
  next.chest.scale    = Vec3::one() * s_a.scaler * 0.99;
  next.wing_in_l.position    = Vec3::new(-s_a.wing_in.0, s_a.wing_in.1, s_a.wing_in.2);
  next.wing_in_l.orientation = Quaternion::rotation_x(-PI/2.0)
                             * Quaternion::rotation_y(-PI/2.5 + wave_slow_cos * 0.06)
                             * Quaternion::rotation_z(-PI/4.0);
  ```
- Gaits (`run.rs`) use `acc_vel`, which is accumulated distance travelled, as the phase (`mixed_vel = acc_vel + anim_time*5`). That way stride frequency follows real speed with no foot sliding. Turning tilt comes from `ori.angle_between(last_ori)`.
- `0.99` and `1.01` scales show up everywhere. They fight z-fighting between overlapping parts, so keep them.

### 1.6 Picking and blending: `voxygen/src/scene/figure/mod.rs`

`FigureMgr::maintain` → `maintain_entity` → one big `match body { Body::BirdMedium(body) => { … } }` arm per body. For bird_medium (around lines 3285–3602):

1. Get the model and `SkeletonAttr` from `bird_medium_model_cache.get_or_create_model`.
2. Get or create a `FigureState<BirdMediumSkeleton>`.
3. If `!character.same_variant(last_character)`, set `state.state_time = 0`.
4. **Base layer** from `(on_ground, moving = |rel_vel|² > 0.2², in_liquid, is_rider)`:
   standing → `IdleAnimation`, ground+moving → `RunAnimation`, airborne → `FlyAnimation`, moving in liquid → `SwimAnimation`, anything else → Idle. Each is computed **from `Skeleton::default()`**, not from the previous frame.
5. **Action layer**: `match CharacterState` (`Sit`→Feed, `BasicBeam`→Breathe, `BasicMelee`→Alpha, `BasicRanged`→Shoot, `Shockwave`, `BasicSummon`, `DashMelee`→Dash, `Stunned`). It is computed **on top of `target_base`** (the input skeleton is the base pose). `stage_progress = timer / stage_duration`. Otherwise → `target_base`.
6. **Blend**: `state.skeleton = Lerp::lerp(&state.skeleton, &target_bones, dt_lerp)` with `dt_lerp = (15.0 * dt).min(1.0)`. That's exponential smoothing toward the target, and it's the only cross-fade between animations.
7. `state.update(...)` (`FigureState::update`, around line 8423):
   - `last_ori = lerp(last_ori, ori, 15*dt).normalized()`
   - `state_time += dt * state_animation_rate / scale`
   - `mat = Mat4::from(ori) * scaling(scale)` (or the mount transform)
   - `FigureLocals::new(mat, col, pos, atlas_offs, is_player, light, glow)` → uniform `u_locals`
   - `compute_matrices(&skeleton, mat, buf, body)` → `update_consts(bound.1, &bones[0..BONE_COUNT])`
   - `avg_vel = s*avg_vel + (1-s)*(pos-last_pos)/dt` with `s = 0.1^dt`
   - `acc_vel += |avg_vel - ground_vel| * dt / scale` (humanoids use `^0.65 * 2.95`), reset to 0 when stopped
   - squash factor, weapon trails, `mount_world_pos`

### 1.7 GPU side: `assets/voxygen/shaders/figure-vert.glsl`, `voxygen/src/render/pipelines/figure.rs`

```glsl
layout(location = 0) in uint v_pos_norm;   // bits 0-26: pos xyz (9 bits each, (v-256)/2), 27-30: bone idx, 31: normal sign
layout(location = 1) in uint v_atlas_pos;  // bits 0-1: normal axis, 2-16 / 17-31: atlas uv
uniform u_locals { mat4 model_mat; vec4 highlight_col; vec4 model_light; vec4 model_glow; ivec4 atlas_offs; vec3 model_pos; int flags; };
struct BoneData { mat4 bone_mat; mat4 normals_mat; };
uniform u_bones { BoneData bones[16]; };
...
uint bone_idx = (v_pos_norm >> 27) & 0xFu;
f_pos = (bones[bone_idx].bone_mat * vec4(pos, 1.0)).xyz + (model_pos - focus_off.xyz);
vec3 norm = bones[bone_idx].normals_mat[axis_idx].xyz;   f_norm = mix(-norm, norm, v_pos_norm >> 31u);
```
- **Rigid skinning, one bone per vertex, no weights.**
- `bone_mat` already includes `model_mat` (orientation and scale). World translation is added separately via `model_pos`, which keeps precision near the camera.
- The mesh: `voxygen/src/scene/figure/cache.rs` (`make_model` / `generate_mesh`) loops over the bone meshes in index order and calls `generate_mesh_base_vol_figure(greedy, mesh, segment, offset, scale, bone_idx)` (`voxygen/src/mesh/segment.rs`). Each vertex is `TerrainVertex::new_figure(atlas_pos, (pos + offs) * scale, norm, bone_idx)`. So **vertex position = voxel corner + manifest offset, in bone-local space**. Colors come from a texture atlas, not vertex colors.
- **Mirroring** (`load.rs`, e.g. `mesh_wing_in_l`): `graceful_load_segment_flipped(name, true, idx)` mirrors the voxel grid **inside the segment** (x → size−1−x). The manifest offset is then used **as-is** (it's already the mirrored value). There's no negative-determinant matrix anywhere, so winding never flips.

### 1.8 Hot reload / dylib

- `voxygen/Cargo.toml`: feature `hot-anim = ["anim/use-dyn-lib"]`.
- With `use-dyn-lib`, every `update_skeleton`/`compute_matrices` call looks up a symbol (e.g. `b"bird_medium_run\0"`, `b"bird_medium_compute_mats\0"`) in a dynamically loaded library. The symbol is exported by `#[cfg_attr(feature = "be-dyn-lib", unsafe(export_name = …))]`.
- `common/dynlib/src/lib.rs` uses `notify` to watch the anim crate's `src/` for `.rs` changes, runs `cargo rustc --package veloren-voxygen-anim --features …/be-dyn-lib -Z unstable-options --crate-type dylib`, copies the result to an `_active_N` file (needed on Windows) and swaps it in under a mutex.
- `inline_tweak` is also a dependency, for live-tweaking literals in debug builds.
- **Java equivalent**: you don't need any of this. Run the viewer in debug mode from IntelliJ and use **HotSwap** (method-body edits apply on recompile, which is exactly what an animation function is). JetBrains Runtime with `-XX:+AllowEnhancedClassRedefinition` also allows adding methods and fields. A small "reload" key that re-instantiates animation objects is enough.

---

## 2. What the Java repo has today

| File | What's there | Relevance |
|---|---|---|
| `viewer/AnimalPose.java` (4240 lines) | `POS`/`ROT` maps keyed `"species/sex\0bone"`: **world-space position and world 3×3 rotation per bone** | It's Veloren's **idle pose already pushed through `compute_matrices`** at one instant. For example parrot `chest z = 4.875 = (5.0 + 1.5) × 0.75`, and `head = chest + (1.5, 4.5) × 0.7425`. The wave term is dropped, the hierarchy and scale are collapsed, and there's no `SkeletonAttr`. **The generator that produced it isn't in the repo.** |
| `viewer/FigurePose.java` | biped_small `SkeletonAttr`-like record (`headY, headZ, chestY, …, footZ`) plus `grip` per species; resolves `bone()` with LargePose → AnimalPose → biped_small fallback | Closest thing to a real `SkeletonAttr`. It's positions only, with no rotations or animation |
| `viewer/LargePose.java` | biped_large attrs (head/jaw/upper/lower/tail/shoulder/hand/leg/foot + `floating`) and a hand-composed hierarchy (`lowerY = upperY + attr.lowerY`) | Partial hierarchy, positions only |
| `viewer/NpcFigure.java` | Reads converted manifests (central/lateral per body), builds `Part{file, ox,oy,oz, mirror, bone, left, tool, model}`. `build()` turns each part into a `VoxInstance(rotation, translation)` around the **part center** and **merges everything into one `VoxScene` → one static mesh**. Includes quaternion helpers (`rotX/Y/Z`, Hamilton `mul`, `mat`) and a hand-ported `Hold` wield stance (Bow/Spear/Staff/Blowgun/default) | Bone names survive in `Part.bone` but are **thrown away at mesh time**. Mirroring uses a `MIRROR_X` matrix with negative determinant. **The wing bug from earlier (line 335, `ox = -part.ox - sizeX`) is still unfixed at `6093f3a`.** |
| `converter/VoxMesher.java` | Per-face (not greedy) mesher. 7 floats/vertex `pos3, normal3, packedColor1`. Splits into several `Mesh` at 65,532 verts. Applies the instance rotation/translation and then **swizzles Z-up → Y-up `(x, z, −y)`**. Flips the quad when `det < 0` | No bone attribute. Everything is in world space |
| `render/ForwardRenderer.java` | One inline GLSL 330 shader: `u_mvp`, `u_model`, `a_position/a_normal/a_color` | No bone uniforms |
| `JvmDcApp.java` | Browser grid + single view. `model.rotate(Y, yaw).rotate(X, pitch)`, Y-up camera. No per-frame time is used for meshes | No animation clock, no UI widgets (just `BitmapFont` text and mouse) |
| `lwjgl3/Lwjgl3Launcher.java` | `GLEmulation.GL30, 3, 3` | Uniform arrays of `mat4[16]` and integer/float attributes are fine |

**Bottom line**: there's no runtime skeleton, no hierarchy, no time, no per-part transforms, and no bone index in vertices. All animation-relevant data (`SkeletonAttr`) has been reduced to one baked frame.

---

## 3. Gap analysis and prioritized plan

Estimates assume one developer who knows the codebase. "d" = focused working day.

### P0: Bone-local meshes (prerequisite, 1–1.5 d)

Two options. Pick **A for the viewer now** and keep B in mind for the game:

**A. One `Mesh` per bone (simplest).** `NpcFigure.buildParts(assembly)` returns `List<BonePart{String bone, int boneIndex, VoxMesh mesh}>`. Each part is meshed **in bone-local space, exactly like Veloren**:
- vertex = voxel corner **+ manifest offset**, Z-up, **no centering, no swizzle**
- for flipped parts, mirror the **voxel grid** (`x → sizeX-1-x`) before meshing and use the offset unchanged. This removes `MIRROR_X`, the `det<0` quad flip, **and fixes the left-wing bug for free**, because you stop re-mirroring an already-mirrored offset.
- draw: `u_model = viewModel * rootFix * boneMat[i]` per part (≤16 draw calls per figure; trivial for a viewer)

**B. One mesh per figure + bone index attribute (Veloren way, better for many NPCs in-game).** Add an 8th float (or a `GL_UNSIGNED_BYTE` generic attribute) `a_bone` to `VoxMesher` → 8 floats/vertex. Upload `uniform mat4 u_bones[16]` (and optionally `u_bone_normals[16]`) and do `u_bones[int(a_bone)] * vec4(pos,1)` in the vertex shader. Keep **all parts of one figure in one `Mesh`**. The 65,532-vertex split must not spread a figure across meshes unless each sub-mesh gets the same bone uniforms (that works, it just costs extra draws).

Also add `VoxMesher.addModelLocal(model, offset, flipped, boneIndex)` so the existing world-space path stays untouched for single `.vox` files.

### P1: Skeleton runtime (1 d)

```java
final class Bone { final Vector3 position = new Vector3(); final Quaternion orientation = new Quaternion();
                   final Vector3 scale = new Vector3(1,1,1);
                   Matrix4 toMatrix(Matrix4 out) { return out.set(position, orientation, scale); }
                   void lerp(Bone target, float t) { position.lerp(target.position,t); scale.lerp(target.scale,t);
                                                     orientation.slerp(target.orientation,t); } }
abstract class Skeleton { final Bone[] bones; abstract void computeMatrices(Matrix4 base, Matrix4[] out, SkeletonAttr a); }
final class BirdMediumSkeleton extends Skeleton { static final int HEAD=0, CHEST=1, TAIL=2, WING_IN_L=3, WING_IN_R=4,
                   WING_OUT_L=5, WING_OUT_R=6, LEG_L=7, LEG_R=8;  // same order as Veloren's skeleton_impls! }
```
- **Hard-code each body's parent chain in `computeMatrices`**, copied line for line from `<body>/mod.rs`. Don't build a generic parent-index tree first. Veloren has quirks (legs parented to root in bird_medium, helper bones like `control`/`torso` in character/biped_small) that a generic tree will get wrong.
- Keep a `copyFrom(Skeleton)` so animations can do `next = base.copy()`.
- libGDX `Quaternion.slerp` is fine (shortest path, normalized).

### P2: Port `SkeletonAttr` instead of baked poses (0.5 d per body; script it)

- Port `SkeletonAttr` + `From<&Body>` for bird_medium (19 species) and quadruped_small. A Java `record BirdMediumAttr(float headY, headZ, chestY, chestZ, …, scaler, feed)` plus a `Map<String,BirdMediumAttr>` keyed `"parrot/male"`.
- **Generate it, don't hand-type it.** A ~50-line script can regex the `match (body.species, body.body_type)` arms out of `voxygen/anim/src/<body>/mod.rs` into JSON (you already have an ANTLR RON pipeline and JSON loading). Expanding `(Parrot, _)` to both sexes is the only subtlety.
- `AnimalPose.java` then becomes a fallback for bodies not ported yet. Once each body is animated, `IdleAnimation(t=0)` reproduces it (sanity test: compare matrices against AnimalPose for parrot).

### P3: Port animation functions (bird_medium idle+run+fly ≈ 1 d; quadruped_small idle+run ≈ 0.5 d)

```java
interface Animation<S extends Skeleton, A, D> { void update(S base, D dep, float animTime, float[] rate, A attr, S out); }
```
- **Start with `bird_medium/idle.rs` (81 lines), `run.rs` (133), `fly.rs` (142)**. Then `quadruped_small/idle.rs` (71) and `run.rs` (125). They translate almost mechanically:
  - `Quaternion::rotation_x(a)` → `new Quaternion().setFromAxisRad(1,0,0,a)` (write `rotX(a)` helpers, and you already have them in `NpcFigure`)
  - `q1 * q2 * q3` → `rotX(a).mul(rotY(b)).mul(rotZ(c))` (libGDX `mul` = post-multiply, same as vek)
  - `Vec3::one() * k` → `set(k,k,k)`
  - `f32::powi/powf/atan2/sin` → `Math.*` with `(float)` casts (or `MathUtils`)
  - `vek::Vec2::angle_between` / `determine_side` → small helpers
- Then attacks (`alpha.rs`, `shoot.rs`, `breathe.rs`, …), driven in the viewer by a fake stage clock (see P6).
- character (16 bones, 33 animations, weapon/`control` bones) and biped_large are the expensive ones (**5–10 d each** for full coverage). Leave them until the pipeline is proven.

### P4: Time and interpolation loop (0.5 d)

Per figure instance:
```java
float stateTime, accVel; Vector3 avgVel; Quaternion lastOri; Skeleton current, target; String lastAnim;
void tick(float dt, AnimInputs in) {
    if (!in.anim.equals(lastAnim)) { stateTime = 0; lastAnim = in.anim; }   // = same_variant check
    float[] rate = {1f};
    pickAndRun(in, stateTime, rate, target);                                // base layer, then action layer on top
    current.lerpTo(target, Math.min(15f * dt, 1f));                         // Veloren's dt_lerp
    stateTime += dt * rate[0] / scale;
    accVel = speed > 0 ? accVel + speed * dt / scale : 0;
    lastOri.slerp(ori, Math.min(15f * dt, 1f));
    current.computeMatrices(base, boneMats, attr);
}
```
Use `Gdx.graphics.getDeltaTime()`, and clamp dt (e.g. ≤ 0.1 s) so a window drag doesn't explode the springs.

### P5: Shader / draw path (0.5 d for A; +0.5 d for B)

- **A (per-part)**: no shader change. `renderer.draw(camera, part.mesh.parts, tmp.set(viewModel).mul(rootFix).mul(boneMats[i]))`. Normals: the shader uses `mat3(u_model) * a_normal`. Bone matrices carry uniform scale, and FRAG already does `normalize(v_normal)`, so normals stay correct.
- **B (skinned)**: `uniform mat4 u_bones[16]` via `shader.setUniformMatrix4fv("u_bones", float[16*16], 0, 256)` (pack `Matrix4.val`, which is column-major like vek, so transpose=false). In VERT: `mat4 b = u_bones[int(a_bone + 0.5)]; vec4 wp = u_model * b * vec4(a_position,1); v_normal = normalize(mat3(u_model * b) * a_normal);`. 16 mat4 = 64 vec4 uniforms, far below GL 3.3 limits. Instancing many figures later → UBO or texture buffer per instance.
- This composes cleanly with the shader work from `VELOREN_SHADERS.md`: the lighting changes are fragment-side, and skinning is vertex-side.

### P6: Animation picker UI in the viewer (0.5–1 d)

There's no scene2d yet, so keep it keyboard-plus-text like the current hints, or add a small scene2d `Stage`/`SelectBox` (libGDX 1.14 has it; needs a skin, so +0.5 d).
- Keys: `[`/`]` cycle animation (Idle/Run/Fly/Swim/Feed/Alpha/Shoot/…), `-`/`+` speed (run/fly velocity 0–22), `P` pause, `,`/`.` scrub `anim_time`, `B` toggle the dt_lerp blend so you can see the cross-fade, `Space` replay the attack stage.
- Synthetic dependencies: `velocity = (0, speed, 0)`, `orientation = last_ori = (0,1,0)` (add a turn slider to see the tilt), `avg_vel = velocity`, `acc_vel += speed*dt`, `global_time += dt`, `on_ground` toggle. For attacks, loop a fake `StageSection` Buildup→Action→Recover with `progress = t / duration` (durations ~0.5 s each).
- Show the current animation, `anim_time`, `rate` and bone count in the header text.
- Optional debug: draw bone origins/axes (a `ShapeRenderer` line per bone) to verify the hierarchy.

### P7 (later, game-side)

- `AnimationPicker` per body mirroring the `maintain_entity` match (base locomotion layer + `CharacterState` action layer)
- `avg_vel`/`acc_vel` from actual movement, `state_time` reset on state change
- Mount points (`mount_bone`/`mount_transform` at the bottom of each `<body>/mod.rs`), weapon trails, squash
- character body with `control`/`control_l`/`control_r`/`hold` and weapon-specific wield animations. That replaces the hand-written `Hold` stance in `NpcFigure`.

### Effort summary

| Step | Effort | Unlocks |
|---|---|---|
| P0 bone-local per-part meshes (+ wing bug fixed as a side effect) | 1–1.5 d | anything moving |
| P1 Skeleton/Bone + bird_medium & quadruped_small hierarchies | 1 d | |
| P2 SkeletonAttr port (scripted) for those two bodies | 0.5–1 d | species proportions |
| P3 idle/run/fly (bird_medium) + idle/run (quadruped_small) | 1.5 d | **first animated figures** |
| P4 time + dt_lerp loop | 0.5 d | smooth transitions |
| P5 draw path (per-part; skinned optional) | 0.5–1 d | |
| P6 picker UI | 0.5–1 d | inspect everything |
| **First milestone** | **≈ 5.5–7.5 d** | |
| Each further animal body (attr + idle/run/fly + its attacks) | 1–2 d | |
| character / biped_large / biped_small full sets | 5–10 d each | NPC humanoids |

---

## 4. Porting pitfalls

1. **Z-up vs Y-up.** Veloren is **Z-up, +Y forward, +X right**, and all animation code, `SkeletonAttr` and manifest offsets assume it. Your mesher swizzles `(x,y,z) → (x, z, −y)`, which is a −90° rotation about X. **Do all skeleton math in Veloren's Z-up space and apply that swizzle once as `rootFix = new Matrix4().setToRotation(Vector3.X, -90f)` at the root.** Never convert per-bone; re-signing every `rotation_y` by hand is where bugs come from. For bone-local meshes, emit Z-up vertex positions (drop the swizzle on that path).
2. **Degrees vs radians.** libGDX `Matrix4.rotate`/`setToRotation`/`Quaternion.set(axis, angle)` take **degrees**. Use `setFromAxisRad`/`rotateRad`. Veloren is always radians.
3. **Quaternion layout.** vek `Quaternion{x,y,z,w}` and libGDX `Quaternion(x,y,z,w)` match. Your `NpcFigure.rotX` returns `{x,y,z,w}` too. Both are right-handed, positive angle is counter-clockwise looking down the axis.
4. **Multiplication order.** vek `q1 * q2` and `chest_mat * Mat4::from(head)` apply the right operand first. libGDX `a.mul(b)` is `a = a·b` (same). **Don't use `mulLeft`** when porting. Note that `Quaternion.mul` mutates in place, so allocate or use temps carefully.
5. **Transform → matrix.** vek is `T·S·R`, libGDX `Matrix4.set(pos, rot, scale)` is `T·R·S`. They're identical for uniform scale, which is all Veloren uses. If you ever add a non-uniform scale, match vek explicitly.
6. **Matrix storage.** vek `Mat4` (repr_c column_major) and libGDX `Matrix4.val` are both column-major, so upload with transpose=false.
7. **Units and scale.** Bones are in voxel units. `base_mat` scales by `scaler/8` (birds, biped_large, golem), `scaler/11` (quadrupeds, biped_small, theropod), `1/13` (fish_small), and so on. `chest.position` is often pre-multiplied by `s_a.scaler` while `chest.scale = scaler*0.99` scales all children. The viewer can ignore the `/8`/`/11` world scale (it auto-frames by span), but **must keep the per-bone `scale` fields**. AnimalPose already shows they matter (head offset × 0.7425).
8. **Left/right.** Left = `-s_a.x` in the animation, and mirrored mesh grid in the loader with the offset used as-is. **Don't mirror the offset again** (that's the current wing bug) and don't mirror via a negative-determinant matrix (it flips winding and needs your `det<0` hack).
9. **Bone order is an ABI.** Mesh part *i* must use bone matrix *i*. Keep one `static final int` per bone, in Veloren's declaration order.
10. **Base vs action layering.** Locomotion animations start from `Skeleton::default()`. Action animations take the *base result* as input and only override some bones. Port that faithfully or attacks will lose the walk underneath.
11. **`dt_lerp` is frame-rate dependent** (`min(15·dt, 1)`, not `1 − e^(−15dt)`). Keep it for visual parity, or switch to the exponential form deliberately.
12. **`acc_vel`/`state_time` reset rules.** `acc_vel` resets to 0 when `avg_vel == 0`, and `state_time` resets on character-state variant change. Without these, run cycles pop or start mid-stride.
13. **float math.** Rust `f32` math vs Java `Math.sin(double)` gives tiny differences, which is fine. Watch integer division and `PI/2.5` literals when transcribing (`PI / 2.5` in Rust is f32).
14. **Mesh splitting.** `VoxMesher` splits at 65,532 verts per `Mesh`. With option B, every split must still get the figure's bone uniforms. With option A it's per part and never an issue.
15. **The baked `AnimalPose` dropped the idle wave term** (`+ wave_slow_cos * 0.06`), so `Idle(t=0)` won't match AnimalPose exactly: chest z 4.92 vs 4.875 for parrot. That's expected.

---

## 5. Licensing (GPL-3.0)

Both projects are GPL-3.0, so porting Veloren's animation code and `SkeletonAttr` tables into your repo is allowed as long as the result stays GPL-3.0 (it is) and you **keep attribution**: a header in each ported file, e.g.

```java
/*
 * Ported from Veloren (https://gitlab.com/veloren/veloren), GPL-3.0-or-later.
 * Source: voxygen/anim/src/bird_medium/run.rs @ ebfff94
 * Copyright (C) Veloren contributors. Modified for jvm-dungeon-crawler.
 */
```
plus a short note in `README.md` / a `NOTICE` file listing the Veloren directories ported from. The voxel assets you already load are also Veloren's. Check the license notes in Veloren's repo/asset tree before redistributing them, and credit them the same way.

---

## 6. Veloren file reference

| Topic | File |
|---|---|
| Traits, Bone, 16-bone cap, macro, dylib glue | `voxygen/anim/src/lib.rs` |
| vek re-exports (column-major Mat4) | `voxygen/anim/src/vek.rs` |
| Easing helpers | `voxygen/anim/src/util.rs` |
| bird_medium skeleton, hierarchy, SkeletonAttr, mount | `voxygen/anim/src/bird_medium/mod.rs` |
| bird_medium animations | `voxygen/anim/src/bird_medium/{idle,run,fly,swim,feed,alpha,shoot,breathe,dash,shockwave,summon,stunned}.rs` |
| quadruped_small skeleton and animations | `voxygen/anim/src/quadruped_small/{mod,idle,run,jump,feed,alpha,combomelee,shockwave,stunned}.rs` |
| Other bodies | `voxygen/anim/src/{character,biped_small,biped_large,bird_large,quadruped_medium,quadruped_low,theropod,dragon,fish_*,golem,arthropod,crustacean}/` |
| Animation selection, blending, `FigureState::update`, `acc_vel`, `avg_vel` | `voxygen/src/scene/figure/mod.rs` (bird_medium arm ~L3285–3602; `FigureState` ~L8278–8660; `dt_lerp` L996) |
| Part loading, flipped segments, bone order | `voxygen/src/scene/figure/load.rs` (e.g. `mesh_wing_in_l` ~L1933) |
| Merging bone meshes with bone index | `voxygen/src/scene/figure/cache.rs` (`make_model` ~L505) |
| Vertex gen with offset and bone_idx | `voxygen/src/mesh/segment.rs` (`generate_mesh_base_vol_figure`) |
| Uniform layouts (`Locals`, `BoneData`) | `voxygen/src/render/pipelines/figure.rs` |
| Skinning shader | `assets/voxygen/shaders/figure-vert.glsl` |
| Hot reload | `common/dynlib/src/lib.rs`, `voxygen/Cargo.toml` (`hot-anim`) |
| Transform lerp/slerp, Mat4 from Transform | vek `src/transform.rs`, `src/mat.rs`, `src/quaternion.rs` |
