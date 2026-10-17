# Custom Voxel Assets

## Overview
Custom voxel assets live under the `custom_assets/` directory at the project root. Every asset lives in its own subfolder, containing exactly one JSON definition file named after the folder, plus its referenced `.vox` model parts:

```
custom_assets/<category>/<asset>/<asset>.json
custom_assets/<category>/<asset>/*.vox
```

Supported categories include `item/`, `npc/`, `placement/`, and `trap/`.

## Directory Scanning and Resolution Rules
1. **Scanning:** At startup, `CustomAssetRegistry` scans `custom_assets/*/*/<folder>.json` for definition files matching `<folder>/<folder>.json`.
2. **Precedence:** Custom asset IDs (e.g. `trap.lever` or `npc.mimic`) take precedence over converted base asset IDs with the same key.
3. **Relative Paths:** All `.vox` model references declared inside an asset's JSON definition are resolved relative to that asset's parent folder.
4. **Resilience:** Malformed or corrupt JSON files are logged with warnings and skipped rather than crashing the application.

## Schema
An asset JSON definition includes the following primary sections:

- `id`: Fully-qualified identifier (e.g. `trap.lever`, `npc.mimic`). Defaults to `<category>.<folder>`.
- `scale` & `units`: Scale factor (typically `0.09090909090909091`, representing 11 voxels per tile, Z up).
- `parts`: List of `.vox` model parts. Each part specifies its filename, offset, pivot, joint transforms, mirror flag, and optional per-state visibility.
- `placeable`: Gameplay and placement properties (footprint, interaction verbs, states, trap/light data).
- `palette`: Color definitions and named palette overrides (`cover_palettes` or `palettes`).
- `body` & `skeleton_attr`: NPC character family, species, body type, and rest-pose row numbers.
- `pivots` & `states`: Pivot points and state definitions (e.g., `closed_disguised` vs `open_snarl`).
- `license`, `credit`, `source`: License and attribution information.

## Per-State Part Visibility
Parts can define state-dependent visibility. A part can be marked as hidden in specific states (e.g. mimic legs hidden when in state `closed_disguised`). When building or meshing a model for an active state, hidden parts are filtered out.

## Palette Swaps
An asset can define named palettes (e.g. cover colors for spellbooks like `umber`, `crimson`, `oak`, `azure`). A named palette overrides specific 1-based palette indices in the model's `.vox` palette array before `VoxMesher` builds the mesh.

## Animal Pose Rows
NPC species pose rows are loaded from the asset's `skeleton_attr` into `AnimalAttrs`. If a requested species pose row is not found, `AnimalAttrs` logs a warning and returns an empty row rather than silently falling back to another species.
