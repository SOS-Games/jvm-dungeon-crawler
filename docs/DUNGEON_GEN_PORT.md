# Porting `SOS-Games/dungeon-generator` to Java

*Research only. Nothing in either repo was changed. Sources: `SOS-Games/dungeon-generator` @ `9837c0b` ("initial", pushed 2025-04-21 9:29 PM ET). `SOS-Games/jvm-dungeon-crawler` @ `6093f3a` ("animals"; `git pull` said it was already up to date). Godot engine sources @ `4.4-stable`, used for the RNG and sort details.*

---

## 0. TL;DR

* **Language: GDScript in a Godot 4.4 project.** It has no third-party dependencies, only engine built-ins (`RandomNumberGenerator`, `TileMapLayer`, `Dictionary`, `Array`, `Vector2i`). There is **no LICENSE file, and GitHub shows `license: null`.** You own it, so add a license before you reuse the code (see §5).
* **The code is small: ~600 effective lines.** `DungeonGenerator.gd` is 675 lines, about half of which are debug prints and comments. `MinHeap.gd` is 76 lines.
* **Algorithm:** rejection-sampled **fixed 3×3 rooms** with a 5-tile keep-out buffer, then a **Kruskal MST** over Manhattan distances between room centres, then **A\*-carved corridors** with custom costs (dominant-axis bias, penalty next to rooms, penalty for crossing a corridor), then **shortcut loops**: non-MST pairs that are close in space but ≥3 hops apart in the graph. **It does not use BSP or cellular automata.**
* **Output:** a 2D `logical_grid[y][x]` of `"WALL" | "ROOM_FLOOR" | "CORRIDOR_FLOOR"`, plus `rooms[]` and `corridors[]` (each corridor stores its path tiles and the two room ids it connects). A Godot `TileMapLayer` is painted at the same time. It has **no seed**: `rng.randomize()` runs on every call.
* **Port:** add a pure-Java module `:dungeon-gen` (no libGDX, JUnit 5 only) that `:core` depends on. A faithful port takes about **1–1.5 days**. Adding tests, a seed, cleaner output types and fixes for the known bugs brings it to **about 2.5–3 days**. Hooking it into the crawler (voxel walls and floors, recast4j navmesh, Bullet collision, spawns) is **about another 3–5 days**.
* **Golden tests:** an exact match with Godot output is possible, but you'd have to port Godot's PCG32 bounded RNG, its unstable introsort, and the exact MinHeap behaviour, and also patch a seed into the Godot copy to produce fixtures. I recommend porting PCG32 (cheap) and making Java goldens the source of truth after one cross-check pass (§3.5).

---

## 1. File-by-file map

| File | What it is | Port? |
|---|---|---|
| `DungeonGenerator.gd` | The whole generator. It `extends Node` and generates in `_ready()` | **Yes**, split into several classes |
| `MinHeap.gd` | Binary min-heap of `{priority, item}` dictionaries, used as the A* open list | Replace with `PriorityQueue`, or port as-is if you want parity (§3.3) |
| `Main.tscn` | Scene with `map_width=150`, `map_height=150`, `num_rooms=100`, a `Camera2D`, and a `TileMapLayer` | Use as the **default preset** only |
| `mini_tiles.tres` | TileSet: 8×8 atlas over `cavesofgallet_tiles.png`, with custom data layer `tileType`: (0,0)=`WALL`, (0,1)=`ROOM_FLOOR`, (2,11)=`CORRIDOR_FLOOR` | No. It's rendering-only |
| `cavesofgallet_tiles.png` | 8×8 pixel-art tileset | **No.** Its origin and license aren't stated in the repo, so don't ship it |
| `project.godot`, `icon.svg`, `*.import`, `*.uid`, `.editorconfig`, `.gitattributes`, `.gitignore` | Godot boilerplate | No |

The only "rendering/export" code is `target_layer.set_cell(...)`, which runs alongside every `logical_grid` write, and a console dump in `_ready()`. There's no file export.

---

## 2. How the generator works

### 2.1 Parameters

| Name | Kind | Default in script | Value in `Main.tscn` | Meaning |
|---|---|---|---|---|
| `map_width`, `map_height` | `@export int` | 50, 30 | **150, 150** | Grid size in tiles |
| `num_rooms` | `@export int` | 15 | **100** | Target room count (it may place fewer) |
| `room_buffer` | `@export int` | 5 | 5 | Wall-only margin around each new room, also the map-edge margin |
| `room_connection_multiplier` | `@export float` | 0.2 | 0.2 | Shortcut count = `int(rooms * k)` |
| `ROOM_WIDTH/HEIGHT` | const | 3, 3 | – | **All rooms are 3×3** |
| `MIN_CORRIDOR_LEN` | const | 4 | – | A path must have ≥5 tiles, endpoints included |
| `ADJACENT_ROOM_COST_PENALTY` | const | 5 | – | Extra cost for stepping on a wall tile that touches room floor |
| `TURN_PENALTY` | const | **0** | – | Turn cost. It's computed but adds nothing |
| `CROSSING_CORRIDOR_COST_PENALTY` | const | 2.0 | – | Extra cost for stepping onto an existing corridor |
| `DOMINANT_AXIS_COST_MULTIPLIER` | const | 0.95 | – | Step-cost multiplier along the dominant axis |
| axis threshold | local | 1.5 | – | A pair is "horizontal" if `dx > 1.5·dy`, and vice versa |

### 2.2 Data structures

* `logical_grid: Array` holds rows of tile-type **strings**, indexed `[y][x]`. y points down.
* `rooms: Array[Dictionary]` holds `{id, position: Vector2i (top-left), width, height, center = position + (w/2, h/2) = +(1,1), connected_corridor_ids: []}`. `id` is the room's index in `rooms`.
* `corridors: Array[Dictionary]` holds `{id, path_tiles: Array[Vector2i], connects_rooms: [a, b]}`.
* `global_corridor_id_counter` is **never reset** between runs. Only `rooms` and `corridors` are cleared.
* A* state uses `Dictionary<Vector2i, {g,h,f,parent}>` and a `closed_set: Dictionary<Vector2i,true>`.

### 2.3 Pipeline (`generate_dungeon`)

1. **`_initialize_grid`** sets every cell to `WALL`, in both the grid and the tilemap.
2. **`_place_rooms`** does rejection sampling, with at most `num_rooms * 20` attempts:
   * `x = randi_range(buffer, W - 3 - buffer - 1)`, and likewise for y. Both ends are inclusive.
   * `_is_placement_valid` requires that the rectangle `[x-buffer, x+3+buffer) × [y-buffer, y+3+buffer)` is inside the map and is **all WALL**. That puts at least 5 wall tiles between any two rooms. The upper bound of the random range already keeps this check in bounds, so the bounds test never fails.
   * A room that passes gets the next id, and its 3×3 floor is carved as `ROOM_FLOOR`.
   * If fewer than 2 rooms are placed, generation stops.
3. **`_determine_mst_connections`** (Kruskal):
   * It builds all `n(n-1)/2` edges `{dist, u, v}`, where `dist` is the **Manhattan distance between centres**. That's 4,950 edges for 100 rooms.
   * It sorts them with `sort_custom(a.dist < b.dist)`. **Godot's sort is an unstable introsort**, so the order of equal-distance edges depends on the implementation.
   * Union-find `_find_set` uses recursive path compression without union-by-rank (`parent[b] = a`). It stops after `n-1` edges.
4. **`_carve_specific_corridors(MST pairs)`** runs this for each `(room_a, room_b)`:
   * **Candidate door tiles** (`_get_potential_connection_points`): the rows directly above and below the room for `x_offset ∈ [-1, w]`, which **includes the 4 diagonal corners**, plus the columns directly left and right for `y_offset ∈ [0, h)`. Only tiles that are currently `WALL` count, so a door tile already used by an earlier corridor drops out. Duplicates are removed. Top and bottom read `logical_grid`. Left and right read the TileMap custom data instead. The result is the same, but it's an inconsistency.
   * Every start×end pair is listed with its Manhattan distance and **sorted ascending** (unstable again).
   * Pairs are tried in order, skipping any where either endpoint is no longer `WALL`. **The first pair whose A\* succeeds wins.**
   * The **dominant axis** for the pair is HORIZONTAL if `dx > 1.5·dy`, VERTICAL if `dy > 1.5·dx`, and NONE otherwise.
   * **A\*** (`_find_path_astar`):
     * 4-neighbour moves in the fixed order **up, down, left, right** (`(0,-1),(0,1),(-1,0),(1,0)`).
     * Only `ROOM_FLOOR` tiles block movement, and the end tile is always allowed. Walls and corridors can be walked through, so corridors tunnel through solid rock and may cross each other.
     * Step cost: base 1.0. Multiply by 0.95 when the step is along the dominant axis. Add 5 if the target is a WALL tile 4-adjacent to any room floor, except at the start and end tiles. Add `TURN_PENALTY` (0) on a turn. Add 2.0 if the target is already a corridor.
     * The heuristic is Manhattan distance as an int cast to float. Because steps can cost 0.95, **the heuristic is slightly inadmissible**, so paths aren't guaranteed optimal. That's harmless, but it matters for parity.
     * `node_data` is updated when `tentative_g < old g`, and the neighbour is then pushed again (lazy deletion, with `closed_set` checked on pop).
     * When the end tile is reached, the path is rebuilt by following parents back to the start (the start's parent is the sentinel `(-1,-1)`). If `path.size() >= 5` it's returned. Otherwise the function returns the sentinel `[Vector2(-1,-1)]` (see bug B3).
   * On success, every path tile becomes `CORRIDOR_FLOOR` (overwriting is fine), a corridor record is appended, and both rooms get the corridor id. On failure it only logs a warning, and **the MST edge is lost with no repair**.
5. **`_add_shortcut_connections`** creates loops:
   * It builds a room adjacency list from the corridors that actually got carved.
   * Candidates are every non-MST pair `(i<j)` with `geom_dist` = Manhattan distance between centres.
   * It runs a BFS hop count for each candidate on the MST graph. Pairs that are unreachable (`-1`) or have `path_dist ≤ 2` are dropped.
   * Sort order is `geom_dist` ascending, then `path_dist` descending (unstable).
   * It takes the first `int(n * 0.2)` (20 for 100 rooms) and carves them with the same corridor routine. The graph is **not** recomputed between shortcuts, which the code acknowledges in a comment.
6. `_ready()` prints a summary of room 0's corridors.

**Complexity at 150×150 with 100 rooms:** about 5k edges, about 5k BFS runs over a 100-node graph, and about 120 A* searches on 22.5k cells, each trying up to ~14×14 door pairs until one succeeds. This takes milliseconds to tens of milliseconds in Java.

### 2.4 Randomness

* There's one `RandomNumberGenerator`, and `rng.randomize()` runs on every `generate_dungeon()`, **so there's no way to reproduce a dungeon.**
* RNG calls happen **only** in room placement: two `randi_range` calls per attempt, x then y. Everything after that is deterministic given the rooms. In practice, though, ties are broken by Godot's sort and heap internals (§3.3).
* `Array.shuffle()` appears only in the dead `_determine_connections()`.

### 2.5 Dead or vestigial code

* `_determine_connections()` is an older MST plus random-extra version. It's never called.
* `MinHeap.has_item()` is unused.
* `TURN_PENALTY = 0`, so that branch has no effect.
* `_get_tile_custom_data` exists only because of the left/right door check. Replace it with grid reads.
* Commented-out alternate atlas coordinates.

### 2.6 Bugs and quirks to decide on before porting

| # | Issue | Effect | Recommendation |
|---|---|---|---|
| B1 | **Corner door tiles.** The top/bottom loop runs `x_offset ∈ [-1, w]` | A corridor can start at a tile that only touches the room **diagonally**. In a 4-connected or physical 3D world, that room is sealed off. The +5 adjacency penalty also pushes A* away from walking along the room edge to fix it | In the Java port, default to `x ∈ [0, w)`. Keep a `legacyCornerDoors` flag for parity tests |
| B2 | **Unintended openings.** Only room floor blocks A*. Corridor tiles next to *other* rooms cost only +5 | A corridor can brush another room (making a door that isn't in `corridors`), or merge into another corridor. `connects_rooms` then **under-reports** real connectivity | After carving, compute true connectivity by flood fill and use *that* for the room graph and navmesh. Optionally treat other rooms' halos as hard obstacles |
| B3 | **"Too short" sentinel.** It returns `[Vector2(-1,-1)]`, a `Vector2` in an `Array[Vector2i]`, and then tests `path[0] == -1`, which compares a `Vector2i` to an `int` | Fragile. Depending on Godot's conversion and comparison rules it either never matches or errors. If it slipped through, it would carve `logical_grid[-1][-1]`, which is the *bottom-right* cell because of negative indexing. **With `room_buffer ≥ 5` it can't trigger**: the closest door pair is 5 tiles apart, which is exactly the minimum | Use an explicit result type in Java (`PathResult.ok / tooShort / none`) |
| B4 | **MST edge failures aren't repaired** | The dungeon can be disconnected, and shortcuts can't bridge components because BFS returns −1 and those candidates are dropped | Add an optional repair pass: try the next-best edge between components, or allow corridors through room halos |
| B5 | `global_corridor_id_counter` isn't reset | Corridor ids keep growing across regenerations | Reset it per run |
| B6 | All rooms are 3×3 | Fine for a 2D proof of concept, but small for a crawler | Make `minRoomW/H` and `maxRoomW/H` parameters. Keep 3×3 for legacy tests |
| B7 | Left/right door checks read the TileMap and top/bottom read the grid | No behavioural difference | Read the grid only |
| B8 | Shortcuts are chosen from the stale MST graph | Shortcuts may be redundant | Optional greedy recompute, off by default |

---

## 3. Port plan

### 3.1 Module layout

Add a new Gradle subproject and keep it **free of libGDX** so it's unit-testable, headless, and reusable (for example by a server or a CLI preview):

```
settings.gradle:   include 'lwjgl3', 'core', 'dungeon-gen'
dungeon-gen/build.gradle:
    apply plugin: 'java-library'          // root already sets Java 21
    dependencies {
        testImplementation platform('org.junit:junit-bom:5.11.0')
        testImplementation 'org.junit.jupiter:junit-jupiter'
        testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
    }
    test { useJUnitPlatform() }
core/build.gradle:  api project(':dungeon-gen')
```

Package `io.github.jvmdc.dungeongen`:

```
dungeongen/
  DungeonGenerator.java      // orchestrates generate(params, seed) -> Dungeon
  GenParams.java             // record: width, height, numRooms, roomBuffer, shortcutMultiplier,
                             //   roomW/H (or min/max), minCorridorLen, costs..., flags (legacyCornerDoors, repairDisconnected)
  Presets.java               // LEGACY_MAIN_TSCN (150x150x100), SCRIPT_DEFAULTS (50x30x15)
  Dungeon.java               // immutable result: TileGrid, List<Room>, List<Corridor>, RoomGraph, long seed
  TileGrid.java              // byte[] cells, width, height, get/set(x,y), idx(x,y)=y*w+x, inBounds
  TileType.java              // enum WALL, ROOM_FLOOR, CORRIDOR_FLOOR (byte codes 0,1,2)
  Room.java                  // id, x, y, w, h, centerX/Y, IntList corridorIds
  Corridor.java              // id, roomA, roomB, int[] pathIdx (packed cells), kind = MST|SHORTCUT
  RoomGraph.java             // adjacency (declared from corridors + derived from flood-fill), BFS hops
  internal/
    RoomPlacer.java          // _place_rooms + _is_placement_valid
    Mst.java                 // edges, sort, Kruskal, DisjointSet
    DisjointSet.java         // _find_set/_unite_sets (iterative)
    DoorFinder.java          // _get_potential_connection_points
    CorridorRouter.java      // A* (+ cost model) + _reconstruct_path; returns PathResult
    CorridorCarver.java      // _carve_specific_corridors
    Shortcuts.java           // _add_shortcut_connections + BFS
    MinHeap.java             // optional exact port for parity
    Pcg32.java               // Godot-compatible RNG (seed, nextInt(bound), range(from,to))
    GodotSort.java           // optional: port of SortArray introsort for parity mode
  debug/
    AsciiDump.java           // '#', '.', ',' text dump for tests and logs
    PngDump.java             // java.awt.image BufferedImage → PNG preview (test/tool only)
```

### 3.2 Class-by-class mapping

| GDScript | Java |
|---|---|
| `@export` vars and consts | `GenParams` record, with a `validate()` method (for example `W > roomW + 2*buffer`) |
| `rng` + `randomize()` | `Pcg32 rng = new Pcg32(seed)`. **The seed is always explicit.** `generate(params)` without a seed draws it from `SplittableRandom` and stores it in `Dungeon.seed` |
| `logical_grid[y][x]` strings | `TileGrid` holding a `byte[] cells` (row-major, `y*w+x`) and a `TileType` enum. Compare with `==` and never use strings |
| `rooms: Array[Dictionary]` | `List<Room>` (`ArrayList`). `Room` is a small final class with a mutable `corridorIds` |
| `corridors` | `List<Corridor>` |
| `_initialize_grid` | `new TileGrid(w,h)` filled with WALL. The tilemap half is dropped |
| `_place_rooms` / `_is_placement_valid` | `RoomPlacer.place(grid, params, rng)` |
| `_get_manhattan_distance` | `static int manhattan(Room a, Room b)` |
| `_determine_mst_connections` | `Mst.kruskal(rooms) -> List<Edge>`, where `Edge` is `record(int dist, int u, int v)` |
| `_find_set` / `_unite_sets` | `DisjointSet`. **Make find iterative** because the recursion has no depth bound. Keep `parent[rootB] = rootA` for parity |
| `_carve_specific_corridors` | `CorridorCarver.carve(List<int[]> pairs, Kind kind)` |
| `_get_potential_connection_points` | `DoorFinder.candidates(room, grid, legacyCornerDoors) -> int[]` of packed indices. Keep the **insertion order** (top, bottom interleaved per x, then left, right per y), because the stable/unstable sort depends on it |
| `_find_path_astar` | `CorridorRouter.find(start, end, axis) -> PathResult`. Use `double[] g` and `int[] parent` arrays of size `w*h` instead of dictionaries, plus a `long[]`/`int[]` visited stamp so they don't have to be cleared each call |
| `_reconstruct_path` | Walk `parent[]` into an `IntArrayList` (or `int[]`) and reverse it |
| `MinHeap.gd` | `java.util.PriorityQueue<long/record>` for normal use. Use `internal.MinHeap` (exact port with strict `<` and the same sift logic) when you need parity |
| `_add_shortcut_connections` | `Shortcuts.add(...)` |
| `_build_room_adjacency_list` | `RoomGraph.fromCorridors(...)`, using `int[][]` or `List<IntList>`, deduplicated |
| `_bfs_path_distance` | `RoomGraph.hops(u, v)` with an `int[] dist` filled with −1 and an `int[]` queue. You could also run **one BFS per source** and fill an all-pairs table, which is O(n·(n+e)) instead of O(n²·(n+e)) |
| `print`/`push_warning` | `java.lang.System.Logger` or a `Consumer<String> log` in params. No libGDX `Gdx.app.log` in this module |
| `_ready` dump | `AsciiDump` plus a `main()` in a test or tool |
| tilemap painting | Dropped. Rendering lives in `:core` (§3.4) |

### 3.3 GDScript→Java pitfalls

* **Integer division:** `ROOM_WIDTH / 2` gives 1, and `(index-1)/2` in the heap is the same in Java for non-negative values. GDScript `int / int` truncates toward zero just like Java, so no change is needed, but **don't** let a `float` slip in.
* **`int(x * 0.2)`** truncates. Use `(int)(n * mult)` with a `double` `mult`. For n=100, `100*0.2` is `20.000000000000004`, which gives 20 in both.
* **Floats:** GDScript `float` is **64-bit**. Use `double` for every A* cost (`0.95`, `2.0`, `5`). Using Java `float` would change tie-breaking.
* **`abs`** on ints is fine (`Math.abs`). Coordinates are never `Integer.MIN_VALUE`.
* **`randi_range(a, b)` is inclusive** at both ends, and Godot implements it as `rand(|a-b|+1) + min(a,b)` using PCG32 bounded rejection. Java's `Random.nextInt(a, b)` excludes `b`. Use `Pcg32.range(a, b)`.
* **Godot PCG32:** to reproduce Godot sequences from a seed:
  * `seed(s)` runs `state=0; inc=(1442695040888963407<<1)|1; next(); state+=s; next()`.
  * `next()` is the standard XSH-RR (`state = old*6364136223846793005 + inc`).
  * `bounded(b)` computes `threshold = (-b) % b` (unsigned 32-bit), then loops `r = next()` until `r >= threshold` and returns `r % b`.
  * In Java, use `Integer.remainderUnsigned`, `Integer.toUnsignedLong`, `>>>` and `Integer.rotateRight`.
  * That's about 30 lines. Test it against vectors printed from Godot.
* **Sort stability:** `Array.sort_custom` is an **unstable introsort** (`core/templates/sort_array.h`, threshold 16, median-of-3), while Java's `List.sort` is a **stable** TimSort. Equal-distance edges are very common with Manhattan distances, and **different tie order means a different MST and different corridors.** Options:
  * (a) Accept the difference and pin Java goldens (recommended).
  * (b) Port `SortArray` (about 150 lines) to get exact parity.
  * (c) Add explicit tie-breakers (`dist`, then `u`, then `v`). This is deterministic and readable, but it won't match Godot.
* **Heap tie-breaking:** `MinHeap` uses strict `<`, so equal-`f` nodes come out in heap-structure order. `PriorityQueue` behaves differently. For deterministic Java behaviour that doesn't depend on JDK internals, key the heap on `(f, insertionCounter)`, or port `MinHeap` exactly when you want parity.
* **Collections and keys:** don't use `HashMap<Vector2i,…>` or a `record Point` as keys in the A* loop, because that creates garbage and hashing overhead. Pack cells as `idx = y*w + x` and use flat arrays. Only the dedup in `DoorFinder` needs a set, and a small `boolean[]` or a linear check over ≤14 items works.
* **Iteration order:** GDScript `Dictionary` keeps insertion order, while Java `HashMap` doesn't. Nothing in the algorithm iterates a dictionary in a way that affects output, except `seen` in door dedup, which iterates the `points` array. If you do use maps, use `LinkedHashMap`/`LinkedHashSet`.
* **Closures/lambdas:** `sort_custom(func(a,b): return a.dist < b.dist)` is a *less-than predicate*. Java wants a `Comparator`, so write `Comparator.comparingInt(Edge::dist)`. The two-key shortcut sort becomes `comparingInt(geom).thenComparing(comparingInt(path).reversed())`. Never translate a boolean less-than into `a.dist < b.dist ? -1 : 1`, because that breaks the comparator contract and Java throws "Comparison method violates its general contract".
* **Negative indexing:** GDScript `arr[-1]` is the last element, while Java throws. That's good, because it exposes bug B3.
* **Typed-array conversions** (`Vector2` vs `Vector2i`, B3) disappear with a proper result type.
* **Recursion:** make the union-find `find` iterative.
* **Remove-while-iterating:** the backwards `remove_at` loop over shortcuts becomes `removeIf` or building a new list. Keep the *original order* of the survivors, since it feeds an unstable sort.
* **Y axis:** the grid is y-down. In the crawler (libGDX 3D, and Veloren's Z-up assets) map grid `(x, y)` to world `(x, -y)` or `(x, z)` consistently in **one** adapter class.

### 3.4 Feeding the crawler

`:core` already declares **`org.recast4j:recast/detour 1.5.7`** and **`gdx-bullet`**, but nothing uses them yet, so the generator output should target both.

The `Dungeon` result provides:

1. **Tile grid** (`TileGrid`): `WALL`, `ROOM_FLOOR`, `CORRIDOR_FLOOR`. Add `DOOR` as a derived tag on corridor tiles touching room floor, and `VOID` for wall cells with no floor neighbour (they don't need geometry).
2. **Rooms:** bounds, centre, `kind` (assigned by the crawler: start, boss, treasure, normal), and `depth` (BFS hops from the start room).
3. **Room graph:** the *declared* edges (MST plus shortcuts) and the *derived* edges from a flood fill over floor tiles (see B2). Use the derived graph for anything gameplay-critical.
4. **Corridor paths** as ordered tile lists. These are good for placing torches and patrol routes.

Adapter classes in `:core` (`io.github.jvmdc.world`):

* **`DungeonVoxelizer`** turns `TileGrid` into voxel chunks. Each tile becomes an `S×S` voxel column (for example S=8 at Veloren's 1 voxel ≈ 0.1 m is 0.8 m, which is too narrow; S=24–32 gives about 2.4–3.2 m per tile). Floor gets a slab, walls get height H **only where they border floor** (skip interior rock), and the ceiling is optional. Feed the voxels to the existing greedy `VoxMesher` path so walls share the figure shading and AO pipeline from the shader report. Pick floor and wall palettes per room `kind`.
* **`DungeonNavmesh`**: either
  * (a) build recast4j input from the floor quads plus wall quads (an `InputGeomProvider` over a flat `float[] verts` / `int[] tris`), configure agent radius and height, and build a `NavMesh` for detour queries, or
  * (b) for a strict grid crawler, skip recast and run grid A* directly on `TileGrid`. Reuse `CorridorRouter` with a plain cost model.

  Option (a) is right if movement is free and continuous. Mirror whatever you settle on for jvm-mw.
* **`DungeonCollision`**: one `btBvhTriangleMeshShape` from the same wall and floor triangles (static), or merged `btBoxShape`s per run of wall tiles. Reuse the triangle arrays you build for recast, so both come from one source.
* **`SpawnPlanner`**: room centres as spawn anchors (3×3 rooms give exactly one centre tile, larger rooms give a random free tile). Put the start at a leaf (degree-1) room on the graph periphery, and the boss at the farthest BFS depth from the start. Treasure goes in the other leaf rooms. Enemy density scales with depth. All of it uses `Pcg32` forked from the dungeon seed (`seed ^ 0x9E37...`), so content stays reproducible.

### 3.5 Test strategy

Use JUnit 5 in `:dungeon-gen`. It has no GL, so it runs anywhere, including CI.

1. **RNG parity:** check `Pcg32` against vectors printed from Godot 4.4: `rng.seed = 12345`, then 20× `randi_range(5, 141)`. This is cheap and proves room placement can match.
2. **Invariant tests** over ~500 random seeds × presets:
   * the grid border is all WALL
   * rooms don't overlap, and every pair keeps ≥ `buffer` wall tiles between them
   * every corridor has ≥ `minLen+1` tiles and is 4-connected along its path
   * each corridor starts and ends 4-adjacent to its two rooms (this test **fails under `legacyCornerDoors`**, which documents B1)
   * the MST has `rooms-1` edges
   * shortcut count ≤ `int(n*k)`
   * declared connectivity ⊆ derived connectivity
   * with `repairDisconnected`, all rooms are reachable
3. **Golden seeds:** for about 5 seeds × 3 presets (tiny 50×30×15, legacy 150×150×100, large), commit an **ASCII dump** (`#`, `.`, `,`) plus a JSON summary (room list, corridor endpoints and lengths) under `src/test/resources/golden/`. The test regenerates and diffs. A `-PupdateGoldens` flag rewrites them. These protect against refactors.
4. **Optional Godot cross-check (parity mode):**
   * In a scratch copy of the Godot project, replace `rng.randomize()` with `rng.seed = SEED`, reset the corridor counter, and dump `logical_grid` as text.
   * Run the Java port in `ParityMode` (exact `MinHeap`, `GodotSort` port, `legacyCornerDoors=true`) and diff.
   * Do this **once** to validate the port, then rely on the Java goldens. Exact parity isn't worth keeping long-term once you start changing the algorithm (room sizes, fixes).
5. **Performance smoke test:** legacy preset under ~100 ms.
6. **Visual check:** `PngDump` writes `build/dungeon-<seed>.png` in a test or task, plus a debug "Dungeon" mode in the viewer later.

---

## 4. Effort estimates

| Step | Estimate |
|---|---|
| Module skeleton, `GenParams`, `TileGrid`, enums, `AsciiDump` | 0.25 d |
| Room placement, Kruskal and DSU, door finder | 0.25 d |
| A* router, carver, shortcuts and BFS | 0.5 d |
| `Pcg32` (Godot-compatible) and vector test | 0.25 d |
| Invariant tests and golden harness | 0.5 d |
| Fixes as flags (B1 corners, B2 derived graph, B4 repair, B5, variable room sizes) | 0.5–0.75 d |
| **Subtotal: standalone generator** | **≈2.5–3 d** (a bare faithful port is ≈1–1.5 d) |
| Optional exact Godot parity (`GodotSort` port, MinHeap, Godot fixture dump) | +0.5–1 d |
| `DungeonVoxelizer` and meshing in `:core` | 1–1.5 d |
| recast4j navmesh from floor geometry, plus debug draw | 1–1.5 d |
| Bullet static collision from the same triangles | 0.5 d |
| `SpawnPlanner` and room kinds | 0.5–1 d |
| **Subtotal: crawler integration** | **≈3–5 d** |

---

## 5. License notes

* `dungeon-generator` has **no license file**, and GitHub reports `license: null`. **You're the author**, so you can relicense it any way you like. Add `LICENSE` (GPL-3.0 to match the crawler, or MIT/Apache if you want the `dungeon-gen` module reusable outside GPL projects) or at least an SPDX header in the Java files.
* If `dungeon-gen` stays a separate, libGDX-free module, a permissive license on it is compatible with the GPL-3.0 crawler that consumes it. The combined game is still GPL-3.0 because of the Veloren assets and code.
* **Don't port or ship `cavesofgallet_tiles.png`.** The repo doesn't say where it came from or what license it has. The crawler uses Veloren voxels anyway.
* Godot is MIT. Porting the *algorithms* of PCG32 (Apache-2.0/MIT, from pcg-random.org) and `SortArray` into Java for parity is fine. Keep a short attribution comment in `Pcg32.java` and `GodotSort.java`.
* No Veloren code is involved in this port.

---

## 6. Quick reference: the algorithm in pseudocode

```
grid = WALL everywhere
repeat up to numRooms*20 times while rooms < numRooms:
    x = rng.range(buf, W-3-buf-1); y = rng.range(buf, H-3-buf-1)
    if rect [x-buf, x+3+buf) x [y-buf, y+3+buf) all WALL: carve 3x3 ROOM_FLOOR, add room
edges = all pairs (manhattan(center_i, center_j)), sorted
mst = kruskal(edges)
carve(mst pairs)
graph = adjacency(carved corridors)
cands = non-MST pairs with bfsHops(graph) >= 3, sorted by (geom asc, hops desc)
carve(first int(n*0.2) cands)

carve(pairs): for (a,b): doors(a) x doors(b) sorted by manhattan;
    first pair (both still WALL) where A* finds path with >= 5 tiles -> mark CORRIDOR_FLOOR
A*: 4-neigh, ROOM_FLOOR blocks except end; cost = 1 (x0.95 on dominant axis)
    +5 if wall touching any room (not start/end) +2 if existing corridor; h = manhattan
```
