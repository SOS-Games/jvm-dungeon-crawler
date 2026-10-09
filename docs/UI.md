# Reusing Veloren's UI systems and UI graphics in jvm-dungeon-crawler

*Research only. Nothing in any repo was changed. Sources: `SOS-Games/jvm-dungeon-crawler` @ `6093f3a` (libGDX 1.14.0, Java 21, GPL-3.0). `veloren/veloren` master @ `ebfff94` (2026-10-06 6:56 PM ET), sparse-cloned to `/workspace/veloren_ui`, with all 893 `assets/voxygen/element/**/*.png` pulled from Git LFS and measured. Barony facts come from `/workspace/barony` (`src/ui/*`, `player.hpp`). Library versions were checked on Maven Central and GitHub on 2026-10-09.*

---

## 0. TL;DR

* **UI code: reuse the design, not the code.** Veloren's UI runs on three Rust libraries: a fork of **conrod_core** (immediate-mode widgets, used for the whole in-game HUD), a fork of **iced_native** (retained-mode, used for the main menu, login, server list and character select), and **egui** (debug only). Rendering goes through wgpu, with glyph_brush for text and a guillotiere atlas. None of that runs on the JVM. What does carry over: **layout numbers, colours, slot and drag-drop rules, tooltip content rules, the item-icon render recipe, and the i18n text**. Rebuild the widgets with **scene2d.ui** (Skin, TextureAtlas, NinePatch, TintedDrawable, DragAndDrop, Tooltip). Use **imgui-java** only for debug tools.
* **UI graphics: a strong yes.** There are **893 PNGs** (about 12.6 Mpx in total, 786 of them ≤ 64 px). They cover panel frames and backgrounds, buttons with hover and press states, item slots, **paperdoll slot silhouettes** (head, chest, hands, legs, feet, ring, neck, back, main and off hand, and more), HP, energy and XP bar frames, minimap and map frames, 43 buff and debuff icons, about 300 ability icons, crosshairs, chat icons and loading animations. The frames are **greyscale, tinted at runtime** with `UI_MAIN = (0.61, 0.70, 0.70)`, which maps straight onto libGDX `TintedDrawable`. Almost all frames are clean rectangles with corner ornaments, so they **9-slice well**. One catch: Veloren itself never 9-slices. It draws each panel at its authored size, so you'll have to write the split values yourself.
* **Item icons are not PNGs.** All 1,395 entries in `assets/voxygen/item_image_manifest.ron` are `VoxTrans(...)`. At runtime, Veloren rasterises the item's `.vox` model **on the CPU** (with the `euc` software rasteriser) at the exact on-screen slot size, using an orthographic camera, one light and per-vertex AO. That's easy to copy in libGDX with a `FrameBuffer`, your existing `VoxReader`/`VoxMesher`, and the manifest converted by your existing RON→JSON `Converter` (see §3).
* **Licensing is fine with a few cautions.** `credits.ron` has `other_art: []`, so **every UI PNG is GPL-3.0**, the same as your repo. Exclude the CC-BY-NC-SA models you already know about. Of those, **only the glider `vroom.vox` could ever turn up as an icon**, and it isn't in the item manifest (ingot icons use `voxel.item.mineral.ingot.*`, not the NC `sprite/mineral/ingot`). Fonts: Veloren's **main HUD font "cyri" (HaxrCorp 4089, 1,020 uses) is CC BY-SA 3.0**, and the title font "alkhemi" (Alkhemikal) is CC BY 4.0. Ship both as separate font files with their licence texts, or pick an OFL pixel font instead (§4).
* **Effort:** a playable Barony-style HUD (bars, hotbar, inventory with paperdoll, drag-drop, tooltips, message log, rendered item icons) is about **11–16 days**. The full set, adding automap, chest, shop, character sheet, spell list, menus, settings and i18n, is about **22–33 days**. Plan in §6.

---

## 1. Veloren's UI tech, and what's portable

### 1.1 Stack (`voxygen/Cargo.toml`)

| Layer | Veloren | Used for | Portable to Java? |
|---|---|---|---|
| Widgets (immediate mode) | `conrod_core` fork (`gitlab.com/veloren/conrod`, branch `copypasta_0.7`) | The whole HUD (`voxygen/src/hud`, about 35k lines) plus custom widgets in `ui/widgets` (slot, item_tooltip, image_frame, image_slider, radio_list, rich_text, outlined_text, ghost_image, ingame) | No. Copy the layouts instead. |
| Widgets (retained mode, Elm-style) | `iced_native` fork (`Imberflur/iced`, tag `veloren-winit-0.28`) with a custom renderer in `ui/ice/renderer` | Main menu, login, servers, world selector, credits, char select (`voxygen/src/menu`) | No |
| Debug UI | `egui 0.33` (`egui-ui` feature, left out of release builds) | Dev panels | Use imgui-java instead |
| Text | `glyph_brush 0.7` + TTF | All text | Replace with `gdx-freetype` |
| Graphic cache | `ui/graphic/mod.rs`: guillotiere `SimpleAtlasAllocator`, atlas sized to the window resolution, images over 20% of the atlas get their own texture, premultiplied alpha | Packs PNGs and rasterised voxel graphics on demand | Replace with `TextureAtlas` (offline) and `FrameBuffer`/`PixmapPacker` (runtime) |
| Voxel→image | `ui/graphic/renderer.rs` (`euc` CPU rasteriser) | Item icons, a few `.vox` UI pieces (arrows, dividers, frame corners, `v_logo.vox`) | **Port the recipe** (§3) |
| Pixel-art downscale | `ui/graphic/pixel_art.rs::resize_pixel_art` (area-weighted, alpha-correct) | `VoxelPixArtGraphic` | Not needed with integer scaling |
| Scaling | `ui/scale.rs`: `ScaleMode::{Absolute, RelativeToWindow(1920×1080), DpiFactor}` | Logical units based on 1080p | Same idea with a scene2d `ScreenViewport` |
| i18n | Project Fluent `.ftl`, `client/i18n` crate | 48 English `.ftl` files, about 4,200 keys, 44 locales | **Text is reusable**; the loader needs a Java Fluent library or a small parser |

### 1.2 Design and data that can be copied (GPL → GPL, keep attribution)

* **Colour palette** (`hud/mod.rs:152–224`): `UI_MAIN (0.61,0.70,0.70)`, `UI_SUBTLE`, `MENU_BG (0.1,0.12,0.12)`, `HP_COLOR (0.33,0.63,0)`, `LOW_HP_COLOR`, `CRITICAL_HP_COLOR`, `STAMINA_COLOR (0.29,0.62,0.75)`, `XP_COLOR`, `BUFF/DEBUFF_COLOR`, chat channel colours, and **item quality colours** (`QUALITY_LOW … QUALITY_ARTIFACT`). Put them all into the Skin's `colors` block.
* **Layout numbers** (logical px at 1080p):
  * Skill bar: frame/bg 524×80. HP bar: frame 484×24 with a 480×18 fill and 2 px inset (`skillbar.rs:571–606`). The energy and poise bars work the same way.
  * Hotbar: 10 slots (`hotbar.rs` `Slot::One..Ten`), 40×40 `skillbar/slot.png`, with a `hotbar_index.png` key label.
  * Bag panel: 424×708 art. Grid slot is 57.8 px, or 40 in compact mode and 20 in details mode (`bag.rs:310`).
  * Paperdoll slots: 40×40, each with a 32×40 silhouette icon tinted `UI_MAIN` (`bag.rs:2430–2445`).
  * Minimap: 174×190. Full map, diary and settings windows: about 601×443.
* **Slot interaction model** (`hud/slots.rs`, `ui/widgets/slot.rs`, 835 lines): `SlotManager` handles click-drag, swap, split, right-click use or equip, and drop outside to throw. `SlotKind` covers Inventory, Equip, Hotbar, Trade, Ability and Crafting. The same semantics fit Barony (left-click pick up, right-click use, shift-click quick-move, drop into the world).
* **Tooltip content rules** (`ui/widgets/item_tooltip.rs`, 1,387 lines; `hud/util.rs`): title coloured by quality, kind and hands line, stat lines with comparison arrows (`util::comparison`), durability and price. The line-count logic (`stats_count`, `line_count`) is engine-independent.
* **The item image manifest** (§3), the **buff→icon mapping** (`hud/buffs.rs`) and **ability→icon** (`util::ability_image`).

### 1.3 How to rebuild it in libGDX

| Veloren widget / pattern | scene2d.ui equivalent |
|---|---|
| `Image::new(id).w_h().color(UI_MAIN)` | `Image(skin.getDrawable("frame_tinted"))`, with tint defined once as a `Skin$TintedDrawable` |
| Background + frame image pairs (`*_bg.png` + `*_frame.png`) | `Stack(bgImage, frameImage)` or a custom two-layer `Drawable` |
| `Button::image().hover_image().press_image()` | `ImageButton` / `Button` style with `up`/`over`/`down`/`checked` drawables (the files already come in `x`, `x_hover`, `x_press` triplets) |
| `Slot` widget + `SlotManager` | Custom `SlotActor extends Widget` + `scene2d.utils.DragAndDrop` (Source/Target/Payload). Draw the item icon region plus amount text |
| `ItemTooltip` | `Tooltip<Table>` with a shared `TooltipManager` (set `initialTime ≈ 0.1s`); 9-patch from `generic/frames/tooltip` |
| `ImageSlider`, progress bars | `ProgressBar` / `Slider` with `background` = bar frame, `knobBefore` = tinted `bar_content` |
| Scrollbars, chat log | `ScrollPane` + `Label` (enable `BitmapFont.getData().markupEnabled` for `[#RRGGBB]` colours) |
| `RadioList`, tabs | `ButtonGroup` of `CheckBox`/`ImageButton` |
| `OutlinedText` | FreeType `borderWidth=1, borderColor=black` |
| `ingame` widgets (overhead names, HP bars) | Project world→screen each frame, then position actors in a separate Stage group (or draw with `SpriteBatch`) |
| iced menus | Plain scene2d `Table` screens |
| egui | **imgui-java 1.92.7.1** (`io.github.spair`, MIT, LWJGL3 backend; integration on the libGDX wiki). Keep it in the `lwjgl3` module behind a dev flag. **VisUI 1.5.9** (Apache-2.0, built for libGDX 1.14.1) is an option for editor-style windows, but its look doesn't match Veloren's. |

The immediate-mode habit of rebuilding the HUD every frame isn't needed. Build actors once and update values (HP, amounts, cooldowns) from game state each frame, or bind them to change events.

---

## 2. Veloren's UI graphics

### 2.1 Inventory (`assets/voxygen/element`, measured)

893 PNGs, 12 `.vox` files and 1 `.ron`. All are RGBA PNGs, mostly authored at 1× for a 1080p logical layout. The most common sizes are 20×20 (240), 24×24 (209), 32×32 (46), 17×17 (43), 18×18, 40×40, 16×16 and 21×21. Ten 448×448 race portraits make up 2 Mpx on their own.

| Folder | Count | What's there | Barony use |
|---|---|---|---|
| `ui/generic/frames` (+ `bubble`, `bubble_dark`, `tooltip`, `frame_art`, `loading_screen`) | 43 + .vox | `window_3`, `esc_menu`, `prompt_dialog_top/mid/bot`, `selection{,_hover,_press}`, `enemybar*`, `banner_*`, tooltip edge and corner pieces, speech bubbles | Generic windows, dialogs, tooltips, enemy health bar, speech bubbles |
| `ui/generic/buttons` (+ `checkbox`, `radio`) | 72 | `button` (106×28) h/p, `border` 40×40 h/p, `armor_slot` 86×86 (+empty/selected), close, arrows, eye, mute, search, settings, social, map, spellbook, group, unlock, `hotbar_index`, `key_button` | All buttons; paperdoll slot frame |
| `ui/generic/slider`, `icons`, `controller` | 5, 8, 45 | Slider pieces, small icons, gamepad glyphs | Settings, gamepad prompts |
| `ui/bag` (+ `backgrounds`, `buttons`, `icons`) | 10 + 16 + 31 + 11 | Panel art 424×708 / 424×548 / 424×482 (bg + frame pairs). **16 paperdoll silhouettes** (back, bag, belt, chest, feet, glider, hands, head, lantern, legs, mainhand, necklace, offhand, ring, shoulders, tabard). Grid/list toggle, expand/collapse, sort | **Inventory and paperdoll, almost one-to-one** |
| `ui/skillbar` | 18 | `frame`/`bg` 524×80, `health_frame/bg` 484×24, `energy_*`, `poise_*`, `exp_frame*`, `slot` 40×40, `bar_content`, `enemy_bar_content`, `level_up`, `decayed_bg` | HP/MP/XP bars, hotbar |
| `ui/minimap` (+ buttons, icons) | 3 + 16 + 4 | `mmap`/`mmap_frame` 174×190, `mmap_closed`, zoom buttons, player arrow `indicator_mmap{,_colored}`, `mmap_view` | **Minimap frame and player arrow** |
| `ui/map` (+ buttons, icons) | 3 + 69 + 8 | `map_bg`/`map_frame` 601×443, `map_frame_art` 765×765, POI and difficulty markers, dungeon markers | Full automap window and markers |
| `ui/chat/icons` | 19 | Channel and info/error icons (`command_error`, `command_info`, faction, group, kill…) | Message log line icons |
| `ui/crafting` (+ icons) | 4 + 16 | Crafting window 470×460, category icons, quality indicator | Optional (Barony has no crafting beyond its tinkering kit) |
| `ui/diary` (+ buttons) | 15 + 13 | 601×443 book frame, skill-tree backgrounds (~930×620), `spellbook_bg`, ability description frames | **Spell list / character sheet** backgrounds |
| `ui/settings` (+ buttons, icons) | 4 + 7 + 8 | 526×443 settings window, crosshair style icons | Settings and crosshair |
| `ui/social`, `groups`, `invite`, `quests`, `char_select` | ~38 | Social and group frames, quest window, race portraits and icons | Co-op party frames; char-create portraits |
| `de_buffs` | 43 | Buff and debuff icons (burning, poisoned, bleed, frozen, wet, cursed, haste, invincibility, potion sickness…). Most are ~20×20, but `buff_potion_0` is 384×384 | **Status-effect row** |
| `skills/*` | ~300 | Ability icons per weapon tree (sword 80, bow 35, axe 23, hammer 21, staff 5) + 114 skill-tree nodes | Weapon-skill / spell icons (partial) |
| `animation/*` | 8 + 9 + 34 | `buff_frame` (8 frames), `crosshair_charge` (9), loaders (boat, cauldron… + manifest.ron) | Buff timer frame, charge crosshair, loading spinner |
| `weapons` | 13 | Weapon-type glyphs | Skill/proficiency icons |
| `items` | 1 | `item_bag_leather_large.png` only | (icons are rendered from `.vox`) |
| `v_logo`, `not_found` | | Logo, "?" placeholder | Placeholder icon |

Other UI-related assets:
* **Loading screens:** `assets/voxygen/background/bg_1…41.jpg` (screenshots, GPL by default).
* **Cursors:** Veloren has **none**. It uses the OS cursor (`window.rs` only toggles visibility and grab). The crosshairs are in `ui/settings/icons/crosshair_*` and `animation/crosshair_charge`.
* **World map:** Veloren's map *image* is generated from worldsim at runtime, so there's nothing to reuse. For a dungeon automap you generate the image from your own tile grid (§5).

### 2.2 Style notes
* The frames are drawn in **neutral greys and rely on tinting**. One `UI_MAIN` tint lets you re-theme the whole HUD, and the skin can offer alternative tints (Barony-ish warm brown, for example) for free.
* Frame/bg pairs are separate files so the background can be tinted separately and drawn semi-transparent. Keep that split.
* The art is pixel art with 1–2 px lines. It **must be drawn at integer scale with nearest filtering**, or it shimmers. Veloren gets away with arbitrary scaling because its UI shader samples carefully; in libGDX use integer scaling (§4.3).

---

## 3. Item icons: rendered from `.vox` at runtime

### 3.1 What Veloren does
* `assets/voxygen/item_image_manifest.ron` (5,684 lines) maps an `ItemKey` (`Simple("common.items…")`, `ModularWeapon((primary, material, hands))`, `ModularWeaponComponent`…) to an `ImageSpec`. All **1,395 entries are `VoxTrans(spec, offset[3], rot_deg[3], zoom, model_index?, color?)`**; there are no `Png` entries. Models referenced: weapon 687, armor 450, item 187, sprite 31, glider 18, lantern 16, object 6.
* `hud/item_imgs.rs::create_graphic` → `Graphic::Voxel(segment, Transform{ori, offset, zoom, orth:true, stretch:false}, SampleStrat::None)`.
  * `ori = Rz(z)·Ry(y)·Rx(x)` in degrees: X is applied first, then Y, then Z.
  * `graceful_load_segment_no_skin` removes material cells (skin, hair, eyes) and "hollow" cells. If `color` is set, it recolours pure-grey voxels with `recolor_grey` (linear multiply, base grey 178), which is how dyed armour is done.
* `ui/graphic/renderer.rs::draw_voxes`:
  * MVP = `ortho(-1..1, near 0, far 1) · scale(2/max(w,h,d) · zoom) · translate(offset) · ori · translate(-size/2)`, rendered at **exactly the requested pixel size**. With `SampleStrat::None` there's no supersampling for items, which keeps edges crisp.
  * Per-vertex lighting: `light = ao/4 · (max(n·-L,0) + 0.25) · 2.5`, with `L = normalize(-1,-1,-1)`. Colour = `light · srgb→linear(col)`, then output `linear→srgb`.
  * AO is the classic 3-neighbour corner rule (`ao_level`), with the quad flipped when `a+c > b+d`. That's the same AO rule recommended in `VELOREN_SHADERS.md`, so it can share `VoxMesher` code.
* Icons are cached by (graphic id, pixel size) in the atlas, so they're re-rendered whenever the UI scale changes.

### 3.2 How to do it in libGDX (recommended: runtime icon atlas + optional bake)
1. **Convert the manifest:** run the existing `Converter` (`RonToJsonVisitor`) on `item_image_manifest.ron`. Map keys come out as strings such as `"Simple(\"common.items.weapons.sword.starter\")"`, which is fine as a lookup key, or normalise them in a small post-pass. Keep only the items your game actually uses (for a Barony item list, a hand-written mapping file `barony_item → veloren vox + transform` is cleaner).
2. **Load the vox:** use your existing `VoxReader` with `model_index`. Drop material and hollow palette cells the way Veloren does, and apply `recolor_grey` for tints.
3. **Mesh:** use `VoxMesher` with AO (§3.1) and convert Veloren Z-up to your camera space once.
4. **Render:** an `IconRenderer` holding one `FrameBuffer(RGBA8888, 1024 or 2048 square, hasDepth=true)` used as an atlas page. For each icon:
   * `glViewport` to its cell (cells sized as `slotPx × uiScale`, e.g. 40 → 80 at 2×).
   * Clear that cell with scissor, draw with an `OrthographicCamera` and a tiny shader carrying Veloren's constants above (sRGB handling is shared with the shader plan), depth test on.
   * Wrap the result as a `TextureRegion` (remember FBO textures are y-flipped: use `region.flip(false, true)`).
   * Set nearest filtering, and **no MSAA**, to match Veloren's crisp look.
5. **Lazy + cached:** render on first use and keep an LRU across pages. 1,395 icons at 80×80 is about 9 Mpx, so 3 pages of 2048² if everything is pre-rendered, but a Barony game needs a few hundred. Re-render on UI scale change.
6. **Optional offline bake:** a Gradle `:tools` task (headless LWJGL3 window or `Lwjgl3Application` with a hidden window) renders the same set to PNGs and feeds the TexturePacker pipeline. That gives zero startup cost and lets you hand-touch icons, at the price of losing dye variants at runtime.
7. **Verify** against a Veloren screenshot for a few items (sword, helmet, potion) to confirm the Y-axis direction and rotation order, since `euc` and GL disagree on the handedness of screen Y.

The same renderer covers the 12 `.vox` UI pieces (frame corners, dividers, arrows, logo) and any **3D paperdoll preview** of the player figure, using a perspective camera and the animated skeleton from `VELOREN_ANIMATION.md`.

---

## 4. Fonts, licensing and the packing pipeline

### 4.1 Fonts (`assets/voxygen/font`, licences from `credits.ron`)

| Font | Role in Veloren | Licence | Ship it? |
|---|---|---|---|
| **HaxrCorp 4089 Cyrillic AltGr (Extended)** → `cyri` | **Main HUD/body font** (1,020 uses; sizes 14, 12, 20, 18, 15…) | **CC BY-SA 3.0** (`haxrcorp…-license.txt`) | OK as a **separate, unmodified font file** with licence and attribution. CC BY-SA **3.0** is not one-way GPL-compatible (4.0 is), so don't merge or modify it into GPL-licensed derived assets. |
| **Alkhemikal** → `alkhemi` | Titles, headings | CC BY 4.0 | Yes, with attribution (GPLv3-compatible) |
| **GoNotoCurrent** → `universal` | Fallback for all scripts (14.9 MB) | OFL | Optional; large. OFL is fine if you bundle the licence and don't sell the font on its own. |
| Wizard (Extended) | Older pixel font | CC BY-SA 3.0 | Same caveat as HaxrCorp |
| Metamorphous, Sarabun, NotoSansTC | Locale-specific | OFL | Only if needed |
| OpenSans | — | Apache-2.0 | Fine |
| bdfUMplus outline | JP | MIT | Fine |
| EnterCommand | — | CC BY 4.0 | Fine |
| WenQuanYi Zen Hei | CJK | GPL2 | Skip (GPLv2-only, 11.5 MB, not needed) |

Note: the four CC BY-SA `.ttf` objects (HaxrCorp ×2, Wizard ×2) didn't download from GitLab LFS in this session. Run `git lfs pull` in your Veloren checkout before testing them.

Rendering: use `gdx-freetype` (`FreeTypeFontGenerator`). Generate each pixel font at its native pixel size (and integer multiples), with `mono=true`, `hinting=None` (or `Full`), `minFilter/magFilter=Nearest`, and `borderWidth=1` for outlined HUD text. Pre-generate the sizes you use rather than scaling a BitmapFont.

### 4.2 Licensing summary
* **Veloren code and UI art: GPL-3.0**, compatible with your GPL-3.0 repo. `credits.ron` says unlisted assets are "assumed to be GPL3", and `other_art: []` lists no UI exceptions. When you port layout or tooltip logic, add a header such as "Derived from Veloren voxygen/src/hud/…, GPL-3.0" plus a `CREDITS.md`/`NOTICE` section.
* **Exclude CC-BY-NC-SA 4.0:** `voxel/glider/vroom.vox`, `voxel/sprite/mineral/ingot/*`, `voxel/sprite/wood/log/wood.vox`. **None of the UI PNGs are NC.** The item manifest doesn't reference `vroom` or the NC ingot/log sprites. Still, filter by path in the icon pipeline, as a guard.
* Veloren's NC music is irrelevant to UI.
* Barony's code is BSD-2-Clause (keep the header if you port anything). Barony's **art is not open**, so take only its layout ideas.

### 4.3 Packing pipeline (Gradle `:tools` module or a `JavaExec` task)
1. **Select:** a `ui-assets.txt` allowlist of the Veloren element paths you use (start with about 150). Copy from `velorenAssetPath` (already in `local.properties`) into `assets-src/ui/veloren/...`, keeping the original names so diffs against Veloren stay easy.
2. **Nine-patch:** a `ninepatch.json` table `{ "ui/bag/inv_frame": [left,right,top,bottom], ... }`. A small step writes `name.9.png` copies with the 1 px black split markers, and TexturePacker keeps the splits in the atlas. Leave the original PNG untouched. Typical splits are 6–12 px for button and slot borders and 24–40 px for the ornamented panel corners (check visually; the large panels have header bars, so give them a top split around 30 px).
3. **Animations:** rename frames to `name_0.png`, `name_1.png` so `useIndexes` produces `atlas.findRegions("buff_frame")` for `Animation<TextureRegion>`.
4. **Pack:** `com.badlogicgames.gdx:gdx-tools:1.14.0` `TexturePacker.process(settings, in, out, "ui")` with:
   * `filterMin = filterMag = Nearest`, `paddingX = paddingY = 2`, `duplicatePadding = true`, `edgePadding = true`, `bleed = true`;
   * `maxWidth = maxHeight = 2048` (everything ≤ 64 px fits in one page; put the big panel art and diary backgrounds on a second page, or keep them as separate textures);
   * `premultiplyAlpha = false` (or true together with a matching blend func); `combineSubdirectories = true`.
5. **Skin JSON:** `ui.json` holding:
   * the `colors` palette (§1.2);
   * `Skin$TintedDrawable` entries (`frame_main: { name: "ui/bag/inv_frame", color: ui_main }`);
   * `BitmapFont` entries (or fonts registered from FreeType at runtime before loading the skin);
   * `ButtonStyle`/`ImageButtonStyle`, `ProgressBarStyle`, `ScrollPaneStyle`, `WindowStyle`, `TooltipStyle`, `LabelStyle`.
6. **Pixel-perfect scale:** one HUD `Stage` with a `ScreenViewport` and `setUnitsPerPixel(1f/scale)`, where `scale = max(1, round(screenHeight / 1080f × userUiScale))` is restricted to an integer by default, plus an optional "smooth" mode with non-integer scale and linear filtering for 1440p users. Snap actor positions to whole pixels (`Actor.setPosition(Math.round…)`, `Table.round = true`, which is the default).
7. **Item icons:** the runtime `IconRenderer` atlas (§3.2), or the baked PNGs packed into a separate `items.atlas`.

---

## 5. Barony-style UI needs mapped to Veloren assets

| Barony element | Barony facts | Veloren assets | Gap / custom work |
|---|---|---|---|
| **HP / MP bars** | Bottom-left bars | `skillbar/health_frame/bg` (484×24) + `bar_content` tinted HP colours; `energy_*` tinted blue for MP; `exp_frame*` for XP | Shorten to Barony proportions with a 9-patch of the bar frame |
| **Hotbar** | 10 slots (`NUM_HOTBAR_SLOTS=10`), 5 alternate sets | `skillbar/slot.png` 40×40, `generic/buttons/hotbar_index.png`, `skillbar/frame` | Alternate-set indicator (simple text or arrows from `generic/buttons/arrow_*`) |
| **Inventory grid** | 12×3 classic, 5×6 in new UI, plus backpack | `bag/inv_bg*/inv_frame*`, `generic/buttons/border*` slots, `bag/buttons/inv_grid*`, sort buttons | None; resize the panel with a 9-patch |
| **Paperdoll / equipment** | Helm, breastplate, gloves, boots, shield, weapon, cloak, amulet, ring, mask (+ quiver in newer versions) | `generic/buttons/armor_slot*` + `bag/backgrounds/{head, chest, hands, feet, offhand, mainhand, back, necklace, ring, legs, …}` | **Mask/face** and **quiver** silhouettes (reuse head/back or draw two 32×40 icons). Optional 3D figure preview via the FBO renderer |
| **Item icons** | Pre-drawn sprites | Rendered from Veloren `.vox` (weapons, armour, potions ×10, food ×30, keys ×12, rings ×10, lantern, torch, bomb, coin) | **Scrolls, spellbooks, arrows/quivers, wands, gems in Barony variety** have no direct models (`voxel.item.recipe.recipe_basic` can stand in for a scroll). Make a few `.vox` with MagicaVoxel in the same style |
| **Minimap / automap** | Map + message panel, explored tiles only | `minimap/mmap`, `mmap_frame` (174×190), `indicator_mmap*`, `map/map_bg/frame` (601×443), map marker icons | Generate the map image: a `Pixmap` from your dungeon grid (1 px or 2 px per tile, fog-of-war mask, doors and stairs colours), upload as a `Texture`, rotate with the player for the minimap |
| **Message log** | Scrolling coloured text | Chat channel colours and `chat/icons` | Build it as a `ScrollPane` + markup labels; fade old lines like Veloren's chat |
| **Character sheet** | STR/DEX/CON/INT/PER/CHR, level, XP, gold, weight | Diary/bag "Stats" layout, `diary_bg/frame`, `exp_frame` | **6 attribute icons**; Veloren's stat icons are combat stats. Draw or reuse buff icons |
| **Spell list** | Learned spells, selected spell on hotbar | `diary/spellbook_bg`, ability icons (`skills/staff*`, `skills/skill_*`, `heal_*`, `skill_fire_aoe`, `skill_flamethrower`) + buff icons | **Most Barony spells have no icon** (light, identify, levitation, teleport, slow, dig, …). Plan for about 30 new 20×20 icons in Veloren's framed style |
| **Shop / trade** | Shopkeeper inventory with prices, buy/sell | `hud/trade.rs` layout, slot grid, `utility/veloren_coin` icon, price text | Buy/sell buttons from `generic/buttons/button*` |
| **Chest / loot** | Container grid next to the inventory | `bag/inv_middle*` panel, `loot_scroller` style list | None |
| **Status effects** | Icons with timers | `de_buffs/*` (43) + `animation/buff_frame` (8-frame timer ring) | Barony-specific effects (hunger, drunk, blind, paralyzed, levitating, telepathy…) need icons; several map to existing ones (frozen→paralyzed, cursed, poisoned, burning, haste, invincible) |
| **Crosshair** | Simple | `settings/icons/crosshair_inner/outer_*`, `crosshair_charge` | None |
| **Pause / settings / main menu** | | `generic/frames/esc_menu`, `settings/settings_bg/frame`, `button*`, sliders, checkbox and radio | Layout only |
| **Loading screen** | | `background/bg_*.jpg`, `animation/loaders/*` | Swap the overworld screenshots for dungeon ones eventually |
| **Overhead names / enemy HP** | | `generic/frames/enemybar*`, `skillbar/enemy_bar_content` | World→screen projection |
| **Speech bubbles** | NPC talk | `generic/frames/bubble*` | None |
| **Cursor** | | None in Veloren | Make a 16–32 px cursor PNG; `Gdx.graphics.newCursor(pixmap, hx, hy)` |

---

## 6. Recommendation and prioritised plan

**Approach:** scene2d.ui + a packed TextureAtlas of Veloren element PNGs (9-patched, tinted via the Skin), FreeType pixel fonts, an FBO-based item-icon renderer that copies Veloren's voxel→image recipe, and imgui-java for debug only. Treat Veloren's HUD code as a spec for sizes, colours and behaviour, not something to translate line by line. Keep all UI in a `ui` package of `:core`, and keep the asset tooling in a new `:tools` Gradle module so `gdx-tools` doesn't end up in the game jar.

| # | Task | Effort | Depends on |
|---|---|---|---|
| 0 | **Pipeline:** `:tools` module, allowlist copy from the Veloren checkout, `ninepatch.json` → `.9.png`, TexturePacker settings, `ui.json` skin (palette, tinted drawables, styles), FreeType font loading, integer-scale HUD `Stage`, `CREDITS.md` entries | 2–3 d | — |
| 1 | **HUD core:** HP/MP/XP bars, 10-slot hotbar with key labels, crosshair, status-effect row with timer frames, message log | 2.5–3.5 d | 0 |
| 2 | **Item icon renderer:** `IconRenderer` (FBO atlas, Veloren lighting and AO, tint/recolour, LRU), manifest conversion or Barony mapping file, NC path filter, comparison against Veloren screenshots | 2–3 d | 0, VoxMesher AO (shader plan) |
| 3 | **Inventory + paperdoll + drag-drop + tooltips:** `SlotActor`, `DragAndDrop` rules (pick up, swap, split, use, drop to world), equipment panel with silhouettes, quality-coloured item tooltips with stat comparison | 4–6 d | 0, 2, item/stat model |
| — | **MVP subtotal (0–3)** | **≈ 11–16 d** | |
| 4 | **Minimap + full automap:** grid→Pixmap with fog-of-war, rotation, markers, Veloren frames | 2–3 d | dungeon gen port |
| 5 | **Chest/loot and shop/trade windows** | 3–4 d | 3 |
| 6 | **Character sheet + spell list** (plus about 30 spell icons and 6 attribute icons as art tasks, about 2–4 d of pixel art not counted here) | 2–3 d | 3 |
| 7 | **Menus:** main menu, pause, settings (video, audio, controls, UI scale), loading screen | 2–4 d | 0 |
| 8 | **i18n:** UI strings in `.ftl`. Either `net.xyzsd.fluent:fluent-base` (2.0 targets JDK 23 while your build is Java 21, so use the 0.7x line or raise the toolchain; it pulls in ICU) or a small FTL-subset parser (en has only 26 select expressions across about 4,200 keys). Reuse Veloren item names and descriptions from `i18n/en/item/*.ftl` where items match | 1–2 d | — |
| 9 | **Debug overlay:** imgui-java 1.92.7.1 in `lwjgl3` behind `-Ddebug`, panels for detection, navmesh, AI state and UI scale | 0.5–1 d | — |
| — | **Full total** | **≈ 22–33 d** | |

**Things to decide or verify first**
1. The Barony item list and how it maps to Veloren `.vox` models, which decides how much icon art is missing.
2. Integer-only UI scaling vs. a smooth option, and the default scale at 1440p.
3. Font choice. Accept CC BY-SA 3.0 HaxrCorp as a separate file, or switch the body font to an OFL pixel font (for example from the Noto/OFL pixel set) to keep licensing simple.
4. Whether icons are rendered at runtime (dye variants, no bake step) or baked (no startup cost). Starting with runtime and adding the bake later is easy.
5. Exact 9-patch splits for the ornamented panels. Check them visually in a quick Skin Composer/scene2d test screen.

**Why not port conrod/iced or call into Rust via JNI?** It would mean keeping a second renderer (wgpu) in sync with libGDX's GL context, plus Rust cross-compiles for every OS, all to recreate widgets that scene2d already provides. The valuable parts are the art and the layout knowledge, and both transfer without any Rust.
