# Custom voxel assets

Root: `custom_assets/` (next to the converted base `assets/`). Every asset lives in its own folder with exactly one JSON definition named after the folder:

```
custom_assets/<category>/<asset>/<asset>.json
custom_assets/<category>/<asset>/*.vox
```

Categories: `trap/`, `placement/`, `item/`, `npc/`. There are no shared manifest or palette files.

## Loader scan rule
1. Glob `custom_assets/*/*/*.json` and keep the files whose name matches their folder (`<asset>/<asset>.json`).
2. Parse each one. Its `id` (`<category>.<asset>`, for example `trap.lever`) is the registry key. Custom ids replace base ids with the same key, so load them after the base manifests.
3. Resolve every `file` in `parts` relative to that JSON's folder. Never resolve against another asset's folder.

## What an asset JSON holds
- `id`, `units` (11 voxels = 1 tile, z up), `scale` (1/11).
- `parts`: one entry per .vox. Fields are `name`, `file`, `size`, `offset`, `at` (position relative to the first part), optional `pivot`, `joint` (rotate/translate/spin with named `states`, plus `hinge`/`axis`), `attach` points, `emissive` and `anim`. `model_id` keeps the old model id for reference.
- `placeable`: gameplay data, including type, placement, footprint, blocking, interaction, states, attach points, and trap/light/outcome data. Every value marked `_placeholder` is a guess.
- `palette`: the asset's own colours. Placement props carry stone, wood, metal, water, candle and offering tones inline. The spellbook carries its 10 `cover_palettes`; copy one into `cover_indices` 1-3 before meshing. The mimic carries its byte palette and rules.
- `removed_baked_previews`: metadata for the preview-only files that were removed. Animate the parts instead.
- `license`, `credit`, `source`.
- `npc/mimic`: `body` (quadruped_low family), slot `parts` (central and lateral, with `file: null` for empty slots), `skeleton_attr`, `pivots`, `states` and `source_build` (the original assembly, for reference).

## Licence
`trap/pedestal`, `trap/spike_trap` and `npc/mimic` are GPL-3.0 derivatives of Veloren art and must credit the Veloren contributors. The other assets are original to this project.
