# Custom Assets System

This document describes the layout, loading runtime, and JSON field specification for custom assets in the project.

## Folder Layout

Custom assets are located in the `custom_assets/` folder at the repository root. Each asset resides in its own subfolder grouped by category:

```
custom_assets/
├── item/
│   └── spellbook/
│       ├── spellbook.json
│       ├── spellbook_plain.vox
│       ├── spellbook_studded.vox
│       └── spellbook_clasp.vox
├── npc/
│   └── mimic/
│       ├── mimic.json
│       ├── chest.vox
│       ├── lid.vox
│       ├── front_left.vox
│       ├── front_right.vox
│       ├── back_left.vox
│       └── back_right.vox
├── placement/
│   └── altar/
│       ├── altar.json
│       └── altar.vox
└── trap/
    └── lever/
        ├── lever.json
        ├── lever_base.vox
        └── lever_handle.vox
```

Every asset folder contains exactly one JSON definition named after the folder (e.g. `<folder>.json`), alongside all referenced `.vox` voxel files. Relative paths in the asset JSON are resolved relative to that asset's directory.

---

## Asset Overrides & Resolution

1. **Resolution Precedence**: `AssetResolver` checks `custom_assets/` first before falling back to converted base assets.
2. **Registry Overrides**: At startup, `CustomAssetRegistry` scans all asset definitions under `custom_assets/*/*/<folder>.json`. Custom IDs replace base/converted base IDs with the same name.
3. **Error Handling**: Malformed or unparseable JSON files are logged and skipped rather than causing startup crashes.

---

## JSON Field Specification

An `AssetDef` JSON file supports the following top-level and nested fields:

### Top-Level Fields

- **`id`** (`string`): Dotted asset ID (e.g., `"trap.lever"`, `"npc.mimic"`).
- **`scale`** (`number`): Scale multiplier for world units (default: `1/11` or `0.090909...`).
- **`units`** (`string`, optional): Human-readable unit description (e.g., `"voxels; 11 voxels = 1 tile"`).
- **`body`** (`object`, optional):
  - **`family`**: Body skeleton family (`"quadruped_low"`, `"biped_small"`, `"character"`, etc.).
  - **`species`**: Species identifier (`"Mimic"`).
  - **`body_type`**: Variant or gender (`"Male"`).
- **`parts`** (`array` of objects): Voxel model components (see below).
- **`palette`** / **`appearance`** (`object`, optional): Palette definitions and color swaps.
- **`placeable`** (`object`, optional): Gameplay placement data (`type`, `footprint`, `blocks_movement`, `blocks_sight`, `interactable`, `states`, `placement`, `light`).
- **`skeleton_attr`** (`object` or `array`, optional): Skeleton rest-pose values registered into `AnimalAttrs`.
- **`license`** / **`credit`** / **`source`** (`string`): Legal license and attribution fields.

### Part Fields (`parts` Array)

Each element in `parts` defines a voxel mesh part:

- **`name`** (`string`): Internal part name (e.g., `"lever_base"`, `"handle"`, `"lid"`).
- **`file`** (`string` or `null`): Filename of the `.vox` asset relative to the asset folder (e.g., `"lever_base.vox"`). Parts with `file: null` are skipped.
- **`model_id`** (`string`, optional): Unique identifier for looking up this part mesh.
- **`size`** (`[x, y, z]`): Voxel dimensions.
- **`offset`** (`[x, y, z]`): Mesh origin offset relative to assembly.
- **`pivot`** (`[x, y, z]`, optional): Rotation pivot in local voxel coordinates.
- **`at`** (`[x, y, z]`, optional): Attachment vector.
- **`slot`** (`string`, optional): NPC bone slot (e.g., `"head_upper"`, `"chest"`, `"front_left"`).
- **`hidden_in`** / **`hidden_states`** (`array` of strings, optional): List of state names in which this part is hidden (e.g., `["closed_disguised"]`).
- **`joint`** (`object`, optional):
  - **`type`**: Kinematic joint type (`"rotate"`, `"translate"`, `"spin"`).
  - **`axis`**: `[x, y, z]` rotation/translation axis.
  - **`deg_per_sec`**: Continuous rotation speed for spin joints.
  - **`states`**: Map of state names to rotation angles (`number`) or translation vectors (`[x, y, z]`).

---

## Features

### Per-State Part Visibility

Parts specify state names in `hidden_in`. When an asset or rig is posed in that state (e.g., Mimic in `"closed_disguised"`), matching parts are hidden during meshing/rendering.

### Palette Swapping

An asset can define named color palettes in `palette.cover_palettes` or `appearance.palettes`. Overridden palette indices (e.g., indices 1–3 for spellbook covers) are applied before `VoxMesher` generates meshes.

### Animal Pose Rows

NPC species defined in custom asset JSON files register their `skeleton_attr` rows into `AnimalAttrs`. If a species is missing, a warning is logged rather than silently falling back to another species' pose row.
