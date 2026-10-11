# Custom voxel assets

Override root: `assets/custom/` (sits next to the converted base `assets/`). Model specifiers are dotted like the converted base manifests: `custom.voxel.trap.lever_base` -> `assets/custom/voxel/trap/lever_base.vox`.

- `voxel/` - .vox files: `trap/`, `placement/`, `item/spellbook/`.
- `manifest/models.json` - `models`: id -> vox, size, offset, scale (1/11 = 11 voxels per tile), pivot, attach points. `rigs`: multi-part models; each part has `model`, `at` (voxels, relative to the first part), and an optional `joint` (rotate/translate/spin with named states). `baked_preview` models are for previews only; animate the rig instead.
- `manifest/placeables.json` - gameplay data per prop: type, placement, footprint, blocks movement or sight, interaction, states, attach points, and trap data (trigger, effect, damage, reset). Everything marked `_placeholder` is a guess.
- `manifest/spellbook_appearance.json` - 3 designs x 10 cover palettes. Copy the palette into indices 1-3 when the mesh is built.
- `manifest/placement_palette.json` - stone, wood and metal tones for the placement props.

Loader: read `assets/custom/manifest/*.json` after the base manifests and let ids in custom replace base ids. Resolve a specifier by checking `assets/custom/...` first, then `assets/...`.
Licence: `pedestal_empty` and the spike parts are GPL-3.0 derivatives of base assets. Credit the source.
