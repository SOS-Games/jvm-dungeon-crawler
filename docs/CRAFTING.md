# CRAFTING.md: Barony crafting and action abilities for the player and smart party bots

Status: research and design only. No repo changes. Target repo `SOS-Games/jvm-dungeon-crawler` @6093f3a (libGDX 1.14, GPL-3.0).
Related docs: CLASSES.md (K9), LOOT.md (L4/L6/L10), ECS.md, ECS_ASHLEY_FORK.md, NPC_AI.md, TRAPS.md, SOUNDS.md, UI.md.
Artifacts: `crafting_research/stations.png`, `crafting_research/tools_ingredients.png`, `crafting_research/vox/*.vox` (29 real voxels, not LFS pointers), `crafting_research/vel/recipe_book_manifest.ron` + `recipe.rs`, and `crafting_research/sample/{alchemy.json,actions.json}` (draft data files).

## TL;DR

- Barony has **four menu crafts**. All four run through `GenericGUIMenu` in `src/interface/interface.cpp`:
  - alchemy (alembic),
  - tinkering (tinkering kit: craft, salvage, repair, upgrade),
  - scribing (enchanted feather),
  - cooking (cauldron or frying pan).
- It also has **world actions**: lockpicking (chests, doors, wall locks, bombs, bots/automatons, mimics), arrow-trap disarming, door/furniture bashing, digging, filling bottles at fountains/sinks, appraisal (a background timer), reading spellbooks, the Bard instrument aura, the Sapper booby trap, and deploying tinker gadgets.
- In Barony these are almost all **instant UI transactions or per-swing attempts**. None are timed channels.
  - For bots to look and feel like players, I propose **short timed actions** (1–3 s) that you can interrupt, run through one data-driven `ActionSystem`.
  - The player's UI and the bot brain both submit the same `ActionIntent` through the existing `Controller`/`InputFrame` path.
- All the formulas and recipe tables are in Barony **code**, which is BSD-2-Clause, so they can be reused if the copyright header is kept.
  - Item values (`items.json`), `appraisal_tables.json` and all language text are **closed data**. Re-derive or invent those.
- On the art side, Veloren covers stations well: cauldron, cooking pot, crafting bench, repair bench, salvaging station, anvil, forge, campfire/bonfire, fountain, bookshelf. It also covers potions, lockpicks, the mortar and pestle, bombs, the pickaxe, the hammer and many ingredients.
  - Gaps: portable alembic, frying pan, tinkering kit, quill/feather, blank scroll, metal/magic scrap, tinker bots, beartrap, towel, sink, Barony-style tech goggles.
- Estimate: **≈25 dev-days** total. Of that, ≈7 d overlaps CLASSES K9 and LOOT L4/L6/L10 and is counted once (see §6).
  - The MVP vertical slice ("the healer bot brews a healing potion, asks you for your juice, and tells the party the recipe") takes **≈9–10 d**.

---

## 1. Barony inventory: mechanics, checks, UI, code and closed data

Barony paths are relative to `/workspace/barony/src/`. Proficiency tier = `skill/20` (0–5). "Capstone" = skill 100 (`SKILL_LEVEL_LEGENDARY`). Unless noted, all randomness uses `local_rng`.

### 1.1 Alchemy (alembic). Brewer's core
- **UI flow:**
  - Apply the alembic to open the GenericGUIMenu in ALCHEMY mode.
  - Pick a base potion, then a secondary one; the "Brew" button runs `alchemyCombinePotions()` (interface.cpp:9505).
  - The menu also has an **experiment** mode, which lets you use unidentified potions. Normal brewing requires identified inputs.
  - Recipes you know show in a recipe list (`clientLearnedAlchemyRecipes`) and come from `alchemyAddRecipe` :9017.
  - Brewing is **instant**.
- **Ingredients:** `isItemMixable` :8388. Bases are listed at :10475 (water, booze, juice, acid, invisibility, polymorph). Secondaries are at :10493 (sickness, confusion, cure ailment, blindness, restore magic, speed, polymorph).
- **Matrix:** `alchemyMixResult` :8720. Order doesn't matter: if a combination fails it is retried flipped. The full table is in `crafting_research/sample/alchemy.json`.

  | Base \ secondary | sickness | confusion | cure ailment | blindness | restore magic | speed |
  |---|---|---|---|---|---|---|
  | booze | confusion | acid | speed | strength | blindness | paralysis |
  | juice | booze | booze | restore magic | cure ailment | **healing** | invisibility |
  | acid | firestorm | juice | firestorm | icestorm | icestorm | thunderstorm |
  | invisibility | blindness | paralysis | levitation | polymorph | **extra healing** | restore magic |

- **Special rules:**
  - Acid + water, or acid + anything that doesn't match, gives an **explosion** (`alchemyCookResult` :8572 / combine).
  - Any other non-match gives **sickness**.
  - A polymorph input gives a random potion.
  - Two potions of the same type merge.
  - **Water + X tries to duplicate X**, with a 50 + 10·tier % chance; on failure the result is water.
- **Explosion:**
  - You learn the TOOL_BOMB recipe.
  - A fireball burst spawns on the brewer (`spawnMagicTower`-style).
  - Hazard goggles protect you.
  - The GUI closes.
- **Results:**
  - Brewed potions are always EXCELLENT.
  - Beatitude: the result takes the least blessing or the most curse of the inputs. A blessed alembic adds +1. At capstone the result gets ±2 in the direction of its sign.
  - The empty bottle comes back with a 50 + 5·tier % chance.
- **Alembic wear:** it degrades a step on 100 % of explosions, 10 % of brews that use acid, and 2.5 % of other brews.
- **Skill gain:** always when you learn a new ingredient, otherwise 1 in 6.
- **Learning:**
  - `alchemyLearnRecipe` :10357 runs when you identify an ingredient by brewing.
  - `alchemyLearnRecipeOnLevelUp` :10513 grants juice and booze at 20, acid at 40, and invisibility and polymorph at 60. On top of that, every 5 skill points you learn a random potion.
- **Thrown potions** scale with an alchemy multiplier (`actthrown.cpp:1212`). Drinking returns the empty bottle 60–80 % of the time (`items.cpp` ~3663).
- **Water source:** apply an empty bottle to a fountain or sink (`item_tool.cpp:1080`).
- **Brewer class:** Barony has no Brewer-only code path. The class is Alchemy 50 plus a starting alembic and kit, plus the race.
- **Closed data:** potion names and descriptions (lang), and the appearance names and item values in `items.json`. The **per-run shuffled potion appearances** are code (BSD), but the colour/label strings are closed.

### 1.2 Cooking
- **Cauldron:**
  - It's a world entity (`actcampfire.cpp` `actCauldron` :176) used at TOUCHRANGE, **one user at a time**.
  - The workbench entity is `actWorkbench` :371 and uses the same pattern.
- **Frying pan:** the menu needs **4 torches** as fuel unless a lit campfire is within 32 units (~interface.cpp:20360).
- **Results:**
  - Towel + ration → grease balls.
  - Water + 4 rations → 4 slop balls.
  - 4 rations + seasoning → (1 + lvl) flavoured rations, where lvl = (Alchemy + PER)/20. At lvl ≥ 4, or with a chef hat equipped, they're EXCELLENT.
  - Any two foods totalling ≥ 200 satiation → rations.
- **Puke risk:** eating has a puke chance by item status (`Item::foodGetPukeChance`, items.cpp): EXCELLENT 0, SERVICEABLE 4 %, WORN 10 %, DECREPIT 25 %. This matters for bot food choices.
- **Closed data:** food names and satiation values (`items.json`).

### 1.3 Tinkering (tinkering kit). Machinist's core
- **UI:** a GenericGUIMenu with Craft, Salvage and Repair tabs, plus upgrade. Crafting is instant.
- **Proficiency:** tinkering uses **Lockpicking** proficiency + PER.
- **Craft list** :10549. The cost function is at :11403, and the tier gates at :12298 (tier×20 skill).

  | Tier (skill) | Items (metal / magic scrap) |
  |---|---|
  | 0 | lockpick 2/0, glasses 8/4, empty bottle 2/2, decoy 8/1 |
  | 1 (20) | beartrap 12/0, gyrobot 16/12, sleep bomb 4/8, freeze bomb 8/12, dummybot 8/4, lantern 8/4 |
  | 2 (40) | bomb 8/12, teleport bomb 4/8, sentrybot 16/8 |
  | 3 (60) | spellbot 8/16, tech goggles 10/40 |
  | 4 (80) | backpack 20/4 |

- **Bot quality:** a crafted bot's status comes from the skill margin over the item's gate.
- **Salvage** (:10735): yields from item value (:11477) plus a tier bonus. A crafted item can never salvage for more than it cost.
- **Kit wear:** 5 % chance per craft or repair.
- **Repair** (:12144):
  - Bots cost craft cost/4.
  - Repairing the kit itself costs 16 metal.
  - Other gear needs skill 60/80/100 by value tier and costs (salvage × 8) + |beatitude| × 4 magic.
  - There's an upgrade cap at :12818.
- **Deploy:** throwing a bot makes it a follower (`item_tool.cpp:2021`). Placing a beartrap is at `item_usage_funcs.cpp:4828`. Bombs are placed on doors, chests and walls, and the lockpick can switch their trigger mode.
- **Closed data:** item values (they drive salvage yields), names and descriptions.

### 1.4 Scribing (enchanted feather)
- **UI:** apply the feather, pick a **blank scroll**, then pick a label from the run-shuffled scroll labels (interface.cpp :12941–13400). Writing a scroll also **teaches that label**, i.e. identifies its type.
- **Charge cost per scroll type:**

  | Charges | Scroll types |
  |---|---|
  | 2 | mail |
  | 4 | light, fire, destroy armor |
  | 6 | summon, identify, remove curse |
  | 8 | food, teleport |
  | 12 | repair, magic mapping |
  | 16 | enchant weapon/armor, spellbook repair |

- **Cost scaling:** the cost is adjusted by tier = (Sorcery + INT)/20. A blessed feather counts as tier 5, a cursed one as tier 0.
- **Closed data:** the scroll label words (lang) and names.

### 1.5 Scroll and spell item effects (ItemEffect GUI)
- **ItemEffect GUI handlers:** identify :6530, remove curse :6453, enchant :7119, repair :7214; repairability is checked at :5605.
- **Spellbooks:** `item_Spellbook` (item_usage_funcs.cpp:5982) calls `addSpell` (magic/spell.cpp). You learn the spell if school proficiency + INT ≥ its difficulty. A cursed book makes you forget a spell instead.
- **Scope:** scroll effects are already in LOOT L4. Here they only appear as actions bots can choose.

### 1.6 Lockpicking, disarming and sabotage
- **Lockpick:** it's equipped as a weapon. Each swing is one attempt (`applyLockpick`, `item_tool.cpp:270`; swing at `acthudweapon.cpp:1552`).
- **Lock health:** chests 40 (`actchest.cpp:864`); doors 20 or 50 (`actdoor.cpp:56/:424`).
- **Per attempt:**
  - Lock damage = 3 + prof/20 + rand(prof/10).
  - Success if prof > rand % 200, at capstone, or when lock health reaches 0.
  - On failure the lockpick degrades with a 1/5 chance.
- **Other targets:** wall locks, bombs (toggle the trigger), and bots/automatons/mimics, which get disassembled for scrap.
- **Skeleton key:** opens locks outright (`item_tool.cpp` ~27–110).
- **Arrow-trap disarm** (`applyLockpickToWall`, `items.cpp:6133`):
  - skill = prof/10.
  - Fails automatically if skill < 2, or if rand % skill == 0. **A failure fires the trap.**
- **Sabotage spell:** `castSpell.cpp:4680`.
- **Sapper booby trap:** `castSpell.cpp:3330`. Class spells are at `castSpell.cpp:9480–9550`.

### 1.7 Doors, bashing, digging, search and stealth
- **No kick or search verb:** Barony has no dedicated kick and no "search" action. Doors and furniture are **bashed with ordinary attacks** (`entity.cpp` ~11944–12075).
  - Damage per hit = 1 + axe bonus.
  - Axe bonus = Axe prof/20 (capped at 9) with an axe. The same bonus applies to unarmed hits on doors.
  - Gremlins get +1, a gnome with a pickaxe gets +2–4, and the golem smash gets +20.
  - Wooden doors have 15–19 HP. Iron doors have 100 HP, are immune to magic, and normal hits don't hurt them.
- **Digging:** one pickaxe hit removes a diggable wall (`entity.cpp` ~16630).
- **Search:** hidden things are found passively by perception or skill checks.
- **Stealth:** sneaking is a movement state (DETECTION.md); there's no channelled "hide".

### 1.8 Appraisal and identification
- **Appraisal timer:** a background timer runs on one unidentified item at a time (`actplayer.cpp:8455–8600`).
  - The time comes from `getAppraisalTime` (`identify_and_appraise.cpp` ~:652), and `appraisalPossible` decides whether it can run at all.
  - The current timings come from the **closed `data/appraisal_tables.json`**. The old hard-coded thresholds are still in the code as comments; reuse them only as inspiration.
- **Identification:** also comes from the identify scroll or spell, and from selling or using the item.

### 1.9 Bard and Shaman
- **Bard:** holding block with an instrument in the off-hand starts an aura song. It drains a % of max MP per tick (`entity.cpp` ~5964). It's the only true **channel** in this set.
- **Shaman:** shapeshift and spirit spells only; no crafting.

### 1.10 Barony vs our design
| Barony | Here |
|---|---|
| Instant menu crafts | Short timed, interruptible actions that are the same for player and bot |
| One GUI menu struct per player | A UI-independent `CraftingQuery` API; the UI and the bot brain are both clients |
| Per-player recipe knowledge | Per-character `Knowledge`, shared with the party through banter (decision D2) |
| Shuffled appearances (code) | `RunTables` saved at floor transition |

---

## 2. Art: Veloren mapping and gaps (see the PNG sheets)

Sources are under `veloren_*/assets/voxygen/voxel/`. All are GPL-3.0 unless noted. I checked them against `credits.ron`. **The only CC-BY-NC-SA entries are:**
- glider `vroom.vox`,
- `sprite/mineral/ingot/*` (the 10 world ingot sprites),
- `sprite/wood/log/wood.vox`,
- GeekyGami music.

None of the picks below are NC.

| Need | Veloren asset | Fit |
|---|---|---|
| Cauldron (cooking/brewing station) | `sprite/crafting_station/cauldron` | ✅ |
| Cooking station | `crafting_station/cooking_pot`, `furniture/hearth`, `furniture/oven_arabic` | ✅ |
| Campfire (frying-pan fuel) | `sprite/camp/camp`, `furniture/bonfire_M_lit` | ✅ |
| Workbench / tinker station | `crafting_station/crafting_bench`, `repair_bench`, `salvaging_station`, `furniture/carpenter_table-0` + `carpenter_tools_wall` | ✅ |
| Smithing dressing | `anvil`, `forge`, `furniture/smith_table-0`, `quench-0`, `forge_tools`, `blacksmith_bellows` | ✅ |
| Fountain (fill bottles) | `furniture/fountain_arabic`, `barrel_wood_water`, `bucket_wood_M-0` | ✅ |
| Library / scribe dressing | `furniture/bookshelf_middle-0`, `misc/crystal_ball` | ✅ |
| Portable alembic | `item/.../mortar_pestle` (stand-in) | ⚠ needs a custom alembic for readability |
| Potions + empty bottle | `potion_empty`, `potion_red` + 5 consumable potions (recolour per shuffled appearance) | ✅ |
| Lockpicks | 3 lockpick item models | ✅ |
| Bomb | bomb item | ✅ (recolour for freeze/sleep/teleport) |
| Pickaxe | `weapon/tool/pickaxe_stone` | ✅ |
| Repair hammer | `weapon/hammer/craftsman` | ✅ |
| Ingredients / scrap | `ooze`, `embers`, `oil`, `resin`, `plant_fiber`; **magic scrap** → `item/mineral/ore/veloritefrag`; **metal scrap** → `item/mineral/ore/iron` (or `item/mineral/ingot/*`; see note) | ⚠ |
| Gadget cores | `dwarven_battery`, `golem_core` | ✅ as parts |
| Recipe/blank scroll | `recipe_basic`, `recipe_alchemy` | ⚠ stand-in for blank and written scrolls |
| Tinkering kit | `sewing_set` (stand-in) | ❌ a toolbox model is needed |
| Frying pan, quill/feather, towel, beartrap, sink | — | ❌ custom (small; ~0.5 d each) |
| Tinker bots (dummy/gyro/sentry/spell) | none fit | ❌ custom, or recolour small Veloren automaton/golem NPCs (check credits) |
| Tech/hazard goggles | — | ❌ custom (or reuse an existing glasses/head-armour mesh) |

- **Ingot note:** the *item* ingots under `item/mineral/ingot/` are not on the NC list. Only the *sprite* path is. They may still be ports of the NC sprites, so prefer ores to be safe.
- **UI:** Veloren `ui/crafting` has a 470×460 crafting window plus 16 category icons (UI.md:83), which work for the craft panels.
- **Sound:**
  - `sfx/crafting/hammer.ogg` (GPL), `liquid` (potion), `ambient/fire` (campfire), `explosion`/`fuse`.
  - Missing: lockpick clicks, bubbling, scribble, sizzle. These need CC0/CC-BY sources (SOUNDS.md:82–83).
- **Veloren's own crafting data is reference only:**
  - `recipe_book_manifest.ron` has 326 recipes: CraftingBench 132, Anvil 72, Loom 56, Cauldron 20, Forge 10, CookingPot 8, SpinningWheel 7, TanningRack 4.
  - It shows a clean input model: `Item | Tag | TagSameItem | ListSameItem` plus a `craft_sprite` station. Our schema borrows that. Its content is a survival-crafting economy that doesn't fit Barony and isn't needed.

---

## 3. Shared Action/Recipe system (ECS)

Goals:
1. The human and the bots go through **one code path**: the same validation, timings, failure odds and animations.
2. Data-driven JSON.
3. The sim is deterministic per seed and UI-free.
4. Nothing new is persisted mid-floor, because saves happen only at floor transitions (ECS.md).

### 3.1 Packages (new)
```
io.github.jvmdc.sim.action    ActionDef, ActionDefs (loader), ActionIntent, ActiveAction, ActionSystem,
                              InterruptRules, OutcomeResolver (+ resolvers/*), ActionEvent
io.github.jvmdc.sim.craft     RecipeDef, RecipeBook, CraftingQuery, InventoryTx, Station, Knowledge,
                              RunTables (shuffled appearances/labels), resolvers: Alchemy, Cooking, Tinker, Salvage, Repair, Scribe
io.github.jvmdc.sim.party     PartyNeeds (blackboard), PartyLedger (reservations), PartyKnowledge, PartyRequest
io.github.jvmdc.sim.ai.craft  CraftPlanner, ActionOption, UtilityScorer, SafetyPolicy, WorldActionAdvisor
io.github.jvmdc.ui.craft      AlchemyPanel, TinkerPanel, ScribePanel, CookPanel (Scene2D; only call CraftingQuery and push intents)
assets/data/actions.json, recipes/{alchemy,cooking,tinker,scribe}.json, items/*.json (crafting tags)
```

### 3.2 Data model
- **`ActionDef`** (draft: `crafting_research/sample/actions.json`). Fields:
  - `id`.
  - `kind`: `INSTANT | TIMED | ATTEMPT | CHANNEL | BACKGROUND`.
  - `target`: `NONE | ITEM | ITEM_PAIR | ITEM_SET | RECIPE | ENTITY | TILE_FACE`.
  - `requires`: tool tag, station kind and range, weapon or off-hand tag, fuel, min skill.
  - `durationSec`: an expression.
  - `interrupt[]`.
  - `consume`: `ON_START | ON_COMPLETE`. The default is ON_COMPLETE, with items reserved at start.
  - `resolver`: names a Java `OutcomeResolver`.
  - `noise`: feeds the DETECTION noise radius.
  - `anim`, `sfx`.
  - `bot{roles, minSkill, risk, needsSafeArea, warnParty}`.
- **`RecipeDef`:**
  - `id`, `station`/`tool`.
  - `inputs[]` of `{item | tag | sameItemOfTag, count, identified?}`.
  - `outputs[]`.
  - `gate{skill, attr, tier}`.
  - `quality` rule.
  - `sideOutputs` (e.g. an empty bottle with p%).
  - `failure` table.
  - `knowledge{startKnown, learnOn: [LEVEL, USE, IDENTIFY, BOOK]}`.
- **Alchemy** uses a pair matrix plus rules instead of one recipe per pair (`sample/alchemy.json`). The `AlchemyResolver` interprets it.
- **Items** get crafting tags: `alchemy.base|secondary`, `scrap.metal=N`, `scrap.magic=N`, `food.satiation`, `seasoning`, `fuel`, `hazard_goggles`, `lockpick`, `pickaxe`, `instrument`.
- **Expressions:** a tiny evaluator over `tier`, `skill`, `attr.PER`, `item.*` and `recipe.*`. Use exp4j (Apache-2.0) or a hand-written one (~150 LoC).

### 3.3 Components (stale-safe: long `EntityRef`s only)
- **`ActionQueue`:** one pending `ActionIntent` plus an optional repeat count ("brew ×3").
- **`ActiveAction`:**
  - `defId`, `recipeId`.
  - `targets` as `long` refs or item uids.
  - `startTick`, `endTick`.
  - `reservationId`, `seed`, `attemptsLeft`.
- **`Station`:** `kind`, `occupant: long`, `lit`, `useRange`. Barony allows one user per cauldron.
- **`Knowledge`:**
  - `BitSet` or `IntSet` for known recipes, ingredients, scroll labels and potion appearances.
  - `Set<String>` for learned "facts" (e.g. `acid+water=explode`).
- **`ToolWear`:** this is already the item status/durability field.
- **`BackgroundTask`:** the appraisal timer (one per character), which survives other actions.

### 3.4 Intent flow (identical for player and bot)
```
UI panel ── CraftingQuery.list(actor, context) ──> RecipeView[] {known, feasible, missing[], predicted{quality, successP, risk}}
   │                                                     ▲
   └─ on click: controller.submit(ActionIntent)          │ same API
BotBrain/CraftPlanner ── controller.submit(ActionIntent) ┘
InputFrame.action (one slot per frame) ─> ActionSystem (RESOLVE phase, 60 Hz)
```
- **`ActionIntent`:** `{actionId, recipeId?, targets[], repeat, experimental}`.
  - It rides in `InputFrame` next to movement and attack bits, so bots really are player mimics (ECS.md Controller).
  - `CraftingQuery` is pure: it never mutates, and the UI and the bots get identical `predicted` numbers. Bots don't get extra knowledge, because `predicted` only uses the actor's `Knowledge`.
- **ActionSystem steps:**
  1. **Validate:** range/LOS, station free, tool equipped, inputs present, knowledge (or the experimental flag), and not already acting. On failure, emit `ActionEvent.REJECTED(reason)`. The UI shows a toast and the bot drops the option.
  2. **Start:** `InventoryTx.reserve(inputs)` locks item uids against drop, give or other use. Set `Station.occupant`, add `ActiveAction`, set the `CharacterState.WORKING(anim)` pose, and post `STARTED` (sfx, bark hook).
  3. **Tick** — check interrupts:
     - `DAMAGE`: any hit ≥ 1 HP. Configurable: in Barony-like tuning, combat damage cancels.
     - `MOVE`: displacement > 0.25 tile, including knockback.
     - `ATTACK`/`BLOCK_RELEASED`.
     - `TARGET_LOST`: stale ref or out of range.
     - `STATION_LOST`, `STUN`, `MP_EMPTY`, `ITEM_LEFT_INVENTORY`.
     
     On interrupt, `InventoryTx.rollback()` runs and nothing is consumed. Post `INTERRUPTED(cause)`.
  4. **Complete:**
     - The resolver runs with a deterministic RNG `(runSeed, actorId, tick)`.
     - It returns `Outcome{itemOps[], effects[], skillXp[], learned[], toolWear, events[]}`.
     - `InventoryTx.commit(itemOps)` applies the ops atomically.
     - If inventory is full, outputs drop at the actor's feet (Barony behaviour).
     - Post `SUCCEEDED`, `FAILED` or `CRIT_FAILED`, plus `LEARNED(recipe)`, `TOOL_BROKE` and `EXPLODED`.
- **Kinds:**
  - **ATTEMPT** (lockpick, disarm, bash, dig) reuses the attack swing cadence. One swing = one resolver call. "Repeat until done" lives in the intent's `repeat`, which the human gets by holding the button.
  - **CHANNEL** (bard) pays upkeep every second.
  - **BACKGROUND** (appraisal) runs alongside other actions.
- **Proposed durations (D1):**

  | Action | Duration |
  |---|---|
  | brew | 1.5 s |
  | cook | 3 s |
  | tinker | 1 s + 0.75 s·tier |
  | salvage | 0.75 s |
  | repair | 2 s |
  | scribe | 2 s |
  | read spellbook | 2 s |
  | fill bottle | 0.5 s |

  Lockpick, bash and dig take one swing each. Appraisal keeps Barony's background model with our own time table.
- **Failures and side effects (resolver output):**
  - sickness potion; explosion (AoE fire, friendly fire included, goggles negate);
  - duplicate fail → water;
  - alembic/kit/feather wear or breakage;
  - lockpick wear (1/5 on fail);
  - trap fires on a failed disarm;
  - noise events for bashing, digging and explosions (wakes monsters via DETECTION);
  - puke chance when eating.
- **Floor-transfer save:**
  - `ActiveAction` is dropped on transfer: reservations are released and no items are lost.
  - `Knowledge`, `RunTables` and the tool status are saved.
  - The TimerWheel and EventQueue aren't saved (already decided).

### 3.5 Bot decision-making (`sim.ai.craft`, runs inside BotBrain)

Cadence:
- `CraftPlanner` re-plans at **1 Hz**, staggered, but only when `NPC_AI` says the party is **out of combat**: no hostile is aware of the party within R, and none has been for 5 s.
- The ActionLayer still executes at 10 Hz.
- During combat, only "use" options run (quaff a known potion, throw a bomb), and those go through the normal combat utility.

**Options.** Each `ActionOption` is `{intent, utility, cost, reservations[], needsAsk[], plan[≤3 steps]}`. Generators:
1. **Restock:** brew the party's target potions (healing / extra healing / cure ailment by default), water-duplicate known good potions, and cook rations when food stock is below N days.
2. **Maintain:** repair gear below WORN (Machinist), repair tinker bots, and salvage junk the party ledger has marked "nobody wants".
3. **Knowledge:** appraise the oldest unidentified item, read unread spellbooks if the INT/prof check passes, and write scrolls (casters with a feather).
4. **Access:** pick known locked chests and doors, disarm known arrow traps on the planned path, bash doors only if allowed, and dig shortcuts when the leader pings.
5. **Experiment** (gated by personality): mix two unidentified potions.

**Utility.**
```
U = need(partyNeeds) × feasibility(successP) × roleAffinity(class) × personalityMod
    − timeCost(sec × urgency) − riskCost(expectedHarm × caution) − resourceCost(value of reserved items to others)
```
- **`PartyNeeds` blackboard** (recomputed when inventory or party state changes):
  - potions per member vs targets, food days, gear condition, unidentified backlog;
  - known locked containers and known traps on route;
  - scrap stock and MP.
- **Role affinity** comes from `ClassDef.bot.crafting` (new field):

  | Class | Crafting roles |
  |---|---|
  | Brewer, Healer | ALCHEMIST |
  | Machinist | TINKERER (+ lockpicks) |
  | Rogue | LOCKS, TRAPS |
  | Merchant, Bard | APPRAISER |
  | Wizard, Scion, Conjurer | SCRIBE, READER |
  | Barbarian, Warrior | BASHER |
  | Anyone with a pan | COOK |

  A role isn't exclusive. It multiplies U by 1.5–2.
- **Lock choice:**
  - `WorldActionAdvisor` compares a key (always) with lockpicking.
  - Lockpicking expected swings ≈ min(Geometric(prof/200), lockHP/(3 + prof/20 + prof/20)), plus expected lockpick loss.
  - The alternative is bashing: hits = HP/(1 + axeBonus), with noise.
  - If a mimic is suspected (appraiser perception roll), it adds "stand back" or "bash first".

**Inventory planning.** This is a backward chain of depth ≤ 2 over **known** recipes only. Example goal: "healing potion".
- Recipe: juice + restore magic.
- Missing juice → juice = acid + confusion (known?) → else ask the party.
- Steps are `GATHER(item from party)`, `FILL(bottle at a known fountain)`, `CRAFT(recipe)`.
- It never plans through unknown results unless the Experiment generator is allowed.

**Party resource reservation (`PartyLedger`).**
- A reservation is `{itemUid, holder, purpose, ttl=30 s}`. It's shared with the loot "dibs" system (LOOT.md), so two bots never plan with the same juice, and it's released on interrupt or replan.
- Bots treat items held by other **bots** as negotiable:
  - A `GiveItem` intent goes to the owner.
  - The owner's planner accepts if the item isn't in its own top plan, or if the requester's U is higher.
  - Either way it's a visible hand-over (throw/drop as in Barony, or a direct give in range).

**Asking the player (`PartyRequest`).**
- Any plan that needs the player's items, a station the player is standing at, or the player to **wait** creates a request.
- The request shows as a chat bubble + log line through `DialogueSystem`, using our own text: e.g. "Can I have your 2 bottles of juice? I'll brew healing." / "Give me 5 seconds at the cauldron."
- **Quick reply:**
  - Default keys `Y`/`N`, or a click on the bubble/log entry.
  - The radial "Yes / No / Later / Never ask for this" options take the place of typing.
  - Timeout 10 s means "no".
  - "Never" writes a per-bot, per-purpose suppression.
- **Rate limit:** 1 open request per bot, at most 1 every 30 s per party. Requests are blocked while the player is in combat or a menu.
- A "yes" makes the player character auto-give, or opens the give UI with the item pre-selected (D4).

**Leader following vs crafting.**
- If the leader moves beyond the regroup radius, the timed action is **interrupted**, the bot follows, and it re-plans later. A stubborn personality may first bark "Wait!" once.
- The bot starts long actions only when the leader is idle, in a menu, or also crafting.

**Potion safety by personality (`SafetyPolicy`).**

Traits used: `caution`, `curiosity`, `greed`, `loyalty` (from the Personality component).
- **Drinking unknown potions:**
  - Never, if caution is high.
  - Otherwise only when (a) it's an emergency (HP < 25 %, no known heal, no healer in range) and recklessness exceeds a threshold, or (b) the party is safe, HP is full, curiosity is high, **and** the player approved via request ("Mind if I taste-test this?").
  - Bots prefer the identify/appraise route, or throwing unknown potions at enemies.
- **Experimental brewing:**
  - Only when safe; the bot first walks ≥ 3 tiles from allies (explosion radius) and barks "Stand back".
  - It never mixes acid with an unknown, once the bot knows the explode fact.
  - Hazard goggles raise willingness.
  - It needs curiosity ≥ T and inputs flagged as surplus by the ledger.
- **Gear:** never equip unidentified gear (curse risk) unless greed ≥ T and there's no appraiser in the party.
- **Disarming:** don't attempt if skill < 20 (auto-fail). Otherwise bark "Clear the line!" and wait until no ally stands in the trap's fire lane (from `KnownHazards`, TRAPS.md).
- **Eating:** prefer EXCELLENT food. Avoid DECREPIT unless starving.

**Banter hooks** (DialogueSystem events; text authored by us; reuse ReactionQueue cooldowns):
- `CRAFT_START{kind}`, `CRAFT_SUCCESS{item}`, `CRAFT_FAIL`, `EXPLODED{byWhom}`, `LEARNED_RECIPE{recipe}`.
- `TOOL_BROKE`, `LOCK_OPENED`, `LOCK_JAMMED`, `TRAP_DISARMED`, `TRAP_TRIGGERED{victim}`, `DOOR_BASHED`.
- `REQUEST_ASK/ACCEPTED/DECLINED`, `TASTE_TEST{result}`, `ATE_BAD_FOOD`, `APPRAISED{item, value}`.
- **Cross-bot reactions:** e.g. a cautious bot scolds after `EXPLODED`, and the greedy bot comments on `APPRAISED` value.

**Sharing known recipes.**
- When an actor gains `LEARNED_RECIPE`, the bot barks it ("Juice and speed make invisibility!"). After 2–5 s, `PartyKnowledge.share()` copies the fact into every party member's `Knowledge` that heard it (in range/LOS).
- The player gets a journal entry.
- The player can also "teach" from the journal: an action that costs a few seconds in proximity.
- This keeps per-character data (D2) but feels like shared play.

**Determinism and test hooks.**
- The planner only reads the components and `CraftingQuery`, so it can be unit-tested headless.
- Each option logs its score breakdown to the debug overlay (NPC_AI debug view).

---

## 4. Licensing notes

- **Formulas:** transcribing Barony's formulas and matrix into our JSON/Java is BSD-2-Clause code reuse.
  - Keep the Turning Wheel LLC copyright and licence text: in `NOTICE` and in the data file header (`_license` field, as in `sample/alchemy.json`).
  - BSD → GPL-3.0 is compatible.
- **Don't copy:**
  - `items.json` values and weights, `appraisal_tables.json`, `lang/*` strings (item names, scroll labels, potion appearance words, messages);
  - Barony models, sprites and sounds.
  - Write our own names and barks.
- **Veloren assets:** GPL-3.0, with the authors kept in `ASSET_CREDITS`. Exclude the CC-BY-NC-SA list in §2.
- **CC-BY sounds:** need attribution (SOUNDS.md).

## 5. Integration points in existing plans
- **CLASSES K9:**
  - Alchemy (2–3 d) and Machinist (3–4 d) are now implemented **inside** C3/C5 below (crafting front-end).
  - Gadget behaviours (bot followers, bomb triggers, beartraps, booby trap) stay in K9, minus ≈1.5 d already covered by the shared action core.
- **LOOT:**
  - L4 (identify, appraise, scrolls) supplies the effects; C6 and the appraisal `BACKGROUND` kind only wrap them.
  - L6 (chests, locks) supplies the lock entities; C7 supplies the pick/bash resolvers.
  - L10 (UI) gets the craft panels in C9.
- **NPC_AI:**
  - `ActionLayer` gets a `CRAFTING` mode.
  - The rule list adds "out-of-combat craft opportunity" and "regroup interrupts crafting".
- **DETECTION:** the `noise` field drives noise radius.
- **SOUNDS:** new `SfxEvent` keys: `Craft.Bubble`, `Craft.Hammer`, `Craft.Scribble`, `Craft.Sizzle`, `Lock.Click`, `Lock.Open`, `Lock.Break`.

## 6. Prioritized plan

| ID | Work | Files (new unless noted) | Days | Notes |
|---|---|---|---|---|
| **C1** | Action core: `ActionDef` loader, expression eval, `ActionIntent` in `InputFrame`, `ActionSystem` (TIMED/ATTEMPT/CHANNEL/BACKGROUND), `InterruptRules`, `InventoryTx`, `ActionEvent` | `sim/action/*`, `sim/comp/{ActionQueue,ActiveAction}.java`, edit `sim/input/InputFrame.java` (ECS.md) | 3 | Foundation for everything |
| **C2** | `RecipeDef`/`RecipeBook`, `Knowledge`, `RunTables` (shuffles, saved at transfer), `CraftingQuery` pure API, item crafting tags | `sim/craft/*`, `assets/data/recipes/*.json` | 1.5 | |
| **C3** | Alchemy resolver + alembic + fountain fill + explosion | `sim/craft/AlchemyResolver.java`, `recipes/alchemy.json` | 2 | Replaces K9 alchemy estimate |
| **C10a** | Bot planner MVP: `PartyNeeds`, `PartyLedger`, `CraftPlanner` (restock + identify), `SafetyPolicy` | `sim/party/*`, `sim/ai/craft/*` | 2 | |
| **C11a** | `PartyRequest` + quick-reply bubbles + `LEARNED_RECIPE` share + core banter hooks | `sim/dialogue/*` edits, `ui/hud/RequestBubble.java` | 1 | |
| **C9a** | Alchemy panel (Scene2D) over `CraftingQuery` | `ui/craft/AlchemyPanel.java` | 1 | Extends L10 |
| — | **MVP slice = C1+C2+C3+C10a+C11a+C9a ≈ 10.5 d** (≈9.5 d net of K9 overlap) | | | |
| **C4** | Cooking: cauldron/campfire `Station`, frying pan, food quality and puke | `CookingResolver`, `recipes/cooking.json` | 1 | |
| **C5** | Tinker craft, salvage, repair, upgrade; kit wear | `TinkerResolver`, `SalvageResolver`, `RepairResolver`, `recipes/tinker.json` | 2 | Gadget behaviour stays in K9 |
| **C6** | Scribing + spellbook reading (+ ItemEffect wrappers) | `ScribeResolver`, `LearnSpellResolver` | 1 | Effects in L4 |
| **C7** | World actions: lockpick, disarm, bash, dig, skeleton key | `resolvers/{Lockpick,Disarm,Bash,Dig}Resolver.java` | 1.5 | Lock entities in L6 |
| **C8** | Bard channel aura (+ upkeep) | `BardAuraResolver` | 0.5 | |
| **C9b** | Tinker, scribe and cook panels | `ui/craft/*` | 1.5 | |
| **C10b** | Planner: maintain/access generators, `WorldActionAdvisor`, inventory backward chain, bot↔bot `GiveItem` negotiation, experiment mode | `sim/ai/craft/*` | 2 | |
| **C11b** | Full banter set, journal + teach action, "never ask" suppressions | | 0.5 | |
| **C12** | Art: import stations/tools (converter pipeline), custom alembic, pan, toolbox, quill, blank scroll, scrap, beartrap, bots; sfx gaps | `assets/`, `ASSET_CREDITS` | 3 | Bots could reuse K9 art |
| **C13** | Tests: golden recipe tests (matrix vs Barony), resolver odds Monte Carlo, headless bot scenarios ("brews healing when low", "never mixes acid+unknown", "asks then waits") | `core/src/test/...` | 1.5 | |
| | **Total ≈ 25 d (≈ 21.5 d net of K9/L-plan overlap)** | | | |

Order: MVP → C7 (Rogue/locks are very visible) → C5 → C4 → C6 → C10b/C11b → C8 → art polish C12 in parallel.

## 7. Decisions for the user

1. **D1 Timed vs instant.** Barony is instant. I recommend short timed actions (above) for everyone, so bot crafting is visible and interruptible and the player gets no unfair advantage. Alternative: instant for both.
2. **D2 Knowledge.** Per-character knowledge plus spoken sharing (recommended), fully party-shared, or per-character with explicit teach only.
3. **D3 Pause.** Should crafting menus pause the singleplayer sim? Barony doesn't. I recommend no pause, with an optional "slow time in menus" setting.
4. **D4 Bot autonomy over items.** A global setting: bots may craft from their *own* items automatically (recommended), *ask* for anyone else's, or never touch the player's without a "yes".
5. **D5 Unknown potions.** Allow reckless or curious bots to taste-test, with permission when safe and without permission in emergencies (recommended), or ban it.
6. **D6 Experimental alchemy by bots.** Off, ask-first (recommended), or allowed for curious personalities.
7. **D7 Balance.** Keep Barony's numbers verbatim (BSD) for v1 (recommended), or retune now.
8. **D8 Digging.** One hit per tile as in Barony (recommended), or multi-hit on the voxel grid.
9. **D9 Primitive recruits.** Confirm they get none of this: no crafting, only the existing primitive behaviours.
10. **D10 Art gaps.** Make custom voxels (≈3 d) or ship with stand-ins first (mortar_pestle as alembic, sewing_set as kit, recipe scroll as blank scroll).
11. **D11 Door bashing by bots.** It makes noise and wakes monsters. Allow it automatically, ask first (recommended), or only when the leader attacks the door.
