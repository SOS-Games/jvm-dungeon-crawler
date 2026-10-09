# Barony-style loot and loadouts for jvm-dungeon-crawler, built from Veloren assets

*Research only. No repo was changed, nothing was committed and no PRs were opened.*

**Sources**
- **Barony**: `TurningWheel/Barony` @ `962a5ce` (2026-02-20 10:09 PM ET), clone at `/workspace/barony`.
  - The **code is BSD-2-Clause**. The **game data is not open**, and that includes `items/items.json`, which holds every item's level, gold value, weight, names and model variations.
  - Files read: `src/items.hpp`, `src/items.cpp` (`newItem` :181, `itemLevelCurve` :627, `itemLevelCurvePostProcess` :350, `dropItemMonster` :1982, `weaponGetAttack` :4601, `armorGetAC` :5517, `canUnequip` :5873, `buyValue`/`sellValue` :5957/:6025), `src/actchest.cpp` (`createChestInventory` :49–783), `src/maps.cpp` (level gen :3850–4760, breakables :6140–6240, item and gold init :7440–7790), `src/entity.cpp` (`createMonsterEquipment` :19935), `src/actmonster.cpp` (death drops :3690–3930), `src/monster_goblin.cpp` (`initGoblin`), `src/monster_shopkeeper.cpp` (:250–970), `src/stat_shared.cpp` (`setDefaultMonsterStats`, 50 monster templates), `src/charclass.cpp` (`initClass` :693), `src/interface/identify_and_appraise.cpp` (:656), `src/mod_tools.cpp` (:886, `items.json` loader), `src/scrolls.hpp`.
- **Veloren**: master @ `ebfff94` (2026-10-06 6:56 PM ET), **GPL-3.0**. New sparse clone at `/workspace/veloren_loot`.
  - Data: `assets/common/{loot_tables (245 files), entity (302), loadout (90), items (1,312), material_stats_manifest.ron, trading/}`.
  - Code: `common/src/lottery.rs`, `common/src/comp/inventory/{loadout_builder.rs, item/{mod,armor}.rs, trade_pricing.rs}`, `common/src/figure/mod.rs`, `common/src/comp/body/item.rs`, `server/src/events/entity_manipulation.rs`, `voxygen/src/scene/figure/load.rs`.
  - Manifests: `assets/voxygen/{item_image_manifest.ron, voxel/item_drop_manifest.ron, voxel/humanoid_armor_*_manifest.ron, voxel/biped_weapon_manifest.ron}`, plus the `.vox` files for items, rings, necklaces, potions, lanterns, bombs and three armor sets (LFS objects pulled). Also `assets/credits.ron`.
  - From `/workspace/veloren_traps`: `common/src/terrain/sprite/mod.rs` (chest → loot table map, :1056–1085).
- **Crawler**: `SOS-Games/jvm-dungeon-crawler` @ `6093f3a` (master, up to date). It has no item, inventory or loot code yet. Useful pieces already there:
  - `converter/{Converter,RonToJsonVisitor,VoxReader,VoxMesher}.java`.
  - `viewer/NpcFigure.java`, which already dresses biped_small NPCs from the `biped_small_armor_*` manifests and puts weapons in hands from `biped_weapon_manifest`.
- **Prior reports**: `COMBAT.md` (C0–C12, including item status in combat), `MOVEMENT.md` (weight → speed, human body loading), `TRAPS.md` (bear traps, keys, chests), `BARONY_ENVIRONMENT.md` (props, chest sprites), `DUNGEON_GEN_PORT.md` (rooms graph), `DETECTION.md`. There's no `UI.md` in `/workspace`, so the inventory UI estimate here is standalone.
- **Artifacts from this research**:
  - `/workspace/loot_renders/sheet_{magic,jewelry,misc}.png`: previews of scroll, potion, charm, ring, amulet, gem, coin, key, lantern, bomb and food models.
  - `/workspace/loot_tools/`: a RON→JSON batch harness that uses the repo's own `Ron.g4` + `RonToJsonVisitor`, plus the preview renderer.

---

## 0. TL;DR

1. **Barony's loot logic is all code (BSD-2-Clause), but the item table it runs on is closed data.**
   - Every random item comes from `itemLevelCurve(category, minLevel, maxLevel)`. It picks **uniformly among items of that category whose `level` falls in the window**. Floor items use `0..depth`, chests and shops `0..depth+5`, spellbooks `+6` and chest potions `+7`. Gems come out as worthless glass 90% of the time.
   - Each item then gets a **status** (BROKEN → EXCELLENT, which adds −3…+1 attack/AC), a **beatitude** (−100…+100; cursed items can't be unequipped, and the value adds straight to attack/AC), **identified** (false by default) and an **appearance** seed.
   - **Chests** pick one of 8 coded themes. **Monsters** get loadouts from per-race weighted `switch` tables in `monster_*.cpp` / `stat_shared.cpp` and **drop everything they wear and carry** plus a gold bag. **Shops** are 10 coded store types with buy/sell formulas.
   - The per-item `level`, `gold_value` and `weight` (plus names and variations) live in the **closed `items.json`**, so we must author our own.
2. **Veloren has strong loot infrastructure, but no ARPG layer.**
   - `LootSpec` is a nested, weighted DSL: `Item | LootTable | Lottery | All | MultiDrop | ModularWeapon | Nothing`.
   - There are tiered tables `weapons/tier-0..5` and `armor/tier-0..5` (bronze → iron → steel → cobalt → bloodsteel → orichalcum, with matching cloth and hide), plus dungeon chests `DungeonChest0..5` (gnarling → adlet → sahagin → haniwa → myrmidon → cultist) with 25 to 2,000 coins.
   - Loadouts are `LoadoutSpec` with weighted `Choice` per slot and `inherit`, and entity configs tie body + loadout + loot table together.
   - There are ~1,300 item defs with `Quality` Low…Legendary, and ~1,355 dropped-item model entries.
   - It has **no** identification, curses, status grades, depth scaling or shops-from-tables (prices are inferred from loot frequency). **NPCs don't drop what they wear.**
3. **Recommendation: Barony rules, Veloren content.**
   - Port Veloren's `LootSpec`/`LoadoutSpec` evaluators to Java (≈300 lines). Feed them **JSON converted from Veloren `.ron`** with the existing converter. I verified that all 1,960 relevant files parse; there's one small number-format bug.
   - Add three crawler extensions:
     - `Category(cat, minΔ, maxΔ)`, a port of `itemLevelCurve` over **our own item catalog**.
     - `Gold(min, max, perDepth)`.
     - `Roll{status, beatitude, identified}` modifiers.
   - Map Barony depth onto the **Veloren material tiers**, put **status/beatitude/identify** on every item instance, and use Barony's **drop-what-you-wear** rule. Monsters with humanoid bodies (draugr, orc, human, elf, dwarf, danari) wear **real, lootable Veloren armor that renders on them**. Gnarlings and other biped_small/large monsters keep their NPC-only armor visuals and drop from loot tables.
4. **Art coverage is good:**
   - Weapons in 6 materials.
   - ~15 armor sets × 8 slots.
   - 10 rings, 18 necklaces, 6 gems, coins and a coin pouch.
   - 7 potion looks, 8 rolled-parchment "recipe" models that are perfect **scrolls**.
   - Lanterns and torches, lockpicks, keys, bombs, lots of food.
   - Dropped-item models for nearly everything (only 13 of 781 referenced player items lack one).
   
   **Gaps**: spellbooks/tomes (no standalone book model), crossbow, sling, whip, flail, player-size spear, arrows/quiver as items, a closed bear trap, towel/blindfold/mirror-type tools, a worthless glass gem (a recolor is enough), and only 2 shields.
5. **Icons**: Veloren ships **no PNG item icons**. All 1,395 `item_image_manifest` entries are `VoxTrans` (render the .vox with a given offset, rotation and zoom), so we bake an icon atlas offline from the same parameters.
6. **Effort**: an MVP (catalog of ~40 items, loot engine, floor/chest/monster drops, world item models, status/beatitude, basic list UI) is **≈10–13 dev-days**. The full system (identify/appraise/curse UX, shops, class kits, humanoid armor on figures, icon baker, full catalog, balance sim) is **≈27–38 dev-days**. Both are on top of COMBAT C0–C2 and MOVEMENT's human body loader. See §7.

---

## 1. Barony: loot and loadouts

### 1.1 Item instance model (`items.hpp:552–700`, code)

| Field | Values | Notes |
|---|---|---|
| `type` | 524 `ItemType` enum values | e.g. `WOODEN_SHIELD, BRONZE_SWORD … SPELLBOOK_DOMINATE` |
| `category` (from data) | `WEAPON, ARMOR, AMULET, POTION, SCROLL, MAGICSTAFF, RING, SPELLBOOK, GEM, THROWN, TOOL, FOOD, BOOK` (+ internal `SPELL_CAT, TOME_SPELL`) | 13 player-facing categories |
| `status` | `BROKEN, DECREPIT, WORN, SERVICABLE, EXCELLENT` (0–4) | Weapons: `attack += status − 3` (`items.cpp:4920`). Armor: per type, e.g. `3 + (status−1)`. BROKEN destroys the item. Degradation odds are in COMBAT.md §1.5 |
| `beatitude` | −100…+100, usually −2…+2 | Added to attack/AC (`weaponGetAttack`, `armorGetAC`). `< 0` means cursed: **can't unequip**, and trying identifies it (`canUnequip` :5928). Blessed buy price ×3·b |
| `identified` | bool | Unidentified items hide status and beatitude and show an "unidentified name" |
| `appearance` | random u32 | Picks the model variation (`appearance % variations`) and the scroll label from `scroll_label[25]` (`scrolls.hpp`, NetHack-style "ZELGO MER" …) |
| `count` | stack | |
| `isDroppable`, `ownerUid`, shop flags | | `MONSTER_ITEM_UNDROPPABLE_APPEARANCE` hides monster-only gear |

Equip slots (`ItemEquippableSlot`): **weapon, shield/offhand, mask, helm, gloves, boots, breastplate, cloak, amulet, ring** (10).

**Closed data (`items/items.json`, loaded by `ItemTooltips_t::readItemsFromFile`, `mod_tools.cpp:886`, hash-checked).** This is where each `ItemGeneric` gets its `level` (random-gen level, −1 = never random), `gold_value`, `weight`, identified and unidentified names, `variations`, model indices, equip slot and attribute map. **You can't copy any of it.** The per-type attack and AC bonuses, though, are hard-coded in `weaponGetAttack`/`armorGetAC` (code), so the formulas can be reused.

### 1.2 The level curve (`itemLevelCurve`, `items.cpp:627–784`)
```
candidates = { t | category(t)==cat && level(t)!=-1 && minLevel <= level(t) <= maxLevel }
  shops: hat store only hats; exclude SHOP_EXCLUDE_* attributes
  tools: tin opener 50%, lantern 75%, frying pan 25% kept
  armor: backpack 25% kept; grass sprig only on "The Swamp"
if cat == GEM and rand%10 != 0: return GEM_GLASS         // 90% worthless glass
return uniform pick from candidates                        // no weighting by level
```
`itemLevelCurvePostProcess` (:350) fixes thrown-weapon statuses and, for **spellbooks**, re-picks a spell weighted by difficulty against `itemLevel` (40/30/10 weights, rotating schools after the previous book).

Depth windows used by callers:

| Source | Window | Status | Beatitude | Identified |
|---|---|---|---|---|
| Random floor item (`maps.cpp:7445–7700`) | `0..depth` | uniform DECREPIT…EXCELLENT | 50%: uniform −2…+2, else 0 | no |
| Editor-fixed floor item | — | `DECREPIT + (d>5) + (d>15) + (d>20)` | +1 | no |
| Chest (`actchest.cpp`) | `0..depth+5` (books +6, potions +7). Min level 5 at depth ≥18, 10 at ≥32 for weapons/armor | WORN…EXCELLENT | 0 | no |
| Shop (`monster_shopkeeper.cpp`) | `0..shoplevel+5` (arms store ≥18 uses `5..` / `10..`) | WORN…EXCELLENT | ≥18: blessed shopkeeper, `rand % (1–3)` | some (potions yes) |
| Monster editor slots (`createMonsterEquipment`, `entity.cpp:19935`) | `0..depth` | per slot | per slot | per slot |

### 1.3 Floor loot generation (`maps.cpp`)
- **Spawn points.** A level has `30 + rand%10` entity spots, or the map's `GENBYTES` min/max for forced monster, loot and decoration counts. Half the spots are decorations. In the rest, loot vs monster depends on player count: **1p 3/4 loot, 2p 2/3, 3–4p 1/2** (`:4706–4760`).
- **Each loot spot.**
  - **10% gold**: `10 + rand%100 + depth` (`:7771`). The bag model is used at ≥5 gold, a coin model below that.
  - **Otherwise a random item**: the category is uniform over the 13. THROWN is re-rolled 2/3 of the time and BOOK 1/2. Half the time MAGICSTAFF is excluded. In multiplayer there's an extra 1/5–1/8 chance of FOOD.
- **Breakables** (barrels, pots; `:6140–6240`): a hidden monster, or 50% **3–5 gold piles of 2–4**, or a random item. After 10+ floor items, a breakable "steals" an existing floor item (max 6).
- **Decorations** include chests (1/7 or 1/4 of decoration rolls), plus fountains, sinks, tombs, tables, etc.

### 1.4 Chest themes (`createChestInventory`, `actchest.cpp:49–760`)
The theme is `rand%8`. At depth >10 the "garbage" theme is always re-rolled, and below that it's re-rolled 50% of the time. Mimics and locked chests are separate entity flags.

| # | Theme | Contents (all `WORN..EXCELLENT`, beatitude 0, unidentified) |
|---|---|---|
| 0 | Random | 1–5 items, each from a random category, `0..d+5` |
| 1 | Garbage | 50%: one scroll. Else 1–3 rocks, with a 1/20 chance per item of a scroll or a rose mask |
| 2 | Food | 1–5 food, plus 1/10 chef hat or 1/20 grass sprig |
| 3 | Treasure | 1 gem (75% real, 25% glass). 50% ring or amulet. 75% identify scroll. 25% remove-curse scroll |
| 4 | Arms | 1/3 each: one weapon, one armor, or weapon + armor + 0–1 thrown stacks (3–5) |
| 5 | Tools | 0–2 bear-trap stacks, 1–2 random tools (pickaxe…), rare backpack, tinkering kit + scrap, or mouth-knife |
| 6 | Magic | 1/4 each: 3–5 scrolls (+ rare feather and blank scrolls), 1–3 spellbooks, 1 staff, or a "wizard chest" (1–2 scrolls + book + staff + one of cloak/hat/feather/focus) |
| 7 | Potions | 1–3 potions `0..d+7`, rare alembic + empty bottles, rare goggles or plague mask |

### 1.5 Monster loadouts and drops (code)
- **Per-race init** (`monster_goblin.cpp:26–230` as the example):
  - **Weapon**: `rand%10` → 3 shortbow, 3 bronze axe, 2 iron mace, 1 iron axe, 1 fire staff. All `WORN`, beatitude `−1..+1`.
  - **Shield**: torch 2, none 3, wooden 2, bronze 2, iron 1. Ranged goblins get a quiver instead.
  - **Cloak**: none 6, cloak 3, magic-reflection 1. Helm, armor, boots and gloves work the same way.
  - **Boss variant 1/50** ("potato king"): artifact mace + jester hat, 3 goblin followers. 1/8 spawn asleep.
- **Templates** (`stat_shared.cpp`, 50 types): HP, `GOLD` + `RANDOM_GOLD`, proficiencies, which slots are generated, and up to 6 inventory slots with a % chance (e.g. a gnome has a 33% fish, 10% random gem, 2% book). Editor maps can override any slot, with category or exact item.
- **Death** (`actmonster.cpp:3690–3930`):
  - **Every equipped item and every inventory item is dropped** as a world item (spellbooks used as monster "weapons" are deleted).
  - Exceptions: summoned skeletons, "inner demon", `monsterNoDropItems`, and items with `isDroppable=false`. A shopkeeper drops each stack 50% of the time.
  - `GOLD × globalGoldPercent` drops as a bag.
  - Because monster gear is mostly WORN with −1…+1 beatitude, **a lot of it is junk or cursed**. That's part of the Barony feel ("don't equip the goblin's helmet before identifying it").

### 1.6 Player class starting kits (`charclass.cpp:693–3690`, code)
These are fixed `newItem(type, status, beatitude, count, …, identified=true)` calls per class, some with race and DLC branches. Examples:

| Class | Kit |
|---|---|
| Barbarian | iron axe, wooden shield, leather helm, ring of strength, 2 bread, meat, 2 torches, booze, 2 bronze tomahawks |
| Warrior | iron spear, bronze shield, leather helm, iron breastpiece, leather boots, bread, fish |
| Rogue | bronze sword (EXC), cloak, hood, gloves, leather breastpiece, 5 sickness potions, shortbow + 15 knockback arrows, decoy, 3 lockpicks |
| Wizard | quarterstaff, wizard hat and doublet, cloak of protection, amulet of magic reflection, boots, light staff, 2 restore-magic potions, fireball/cold/light spellbooks |
| Merchant | bronze axe, phrygian hat, leather breastpiece, monocle, pickaxe, gloves, 3 remove-curse + 2 identify scrolls, food |
| Healer | quarterstaff, hat, doublet, cloak, 3 extra-healing potions, cure-ailment and healing books, slow staff, food, towels |

There are 25 classes in total (Barbarian…Paladin, including DLC). The kits are fixed lists, so they're design data you can rewrite freely with Veloren items.

### 1.7 Identification, curses, appraisal
- Items are unidentified by default (class kits and some shop stock are identified).
- **Appraisal** (`identify_and_appraise.cpp:656`): the selected item is identified after a timer.
  - The timer depends on `gold_value` and `PRO_APPRAISAL + PER·k`: cheap items are fast, expensive ones slow.
  - Food, scrolls and potions scale by `max(0.2, 1 − skill/100)` with a minimum of 2 s.
  - Already-progressed items resume at 0.75–0.5× (`actplayer.cpp:8428`).
- **Scrolls**: identify, remove curse, enchant weapon/armor (beatitude +), repair (status +), destroy armor, etc. These are all `item_Scroll*` functions in code.
- **Curses**: beatitude < 0 subtracts from attack/AC, locks the slot, and shows when you try to unequip. Blessed potions and scrolls are stronger (per-effect code).
- **Value**:
  - `buyValue = gold·(identified ? 0.8 : 1.25) · (b>0 ? 3b : 1+b/2) · (status+5)/10 / ((50+trading)/150)`
  - `sellValue = gold·(identified ? 1.2 : 0.75) · (1+b/20) · (status+5)/10 · (50+trading)/150 · (1+CHR/20)` (`items.cpp:5957–6060`)
  - Unidentified gems sell as glass.

### 1.8 Shops (`monster_shopkeeper.cpp:250–970`)
- **Store types** (`rand%10` unless the map sets one): 0 arms & armor, 1 hats/helms, 2 jewelry, 3 books, 4 apothecary, 5 staffs, 6 food, 7 hardware/tools, 8 weapons & hunting, 9 general. There's also 10 "mysterious" (map-placed).
- **Stock**: 10–14 items. At depth ≥18/25/30 the "blessed shopkeeper" sells blessed gear (`beatitude = rand % 2..3`, status raised to SERVICABLE+). Special-case singletons cover alembics, lockpicks, backpacks, tinkering kits, foci and feathers.
- On death the shopkeeper drops about half its stock.

### 1.9 Code vs closed data (what you may reuse)

| Barony piece | Where | Reuse? |
|---|---|---|
| Category/status/beatitude/identified model, equip slots | code | ✅ Concept freely. Literal translation needs the BSD header |
| `itemLevelCurve` algorithm, depth windows, chest themes, floor/breakable rules, death-drop rule, shop types and price formulas, appraisal timer formula | code | ✅ Same |
| Monster loadout weight tables, class kits | code (fixed item lists) | ✅ Structure. Rewrite contents with Veloren items |
| `weaponGetAttack`/`armorGetAC` per-type numbers | code | ⚠ Usable, but tied to Barony's item list. Prefer Veloren stats (§4.6) |
| **Item `level`, `gold_value`, `weight`, names, unidentified names, variations, attributes** | **closed `items.json`** | ❌ Author your own |
| Item sprites/models, sounds, language strings, spell icons, monster stat JSON in `data/` | closed | ❌ |
| Scroll labels in `scrolls.hpp` | code (NetHack-derived strings) | Use your own list instead |

---

## 2. Veloren: loot, loadouts, items, models

### 2.1 `LootSpec` and `Lottery` (`common/src/lottery.rs`)
- A loot-table file is `Vec<(weight, LootSpec)>`, evaluated as a `Lottery` that picks **one** entry with probability `w/Σw`.
- `choose_seeded(seed)` uses `(seed % 65536)/65536·total` and a binary search over cumulative weights. `choose()` uses a thread RNG, so Veloren isn't deterministic here.

| Variant | Meaning |
|---|---|
| `Item("common.items.x")` | One item (its own `amount` × count) |
| `LootTable("common.loot_tables.x")` | Evaluate another table (one pick per count) |
| `Lottery([(w, spec)…])` | Inline weighted pick |
| `All([spec…])` | Evaluate all |
| `MultiDrop(spec, lo, hi)` | Evaluate `rand[lo..=hi]` times (re-roll each time) |
| `ModularWeapon{tool, material, hands}` | Random primary × secondary of that material |
| `ModularWeaponPrimaryComponent{…}` | Same, for components |
| `Nothing` | Empty |

Results are merged into `(count, Item)` stacks by item hash.

### 2.2 Tier ladder (the backbone for depth scaling)

| Tier | Weapons (`weapons/tier-N`: sword, axe, hammer + bow, staff, sceptre) | Armor (`armor/tier-N`: cloth / hide / metal sets) | Metal set protection (`material_stats_manifest`) |
|---|---|---|---|
| 0 | Bronze / Wood | Linen / Rawhide / Bronze | 45 |
| 1 | Iron / Bamboo | Wool / Leather / Iron | 75 |
| 2 | Steel / Hardwood | Silk / Scale / Steel | 102 |
| 3 | Cobalt / Ironwood | Lifecloth / Carapace / Cobalt | 117 |
| 4 | Bloodsteel / Frostwood | Moonweave / Plate / Bloodsteel | 129 |
| 5 | Orichalcum / Eldwood | Sunsilk / Dragonscale / Orichalcum | 135 |
| L | `legendary_{melee,ranged}`: Caladbolg, Mjölnir, Parashu, … | | |

- Set protection is split over slots by weight: shoulder 2, chest 3, belt 0.5, hand 1, pants 2, foot 1, back 0.5. **Head, ring and neck are 0**, so Veloren helmets are cosmetic (`armor.rs:249–262`). DR is `p/(60+p)` (COMBAT §2.2).
- Other tables: `armor/{cultist, witch, pirate, adventurer, boreal, ashen, twigs*}`, `weapons/{starter, cave, cultist}`, `weapons/components/tier-N`, `consumables/pyrotechnics`, `food/{prepared, wild_ingredients}`, `creature/**` (per species), `humanoids/*`, `sprite/{chest, crate, mud, chest-buried, chest_barn*}`, `spot/*`.

### 2.3 Dungeon chests per tier (`sprite/mod.rs:1056–1085`, `loot_tables/dungeon/*/chest.ron`)

| Sprite | Table | Gear | Coins | Other |
|---|---|---|---|---|
| `DungeonChest0` | gnarling | components t0, weapons t0, armor t0, bamboo twig hat | 25–50 | 2–5 minor potions, food, veloritefrag, quest carving, 2–3 recipes (80%) |
| `DungeonChest1` | adlet | t1 + hog hood | 50–100 | gems (topaz, amethyst, sapphire), potion, food, recipes |
| `DungeonChest2` | sahagin | t2 | 100–250 | pyrotechnics, potion, food, recipes |
| `DungeonChest3` | haniwa | t3 | 250–500 | " |
| `DungeonChest4` | myrmidon | t4 + spikeguard | 500–1000 | " |
| `DungeonChest5` | cultist | cultist weapons/armor, cave weapons | 1000–2000 | pyrotechnics 40%, 2–4 minor potions, alkahest, advanced recipes |

The generic `Chest` sprite uses `sprite/chest.ron`, which is a tier-0 mix.

### 2.4 Entity configs, loadouts and death loot
- **`EntityConfig`** (`entity/template.ron`, `generation.rs`): `name, body: RandomWith("species"), alignment, loot: LootSpec, inventory: (loadout: Inline((inherit: Asset(...), active_hands: InHands((main, off))))), meta: [SkillSetAsset]`. There are 302 configs, 11 dungeon families among them (gnarling, adlet, sahagin, haniwa, myrmidon, cultist, terracotta, vampire, dwarven quarry, sea chapel, fallback).
- **`LoadoutSpec`** (`loadout_builder.rs:239`, `deny_unknown_fields`):
  - Slots: `head, neck, shoulders, chest, gloves, ring1, ring2, back, belt, legs, feet, tabard, bag1–4, lantern, glider, active_hands, inactive_hands`.
  - Each slot is an `ItemSpec`: `Item(id) | ModularWeapon{…} | Choice([(w, Option<ItemSpec>)]) | Seasonal(...)`.
  - `inherit: Base` = `Asset | Combine | Choice`. `Hands` = `InHands((main?, off?)) | Choice`.
  - Example (`loadout/dungeon/cultist/cultist.ron`): every slot is a cultist piece, the head is `Choice([(111, bandana), (1, None)])`, and the weapon is `Choice` over 8 options (orichalcum modular axe or hammer, cultist sword, staff, …).
- **Body defaults**: `default_chest(body)` and `default_main_tool(body)` (`loadout_builder.rs:533, 636`) give big monsters their NPC-only "armor" and "natural weapons" (`npc_armor/biped_large/*`, `npc_weapons/unique/*`).
- **Death**: loot is **rolled at spawn** (`loot.to_items()` → `ItemDrops`, `entity_manipulation.rs:3614`). On death, `distribute_many` hands each stack to damage contributors weighted by damage share, and `LootOwner` locks it for a timeout (`:1257–1330`). **Equipped loadout items are not dropped.** That's the main rule difference from Barony.

### 2.5 Item definitions (`items/**`, `item/mod.rs`)
- **`ItemDef`**: `legacy_name, legacy_description, kind, quality, tags`. Display names come from i18n (`item_i18n_manifest.ron`); the legacy names are fine for English placeholders.
- **`ItemKind`**: `Tool, ModularComponent, Lantern, Armor{kind, stats: Direct | FromSet}, Glider, Consumable{kind: Drink|Food|ComplexFood|Charm|Recipe, effects, container}, Utility{Coins|Collar|…}, Ingredient, RecipeGroup, Quest`.
- **Quality** (rarity color) across all item files: Low 454, Common 258, Moderate 151, High 130, Epic 158, Legendary 143, Artifact 1, Debug 17. This is a rarity label, not a depth level.
- **Armor pieces by slot** (including NPC-only): Head 101, Chest 138, Shoulder 55, Hand 89, Belt 67, Pants 97, Foot 93, Back 39, Neck 18, Ring 10, Bag 16.
  - Player sets have 8 pieces: `head, shoulder, chest, hand, belt, pants, foot, back`.
  - Sets: cloth {linen, woolen, silken, druid, moonweave, sunsilk, cotton}, hide {rawhide, leather, scale, carapace, primal, serpentscale, dragonscale}, mail {bronze, iron, steel, cobalt, bloodsteel, orichalcum}, plus thematic sets (cultist, witch, pirate, assassin, ferocious, merchant, boreal, …).
- **Weapons**:
  - `modular/weapon/primary/{sword, axe, hammer, bow, staff, sceptre}` (~7 heads each: e.g. sword greatsword, katana, longsword, ornate, sabre, sawblade, zweihander) × `secondary/*/{short, medium, long}` (hilts and grips; `Hands` One/Two) × material. This gives **330 modular weapon model combos** in the drop manifest.
  - Fixed weapons: 3 axes, 3 bows, 3 daggers, 6 hammers, 6 sceptres, 2 shields, 4 staves, 5 swords, farm tools.
  - Spears exist only as NPC weapons (hoplite, sahagin spearman, tidal spear).
- **Other items**:
  - Consumables: potion_minor/med/big (red), agility, combustion, curious, freezing, trans; charms (burning, frozen, lifesteal).
  - Food: ~25 + meats.
  - Minerals: gems amethyst/diamond/emerald/ruby/sapphire/topaz, ores, ingots, stones.
  - Keys: 9 dungeon keys + quarry keys. Lockpicks: copper/iron/cobalt.
  - Lanterns: 16, including tin torch. Utility: bomb, fireworks, training dummy, coins.
  - Bags: 13. Recipes: 36 groups → 8 parchment looks.

### 2.6 Models and icons
- **Dropped item models** (`voxel/item_drop_manifest.ron`): 1,355 entries (773 `Simple`, 330 `ModularWeapon`, 252 `ModularWeaponComponent`).
  - Armor pieces point at the **set's multi-model .vox with an index**: 0 head, 1 back, 2 foot, 3 hand, 4 shoulder, 5 belt, 6 pants, 7 chest (e.g. `("voxel.armor.mail.iron", 7)`).
  - When meshing a drop, Veloren **strips skin/material cells** (`load.rs:6146–6160`) and offsets by the model's centre: tools and thrown items shift x−2, neck/back/tabard shift y−2, everything else is centred in XY.
  - `Body::CoinPouch` uses `voxel.sprite.furniture.sack_leather_M`. The single coin is `voxel.item.utility.veloren_coin`.
  - Coverage: of the 781 item ids referenced by loadouts and loot tables, only **13** player items lack a drop model (cardinal and miner sets, `misc.head.headband`).
- **Icons** (`item_image_manifest.ron`): **1,395 entries, all `VoxTrans(model, offset, (rx, ry, rz), zoom[, index])`**, and no PNGs. The game renders icons from the voxel models, so we need an offline icon baker.
- **Humanoid armor rendering** (`load.rs:319–1120`):
  - Each slot has its own manifest (`humanoid_armor_{head,shoulder,chest,hand,belt,back,pants,foot}_manifest`) giving `vox_spec: (model, offset, index)` and an optional `color` for grey-dyed pieces (`recolor_grey`: grey voxels × colour in linear space, base grey 178).
  - Chest is `bare_chest` (skin/clothing material) unioned with the armor. **Head entries are keyed per `(species, body_type, item)`**: 6 species × 2 body types ≈ 59 helmets each.
  - Weapons use `biped_weapon_manifest`. Rings and necklaces **don't render** on the figure.
  - biped_small NPCs (gnarlings, myrmidons, haniwa, …) have their own `biped_small_armor_*` manifests, which `NpcFigure` already handles.
- **Material cells** (`common/src/figure/mod.rs:238–285`): .vox palette indices 0–5 and 7 (raw file color index 1–6, 8) are **Skin, Hair, EyeDark, EyeLight, SkinDark, SkinLight, EyeWhite** placeholders filled from the body's colours. The crawler mesher must map these for humanoids and drop them for dropped items. This is the same "subtract 1" index convention as the glow note in `VELOREN_SHADERS.md`.

### 2.7 Pricing
`trade_pricing.rs` derives prices from **inverse frequency across loot tables** (weights in `trading/item_price_calculation.ron`), plus recipe ingredient costs. It's elegant but heavy (1,333 lines), and it's tuned for an open-world economy. **Don't port it.** Put an explicit `value` on each crawler item instead (§4.2).

### 2.8 Licensing notes specific to loot
- Everything recommended here is GPL-3.0 per `credits.ron`.
- **CC-BY-NC-SA entries to avoid**:
  - `voxel/glider/vroom.vox` (the debug glider's drop model).
  - `assets/voxygen/voxel/sprite/mineral/ingot` ("10 ingot sprites by Supa"). The *item* drop models live at `voxel.item.mineral.ingot.*`; confirm they aren't the same art before shipping ingots. We don't need ingots anyway.
  - `sprite/wood/log/wood.vox`.
  - The music tracks.

---

## 3. Mapping Barony categories to Veloren assets

Preview sheets: `/workspace/loot_renders/sheet_magic.png` (scrolls, potions, charms), `sheet_jewelry.png` (rings, necklaces, gems), `sheet_misc.png` (coin, keys, lanterns, bomb, food, tools).

| Barony category / slot | Veloren source (ids under `common.items.`) | Coverage | Gaps / notes |
|---|---|---|---|
| **Swords, axes, maces** | `modular` sword/axe/hammer × bronze…orichalcum (+ 1h/2h via secondary), plus uniques (caladbolg, parashu, mjolnir, cultist) | ✅ Excellent, 6 tiers × several heads | Barony "mace" = Veloren hammer 1h |
| **Spear / halberd** | NPC spears (`npc_weapons/biped_small/myrmidon/hoplite`, `sahagin/spearman`, `unique/tidal_spear`) | ⚠ Models are sized for small bipeds, no tiers | Author 3–4 spear heads, or scale the hoplite spear |
| **Dagger** | `weapons/dagger/{basic_0, cultist_0, starter_dagger}` | ⚠ 3 models | No humanoid dagger anim (COMBAT §8.5) |
| **Bows** | `modular` bow (7 shapes × 6 woods) | ✅ | |
| **Crossbow, sling** | none (ballista turret is an object) | ❌ | Custom art, or drop these |
| **Whip, flail** | none | ❌ | Drop, or custom |
| **Quarterstaff, magicstaff** | `modular` staff (fire), sceptre (life), uniques (laevateinn, root_evil, caduceus, amethyst, velorite sceptre) | ✅ | Map Barony "staff of X" spell to Veloren staff look by element (COMBAT §4.7) |
| **Thrown** (darts, tomahawks, chakram, bolas) | `utility/bomb`, fireworks, daggers | ⚠ | Use daggers as throwing knives and the bomb as an explosive. The rest is custom |
| **Arrows / quivers** | arrow and dart projectile models (TRAPS §3.2), no ammo items | ⚠ | Veloren bows have infinite ammo. Barony quivers need an item model (custom quiver, or arrow bundle from projectile) |
| **Shield** | `weapons/shield/{starter_shield, shield_1}` | ⚠ 2 models | Author bronze, iron and steel shields (COMBAT §8.6) |
| **Helm / hat** | set `head` × ~20 sets + `misc/head/*` (hood, wanderers_hat, mitre, crown, straw, bandana, bear_bonnet, spikeguard, boreal_warhelm, winged_coronet, witch hat, …) | ✅ Lots | Per-species offsets come from the manifest |
| **Mask** | `misc/head/{gnarling_mask, facegourd, scarlet_spectacles, pipe}` | ⚠ | Veloren has no mask slot. Either merge mask into head or add an overlay slot rendered on the head bone |
| **Breastpiece** | set `chest` (+ `shoulder`, `belt`, `pants`) | ✅ | Decide whether to keep Veloren's extra slots (§8.1) |
| **Gloves, boots** | set `hand`, `foot` | ✅ | |
| **Cloak** | set `back` + `misc/back/*` (short_0/1, dungeon_purple, admin, backpack) | ✅ | Barony "backpack" = `misc/back/backpack` |
| **Ring** | `misc/ring/{gold, amethyst, diamond, emerald, ruby, sapphire, topaz, scratched, abyssal}` + set rings | ✅ 10 | Good look pool for unidentified-ring shuffles |
| **Amulet** | `misc/neck/*` (18: gold, gem pendants, ankh_of_life, pendant_of_protection, fang, shell, carcanet_of_wrath, …) | ✅ | "Amulet of life saving" = ankh |
| **Potion** | `consumable/potion_{minor,med,big}` (red), `agility` (yellow), `combustion` (orange), `curious` (green), `freezing` (cyan), `trans` (pink), `sprite/potion/potion_empty` | ✅ 7 looks | Barony has ~25 potion types. Generate more colours by recolouring the liquid voxels (cheap) |
| **Scroll** | `recipes/*` (models `item/recipe/recipe_{basic, alchemy, blacksmithing, carpentry, cooking, lapidary, leatherworking, weaving}`) | ✅ 8 rolled-parchment looks | Use them as unidentified scroll appearances |
| **Spellbook / tome** | none (only bookshelf sprites) | ❌ | Custom 6×8×2 book .vox (recolourable cover), or cut one book out of `sprite/furniture/bookshelf_*`. Charms (`item/charms/*`) could serve as "tomes/talismans" |
| **Gems** | `mineral/gem/{amethyst, diamond, emerald, ruby, sapphire, topaz}` | ✅ 6 (Barony has 15 + glass) | Worthless glass = recoloured gem. Rock = `crafting_ing/stones` |
| **Gold** | `utility/coins` (single coin) + coin pouch (`sprite/furniture/sack_leather_M`) | ✅ | Same small/large split as Barony |
| **Food** | `food/*` (apple, cheese, carrot, tomato, mushroom, honeycorn, salads, …), `food/meat/*` raw and cooked, roast pig | ✅ | Barony "tin" has no match. Use a small box/jar or skip |
| **Tools: torch / lantern** | `lantern/tin_torch`, `lantern/*` (16) | ✅ | Lanterns already carry light colour/strength in Veloren data |
| **Tools: lockpick, skeleton key, keys** | `utility/lockpick/*` (3), `keys/*` (bone, glass, rusty…) | ✅ | |
| **Tools: pickaxe** | `tool/pickaxe_{stone, steel, velorite}` / `weapons/tool/pickaxe` | ✅ | |
| **Tools: beartrap** | adlet/boreal jaw traps (TRAPS §3.10) | ⚠ No closed variant | |
| **Tools: alembic, towel, blindfold, mirror, tin opener, bombs, bots, foci** | `crafting_tool/mortar_pestle`, `sewing_set`, `utility/bomb` | ⚠ | Alembic ≈ mortar & pestle. The rest is custom or cut |
| **Books (readable)** | none | ❌ | Same book model as spellbooks |
| **Artifacts** | `Quality: Legendary` uniques | ✅ | |

**Bottom line:** about 80% of Barony's item roles have a ready Veloren model and ~95% of the *drop-able gear* does. The must-have custom art is **1 book model, 1 quiver, 2–3 shields, a glass gem recolour and 10–15 potion recolours**, about 1.5–2.5 days of voxel work. Crossbow, sling, whip and flail are optional.

---

## 4. Design for the crawler

### 4.1 Data pipeline (reuse the converter)
I ran the repo's `Ron.g4` + `RonToJsonVisitor` (unchanged, harness in `/workspace/loot_tools`) over `loot_tables`, `loadout`, `entity`, `items` and `voxygen` manifests. **All 1,960 files parsed with no syntax errors.**

Output shapes, all externally tagged:
- Loot table → `[[w, {"Item": id}], [w, {"MultiDrop": [{"LootTable": id}, lo, hi]}], [w, "Nothing"], …]`.
- Loadout slot → `{"Choice": [[111, {"Item": …}], [1, null]]}`.
- `ModularWeapon` → `{"ModularWeapon": {"tool": "Axe", "material": "Orichalcum", "hands": null}}`.

Three quirks to handle:
1. **Bug**: `RonToJsonVisitor.isNumber` passes `00.0` (in `item_image_manifest.ron`, the `Loom` entry) through unchanged, which is invalid JSON for strict parsers. Normalise leading zeros, e.g. `new BigDecimal(text).toString()`.
2. Manifests whose root is `({ … })` come out as a **one-element array** around the map (`item_drop_manifest`). Unwrap it in the importer.
3. **Map keys stay as raw RON text**, e.g. `"Simple(\"common.items.armor.mail.iron.chest\")"` and `"ModularWeapon((\"…primary.sword.longsword\",\"common.items.mineral.ingot.iron\",Two))"`. Add a tiny key parser (`ItemKey.parse`) that returns `Simple(id) | ModularWeapon(primary, material, hands) | ModularWeaponComponent(…)`.

Typed importers (`io.github.jvmdc.items.importer`) whitelist the fields we use and fail loudly on others: `LootSpecImporter`, `LoadoutSpecImporter`, `VelorenItemDefImporter` (kind, quality, armor set, tool kind, consumable effects summary), `ItemDropManifest`, `ItemImageManifest` and `HumanoidArmorManifests`.

### 4.2 Java data model
```java
// Crawler-owned catalog: assets/data/items/catalog.json (our numbers, Veloren art)
enum Category { WEAPON, ARMOR, AMULET, POTION, SCROLL, MAGICSTAFF, RING, SPELLBOOK, GEM, THROWN, TOOL, FOOD, BOOK, GOLD }
enum Slot { WEAPON, OFFHAND, HEAD, MASK, CHEST, HANDS, FEET, BACK, NECK, RING, NONE }   // + optional SHOULDERS, BELT, LEGS (§8.1)
enum Status { BROKEN, DECREPIT, WORN, SERVICEABLE, EXCELLENT }

record ItemDef(
  String id,                 // "iron_longsword", "potion_healing", "ring_protection"
  Category category, Slot slot,
  int level,                 // Barony-style gen level (-1 = never random); our own values
  int value, int weight,     // our own values (Barony's are closed data)
  int stackMax,
  VelorenRef art,            // which Veloren asset(s) render it
  Stats stats,               // weapon (power/speed/range + combat ids, COMBAT §4.13) or armor (protection,…)
  String effect,             // potion/scroll/staff/ring/amulet effect id → EffectRegistry
  List<String> unidLooks,    // appearance pool for shuffled unidentified looks (potions/scrolls/rings/amulets)
  Set<String> tags) {}

record VelorenRef(String itemId,                     // "common.items.armor.mail.iron.chest" or modular spec
                  ModularSpec modular,                // {primary, secondary, material} or null
                  String dropModel, Integer dropIndex,// from item_drop_manifest (or override)
                  String iconKey,                     // key into baked atlas (from item_image_manifest)
                  Integer recolor) {}                 // optional RGB for recolour-generated variants

final class ItemStack {              // mutable instance
  final ItemDef def; Status status; int beatitude; int count; long appearance;
  boolean identified; long uid; int charges; Long ownerUid; boolean droppable = true;
}
```

**Loot spec**: a sealed interface that is a 1:1 port of Veloren's variants plus crawler extensions:
```java
sealed interface LootSpec permits Item, Table, Lottery, All, MultiDrop, ModularWeapon, Nothing,
                                   CategoryCurve, Gold, Roll {}
record CategoryCurve(Category cat, int minLevelDelta, int maxLevelDelta, int minLevelAbs) implements LootSpec {} // = itemLevelCurve
record Gold(int base, int rand, int perDepth) implements LootSpec {}           // Barony 10 + rand%100 + depth
record Roll(LootSpec inner, StatusRoll status, BeatitudeRoll beat, Boolean identified) implements LootSpec {}
```
- `LootContext{depth, tier, source(FLOOR|CHEST|MONSTER|SHOP|BREAKABLE), players, RandomGenerator rng}` is passed down.
- **Use a seeded `SplittableRandom` per level, split per entity**, so co-op peers and replays agree. Veloren's thread RNG isn't deterministic.

**Veloren id resolution**: `Item("common.items.armor.mail.iron.chest")` resolves through `VelorenAlias` (catalog `art.itemId` → `ItemDef`). Veloren items that have no catalog entry are either **auto-wrapped** as generic defs (category from `ItemKind`, level from tier, value from quality) or skipped, controlled by a whitelist. That way Veloren tables like `dungeon/haniwa/chest` work unchanged while you curate the catalog.

### 4.3 Depth scaling (Barony windows over Veloren tiers)
- Suggested mapping (tune once the crawler's floor count is known): **tier = clamp((depth−1)/5, 0, 5)** for a 30-floor run, or `/3` for an 18-floor run. Item `level` = `tier·5 + bump`: plain pieces get bump 0–2, named/legendary pieces 3–4, and quality High+ adds 1–2.
- Floor items: `CategoryCurve(cat, 0, 0)` gives level ≤ depth. Chests: `+5`. Shops: `+5`, with `minLevelAbs` rising at depth 18/32 just like Barony.
- **Uniform pick inside the window** (Barony) means low-tier items stay common deep down. To bias toward the current tier, add a `tierWeight = 1 + 2·[level ≥ depth−4]` option. Default it off for authenticity.
- **Veloren dungeon chest tables 0–5 work as "vault" chests** in treasure or boss rooms: tier = `tier(depth)`, with coins scaled ×0.1 to fit Barony's gold economy (Veloren coins are open-world sized).

### 4.4 Status, beatitude and identification (Barony layer on Veloren items)
- **Rolls by source**, copied from §1.2:
  - Floor: status uniform DECREPIT–EXCELLENT, beatitude 50% −2..+2.
  - Chest: WORN–EXCELLENT, beatitude 0.
  - Monster gear: per loadout entry, default WORN with −1..+1.
  - Shop: WORN–EXCELLENT, and from depth 18 blessed stock.
  - Class kit: fixed, identified.
- **Effects**:
  - Weapons: `attack += beatitude + (status − 3)`, fed into COMBAT §4.2 as a flat bonus scaled ×(Veloren HP scale ≈ 5).
  - Armor: `protection_eff = setProtection·slotWeight·(0.6 + 0.1·status) + 5·beatitude`. Veloren helmets have 0 protection, so give head pieces a small crawler-side value (e.g. tier·4).
  - Potions and scrolls: blessed = stronger or extra, cursed = backfire (per-effect code).
  - BROKEN destroys the item. Degradation odds come from COMBAT C2.
- **Veloren durability** (`has_durability`, repaired at stations) is replaced by `status`. Don't port Veloren durability.
- **Identification**:
  - Unidentified items show the category name plus their look: "a red potion", "a scroll labelled ‘VAS ORT’", "a ruby ring", "an iron chestguard (?)". Status and beatitude are hidden.
  - **Per-run shuffle**: at run start, seed a permutation that maps each potion, scroll, ring and amulet effect to a look from `unidLooks` (7 potion models + recolours, 8 scroll parchments, 10 rings, 18 necklaces). This NetHack/Barony staple works naturally with Veloren's distinct looks.
  - Weapon and armor types are always recognisable (Barony shows them). Only status and beatitude are hidden.
- **Appraisal**: port the timer formula. Time = f(value, Appraisal skill + PER). Run it on the selected inventory item, with progress kept per item.
- **Curses**: an equipped cursed item locks the slot, and trying to unequip identifies it with the message "You can't remove it, it's cursed!". Remove-curse scrolls and blessed water work as in Barony.
- **Scroll set (MVP)**: identify, remove curse, enchant weapon, enchant armor, repair, light, magic mapping, teleport. Effects are in our code.

### 4.5 Monster loadouts on Veloren figures
`MonsterDef` JSON:
```json
{ "id": "draugr_warrior", "body": {"species":"Draugr","bodyType":"Male"},
  "loadout": {"inherit":{"Asset":"crawler.loadout.undead_warrior"}},
  "roll": {"status":"WORN","beatitude":[-1,1],"identified":false},
  "loot": {"Lottery":[[3,"Nothing"],[1,{"CategoryCurve":{"cat":"POTION","minLevelDelta":0,"maxLevelDelta":0}}]]},
  "gold": {"base":0,"rand":20,"perDepth":1},
  "dropEquipped": true, "dropInventory": true }
```
- **Humanoid-body monsters** (Veloren `humanoid` species: Human, Elf, Dwarf, Orc, Danari, **Draugr**) use **real catalog items** in a `LoadoutSpec` with Barony-style weighted `Choice` per slot. Their gear **renders on them** (§4.6) and **drops on death** with its rolled status/curse. This is the "goblin's cursed helmet" experience.
  - Draugr ↔ Barony skeleton, Orc ↔ goblin/bugbear, Human ↔ human/bandit, Dwarf ↔ gnome-ish. Cultists, travelers and pirates already have loadouts to inherit (`loadout/dungeon/cultist/*`, `loadout/world/traveler{0..3}/*`, which are tiered by material).
- **biped_small** (gnarlings, adlets, sahagin, haniwa, myrmidons) and **biped_large / beasts** keep Veloren's **NPC-only armor and weapons as visuals** (`npc_armor/*`, `npc_weapons/*`, already loaded by `NpcFigure`). Their drops come from `loot` (Veloren `loot_tables/dungeon/<family>/*.ron` or `creature/*`) plus a **"gear echo"** table. For example, a gnarling with a `npc_weapons.biped_small.gnarling.mugger` dagger drops a `weapons/dagger/basic_0` 30% of the time. Map it with a small `npcWeaponToPlayerItem.json`.
- **Death**: Barony rule. Drop all equipped and inventory items flagged droppable, plus the gold bag, plus a roll on the loot table. Summons and "illusions" drop nothing. In co-op use Barony's free-for-all; Veloren's damage-share owner lock is optional.
- **Bosses and minibosses**: Barony's 1/50 boss variants map to Veloren minibosses (gnarling chieftain, myrmidon minotaur, cultist warlord). Use their tables (`dungeon/*/chieftain.ron`, `miniboss.ron`) for guaranteed tier+1 or legendary loot.

### 4.6 Rendering armor and weapons on figures
- **Humanoid assembler** (`HumanoidFigure`, extends the planned VELOREN_ANIMATION P1 per-bone meshes):
  - Per slot, look up `humanoid_armor_<slot>_manifest[itemId]`, or the head manifest by `(species, bodyType, itemId)`. Load `model` at `index` with `VoxReader` (multi-model support already exists).
  - Apply `recolor_grey` when `color` is set, then mesh with **MatCell mapping**: raw .vox colour indices 1–6 and 8 → skin, hair and eye colours from `humanoid_color_manifest`.
  - Chest = union of `armor.empty` and the armor. Shoulders are mirrored L/R. The weapon comes from `biped_weapon_manifest`, which `NpcFigure` already reads.
  - Re-mesh only on equipment change and cache by `(body, slot, itemId)`.
- **First person**: the player sees only the hands and weapon (COMBAT C8 / MOVEMENT M6). Gloves use the `hand` manifest on the viewmodel hands, so loot shows up in first person too.
- **Stats**: weapons use Veloren tool stats (`power/speed/range`) × modular material multipliers (COMBAT C0). Armor uses Veloren set protection split by slot weight. Barony's per-type AC tables aren't needed.

### 4.7 Dropped items in the world
- **`WorldItem` entity**: `ItemStack` + mesh from `item_drop_manifest`.
  - Armor uses the set .vox at its index with skin cells removed (`load.rs:6146`). Centre offsets follow Veloren: tools and thrown items x−2, neck/back y−2, others centred.
  - Gold: < 5 coins → `veloren_coin`, otherwise the `sack_leather_M` pouch (Barony's own threshold).
  - Unidentified potions, scrolls, rings and amulets render with their **shuffled look**.
- **Scale**: render drops at the same voxel scale the figure code uses for held weapons (Veloren scales figure voxels via `scaler/11`-style factors, see VELOREN_ANIMATION.md). That way a dropped sword matches the one in a monster's hand. Check this against a held weapon in the viewer. Lay weapons flat on the floor (Barony) instead of standing.
- **Physics**:
  - A small kinematic drop arc (pop out 0.5–1 m from the corpse or chest), then settle on the floor.
  - No Bullet rigid body needed; one block-grid raycast does it.
  - Pickup with the interact ray (DETECTION DDA) or by walking over gold.
  - Merge stacks within 0.5 m.
- **Light**: lantern and torch items emit their Veloren light when dropped, and glowing potions use the glow palette indices (SHADERS note).

### 4.8 Icons
`IconBaker` is a desktop tool task (`:core:bakeIcons`). For each catalog item it reads the `item_image_manifest` entry: `VoxTrans(model, offset, (rx, ry, rz), zoom[, index])`. It renders the meshed model with an orthographic camera into a 64×64 or 128×128 FBO with Veloren-style lighting and an outline, then packs everything into a libGDX `TextureAtlas`.
- Unidentified looks get their own icons.
- Coverage is the same as the manifest, ~1,395 entries, so nearly all catalog items get an icon for free.

### 4.9 Chests and containers
- **Chest themes** are crawler loot tables in JSON. They port Barony's 8 themes using `CategoryCurve` and catalog ids. Example, theme 3 "treasure":
```json
[[1.0, {"All": [
   {"Lottery": [[3, {"CategoryCurve": {"cat":"GEM","minLevelDelta":0,"maxLevelDelta":5}}],
                [1, {"Item":"crawler.gem_glass"}]]},
   {"Lottery": [[1,"Nothing"],[0.5,{"CategoryCurve":{"cat":"RING","maxLevelDelta":5}}],[0.5,{"CategoryCurve":{"cat":"AMULET","maxLevelDelta":5}}]]},
   {"Lottery": [[3,{"Item":"crawler.scroll_identify"}],[1,"Nothing"]]},
   {"Lottery": [[1,{"Item":"crawler.scroll_remove_curse"}],[3,"Nothing"]]}
 ]}]]
```
- **Chest models**: Veloren chest sprites (`sprite.chests.*`, the dungeon chest per family; see BARONY_ENVIRONMENT §5 and TRAPS §3.7 for locks and keys). Use the per-tier dungeon chest look for the vault chest (§4.3).
- **Locked chests** need a lockpick (`utility/lockpick/*`) or a key. **Mimics** reuse Veloren's mimic sprites/entities if available, otherwise a chest with a monster flag. Bashing a chest can break potions (Barony).
- **Breakables** (barrels, pots, crates from Veloren sprites) roll the Barony breakable rule (§1.3), and crates can use `sprite/crate.ron`.

### 4.10 Shops
- `ShopDef` JSON per store type: weighted category curves and fixed singletons (Barony §1.8), with stock size `10 + rand(5)`.
- Prices use Barony's `buyValue`/`sellValue` formulas over our catalog `value`. Shopkeepers buy unidentified items at 0.75× (gems as glass) and can "identify for a fee" (optional QoL).
- The shopkeeper is a Veloren humanoid with a `village/merchant` loadout (`loadout/village/merchant.ron`), plus a shop counter sprite.
- Theft and anger can come later.

### 4.11 Class kits
`ClassDef.kit` is a list of `Roll(Item(catalogId), status, beatitude, count, identified:true)` entries. The crawler versions use Veloren gear:
- **Warrior**: iron modular sword + wooden shield + iron set chest/feet + leather head.
- **Rogue**: bronze dagger + shortbow (wood) + rawhide set + 3 lockpicks + 2 potions.
- **Wizard**: wood staff + linen set + wanderer's hat + ring + 2 scrolls + 2 spellbooks.
- **Merchant**: bronze axe + merchant set (`armor/merchant`) + pickaxe + identify/remove-curse scrolls.

### 4.12 Co-op
Loot is generated on the host from the level seed and item uids are host-assigned. Use free-for-all pickup (Barony). Veloren's damage-share distribution (`distribute_many`) is an option for "fair loot" mode.

---

## 5. Gaps summary (custom art or cuts)

| Need | Option | Effort |
|---|---|---|
| Spellbook / tome / readable book | 1 recolourable book .vox, or cut from `bookshelf_*` | 0.25–0.5 d |
| Quiver / arrow bundle item | New .vox using Veloren arrow voxels | 0.25 d |
| Shields (bronze, iron, steel, tower) | Author in Veloren style | 0.5–1 d |
| Potion variety (~15 more) | Script-recolour the liquid voxels of `potion_red` | 0.25 d (script) |
| Glass gem, rock | Recolour a gem, use `crafting_ing/stones` | <0.1 d |
| Player spears | Scale or adapt hoplite/sahagin spears, or author 3 | 0.5 d |
| Crossbow, sling, whip, flail | Author, or cut from the item list | 0.5–1.5 d (optional) |
| Closed bear trap, towel, blindfold, mirror, tin | Author or cut | 0.5 d (optional) |
| Mask slot | Use head items, or add an overlay slot | code only |

---

## 6. Licensing
- **Veloren-derived Java** (LootSpec/Lottery evaluator, LoadoutSpec builder, recolour, MatCell mapping, item-drop offsets) gets a GPL header naming the source file, commit `ebfff94` and "Copyright (C) Veloren contributors, modified".
- **Converted JSON, .vox and baked icons** are GPL-3.0 assets; list them in NOTICE from `credits.ron`. Don't ship the NC entries (§2.8).
- **Barony-derived Java** that literally ports `itemLevelCurve`, `createChestInventory`, the buy/sell formulas or the appraisal timer must carry the **BSD-2-Clause notice and disclaimer** (template in MOVEMENT.md §6), reproduced in NOTICE.
- **Never copy Barony data**: `items.json` levels, values, weights, names, unidentified names, sprites, sounds or language strings. Write your own catalog numbers and descriptions. Avoid reusing `scrolls.hpp` labels; write your own.

---

## 7. Prioritized plan

**Prerequisites:**
- COMBAT C0 (converter run + `WeaponDef`), C1 and C2 (status and beatitude hooks).
- MOVEMENT's human body loader (3–5 d).
- VELOREN_ANIMATION P1 (per-bone meshes).
- DUNGEON_GEN_PORT room graph (for placement).

| # | Slice | Effort | Key references |
|---|---|---|---|
| L0 | **Converter hardening + importers**: fix `isNumber` leading zeros, unwrap `({…})`, `ItemKey.parse` for manifest keys. Typed importers for LootSpec, LoadoutSpec, ItemDef subset, drop/image/armor manifests. JUnit over all 1,960 files | 1.5–2 d (≈0.5 d shared with COMBAT C0) | `converter/RonToJsonVisitor.java:169–183`; `/workspace/loot_tools/Batch.java`; `lottery.rs:215–420`; `loadout_builder.rs:43–275` |
| L1 | **Item core**: `ItemDef`/`ItemStack`/`Category`/`Slot`/`Status`, inventory (stacking, weight → MOVEMENT speed), equipment slots, equip/unequip with curse lock, `EffectRegistry` stub | 2–2.5 d | Barony `items.hpp:552–700`, `canUnequip` :5873 |
| L2 | **Catalog authoring**: a script drafts `catalog.json` from Veloren (tier from material, value from quality×tier, weight by kind, `art` refs from manifests), then a hand pass. MVP ~40 items, full ~180–220 | 1 d MVP / +2–3 d full | `material_stats_manifest.ron`, `item_drop_manifest.ron`, `item_image_manifest.ron` |
| L3 | **Loot engine**: LootSpec evaluator (Veloren semantics), `CategoryCurve` (itemLevelCurve port), `Gold`, `Roll`. Seeded `SplittableRandom`, depth→tier, `LootSim` CLI (histograms per depth and source) | 1.5–2 d | `items.cpp:627–784, 350–566`; `lottery.rs` |
| L4 | **Barony layer**: source-specific status/beatitude rolls, stat effects (hook into COMBAT C2), unidentified naming + per-run look shuffle, appraisal timer, MVP scroll set (identify, remove curse, enchant, repair), potion effects tied to Veloren buffs (COMBAT C6) | 2–3 d | `maps.cpp:7602–7704`; `items.cpp:4601–4925, 5517–5560, 5957–6060`; `identify_and_appraise.cpp:656` |
| L5 | **World items + level spawn**: `WorldItem` mesh from drop manifest (skin-cell strip, offsets), drop arc, pickup, stack merge, gold coin/pouch. Barony floor-spawn pass over generator rooms (decor vs loot vs monster split, 10% gold), breakables rule | 2–3 d | `maps.cpp:3850–4760, 6140–6240, 7440–7790`; `load.rs:6100–6175` |
| L6 | **Chests**: 8 Barony themes as JSON, chest entity (open lid, contents UI hook), locked/lockpick/key, vault chest using Veloren `DungeonChest0..5` tables by tier, mimic flag | 1.5–2 d | `actchest.cpp:49–760`; `sprite/mod.rs:1056–1085`; TRAPS §3.7 |
| L7 | **Monster loadouts + drops**: `MonsterDef` with LoadoutSpec (Choice per slot), roll profile, gear echo for NPC-only weapons, Barony death drop + loot table + gold, miniboss tables | 2–2.5 d | `monster_goblin.cpp:26–230`; `stat_shared.cpp`; `actmonster.cpp:3690–3930`; `entity/dungeon/**`, `loadout/**` |
| L8 | **Humanoid armor on figures**: 8 slot manifests, species×bodyType head keys, `recolor_grey`, MatCell skin/hair/eye mapping with `humanoid_color_manifest`, bare-chest union, shoulder mirroring, mesh cache. Gloves on first-person hands | 2.5–3.5 d | `load.rs:117–130, 319–1120`; `common/src/figure/mod.rs:238–285`; `humanoid_armor_*_manifest.ron` |
| L9 | **Icon baker**: VoxTrans → ortho FBO → `TextureAtlas`, plus unidentified looks | 1–1.5 d | `item_image_manifest.ron` |
| L10 | **Inventory / chest / shop UI (minimal)**: grid with icons, tooltips (status, beatitude, identified), paper doll, drag to equip/drop, chest transfer, appraisal progress | 3–4 d | Barony `interface/*`; overlaps any future UI work |
| L11 | **Shops**: `ShopDef` × 10 types, shopkeeper humanoid (merchant loadout), buy/sell formulas, sell unidentified, optional identify service | 2–2.5 d | `monster_shopkeeper.cpp:250–970`; `items.cpp:5957–6060` |
| L12 | **Class kits + character-creation hook** | 0.5–1 d | `charclass.cpp:693–3690` |
| L13 | **Custom art** (§5 must-haves: book, quiver, shields, potion recolours, glass) | 1.5–2.5 d (+1.5–2 d optional) | |
| L14 | **Balance pass**: `LootSim` targets (items per floor ≈ Barony 10–20, gold curve, tier mix by depth), CSV + CI bands | 1 d | |
| **Total** | | **≈ 27–38 d** | |

**MVP (≈10–13 d):** L0 → L1 → L2 (40 items: 3 weapon kinds × 3 tiers, 3 armor sets, 4 potions, 4 scrolls, 4 rings/amulets, gold, food, lockpick, torch) → L3 → L5 → L6 (themes 0/2/4/7) → L7 (drops only, using existing `NpcFigure` visuals) → L4 status + beatitude + identify scroll only → a list-style L10.

Then L9 → L8 → full L4 → L11 → L12 → L13 → L14.

---

## 8. Open design decisions for you
1. **Armor slots**:
   - **(a) Barony's 9.** Breastpiece renders the matching Veloren shoulder, belt and pants automatically as a "suit".
   - **(b) Veloren's 8 armor slots.** More loot variety, but heavier inventory management.
   - Recommendation: (a), with a cosmetic set look per breastpiece.
2. **Uniform vs tier-weighted level curve.** Barony's uniform pick keeps low-tier junk common forever.
3. **Helmet protection.** Veloren helmets give 0, so pick a crawler value.
4. **Gold scale.** Barony's tens to hundreds vs Veloren's thousands. Recommendation: Barony scale, with Veloren chest coin ranges ×0.1.
5. **Spellbooks**: keep Barony's learn-a-spell books (needs the book model) or use Veloren charms as "tomes"?
6. **Co-op loot**: free-for-all (Barony) or Veloren-style damage-share ownership?
7. **Which Barony weapon types to cut** (crossbow, sling, whip, flail) vs author.
