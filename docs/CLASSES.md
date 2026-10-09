# Barony classes → Veloren assets → crawler class system

How Barony's 26 classes (and 18 races) work, how each one can be dressed and animated with Veloren's GPL-3.0 assets, and a data-driven class system for jvm-dungeon-crawler, including how the party bots pick and play classes. **This is research only. No repo changes were made.**

**Sources**
- Barony @ `962a5ce` (`/workspace/barony`, BSD-2-Clause code):
  - `src/charclass.cpp` (`initClassStats` :32–691, `initClass` :693–3692, race extras :3357–3530);
  - `src/entity.cpp` (`ClassBaseGrowths` :85–129);
  - `src/monster.hpp:566–592` (`classStatGrowth`);
  - `src/menu.cpp:476–720` (`isCharacterValidFromDLC`);
  - `src/stat.hpp:172–191` (proficiencies).
- Veloren master @ `ebfff94` (`/workspace/veloren_classes`, a sparse clone). The voxels were pulled from GitLab raw on 2026-10-09 at about 12:07 PM ET.
- Earlier reports: COMBAT.md, LOOT.md, MOVEMENT.md, NPC_AI.md, ECS.md.

**Artifacts**

| Path | What |
|---|---|
| `/workspace/class_renders/classes_base10.png`, `classes_dlc1_2.png`, `classes_dlc3.png` | Proposed Veloren look for **all 26 classes**: figure, weapons, the look note, and the race stand-in |
| `/workspace/class_renders/mvp_roster.png` | The recommended MVP six |
| `/workspace/class_renders/species_linen.png` | The 6 Veloren humanoid species × 2 body types in linen starter clothes, labelled with the Barony races each could stand in for |
| `/workspace/class_tools/barony_classes_reference.csv` | Extracted from BSD code by `extract_barony.py`: stat mods, HP/MP, gold, proficiencies, growths, level-up weights, extra spells, paired race and kit item enums for all 26 classes |
| `/workspace/class_tools/{ron.py, figure.py, looks.py, sheet.py}` | RON parser, humanoid assembler/renderer (Veloren head, armor and colour manifests, `recolor_grey`, hollow/override cells, stand-pose bone offsets), class look table, sheet maker |
| `/workspace/class_tools/classes.sample.json` | A worked `ClassDef` example (Warrior, Rogue, Wizard) |

---

## TL;DR

1. **Every mechanical part of a Barony class is code, so it's BSD and reusable.** That covers attribute mods, HP/MP, gold, proficiencies, starting kits (item enums), extra spells, per-level growth, level-up weights, DLC/race gating, and race traits. **Only names, descriptions and flavour text are closed** (`lang/*.txt`), along with item stat tables (`items.json`, see LOOT.md). Write our own class names, blurbs, item names and numbers for items.
2. **26 classes.** They are 13 base classes (Barbarian–Monk; Sexton/Ninja/Monk are unlockable), 4 DLC1 classes (Conjurer, Accursed, Mesmer, Brewer), 4 DLC2 classes (Machinist, Punisher, Shaman, Hunter) and 5 DLC3 classes (Bard, Sapper, Scion, Hermit, Paladin).
   - Each "legendary" DLC class is paired with a race.
   - Barony races are mostly monster bodies. Rat, Spider, Troll and Imp are **Shaman shapeshift forms**, not playable races.
3. **Veloren can dress roughly 20 of the 26 classes convincingly today**, with existing armor sets and weapons. See the renders.
   - **Gaps**:
     - spear, dagger and unarmed attack animations for humanoids;
     - crossbow, sling, whip, flail, chakram, boomerang, bombs and gadgets;
     - jester hat, fez, plumed cap;
     - a head-manifest entry for the unused assassin mask;
     - Machinist bots and spell foci (the Hermit's duck is covered: Veloren has a Duck body);
     - an imp body.
4. **Races:** use Veloren's six humanoid species (Human, Elf, Dwarf, Orc, Danari, Draugr) as **cosmetic + light-trait "lineages"**. Use Veloren non-humanoid bodies only for shapeshift forms, summons and enemies. Barony's own approach is the same: `stat_appearance` lets any race be purely cosmetic.
5. **Recommended MVP roster (6): Warrior, Rogue, Wizard, Healer (Cleric-flavoured), Hunter, Barbarian.**
   - It covers tank, dps, healer, ranged, caster and utility.
   - Every member uses an ability set that already has humanoid animations (sword, axe, bow, staff, sceptre, shield).
   - The only art gap is the Warrior spear. Use the sword-defensive stance until a spear arrives.
6. **Effort:**
   - **Class system MVP: ≈5–7 dev-days** on top of LOOT L1–L4/L8, COMBAT C2/C10/C11, MOVEMENT H1/H2 and ECS E5.
   - **All 26 classes: ≈25–35 more dev-days**, driven by the special mechanics (summons, shapeshift, bots, instruments, brewing) plus ≈6–9 days of custom voxel art.

---

## 1. Barony classes

### 1.1 Base numbers (code)
- A player starts with **HP 30 and MP 30** (`DEFAULT_HP/MP`, `game.hpp:367`), all six attributes at 0, then adds the class modifiers below.
- Proficiencies run from 0 to 100, with tier names at 20/40/60/80/100. There are 16 of them:
  - Lockpicking, Stealth, Trading, Appraisal, Thaumaturgy, Leadership, Mysticism, Sorcery, Ranged, Sword, Mace, Axe, Polearm, Shield, Unarmed, Alchemy.
  - Magic was reworked into three schools: **Sorcery** (damage), **Mysticism** (control/summon/charm) and **Thaumaturgy** (healing/support).
- **Level up** (`entity.cpp:~4065`): each level spends points on attributes, picked by weighted random using the class's `classStatGrowth` row. HP/MP rise by `ClassBaseGrowths` plus a per-stat bonus (HP from STR 1/DEX 2/CON 2; MP from INT 2/PER 2/CHR 1).
- **Alchemy proficiency teaches recipes:** 0 → water, 20 → juice and booze, 40 → acid, 60 → invisibility and polymorph.

### 1.2 Class table (all code-derived)
Attribute order is STR DEX CON INT PER CHR. HP and MP are deltas from 30. "Growth" is HP/MP per level, then HP regen and MP regen. "Weights" are the level-up weights. The full kits are in the CSV.

| # | Class | Pack (paired race) | Attributes | HP/MP/Gold | Proficiencies | Growth | Weights | Kit essentials → playstyle |
|---|---|---|---|---|---|---|---|---|
| 0 | **Barbarian** | base | +2 0 +1 −2 0 −1 | +10/−10 | Axe 50, Shield 25, Mace 25, Unarmed 20, Alch 10 | 4/2/1/1 | 6 5 5 2 4 2 | Iron axe, wooden shield, leather helm, ring of strength, 2 throwing tomahawks, booze, torches. **Bruiser**: big HP, axe and shield, no magic. |
| 1 | **Warrior** | base | +1 +1 0 −2 −1 +1 | 0/−10 | Polearm 50, Leadership 40, Sword/Shield/Ranged 25, Unarmed 10 | 4/2/1/1 | 7 2 6 1 2 6 | Iron spear, bronze shield, iron breastpiece, helm, boots. **Tank/leader**: best armour start, recruits followers easily. |
| 2 | **Healer** | base | −1 −1 +2 +1 0 0 | −10/+10 | Thaum 50, Alch 30, Myst 25, Polearm 25, Appr/Shield 10 | 3/5/1/4 | 3 3 4 6 5 3 | Quarterstaff, healer doublet, hat, cloak, 3 extra-healing potions, heal + cure-ailment books, slow staff. **Support**: heals, cures, weak in melee. |
| 3 | **Rogue** | base | −1 +2 0 −1 +2 −1 | −5/−10 | Stealth 50, Lockpick 40, Appr/Ranged/Sword 25, Alch 20 | 2/3/5/3 | 2 7 1 2 7 5 | Excellent bronze sword, shortbow + 15 knockback arrows, hood of whispers, 5 sickness potions (blade poison), decoy, lockpicks. **Stealth striker**: backstabs, locks. |
| 4 | **Wanderer** | base | 0 +1 +1 −1 +1 −1 | +10/−10 | Alch 30, Stealth/Polearm/Ranged/Trading 25, Unarmed 10 | 4/3/4/3 | 5 4 5 3 5 2 | Quarterstaff, sling, lantern, ring of warning, lots of food, 6 magic-mapping scrolls, detect-food book. **Survivalist/explorer**. |
| 5 | **Cleric** | base | 0 −1 +1 0 +1 0 | 0/0 | Thaum 40, Mace 25, Leadership/Alch 20, Shield 10 | 3/4/3/4 | 4 2 5 5 4 4 | Wooden shield, leather breastpiece, cloak, 2 healing potions, 3 summon scrolls, troll's-blood book. **Battle priest**: mace, shield, regen magic. |
| 6 | **Merchant** | base | 0 −1 0 0 +1 +1 | 0/−10/+1000 g | Appraisal 50, Trading 50, Axe 25, Leadership 20, Alch/Lockpick 10 | 3/3/3/3 | 3 2 4 3 5 7 | Bronze axe, pickaxe, monocle, remove-curse + identify scrolls, 1000 gold. **Economy**: buy power, identify loot, dig. |
| 7 | **Wizard** | base | 0 −1 0 +3 +1 −1 | −10/+20 | Sorcery 50, Polearm 25, Thaum 15, Alch/Appr 10 | 2/5/2/5 | 1 3 2 7 6 5 | Quarterstaff, wizard hat and doublet, cloak of protection, magic-reflection amulet, light staff, fireball/cold/light books. **Glass-cannon caster**. |
| 8 | **Arcanist** | base | −1 +1 −1 +1 +1 −1 | −5/+10 | Sorcery 30, Stealth/Lockpick/Ranged 25 | 2/4/3/4 | 2 6 2 6 6 2 | Crossbow + 15 fire bolts, fire + opening staves, forcebolt book, charging scrolls; class spell *windgate*. **Spell-gunner hybrid**. |
| 9 | **Joker** | base | −1 0 −1 +1 0 +1 | 0/0/+200 g | Lockpick/Trading/Myst 25, Leadership/Ranged 20, Sorc/Alch/Stealth 10 | 3/3/3/3 | 4 4 4 4 4 4 | Sling, 8 cream pies, teleport scrolls, polymorph potion, levitation ring, confuse book, luck gem. **Chaos/trickster**, balanced growth. |
| 10 | **Sexton** | base (unlock) | −1 +1 −1 +1 0 0 | 0/+5 | Stealth 40, Myst 40, Ranged/Alch 20, Mace/Shield 10 | 3/4/2/4 | 3 4 2 4 2 3 | Bronze mace, 3 steel chakrams, fez, crystal shards, sleep/opening/locking books. **Stealthy control caster**. |
| 11 | **Ninja** | base (unlock) | 0 +2 −1 −2 +1 0 | +5/−10 | Stealth 60, Sword 60, Ranged 40 | 2/1/1/1 | 4 6 3 2 4 1 | Crystal sword (decrepit), assassin hood + bandit mask, speed boots, black cloak, 5 throwing daggers, bear traps, paralysis + invisibility potions. **Assassin**: no magic. |
| 12 | **Monk** | base (unlock) | +1 0 +2 0 −1 0 | +10/0 | Unarmed 50, Shield 40, Thaum 25, Alch 20, Leadership 10 | 4/2/2/2 | 4 2 5 3 2 2 | Brass knuckles, tunic, slow-digestion ring, light book. **Unarmed fighter**: CON-heavy, ascetic. |
| 13 | **Conjurer** | DLC1 (Skeleton) | 0 −1 +2 +1 −2 0 | 0/+15 | Myst 40, Leadership 40, Stealth/Ranged/Alch 20, Thaum 15 | 4/3/2/3 | 3 2 4 6 4 4 | Lightning staff, lantern, poison book; class spell *summon*. **Summoner**. |
| 14 | **Accursed** | DLC1 (Vampire) | −2 −3 −2 +5 −1 0 | 0/+10 | Sorcery 60, Stealth 40, Unarmed 40, Myst/Appr 20 | 2/4/1/4 | 3 3 1 6 6 3 | Silver doublet, suede gloves and boots, invisibility book, 3 blood vials. **Permanent vampiric curse** (`castSpell.cpp:139`, `actplayer.cpp:8237/15808`): life-drain, hunger for blood. |
| 15 | **Mesmer** | DLC1 (Succubus) | −2 −2 −3 +2 +2 +4 | −5/+10 | Myst 60, Leadership 60, Sorc 25, Polearm 20 | 2/4/2/5 | 3 3 1 6 4 7 | Cold staff, silver cloak, confusion potions, charm-monster book; class spell *command*. **Charmer**: turns enemies into followers. |
| 16 | **Brewer** | DLC1 (Goatman) | −2 +1 −2 −2 +1 +1 | +10/−10/=100 g | Alch 50, Unarmed/Leadership 25, Axe/Trading/Appr 10 | 3/2/5/3 | 4 5 5 2 3 5 | Iron axe + iron mace, backpack, alembic, empty bottles, about 13 potions (firestorm, acid, blindness, booze, speed, polymorph). **Potion thrower/crafter**. |
| 17 | **Machinist** | DLC2 (Automaton) | −2 0 −3 +1 0 0 | −5/−10 | Lockpick 40, Ranged/Alch/Trading 10 | 3/1/4/2 | 2 5 2 4 7 4 | Crossbow, tinkering kit, apron, dummybot + sentrybot, bear traps, sleep bomb, 16 metal + 8 magic scrap; food only if not Automaton. **Gadgeteer/turret builder**. |
| 18 | **Punisher** | DLC2 (Incubus) | −1 +1 −1 −1 0 0 | 0/0 | Ranged/Axe 25, Sorc/Myst 20 | 4/3/4/3 | 4 3 2 3 4 4 | Whip, crystal battleaxe, punisher hood, ring of conflict; class spells *telepull* and *demon illusion*. **Reach/pull bruiser**. |
| 19 | **Shaman** | DLC2 (Goblin) | −1 0 0 +2 +1 +1 | +5/+10 | Myst 40, Unarmed/Alch/Stealth 10 | 3/3/3/3 | 4 4 4 4 4 4 | Poison staff, shaman mask, bronze sword, rocks, enchanted feather. Spells: **rat, spider, troll and imp forms**, revert, and about 10 form-specific spells. **Shapeshifter**. Forms use their own level-up weights (Rat 1 6 1 3 1 1, Spider 1 1 3 1 6 1, Troll 6 1 3 1 1 1, Imp 1 1 1 3 1 6). |
| 20 | **Hunter** | DLC2 (Insectoid) | −3 +1 −1 −3 +3 0 | −10/−10 | Ranged 50, Stealth 25, Appr 20, Lockpick 10 | 2/2/5/2 | 1 7 1 4 7 4 | Longbow, boomerang, 100 arrows (silver, lightweight, hunting), bracers, poison-resist amulet, telepathy blindfold, speed potions. **Pure archer**. |
| 21 | **Bard** | DLC3 (Gnome) | −2 +1 −2 0 0 +3 | −5/+5/=200 g | Appraisal 50, Sword 25, Myst/Leadership/Stealth/Trading 15 | 2/4/4/4 | 2 5 3 4 3 7 | Rapier, plumed cap, bandit breastpiece, loafers, instrument; class spell *alter instrument*. **Song buffer**. |
| 22 | **Sapper** | DLC3 (Gremlin) | +1 +1 −3 −2 0 −3 | 0/−5 | Mace 50, Stealth/Alch 25, Sorc/Ranged 15 | 3/2/3/2 | 5 5 5 4 3 2 | Steel flail, frying pan, bolas, eyepatch, burn-resist amulet; class spell *booby trap*. **Trapper/demolitions**. |
| 23 | **Scion** | DLC3 (Dryad) | −2 −2 0 +3 +1 −1 | −10/+20/=250 g | Sorc/Myst/Thaum 40, Alch/Appr 10 | 2/4/3/4 | 2 3 2 8 4 5 | Sceptre staff, dark-rift focus, circlet, wizard robe, silver keys; spells *earth elemental*, *telekinesis*, *bless food*. **Noble all-school mage**. |
| 24 | **Hermit** | DLC3 (Myconid) | −2 −2 +2 +1 0 +1 | 0/0 | Myst 40, Mace/Stealth 25, Sorc 15, Alch 10 | 3/4/3/3 | 4 2 3 6 3 5 | Shillelagh, **duck companion**, pipe mask, cloak of protection, water-breathing amulet; spells *magician's armor*, *project spirit*, *deep shade*. **Odd mystic** with the "ducks in a row" mechanic. |
| 25 | **Paladin** | DLC3 (Salamander) | +1 −1 0 +1 −1 +1 | 0/−5 | Sword 50, Thaum/Appr 25, Shield/Leadership 10 | 3/3/3/3 | 6 3 5 3 2 5 | Claymore, chain coif + hauberk, light-sanctuary focus, 4 blessed water; class spell *divine zeal*. **Holy two-hander**. |

**Notes**
- `stat->GOLD` is additive (+1000, +200) for Merchant and Joker, and set with `=` for Brewer, Bard and Scion (CSV column `gold`).
- Some blocks contain commented-out lines (Punisher HP/MP, Brewer Mace/Shield). The CSV excludes them.
- Kits have some conditional items: the Machinist gets food only when not an Automaton, and Barony's DLC classes get extra books and spells via `addSpell`. Re-read `initClass` before porting a specific class.

### 1.3 Races (code) and race × class

| Race | Pack | Trait highlights (code) | Natural Veloren stand-in |
|---|---|---|---|
| Human | base | baseline | Human |
| Skeleton | DLC1 | no food needed, undead special cases (`actplayer.cpp`) | **Draugr** |
| Vampire | DLC1 | levitation and bleed spells, water/sunlight effects | Draugr (pale skin) or Danari |
| Succubus | DLC1 | teleport and self-polymorph spells, starts with cursed items | Danari (female) or Elf |
| Goatman | DLC1 | starts asleep and hungover, +3 booze | Dwarf (no goat humanoid). The goat body is quadruped_small. |
| Automaton | DLC2 | salvage spell, no food, water effects | Dwarf + mail (no golem humanoid). `golem` bodies exist for enemies. |
| Incubus | DLC2 | teleport and shadow-tag spells, cursed items | Orc or Danari |
| Goblin | DLC2 | different weapon-skill gain, faster weapon degradation (`entity.cpp:13197–13376`) | **Orc**, or `biped_small` GoblinThug/Chucker/Ruffian as enemies |
| Insectoid | DLC2 | poison immune, flutter/dash/acid-spray spells | Elf (nothing insect-like among humanoids). Myrmidon is biped_small. |
| Gnome | DLC3 | forge-jewel spell | Dwarf, or `biped_small` **Gnome** for NPCs |
| Gremlin | DLC3 | deface spell | Danari or Dwarf |
| Dryad | DLC3 | thorns and shrub spells, growth effect | Elf + twigs sets |
| Myconid | DLC3 | spores and mushroom spells, growth effect | Human + twigsflowers. `Fungome` (quadruped_small) is the mushroom critter. |
| Salamander | DLC3 | breathe-fire spell, starts at half MP | Danari (red) or Human. `Salamander` exists as quadruped_low. |
| Rat / Spider / Troll / Imp | DLC2 | **Shaman forms** with their own level-up weights | Rat (quadruped_small), Cavespider/Tarantula (arthropod), Cave/Mountain/Swamp troll (biped_large), **no imp** (use a Danari with wings or a small `biped_small` stand-in) |

- **Gating** (`menu.cpp:476–720`):
  - Base classes up to Monk are valid for any race.
  - Each legendary class needs its paired race or an unlock achievement.
  - Non-human races get 2 bonus polymorph potions with base classes and 3 with DLC classes. Dryad, Myconid, Gnome and Salamander get none.
  - `stat_appearance == 1` makes the race purely cosmetic.
- **Recommendation**:
  - Ship lineages as `Human | Elf | Dwarf | Orc | Danari | Draugr`, each with **one small trait** we write ourselves, in the spirit of Barony's traits.
  - Draugr: no hunger, weak to blessed. Orc: weapon skill +, durability −. Dwarf: poison resist + dig speed. Elf: PER +, stealth +. Danari: +MP, fire resist. Human: +1 any stat.
  - **Don't port the monster races as playable** in the MVP.

### 1.4 What's code vs closed

| Item | Where | Reusable? |
|---|---|---|
| Class/race enums, stat mods, HP/MP, gold, proficiencies | `charclass.cpp` | ✅ BSD (keep the notice if ported) |
| Starting kits (item enum + status + beatitude + count) | `charclass.cpp` | ✅ structure. Map to our and Veloren items |
| HP/MP growth, regen, level-up weights | `entity.cpp`, `monster.hpp` | ✅ |
| Class spells, shapeshift, vampiric curse, bots, duck | `magic/*`, `actplayer.cpp`, items code | ✅ as mechanics. Re-implement |
| DLC/race gating, achievements | `menu.cpp`, `scores.cpp` | ✅ logic (we'd use our own unlocks) |
| **Class and race names, descriptions, tooltips, flavour** | `lang/en.txt` and others (closed) | ❌ write our own |
| **Item names, values, weights, levels** | `items/items.json` (closed) | ❌ (LOOT.md) |
| Class portraits and icons, models | closed art | ❌ use Veloren |

---

## 2. Veloren assets per class

### 2.1 What Veloren gives us
- **Humanoid bodies:** 6 species × 2 body types.
  - Hair counts (Danari, Draugr, Dwarf, Elf, Human, Orc): 30, 29, 30, 37, 41, 30.
  - Beard counts in the same order: 15, 5, 24, 7, 9, 6.
  - Accessory counts in the same order: 7, 2, 7, 5, 0, 20.
  - Skin palettes: 7 to 18 per species.
  - Assembly works through `humanoid_head_manifest` + 8 `humanoid_armor_*` manifests. My Python port (`figure.py`) shows it's mechanical; LOOT L8 / MOVEMENT H1 is the Java version.
- **Armor sets** (all 8 slots unless noted):
  - Fighter and rogue sets: adventurer, ashen, assassin, bonerattler, boreal, brinestone, buccaneer, ferocious, golemite, leather_plate, pirate, savage, tarasque.
  - Hide sets: carapace, dragonscale, leather, primal, rawhide, scale, serpentscale.
  - Mail tiers: bronze, iron, steel, cobalt, bloodsteel, orichalcum.
  - Cloth sets: cotton, druid, linen, moonweave, silken, sunsilk, woolen, plus cloth_blue/green/purple.
  - Caster and nature sets: velorite_mage, witch (+hat), cultist, twigsflowers, twigsleaves.
  - Job sets (partial): alchemist, blacksmith, chef, merchant (+turban), miner (+helmet), cardinal (+mitre), rugged.
  - Voxel-only NPC sets: warlock, warlord, velorite_battlemage, leather_blue.
- **Heads:** bear bonnet, crown, winged coronet, spikeguard, hoods, wanderer's hat, straw hat, facegourd, gnarling mask, bandanas, headband, mitre, scarlet spectacles, and more.
- **Backs:** 6 cloaks or backs, 13 bags, 17 gliders (`vroom` is NC; exclude it). Gliders aren't useful in a dungeon.
- **Weapons:** modular sword (agile, heavy, cleaving and other forms × bronze→orichalcum), axe, hammer, bow, staff, sceptre, dagger, shield, and an NPC-only spear.
- **Tools:** **22 instruments** with GPL samples, pickaxes, shovels.
- **Ability sets** (`ability_set_manifest.ron`):
  - Sword: 6 stances (basic, agile, cleaving, crippling, defensive, heavy).
  - Axe, hammer and bow (stances: barrage, heartseeker, hawkstrike, plus elemental arrows).
  - Staff (fire school): firebomb, flamethrower, fireshockwave, napalm, flame cloak, fire dash, fire breath, pyroclasm.
  - Sceptre: lifesteal beam, healing aura, warding aura.
  - Shield: guard, singlestrike, power_guard.
  - Dagger: `tempbasic` only.
  - Spear: simple, NPC.
  - Custom: BasicSummon, Transform (`cursekeeper/transform.ron`), BasicAura, Teleport/Blink.
- **Animations** (`voxygen/anim/src/character/*.rs`): basic, multi, music, throw, consume, sneak, roll, stunned, wield, and others. **There's no humanoid spear, dagger or unarmed-attack animation.**
- **Non-humanoid bodies:** biped_small (Gnome, Gnarling, Goblin×3, Myrmidon, Haniwa, Jiangshi, Mandragora, …), biped_large (3 trolls, saurok×3, Minotaur, Mindflayer, Ogre, Werewolf, Wendigo, Strigoi, …), quadruped_small (Rat, Goat, Fungome, TreantSapling), arthropods (Cavespider, Tarantula, Blackwidow), Salamander (quadruped_low), Bat and VampireBat, golems.
- **Licensing:** `credits.ron` lists **no CC-BY-NC-SA entries under armor, figure, weapons or instruments**. The only NC item I hit is the `vroom` glider. Everything used in the renders is GPL-3.0, so we must ship the source and credits.

### 2.2 Class → Veloren mapping (what the renders show)

✅ = assets exist. ⚠ = substitute. ❌ = needs custom art or animation.

| Class | Species (race stand-in) | Armor (Veloren ids) | Weapons → ability set / stance | Gaps |
|---|---|---|---|---|
| Barbarian | Human | `savage.*` + `misc.head.bear_bonnet` | iron 1h axe + wood shield → **Axe** set (triple chop, cleave, berserk) + Shield guard; or 2h axe | tomahawks (⚠ thrown axe = `throw` anim + axe model) |
| Warrior | Human F/M, Dwarf | `mail.iron.*` (or `leather_plate` + `helmet`) | **spear ❌** (only NPC `tidal_spear`, no humanoid anim) → MVP: sword `Defensive` stance + shield | spear model + thrust anim |
| Healer | Elf | `cloth.silken.*` + `hood` | quarterstaff (staff pole) + **Sceptre** (healing aura, lifesteal beam, warding aura) ✅ | healer doublet (⚠ silken works) |
| Rogue | Human | `assassin.*` + `hood_dark` | sabre 1h → Sword `Crippling` (bleed) or `Agile` + shortbow (Bow) ✅ | lockpick and decoy icons (LOOT) |
| Wanderer | Dwarf | `hide.rawhide.*` + `wanderers_hat` + `back.short_1` | quarterstaff (staff pole as a melee "polearm" ⚠) | sling ❌, lantern (sprite/held light ⚠) |
| Cleric | Human | `cardinal.*` + `mitre` | spiked mace 1h → **Hammer** set + Shield; Sceptre for heals | — |
| Merchant | Human | `merchant.*` + `turban` | bronze axe + pickaxe (tool) | monocle ⚠ (`scarlet_spectacles`) |
| Wizard | Elf | `velorite_mage.*` + `witch.hat` | fire staff → **Staff** set ✅. Cold, lightning and forcebolt need our spell list rendered via Veloren projectiles (COMBAT C10) | — |
| Arcanist | Human | `hide.leather.*` + `hood` | **crossbow ❌** → ⚠ composite bow; magic rod | crossbow model + anim (could reuse bow) |
| Joker | Danari | `cloth_purple.*` + `facegourd` | sling ❌, cream pies (throw ⚠), kalimba prop | jester hat ❌ |
| Sexton | Draugr | `cloth_blue.*` + `hood_dark` | spiked mace bronze → Hammer | fez ❌, chakram ❌ |
| Ninja | Human | `assassin.*` + **`assa_mask-0` (voxel exists, no head-manifest entry)** | katana 1h → Sword `Agile` ✅. Daggers = Dagger (`tempbasic`, **no anim ❌**) or thrown | mask offsets (author 12 entries, ≈1 h), dagger anim |
| Monk | Human | `rugged.*` + `misc.foot.sandals` | **unarmed ❌** (Empty set is `basic`, no humanoid fist anim). Brass knuckles = glove item | fist combo anim (first-person is our own viewmodel, so a smaller gap) |
| Conjurer | Draugr (skeleton) | `cloth.woolen.*` + `hood_dark` + `back.dungeon_purple` | wooden staff → Staff + **BasicSummon** (cultist/mindflayer summon RONs) ✅ summoned bodies (skeleton = Draugr humanoid NPC) | — |
| Accursed | Draugr (vampire) | `cultist.*` + `bandana` | unarmed + spells. Lifesteal = Sceptre `lifestealbeam` ✅ | blood vials (potion recolour) |
| Mesmer | Danari (succubus) | `cloth.moonweave.*` + `winged_coronet` | frost cane (sceptre) | charm = our effect + follower overlay (NPC_AI) |
| Brewer | Dwarf (goatman) | `alchemist.*` + `hat` | iron axe + mace. Thrown potions (`throw` anim + 7 potion models + recolours) ✅ | backpack ⚠ (`back.backpack`), alembic prop |
| Machinist | Dwarf (automaton) | `blacksmith.*` + `hat` | crossbow ❌ → ⚠ bow; craftsman hammer | **dummy/sentry bots ❌** (Haniwa/golem bodies as stand-ins ⚠), traps (TRAPS.md) |
| Punisher | Orc (incubus) | `bonerattler.*` + `spikeguard` | cobalt battleaxe 2h → Axe | **whip ❌** (model + anim) |
| Shaman | Orc (goblin) | `twigsleaves.*` + `gnarling_mask` | bamboo rod (staff). **Forms via Transform**: Rat ✅, Spider ✅, Troll ✅, **Imp ❌** | imp body |
| Hunter | Elf (insectoid) | `hide.primal.*` + `hood` | longbow → **Bow** set (heartseeker/hawkstrike/barrage + elemental arrows) ✅ | boomerang ❌, quiver ❌ (LOOT L13) |
| Bard | Elf (gnome) | `buccaneer.*` | sabre (rapier ⚠) + **instrument** (22, `music` anim) ✅ | plumed cap ❌ |
| Sapper | Dwarf (gremlin) | `miner.*` + `helmet` | spiked mace (flail ❌) + bombs (`throw` + Veloren bomb projectile) ✅ | bolas ❌, frying pan ⚠ |
| Scion | Elf (dryad) | `cloth.sunsilk.*` + `crown` | grand sceptre → Sceptre ✅. Earth elemental = summon (stone/wood golem) ✅ | focus item ❌ |
| Hermit | Human (myconid) | `twigsflowers.*` + `straw` | flimsy 2h hammer (shillelagh ⚠) | duck ✅ (Veloren `bird_medium` **Duck** body exists: `npc.duck.*`), pipe ✅ (`misc.head.pipe`) |
| Paladin | Human F | `mail.steel.*` | zweihander steel 2h → Sword `Heavy` ✅ + Sceptre aura for *divine zeal* | holy focus ❌ |

**Summary of gaps (art and animation):**
- **Animations:** spear thrust, dagger stab and unarmed punch for 3rd-person humanoids. In first person these are our own viewmodel tracks (COMBAT C8), so they cost less there.
- **Models:** crossbow, sling, whip, flail, chakram, boomerang, bolas, quiver, spellbook, focus. Also the jester hat, fez and plumed cap, a dummybot/sentrybot pair, and the imp. (The Hermit's duck is covered by Veloren's bird_medium Duck.)
- **Manifest fixes:**
  - The `assa_mask` head entry.
  - Broken `armor.hide.savage.chest` path (the file is `armor/savage/chest.vox`).
  - Draugr headband head entries.
  - `armor.shoulder.brown_right`/`chain` references.

### 2.3 Shapeshift, summons and companions with Veloren bodies
- **Shaman forms** and polymorph:
  - Swap the entity's `Body` component to Rat (quadruped_small), Cavespider (arthropod), Cavetroll (biped_large), or an imp stand-in.
  - Use the existing NPC viewer bodies and Veloren `Transform` semantics: duration, revert, keep HP%.
  - Form abilities come from the bodies' Custom ability sets ("Quad Small Basic", arthropod bite/web, "Cave Troll"-style melee).
- **Summons** (Conjurer, Scion, Cleric scrolls) use Veloren `BasicSummon` with our creature defs. Skeleton = Draugr loadout, earth elemental = stone golem or Wood Golem.
- **Machinist bots:** pick a small `golem`/Haniwa body + a turret tactic (NPC_AI `RangedKite` with zero movement).
- **Hermit's duck:** Veloren's `bird_medium` **Duck** body (`bird_medium_central_manifest.ron`) as a pet (NPC_AI `pet` role).

---

## 3. Design for jvm-dungeon-crawler

### 3.1 `ClassDef` data model (`assets/data/classes/*.json`)
See `class_tools/classes.sample.json` for three full entries.

```jsonc
{
  "id": "rogue", "name": "Rogue", "blurb": "…our own text…",
  "roles": ["dps","utility"],                       // party-bot tags: tank|healer|dps|support|utility (+ ranged flag)
  "attributes": {"STR":-1,"DEX":2,"CON":0,"INT":-1,"PER":2,"CHR":-1},
  "hp": -5, "mp": -10, "gold": 0,                   // deltas from base 30/30 before hpScale
  "proficiencies": {"STEALTH":50,"LOCKPICKING":40,"SWORD":25, ...},
  "growth": {"hpPerLevel":2,"mpPerLevel":3,"hpRegen":5,"mpRegen":3},
  "statWeights": {"STR":2,"DEX":7,"CON":1,"INT":2,"PER":7,"CHR":5},
  "kit": {"All":[ {"Roll":{"inner":{"Item":"…"},"status":"EXCELLENT","beat":0,"identified":true}}, … ]},  // LOOT.md LootSpec
  "spells": ["…"], "classMechanics": ["backstabBonus"],  // hooks: summon, shapeshift, vampiric, instrument, gadgets, companion
  "look": {"loadout":"crawler.loadout.class.rogue", "speciesHint":["Human","Elf"], "set":"common.items.armor.assassin",
           "abilitySet":"Tool(Sword)", "stance":"Crippling", "offhand":"Tool(Bow)"},
  "bot": {"preferredRange":"flank","engageOrder":1,"seeksBackstab":true,"picksLocks":true,
          "potionHpThreshold":0.4,"lootPriorities":["GEM","RING","AMMO"],
          "personalityPool":["greedy_sly","nervous","sardonic"],"barkTags":["class.rogue","role.utility"]},
  "unlock": "default", "lineageAffinity": {"Draugr":"conjurer"}   // optional Barony-style legendary pairing
}
```
- **`Lineage` def** (`lineages/*.json`): `{id, species, bodyTypes, attributes, trait, startExtras}`. This is the replacement for Barony races.
- **Character = class + lineage + `Body` appearance** (hair, beard, skin, eyes). The appearance is seeded for bots.
- It drops into **ECS E5 prefabs**: `player` / `partyBot` archetype ← class overlay ← lineage overlay ← personality overlay (bots only).

### 3.2 Stats and skills → COMBAT.md rules
- **Attributes** feed the COMBAT §4.2 multipliers directly:
  - STR → melee ×(1+0.055·STR); DEX → ranged; INT → spell.
  - CON → +2 protection per point and HP growth.
  - PER → detection, appraisal speed, ranged accuracy.
  - CHR → recruit odds and shop prices.
- Barony's starting numbers (−3…+5) work as-is because those formulas are Barony's.
- **HP/MP:** `maxHp = (30 + class.hp) · hpScale` (balance.json, **hpScale ≈ 3–5**, the Veloren HP scale noted in LOOT §4.4; tune with the C12 CombatSim). MP stays on Barony's scale (spell costs are Barony's). Energy (Veloren stamina 100) is separate and the same for everyone.
- **Proficiencies → skill roll** `min(1, 0.6 + 0.1·U(−1,1) + prof/250)`.
  - Map Barony skills to Veloren tool kinds: Sword→Sword, Axe→Axe, Mace→Hammer, Polearm→Spear and staff melee, Ranged→Bow, Crossbow and thrown, Unarmed→Empty and gloves.
  - **Sorcery/Mysticism/Thaumaturgy** gate spell schools: failure chance by `difficulty` vs skill, and the INT bonus doubled in-school.
  - Shield → block strength and parry window (COMBAT §4.5). Stealth → backstab (DETECTION).
- **Stance unlocks:** COMBAT suggests unlocking Veloren stance abilities at skill 40/60/80. A class **starts in its signature stance at tier 1**: Ninja Agile, Rogue Crippling, Barbarian (Sword) Cleaving, Warrior and Paladin Defensive/Heavy, Hunter Heartseeker at Ranged 50.
- **Level-up:** C11 uses `statWeights` for weighted attribute picks and `growth` for HP/MP. Shapeshift forms override `statWeights` (Barony's rule).

### 3.3 Starting kits through LOOT.md
- The kit is a `LootSpec` (`All` of `Roll(Item …, status, beat, identified: true)`), evaluated once at character creation with source `CLASS_KIT`. LOOT §4.4 already defines "class kit: fixed, identified". LOOT L12 is 0.5–1 d.
- Item ids are Veloren where possible (`common.items.armor.mail.iron.chest`, modular weapons via `ModularWeapon{tool, material, hands}`), else crawler ids (`crawler.tool.lockpick`, `crawler.potion.*`).
- **The visual loadout is just the equipped kit.** No separate look data is needed. `look.set` and the hints exist only for bots and the character-creation preview.
- Barony's 5 sickness potions on the Rogue, the Wizard's books and similar items map 1:1 to LOOT's MVP potion and scroll list. Classes that need items we don't have yet are gated behind those systems (Machinist gadgets, Brewer recipes).

### 3.4 Party bots: picking and playing classes
- **Picking (party composition):**
  - When the party forms, fill slots greedily so the role tags cover `tank` → `healer` → `dps(ranged)` → `dps/utility`, given the player's class.
  - Example: player = Rogue ⇒ bots Warrior (tank), Healer, Wizard or Hunter.
  - Seeded so each run varies. Each bot then draws a lineage (`speciesHint`), an appearance and a personality from `personalityPool`.
- **ECS mapping:** `PartyMember.role` is the bot's **primary** role tag (ECS uses `tank/healer/archer/caster/scout`; I'd rename it to `tank/healer/dps/support/utility` + a `ranged` flag, or keep ECS's names as aliases). The `Brain` reads `ClassDef.bot` for:
  - `preferredRange` (melee/flank/mid/far) → formation slot and the `RangedKite` vs `SimpleMelee` tactic;
  - `engageOrder` → tank first, then dps, then casters;
  - `potionHpThreshold`, `mpReserve`, `kiteDistance`;
  - healer rules: `HealAllyBelow(0.5)` uses Sceptre `healingaura` or a heal spell, with priority over attacking;
  - utility rules: `DisarmKnownTrap`, `PickLock`, `IdentifyLoot` (Rogue, Merchant), `Recruit` (Warrior and Mesmer high Leadership);
  - class mechanics: Conjurer `SummonWhenEngaged`, Shaman `ShapeshiftWhen(hp<0.4 → troll | scouting → rat)`, Bard `PlaySongWhenAlliesInRange`.
- **Loot etiquette:** `lootPriorities` + personality greed decide who calls dibs on drops. Bots offer items that fit another member's class ("You're the archer, take the quiver").
- **Banter hooks (barks):**
  - Each class gives `barkTags`. The personality gives voice and traits.
  - Lines are picked by `(event, classTag, personalityTag)`. Examples:
    - Wizard × pompous on a fireball kill: "Elementary."
    - Barbarian × boastful on a low-HP enemy.
    - Rogue × greedy on a locked chest: "Leave this one to me."
    - Healer on an ally below 30%: "Hold still!"
  - Cross-class banter: the tank complains when the wizard pulls aggro. The rogue teases the paladin about lockpicking.
  - These go through ECS's `ChatLog` scheduler (`delayTicks(personality)`, max depth 2).
- **Class-aware threat of a bot dying:** if a healer bot is downed, the remaining bots raise their potion thresholds and the tank switches to defensive stance. That's cheap and very "player-like".

### 3.5 Recommended MVP roster
Picks are driven by coverage and by ability sets that already animate on humanoids.

| Class | Role tags | Ability set (existing anims) | Art status | Why |
|---|---|---|---|---|
| **Warrior** | tank, dps | Sword Defensive + Shield (spear later) | ✅ (spear ❌ deferred) | Party anchor, Leadership for recruits |
| **Rogue** | dps, utility | Sword Crippling + Bow | ✅ | Stealth/backstab (DETECTION), locks, traps (TRAPS) |
| **Wizard** | dps (ranged), support | Staff (fire) + our spell list | ✅ | Caster archetype, showcases COMBAT C10 |
| **Healer** (Cleric look option) | healer | Sceptre (healing aura, ward) + staff melee | ✅ | Party sustain. Bots need one. |
| **Hunter** | dps (ranged) | Bow (heartseeker/hawkstrike) | ✅ (quiver is a LOOT L13 item) | Physical ranged, very readable bot behaviour |
| **Barbarian** | dps, tank | Axe + Shield / 2h axe | ✅ | Simple melee bruiser for new players |

- **Next wave**, in order of reuse: Paladin (Sword Heavy + aura), Cleric (Hammer + Sceptre), Ninja (needs the mask entry + dagger/throw), Bard (instruments + `music` anim), Conjurer (BasicSummon), Merchant.
- **Later:** Shaman (Transform + bodies), Brewer (alchemy), Accursed, Mesmer (charm), Sexton, Arcanist, Joker, Wanderer.
- **Last, because they need custom art and systems:** Monk (unarmed anim), Machinist (bots), Punisher (whip), Sapper (traps), Scion, Hermit (duck body exists; the spells are the work).

### 3.6 Effort estimates
Assumes LOOT L1–L4/L8, COMBAT C2/C10/C11, MOVEMENT H1/H2 and ECS E5 are done or scheduled. Their costs aren't repeated here.

| # | Task | Days |
|---|---|---|
| K1 | `ClassDef` / `Lineage` schema + importer + validation tests (kit ids resolve, proficiency names, role tags) | 0.75–1 |
| K2 | Character creation: class + lineage + appearance pickers, 3D preview with the humanoid figure (H1), our blurbs | 1.5–2 |
| K3 | Apply class: attributes/HP/MP/gold/proficiencies, kit via LOOT L12, starting spells, stance pick | 0.5–0.75 (beyond L12) |
| K4 | Level-up with `statWeights` + growth (beyond C11's generic part), level-up UI line | 0.5 |
| K5 | Bot class selection + appearance seed + `ClassDef.bot` → Brain params; healer and utility rules | 1.5–2 |
| K6 | Class/personality bark tables (MVP 6 classes × ~8 events × 3 personalities) | 1–1.5 |
| K7 | MVP 6 class data + balance pass in CombatSim | 1 |
| | **MVP total** | **≈5–7** (+ about 2 h for the assa_mask head entries if Ninja is in) |
| K8 | Lineage traits (6) | 1–1.5 |
| K9 | Class mechanics, full roster. Summons 1.5, shapeshift + 4 forms 3–4, vampiric curse 1, charm/command 1–1.5, instruments/songs 1.5–2, alchemy/brewing 2–3, Machinist gadgets/bots 3–4, booby traps (on TRAPS) 1, telepull/whip 1, duck + Hermit spells 1.5, misc class spells 2–3 | 19–25 |
| K10 | Animations: spear thrust, dagger, unarmed (3rd person + viewmodel tracks) | 2–3 |
| K11 | Custom voxel art: crossbow, sling, whip, flail, chakram, boomerang, bolas, 3 hats, foci, 2 bots, imp | 6–9 |
| | **Full 26-class roster** | **+≈28–40** |

---

## 4. Licensing checklist
- Port mechanics freely. If any Barony code is translated (growth tables, kit logic), keep Barony's BSD-2-Clause copyright header on those Java files and add the notice to `NOTICE`. Numbers copied verbatim as data need attribution too, so put the notice in `NOTICE`.
- **Don't** copy Barony class or race names that are unique terms (Sexton, Accursed, Mesmer, Scion, Punisher, Joker, Arcanist are just English words, which is fine), and don't copy descriptions, lore, tooltips or item names. Write our own.
- Veloren assets are GPL-3.0, which is compatible with the repo. Keep `credits.ron` attribution and **exclude `glider.vroom`** and any other entries `credits.ron` marks NC.

## 5. Decisions for the user
1. **Races:** 6 Veloren lineages with light traits (recommended), or try to port Barony's monster races with non-humanoid player bodies (much larger effort, and first-person hands get odd)?
2. **MVP roster:** confirm Warrior, Rogue, Wizard, Healer, Hunter, Barbarian, or swap in Paladin or Ninja?
3. **Numbers:** reuse Barony's class numbers as a starting point (with BSD notice), or re-tune from scratch in CombatSim?
4. **Role vocabulary:** adopt `tank/healer/dps/support/utility` (+ranged) and update ECS's `PartyMember.role`?
5. **Unlocks:** all classes available from the start, or Barony-style unlocks (achievements / lineage pairing)?
6. **Spear:** commission a spear and thrust animation early (Warrior's identity), or ship Warrior as sword-and-board?

## 6. Caveats
- The renders are a stand-pose approximation made with my Python port. Bone offsets come from `SkeletonAttr`. Hair highlight colours clamp brighter than in-game. The assassin mask offset `(1,5,0)` is hand-tuned.
- Weapon images are raw voxel models (longest axis vertical), not item icons.
- Look picks are proposals, chosen for silhouette and palette. Many alternatives exist: the 6 mail tiers, 7 hide sets and 7 cloth sets can mark gear progression per class.
- The kit item lists come from a regex over `initClass`. Conditional branches (race-specific, challenge-run) need a manual read before porting.
- Veloren files are from master at `ebfff94`. Asset paths can move between versions, so pin a commit for the importer.
