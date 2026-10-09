# Veloren shaders → jvm-dungeon-crawler: research and gap analysis

*Research only. Nothing in the repo was changed.*

- **Java repo:** `SOS-Games/jvm-dungeon-crawler` @ `6093f3a` ("animals", 2026-09-27)
- **Veloren:** `gitlab.com/veloren/veloren` @ `ebfff949` (2026-10-06 6:56 PM ET), shaders in `assets/voxygen/shaders/`, Rust side in `voxygen/src/render/`, `voxygen/src/mesh/`, `common/src/figure/`
- **Licensing:** both projects are GPL-3.0, so you can port GLSL and algorithms directly. Keep attribution in each ported file, e.g. a header like
  `// Ported from Veloren (https://gitlab.com/veloren/veloren), assets/voxygen/shaders/figure-frag.glsl, GPL-3.0-or-later`,
  and mention shader code in the README's Veloren acknowledgment (it currently credits models, sounds and entity data only). Ported code must stay GPL-3.0.

---

## 1. Veloren's rendering pipeline

### 1.1 Frame structure (`voxygen/src/render/renderer/drawer.rs`)

| Pass | What runs | Shaders |
|---|---|---|
| Shadow pass | Directed (sun) shadow map from terrain and figures. Point-light cube shadows (cube faces chosen by a geometry shader). Rain occlusion map. | `light-shadows-directed-vert`, `light-shadows-figure-vert`, `light-shadows-frag`, `point-light-shadows-vert`, `light-shadows-geom`, `rain-occlusion-*-vert` |
| First pass (opaque, HDR) | Skybox, LoD terrain and objects, terrain chunks, figures, sprites, particles, ropes, debug, fluid. Writes **HDR color** (`Rgba16Float` when available) plus a **material target** (`Rgba8Uint`: packed normal + `MAT_*` id) plus `Depth32Float`. Uses **reversed/infinite depth** (note the `524288` far constants). | `skybox-*`, `lod-terrain-*`, `lod-object-*`, `terrain-*`, `figure-*`, `sprite-*`, `particle-*`, `rope-*`, `fluid-vert` + `fluid-frag/{cheap,shiny}` |
| Volumetric / second pass | Full-screen **clouds + fog + reflections**. Reconstructs world position from depth, ray-marches clouds, and applies distance fog/atmosphere. Uses the alpha channel and material target for water and shiny surfaces (SSR). Trails are drawn here too. | `clouds-vert/frag`, `include/cloud/regular.glsl` (or `flat.glsl`), `trail-*` |
| Bloom | Dual-filter (Kawase-style) downsample chain, then upsample chain that adds the levels back together. | `dual-downsample-filtered-frag` (first level, removes NaN/Inf), `dual-downsample-frag`, `dual-upsample-frag` |
| Third pass | **Postprocess**: anti-aliasing (FXAA by default), bloom mix, **exposure tonemap + gamma**, dither, written to the swapchain (an sRGB surface). Then the UI. | `postprocess-vert/frag`, `antialias/*.glsl`, `include/fxaa.glsl`, `ui-*`, `premultiply-alpha-*`, `blit-*` |

Default settings (`voxygen/src/render/mod.rs`, `settings/graphics.rs`): `LightingMode::BlinnPhong`, `ShadowMode::Map`, `CloudMode::High`, `FluidMode::Medium`, `AaMode::Fxaa`, bloom on with factor `Medium = 0.2`, gamma 1.0, exposure 1.0, ambiance 0.5.

Shaders are Vulkan-flavoured GLSL `#version 440 core`. They use `layout(set=…, binding=…)`, separate `texture2D` + `sampler` objects, `#include <x.glsl>` (resolved by `renderer/shaders.rs`), and quality `#define`s injected through `include/constants.glsl` (`FLUID_MODE`, `CLOUD_MODE`, `LIGHTING_ALGORITHM`, `SHADOW_MODE`, `EXPERIMENTAL_*`). They are compiled to SPIR-V (shaderc) for wgpu. **There are no compute shaders.**

**World convention: Z is up** (`f_pos.z` is altitude, `sun_dir.z < 0` means day). All positions are relative to `focus_off`, which keeps float precision near the camera.

### 1.2 Shared uniforms and includes

- **`include/globals.glsl`** is the `u_globals` UBO (set 0, binding 0): `view_mat`, `proj_mat`, `all_mat`, `cam_pos`, `focus_off`, `focus_pos`, `view_distance`, `time_of_day`, `sun_dir`, `moon_dir`, `tick`, `screen_res`, `light_shadow_count` (point lights, point shadows, directed shadows), `shadow_proj_factors`, `medium` (air or water), `select_pos`, `gamma_exposure`, `last_lightning`, `wind_vel`, `ambiance`, `cam_mode`, and so on. It also has the `tick_loop()` helpers.
- **`include/constants.glsl`** holds the mode enums, `MAT_*` ids (sky, block, water, figure, LoD, puddle) and `MU_*`.
- **`include/srgb.glsl`** contains:
  - `srgb_to_linear` and `linear_to_srgb`
  - the BRDF pieces: Schlick Fresnel, Beckmann/Trowbridge distributions, `FresnelBlend_f` (Ashikhmin-Shirley), and **`light_reflection_factor()`**, which picks Lambert, Blinn-Phong or Ashikhmin based on `LIGHTING_ALGORITHM`
  - `rel_luminance`
  - water attenuation (`compute_attenuation*`, Beer–Lambert)
  - **`greedy_extract_col_light_attr()`**, which decodes the col/light atlas (see 1.3)
- **`include/sky.glsl`** contains:
  - sky palette constants for dawn, day, dusk and night (`DAY_LIGHT = (3.8,3.0,1.8)`, `MU_SCATTER = (0.05,0.10,0.23)`, …)
  - `get_sun_color()`, `get_sun_brightness() = max(-sun_dir.z+0.5,0)`, `get_moon_*`, and `DirectionalLight` (shadow, block)
  - **`get_sun_diffuse2()`**, the main sun and moon lighting model (see 1.4)
  - `get_sky_color(dir, …)`, which gives the analytic sky gradient, sun and moon discs/halos and stars (`is_star_at`)
  - `fog()`, `illuminate()` (just `emitted + reflected`), `glow_light()`, `cloud_shadow()` (samples the weather texture), lightning, and `wind_wave`
- **`include/light.glsl`** holds the `u_lights` UBO (20 point lights: pos, col, dir+fov for spotlights) and the `u_shadows` UBO (24 "cheap" point shadows, which are figure-shaped dark blobs). It provides `lights_at()` (point/spot lights with the falloff `3/(5+d²)` plus non-physical ambient terms), `shadow_at()`, and **`apply_cell_material()`** (Glowy: `emitted += 20*col`. Shiny: low alpha so clouds-frag does SSR. Fire: animated noise. Water. SwirlyCrystal).
- **`include/shadows.glsl`** does `ShadowCalculationDirected()` with a `sampler2DShadow` + `textureProj` on `texture_mat * pos`, and `ShadowCalculationPoint()` with a `samplerCubeShadow` (only light 0 casts cube shadows).
- **`include/lod.glsl`** samples the world heightmap (`t_alt`), horizon map (`t_horizon`, used for cheap sun occlusion by mountains: `horizon_at2`), and map color (`t_map`). It provides `alt_at()`, bicubic sampling, `lod_pos`, `lod_norm`, `lod_col`, and `lod_voxels` (fake voxel facets on distant terrain).
- **`include/random.glsl`** has hashes, `noise_2d/3d`, simplex noise, `dither()`, and caustics.
- **`include/point_glow.glsl`** adds volumetric "bloom halos" around point lights, applied in clouds-frag.
- **`include/cloud/regular.glsl`** has `cloud_at()` and **`get_cloud_color()`**: a ray-marched volumetric cloud layer with sun scattering, rain, rainbows and fog. `flat.glsl` is the cheap version.

### 1.3 Vertex format and the col/light atlas (figures and terrain)

Veloren doesn't put colors in vertices. Meshes are **greedy-meshed** (`voxygen/src/mesh/greedy.rs`), and each merged quad gets a rectangle in a per-figure **RGBA8 col/light atlas texture** (`t_col_light`, `Rgba8Unorm`), one texel per voxel face.

**Vertex** (`render/pipelines/terrain.rs::Vertex::new_figure`, two `u32`s, 8 bytes):
- `v_pos_norm`: bits 0–8, 9–17 and 18–26 hold x, y, z as `(pos*2 + 256)` (half-voxel precision, range ±128). Bits 27–30 are the **bone index** (≤16 bones). Bit 31 is the normal sign.
- `v_atlas_pos`: bits 0–1 are the normal axis (x, y or z). Bits 2–16 are atlas u and bits 17–31 are atlas v (15 bits each).

**Atlas texel for figures** (`make_col_light_figure`):
- `R` = `light<<3 | (col.r>>1 & 7)`
- `G` = `surface<<3 | (col.b>>1 & 7)`
- `B` = `col.r & 0xF0 | col.b>>4`
- `A` = `col.g`

`light` (0–31) is the **baked per-corner ambient occlusion**. `surface` is the `CellSurface` (Matte, Glowy, Shiny, Fire, Water, SwirlyCrystal). Terrain texels also pack `glow` (light from emissive blocks) and an AO bit, and terrain's `light` is real sunlight propagation.

**AO bake** (`greedy.rs` ~L758–842 with `segment.rs::generate_mesh_base_vol_figure`): for each face texel, look at the 4 cells in the layer the face points into, around the texel corner: `light_pos`, `light_pos-u`, `light_pos-v`, and the diagonal `light_pos-u-v`. The diagonal only counts if either side cell is open. For figures, `get_light(cell) = 1 if empty else 0`, and `light = round(avg*31.5)`. The fragment shader **bilinearly interpolates** the 4 neighbouring texels' light values (`greedy_extract_col_light_attr`, using `fract(uv)`) and reads the color unfiltered via `texelFetch`. This gives smooth corner AO across each face.

**Palette → cell** (`common/src/figure/mod.rs::from_vox`, `cell.rs::from_index`). Note that `dot_vox` stores `i = raw_index - 1`.

| dot_vox `i` | Raw .vox index | Meaning |
|---|---|---|
| 0, 1, 2, 3, 4, 5, 7 | 1–6, 8 | **Material slots**: Skin, Hair, EyeDark, EyeLight, SkinDark, SkinLight, EyeWhite. Recoloured per species/body at runtime (humanoid characters). |
| 8–12 | 9–13 | Shiny |
| 13–15 | 14–16 | **Glowy** |
| 16 | 17 | Hollow (empty, but carves into other parts) |
| 17–21 | 18–22 | Matte, "override hollow" |
| other | other | Matte |

Colors are **sRGB in the palette** and get converted with `srgb_to_linear` in the shader.

### 1.4 Shader by shader

**`figure-vert.glsl`** (creatures, characters, items, airships)
- Inputs: packed `v_pos_norm`/`v_atlas_pos`, plus UBOs `u_locals` (`model_mat`, `highlight_col`, `model_light` = sunlight at the entity's voxel, `model_glow` = direction and strength of nearby emissive blocks, `atlas_offs`, `model_pos`, `flags`) and `u_bones` (16 × {`bone_mat`, `normals_mat`}).
- Unpacks the position, selects the bone (GPU skinning, one bone per vertex), picks the normal from `normals_mat` columns by axis and sign, outputs a flat normal, then `gl_Position = all_mat * f_pos`.

**`figure-frag.glsl`**
1. Decode color, AO and material from the atlas. Color is converted to linear.
2. Sun shadow: horizon map plus directed shadow map. Then `sun_info.block *= model_light.x`, so entities under a roof get no sun or ambient.
3. Optional color noise for big figures (`scale >= 0.5`).
4. `k_d = 1`, `k_s = R_s ≈ 0.04` (dielectric Fresnel, n = 1.5), `alpha = 1`.
5. **`get_sun_diffuse2`** (sun + moon) **plus `lights_at`** (point lights) go into `emitted_light` (ambient) and `reflected_light` (direct).
6. Add baked glow from `model_glow`.
7. **AO**: `ao = f_ao^1.5`, which multiplies both emitted and reflected light. Point "blob" shadows are applied too.
8. `apply_cell_material` (Glowy adds `20*col` to emitted, and so on).
9. `color = surf*emitted + surf*reflected`, multiplied by `highlight_col`. The output is **linear HDR**. The material target gets the normal and `MAT_FIGURE`.

**`get_sun_diffuse2` with the defaults (Blinn-Phong)**, ignoring water and clouds:
```
sun_light   = max(-sun_dir.z + 0.5, 0)               // brightness by sun height
sun_chroma  = get_sun_color()*5 * sun_light           // DAY_LIGHT ≈ (3.8,3.0,1.8)*5
R_b         = sqrt(1 - n.y²);  R_d = (1+R_b)/2;  R_r = zeta*(1-R_b)/2,  zeta≈0.96
lrf         = (n.z + 1)² / 4                          // hemispherical: top bright, bottom dark
emitted     = (R_b+R_r) * (sun_chroma*MU_SCATTER + moon_chroma*MOON_AMBIANCE) * lrf   // blue-tinted sky ambient
reflected   = (R_d+R_r) * (1-MU_SCATTER) * sun_chroma * shadow *
              ((1-k_s)/π * k_d * max(dot(n,-sun_dir),0) + k_s * pow(max(dot(n,H),0), 1.414))
```

**`terrain-vert/frag.glsl`**
- Same packed vertex idea, but chunk-local with no bones. Also uses a `t_kind` texture (block kind, for snow/ice and similar).
- The frag decodes sunlight (`f_light`, propagated through the voxel world), `f_glow` (block light), AO and sky exposure. It applies the sun, horizon and shadow maps, point lights, glow, rain wetness and puddles, plus snow and ice tinting.

**`sprite-vert/frag.glsl`**: instanced small voxel models (grass, flowers, furniture). Per-instance data is a matrix, glow, light, `wind_sway` and `z_scale`. The vertex shader adds wind animation via `wind_wave`.

**`fluid-vert` + `fluid-frag/cheap|shiny`**: water surface with animated normals, Fresnel, sky reflection, Beer–Lambert underwater attenuation (`MU_WATER`) and caustics.

**`skybox-vert/frag`**: a large box. The frag calls `get_sky_color()`, which gives the sky gradient by time of day, sun and moon, and stars.

**`lod-terrain-*`, `lod-object-*`**: a distant-terrain heightmap mesh plus instanced low-poly trees and houses, colored from the map texture.

**`clouds-frag`**: post-scene full-screen pass. It reconstructs world position from depth, then runs `get_cloud_color(color, dir, cam_pos, dist)`, which ray-marches clouds and applies fog. It also does screen-space reflections for low-alpha (shiny/water) pixels and point-light glow halos.

**Shadows**: a single directed shadow map (orthographic, fitted to the view frustum). The figure version skins with bones. Point light 0 gets a cube depth map, rendered with a geometry shader that emits to 6 layers.

**Bloom**: dual downsample/upsample on the HDR target. The postprocess mixes it in: `mix(color, bloom/4, 0.2)`.

**`postprocess-frag`**: AA, then bloom, then **tonemap** `c = 1 - exp(-c * (exposure + 1.0))`, then **gamma** `c = pow(c, gamma + 0.3)` (that's `pow(c, 1.3)` at defaults, which adds contrast and saturation). After that comes a ±0.5/255 dither done in sRGB space, and finally a write to the **sRGB swapchain**, where the hardware does linear→sRGB.

### 1.5 The asset-viewer equivalent in Veloren

`voxygen/src/scene/simple.rs`, the character-select scene, is the closest thing to your viewer. It fills `Globals` with the camera, `time_of_day`, gamma, exposure and ambiance. It adds at most one point light (the lantern at night), draws skybox, LoD and figures in the first pass, then runs clouds and postprocess. `sun_dir = (-sin a, 0, cos a)` with `a = time_of_day / 86400 * 2π` (`common/src/resources.rs::TimeOfDay::get_sun_dir`). Figure locals are `highlight_col = 1`, `model_light = 1`, `model_glow = 0`.

---

## 2. What the Java repo has today

**GL context** (`lwjgl3/.../Lwjgl3Launcher.java`): libGDX **1.14.0**, `GLEmulation.GL30` with a **desktop OpenGL 3.3 core** context, backbuffer RGBA8 + depth 24, no MSAA. Shaders are `#version 330`. (The project also pulls in `gdx-bullet`, which has nothing to do with rendering.)

**Shaders**: there are **no shader files**. There's one program inlined in `core/.../render/ForwardRenderer.java`:
- **Vertex shader**: inputs `a_position`, `a_normal`, `a_color`; uniforms `u_mvp`, `u_model`; outputs a world normal and color.
- **Fragment shader**: one hard-coded directional light `normalize(0.35,0.85,0.45)`, `shade = 0.32 + 0.68*N·L` (flat grey ambient plus Lambert), and glow given as `color*shade + color` when `a_color.a == 1`. It writes straight to the non-sRGB default framebuffer.
- **Missing**: linearization, tonemap, AO, specular, sky, fog and shadows.
- The background is a flat grey clear (`0.62,0.64,0.68`, tiles `0.40,0.42,0.46`).

**Mesher** (`core/.../converter/VoxMesher.java`):
- Naive per-voxel-face quads (no greedy merge). Faces hidden by occupied neighbours are culled, and "occupied" excludes raw index 0 and 16.
- **Vertex = 7 floats (28 bytes)**: `Position(3)`, `Normal(3)` and `ColorPacked(1)`, where RGB comes from the palette and **alpha = 1 marks glow**.
- 16-bit indices, with the mesh split at 65,532 vertices.
- Vox Z-up is converted to Y-up in `corner()`. Mirrored instances (det < 0) swap winding and flip the normal.
- **No AO, no light value and no material id** beyond the glow bit.

**Asset viewer path**:
1. `JvmDcApp` scans with `VoxCatalog.scan()`. Browser mode draws a grid of tiles (scissored viewports), and View mode draws one model that you rotate by dragging.
2. `VoxCatalog.load()` calls `VoxReader.readScene()` and then `VoxMesher.build()` for plain .vox files. NPC/creature entries go through `NpcFigure.build(Assembly)`.
3. `NpcFigure.build` reads each body-part .vox, applies `FigurePose` / `AnimalPose` bone offsets and rotations (and mirroring), and **bakes all parts into one static `VoxScene`, so the figure becomes a single mesh**. There's no skeleton on the GPU, so no animation.
4. `ForwardRenderer.draw(camera, parts, model)` uses one uniform `u_model`. The camera is a libGDX `PerspectiveCamera` (fov 50, framed by `span`).

**Palette semantics differ from Veloren.** `VoxReader` stores the raw .vox index, and `VoxMesher` treats **raw 13–15 as glowing and raw 16 as hollow**. Veloren, after dot_vox's `-1`, uses **raw 14–16 = Glowy, raw 17 = Hollow, raw 9–13 = Shiny, raw 1–8 = recolourable skin/hair/eye materials**. So the Java viewer likely makes raw 13 glow when it should be shiny, makes raw 16 hollow when it should glow, and doesn't treat raw 17 as hollow. Check this on a model with known glowing parts before changing it. This is a correctness bug in color handling, separate from the shader work.

---

## 3. Gap analysis (prioritized)

Effort: **S** ≈ ½–1 day, **M** ≈ 2–4 days, **L** ≈ 1–2 weeks, **XL** = more. Assumes the current GL 3.3 core context.

### libGDX / GLSL porting notes (apply throughout)

- **GL version.** You're on desktop GL **3.3 core**, so `#version 330` gives you: `uint` and bit operations, `texelFetch`, `usampler2D`, `sampler2DShadow` / `samplerCubeShadow`, **uniform blocks (UBOs)**, MRT via `layout(location=N) out`, float render targets, `gl_VertexID`, and geometry shaders. You don't get compute shaders, but **Veloren doesn't use any**. If you ever target GLES 3.0 (Android/WebGL2), UBOs and integer ops still work but geometry shaders don't.
- **Converting Veloren's GLSL 440 to 330:**
  - Drop `set=`/`binding=` and bind in Java with `glUniformBlockBinding` / `glUniform1i` for samplers.
  - Replace separate `texture2D t` + `sampler s` + `sampler2D(t, s)` with combined `uniform sampler2D t`.
  - Drop `layout(location=…)` on varyings (330 only allows it on vertex inputs and fragment outputs) and match varyings by name. Keep `flat` where it's used.
  - `#extension GL_EXT_samplerless_texture_functions` isn't needed.
  - Use `const` arrays and `vec3[](…)` as usual. 330 allows them.
- **`#include` / `#define` injection.** GLSL has neither, so write a ~50-line Java preprocessor that inlines `#include <x.glsl>` from `assets/shaders/include/` with include guards, and prepends a block of `#define FLUID_MODE …`, `LIGHTING_ALGORITHM …` and so on, mirroring `renderer/shaders.rs` and `constants.glsl`. libGDX `ShaderProgram.prependVertexCode` / `prependFragmentCode` can carry the defines. Set `ShaderProgram.pedantic = false`, because unused uniforms get optimized out.
- **UBOs in libGDX.** There's no wrapper, so use `Gdx.gl30` directly: `glGenBuffer`, `glBindBuffer(GL_UNIFORM_BUFFER)`, `glBufferData`, `glGetUniformBlockIndex`, `glUniformBlockBinding`, `glBindBufferBase`. Lay out the data by std140 rules (vec3 pads to 16 bytes, arrays of structs align to 16). The alternative is plain uniforms, which is fine for the viewer (`setUniformf("sun_dir", …)`), so UBOs can wait until the world renderer exists.
- **Packed `uint` vertex attributes.** libGDX `Mesh` / `VertexAttribute` always uses `glVertexAttribPointer` (float path), so a `uint` input won't work through `Mesh`. Either keep float attributes (simplest; just add `a_ao` and `a_mat` floats) or write a small custom VAO with `Gdx.gl30.glVertexAttribIPointer`. Packing only matters for big world meshes, not the viewer.
- **Coordinate system.** Veloren math is Z-up (`n.z`, `sun_dir.z`, `f_pos.z` altitude, `vec3(0,1,0)` = horizontal "north" in `get_sun_diffuse2`). You convert to Y-up in `VoxMesher.corner()`. Either swizzle in the shader (`vec3 n_z = vec3(n.x, -n.z, n.y)`, the inverse of `corner()`) or keep a Z-up world space and put the axis swap in the view matrix. Pick one convention and apply it to every ported function.
- **Depth.** Veloren uses reversed-Z. libGDX's `PerspectiveCamera` uses standard Z. That's fine for the viewer, but clouds-frag and postprocess reconstruct positions from depth, so adapt `wpos_at()` when porting them.
- **sRGB output.** Lwjgl3 doesn't reliably give an sRGB-capable default framebuffer. The robust option is to render to an HDR `FrameBuffer` (`GLFrameBuffer.FrameBufferBuilder` + `addColorTextureAttachment(GL30.GL_RGBA16F, GL30.GL_RGBA, GL30.GL_FLOAT)` + depth) and do tonemap, gamma and `linear_to_srgb` yourself in a full-screen post shader. Alternatively, `glEnable(GL_FRAMEBUFFER_SRGB)` on an sRGB backbuffer. **SpriteBatch/BitmapFont UI must be drawn after the post pass, outside the HDR buffer.**
- **Scissored tiles.** The browser draws many small viewports. A per-tile HDR FBO is wasteful. Render the 3D tiles into one full-window HDR FBO (keep the scissor), run post once, then draw the UI.

### Tier A — make figures in the asset viewer look like Veloren

| # | Item | Veloren reference | Port notes | Effort |
|---|---|---|---|---|
| **A1** | **Linear color pipeline + Veloren tonemap.** Convert palette sRGB→linear (on the CPU when building the mesh, or with `srgb_to_linear` in the shader), light in linear HDR, then `1-exp(-c*(exposure+1))`, `pow(c, gamma+0.3)`, then `linear_to_srgb`. | `include/srgb.glsl` (`srgb_to_linear`, `linear_to_srgb`), `postprocess-frag.glsl` L210–220, `greedy_extract_col_light_attr` (`return srgb_to_linear(f_col)`) | Simplest first step: do it at the end of the figure fragment shader, writing straight to the backbuffer. Then move tonemapping into a post pass when A6 lands. Without this, everything else will look washed out or too bright. | S |
| **A2** | **Per-vertex AO baked in `VoxMesher`** (Veloren's corner-occlusion "light"). | `voxygen/src/mesh/greedy.rs` ~L758–842, `mesh/segment.rs::generate_mesh_base_vol_figure` (`get_light` = cell empty), `figure-frag.glsl` (`ao = f_ao * sqrt(f_ao)`) | Each Java quad is one voxel face, so per-vertex AO matches Veloren's bilinear-filtered texels. For each corner, sample the 4 cells in the layer the face points into, and count the diagonal only if one of the two side cells is open. `ao = open/4`. Add a float attribute `a_ao`, which grows the vertex from 7 to 8 floats. **Flip the quad's triangle diagonal** when `ao0+ao2 < ao1+ao3` to avoid anisotropy artifacts (Veloren avoids this by sampling a texture instead). Compute per part, in each model's local grid, before the instance transform, so it's per-bone like Veloren. Treat raw-17 hollow cells as empty. | S–M |
| **A3** | **Port the figure lighting model**: `get_sun_diffuse2` (hemispherical blue sky ambient `MU_SCATTER`, `(n.z+1)²/4` top-light term, Blinn-Phong sun with `k_s≈0.04`), time-of-day sun color and brightness, `illuminate = emitted + reflected`. | `figure-frag.glsl`, `include/sky.glsl` (constants, `get_sun_color`, `get_sun_brightness`, `get_sun_diffuse2`), `include/srgb.glsl` (`light_reflection_factor`, Blinn-Phong branch), `common/src/resources.rs` (`get_sun_dir`) | For the viewer, strip water attenuation (`mu = 0`, `cam_attenuation = 1`), cloud shadow (`= 1`), horizon and shadow maps (`shade = 1`), point lights, and lightning. That leaves about 60 lines of GLSL. Uniforms: `u_sun_dir`, `u_time_of_day` (or precomputed `u_sun_color`), `u_cam_pos`, `u_ambiance`, `u_gamma`, `u_exposure`. A fixed "Veloren noon" works (time ≈ 12:00, `sun_dir ≈ (0,0,-1)`), but a time-of-day slider is cheap. Mind Z-up vs Y-up. | M |
| **A4** | **Palette/material semantics** matching Veloren: fix the glow and hollow index ranges, add Shiny, keep a 5-bit `CellSurface` per vertex (`a_mat`), and apply `apply_cell_material` (Glowy: `emitted += 20*col`). Optionally recolour humanoid material slots (raw 1–8) with species skin/hair/eye colors. | `common/src/figure/cell.rs::from_index`, `common/src/figure/mod.rs::from_vox` (`dot_vox` `i = raw-1`), `include/light.glsl::apply_cell_material`, `common/src/figure/mat_cell.rs`, plus species color tables (`common/src/comp/body/humanoid.rs`) | Glow becomes HDR (>1), so it only looks right with A1 tonemapping and A6 bloom. Shiny needs SSR (clouds-frag) to show, so treat it as Matte at first. Recolouring is data work in Java, not shader work. | S (indices + glow), M (humanoid recolor) |
| **A5** | **Sky background** instead of a flat grey clear: a full-screen quad or skybox calling `get_sky_color(dir, …)` with stars off, or at least the day gradient. | `skybox-vert/frag.glsl`, `include/sky.glsl::get_sky_color` / `get_sky_light` | Needs inverse view-projection to get the ray direction per pixel. You can drop cloud features (`CLOUD_MODE_FLAT` path). Makes the viewer look like character select. | S–M |
| **A6** | **HDR render target + postprocess pass + bloom** (FXAA optional). | `postprocess-frag.glsl`, `dual-downsample-filtered-frag.glsl`, `dual-downsample-frag.glsl`, `dual-upsample-frag.glsl`, `drawer.rs` bloom section (~L437–531), `antialias/fxaa.glsl` + `include/fxaa.glsl` | `FrameBuffer` with `GL_RGBA16F`. Bloom is about 5 mip levels of ping-pong FBOs, about 30 lines of GLSL each. Mix at 0.2 and divide by 4 as Veloren does. Glowy voxels (eyes, crystals, flames) get Veloren's halo. Draw the UI after the post pass. | M |
| **A7** | **GPU skinning / bone uniforms** (instead of baking poses into one mesh): per-part meshes with bone index, `u_bones[16]` (`bone_mat`, `normals_mat`). | `figure-vert.glsl`, `render/pipelines/figure.rs` (`BoneData`, `Locals`), `voxygen/src/scene/figure/` | Only needed for animation. It also simplifies mirroring bugs like the wing issue, because offsets live in bone matrices instead of baked mesh math. With plain uniforms, `uniform mat4 u_bones[16]` is fine under 330. Not needed for the static look. | M–L |
| **A8** | Optional fidelity extras: the large-figure color noise (`scale >= 0.5` branch), `highlight_col` (hover tint), a point-light "lantern" at night, and `model_light` dimming. | `figure-frag.glsl`, `include/light.glsl::lights_at`, `scene/simple.rs` | Plain uniforms are fine (an array of ≤20 lights). | S each |

Suggested viewer shader layout after A1–A4:
```
vertex:   a_position(3) a_normal(3) a_color(packed; linear RGB, or sRGB converted in shader) a_ao(1) a_mat(1)
vert:     v_wpos, flat v_norm, v_col, v_ao, flat v_mat
frag:     col = srgb_to_linear(v_col)  (if not done on the CPU)
          n_z = toZUp(v_norm)
          get_sun_diffuse2(sun, moon, n_z, view_dir_z, …) -> emitted, reflected
          ao = v_ao * sqrt(v_ao); emitted *= ao; reflected *= ao
          if (v_mat == GLOWY) emitted += 20.0 * col
          hdr = col*emitted + col*reflected
          // until the post pass exists:
          c = 1 - exp(-hdr*(u_exposure+1)); c = pow(c, vec3(u_gamma+0.3)); frag = vec4(linear_to_srgb(c),1)
```

### Tier B — world level (once there is a world)

| # | Item | Veloren reference | Port notes | Effort |
|---|---|---|---|---|
| B1 | Shader preprocessor (`#include`, mode `#define`s) + shared `globals` UBO | `renderer/shaders.rs`, `include/constants.glsl`, `include/globals.glsl` | Prerequisite for porting more than a couple of shaders verbatim. std140 UBO via `Gdx.gl30`. | S–M |
| B2 | Directed sun **shadow map** | `light-shadows-directed-vert.glsl`, `light-shadows-figure-vert.glsl`, `light-shadows-frag.glsl`, `include/shadows.glsl`, `renderer/shadow_map.rs`, `scene/mod.rs` (frustum fit) | Depth-texture FBO with `GL_TEXTURE_COMPARE_MODE`, so a `sampler2DShadow` works in 330. libGDX `FrameBuffer` needs a depth texture attachment (`FrameBufferBuilder.addDepthTextureAttachment`). Shadow-matrix fitting is the hard part. | M–L |
| B3 | **Terrain mesher + terrain shader**: greedy mesh + col/light atlas, sunlight propagation (`f_light`), block glow, AO, sky exposure | `voxygen/src/mesh/terrain.rs`, `mesh/greedy.rs`, `terrain-vert/frag.glsl`, `render/pipelines/terrain.rs` | The atlas approach matters at world scale for vertex count. Packed `uint` vertices need a custom VAO with `glVertexAttribIPointer`. Light propagation is CPU work. | L–XL |
| B4 | Point lights + cheap point shadows (figure blob shadows), lanterns, spotlights | `include/light.glsl` (`lights_at`, `shadow_at`), `include/point_glow.glsl` | UBO arrays of 20 lights and 24 shadows. Cube point shadows use a geometry shader (`light-shadows-geom.glsl`). Under GL 3.3 that's allowed, or you can render 6 faces in separate passes. | M (lights), L (cube shadows) |
| B5 | **Fog + volumetric clouds** full-screen pass | `clouds-vert/frag.glsl`, `include/cloud/regular.glsl` (or `flat.glsl` first), `include/sky.glsl::fog`, `get_cloud_color` | Needs depth texture + inverse matrices, adapted to non-reversed Z. Expensive ray-march, so start with `CLOUD_MODE_FLAT` / `MINIMAL`. Needs a weather texture (can be constant). | M (fog/flat), L (volumetric) |
| B6 | Water | `fluid-vert.glsl`, `fluid-frag/cheap.glsl` (then `shiny.glsl`) | Cheap is fine first. Shiny needs SSR in clouds-frag and the material target (MRT). | M–L |
| B7 | Sprites (grass, flowers) with instancing and wind | `sprite-vert/frag.glsl`, `render/pipelines/sprite.rs` | `glVertexAttribDivisor`, or libGDX `Mesh.enableInstancedRendering` (in libGDX 1.14). | M |
| B8 | LoD terrain/objects + horizon-map sun occlusion | `lod-terrain-*`, `lod-object-*`, `include/lod.glsl`, `voxygen/src/scene/lod.rs` | Needs world heightmap and horizon textures generated in Java. | L |
| B9 | Particles, trails, ropes, rain occlusion | `particle-vert.glsl` (1,377 lines), `trail-*`, `rope-*`, `rain-occlusion-*` | Late polish. | M–L each |

### Order I'd suggest
1. **A1 + A2 + A3**, the core "Veloren look" for figures: about 1 week total. This alone gets you most of the way: soft corner AO, blue sky-fill ambient with brighter tops, warm sun, filmic exponential tonemap with the 1.3 gamma contrast, and correct sRGB handling.
2. **A4** palette fix (verify first, then do it) and glow, then **A6** HDR post + bloom so glowing voxels bloom like in-game.
3. **A5** sky background (character-select feel).
4. **A7** only when you want animation in the viewer.
5. Tier B when the game world exists: B1, then B3 → B2 → B5 (flat fog) → B4 → B6/B7 → B8.

---

## 4. File checklist to copy or port (with attribution)

**For figures (Tier A):**
- `assets/voxygen/shaders/figure-vert.glsl`
- `assets/voxygen/shaders/figure-frag.glsl`
- `include/srgb.glsl`
- `include/sky.glsl` (constants + sun/moon/`get_sun_diffuse2` + `get_sky_color`)
- `include/light.glsl` (`apply_cell_material`, `lights_at`)
- `include/globals.glsl`, `include/constants.glsl`, `include/random.glsl`
- `postprocess-frag.glsl`, `dual-*-frag.glsl`, `skybox-*.glsl`

Rust algorithm sources:
- `voxygen/src/mesh/greedy.rs` (AO)
- `voxygen/src/mesh/segment.rs`
- `voxygen/src/render/pipelines/terrain.rs` (`new_figure`, `make_col_light_figure`)
- `voxygen/src/render/pipelines/figure.rs`
- `common/src/figure/cell.rs` + `mod.rs` (palette semantics)
- `common/src/resources.rs` (sun dir)
- `voxygen/src/scene/simple.rs` (viewer-like scene setup)

**For the world (Tier B):** `terrain-*`, `sprite-*`, `fluid-*`, `clouds-*`, `include/cloud/*`, `include/shadows.glsl`, `light-shadows-*`, `include/lod.glsl`, `lod-*`, `include/point_glow.glsl`, `voxygen/src/render/renderer/{drawer,shaders,shadow_map}.rs`.
