# Barony-like combat for jvm-dungeon-crawler, built from Veloren code and assets

*Research only. No repo was changed, nothing was committed and no PRs were opened.*

Sources read:
- **Barony**, `TurningWheel/Barony` @ `962a5ce` (2026-02-20). **Code is BSD-2-Clause** (© Turning Wheel LLC). The game data (models, sounds, monster stat files, maps) is not open, so use none of it. Files: `src/entity.cpp` (`Entity::attack`, `getAttack`, `getRangedAttack`, `getThrownAttack`, `getACEffectiveness`, `AC()`), `src/acthudweapon.cpp`, `src/stat.cpp`/`stat.hpp`, `src/items.cpp` (`weaponGetAttack`, `armorGetAC`), `src/monster.hpp` (`damagetables`, `HITRATE`), `src/actmonster.cpp` (`handleMonsterAttack`), `src/actarrow.cpp`, `src/magic/*`, `src/game.hpp`.
- **Veloren**, `gitlab.com/veloren/veloren` master @ `ebfff94` (2026-10-06 6:56 PM ET), **GPL-3.0**. Fresh sparse clone at `/workspace/veloren_combat`: `common/src/combat.rs`, `common/src/comp/{melee,projectile,buff,poise,ability,body/mod,inventory/item/{mod,armor,tool,modular}}.rs`, `common/src/states/{basic_melee,combo_melee2,charged_melee,basic_block,basic_ranged,utils}.rs`, `common/systems/src/{melee,projectile}.rs`, `server/agent/src/{attack,action_nodes,data}.rs`, `server/src/events/entity_manipulation.rs` (from `/workspace/veloren_agent`), `assets/common/abilities/**` (611 files + `ability_set_manifest.ron`), `assets/common/items/{weapons,modular,npc_weapons,armor}/**`, `assets/common/material_stats_manifest.ron`, `assets/common/entity/dungeon/**`, `assets/voxygen/voxel/{biped_weapon_manifest.ron,humanoid_armor_hand_manifest.ron,weapon/**}`, `voxygen/anim/src/{character,biped_small,biped_large}/*`, `voxygen/src/hud/mod.rs`, `assets/credits.ron`.
- **Crawler** `SOS-Games/jvm-dungeon-crawler` @ `6093f3a` (`master`, up to date). There's no combat code yet. Useful existing pieces are `converter/RonToJsonVisitor.java` and `Converter.java`, which turn any Veloren `.ron` into externally tagged JSON (`{"ComboMelee2": {...}}`), and `viewer/NpcFigure.java`, which already reads `biped_weapon_manifest` grips.
- Prior reports: `MOVEMENT.md` (M0–M6, H1–H2), `DETECTION.md`, `VELOREN_ANIMATION.md` (P0–P6), `BARONY_ENVIRONMENT.md`, `NAV_COLLISION_PORT.md`. There's no `TRAPS.md` in `/workspace` yet.

Units: 1 Barony tile = 16 units. 1 m = 1 Veloren block = 1 libGDX unit. 1 generator tile = 3 m. Barony runs at 50 ticks/s, Veloren at variable dt (about 60 Hz), and the crawler sim at a fixed 60 Hz (MOVEMENT.md).

---

## 0. TL;DR

1. **Barony combat is stat-and-dice driven and 2-D.** A melee swing is one `lineTrace` along yaw with a reach of `STRIKERANGE` = 24 units (1.5 tiles). It fires **at the moment you release the button**. Damage works like this:
   - `atk = (8 + weaponAtk(+beatitude, +status−3))·(1 + 0.055·STR)` (players).
   - Armour: `max(0, atk·0.75 − AC) + 0.25·atk`. Shield-defending makes armour fully effective and adds `5 + PRO_SHIELD/5` AC.
   - Multiplied by the race×weapon-type damage table and by a proficiency roll `min(1, U(0.4,0.6) + skill/200)`.
   - ×2 for a full charge (30 ticks = 0.6 s).
   - **Backstab** on unaware monsters (state WAIT/PATH/HUNT, attacker facing within ±60° of the target's facing) adds `+(stealth/20+2)·2` flat. Engaged targets give a 50% chance at a smaller flank bonus.
   - Per hit, weapons degrade about 1/50 (×1+skill), and armour or shields degrade. Proficiency rises 1/10 per hit and 1/8 per kill. Monsters swing every `HITRATE` = 45 ticks (0.9 s) after telegraphed windup poses.
2. **Veloren combat is ability-data driven and continuous.**
   - **Abilities**: each weapon kind maps to an `AbilitySet` (primary, secondary, guard, specials), and each ability is a `.ron` file naming a character-state kind (`ComboMelee2`, `ChargedMelee`, `BasicBlock`, `BasicRanged`, …) with stage durations and a `MeleeConstructor` (`Slash`/`Stab`/`Bash`, damage, poise, knockback, range in metres, angle in degrees).
   - **Weapon stats** (`power, speed, range, effect_power, energy_efficiency, buff_strength, equip_time`) scale those numbers. Modular weapons multiply primary × hilt × material.
   - **Hit test**: at `hit_timing` inside the Action section, a one-shot **wedge test** runs (`dist < r_a + r_b + range`, `angle < max_angle + atan(r_b/d)`, z band, plus 9 wall rays).
   - **Damage pipeline**: precision (back flank +0.75·(precision−1), headshots, stunned targets) → block (subtract `block_strength·tool.power`, plus a parry window during buildup) → armour DR `p/(60+p)` → damage-kind side effects (Slashing drains energy, Crushing deals poise, Piercing ignores 75%·dmg of protection).
   - **Poise** thresholds 50/30/15/5 trigger Interrupted, Stunned, Dazed and KnockedDown states with animations.
   - About 60 buffs. Energy is a stamina pool (100, regen ramps to 10/s).
   - **NPC AI** is 86 hand-written tactic functions picked from the weapon's ability spec.
3. **Recommendation: Barony rules, Veloren machinery and art.**
   - Port Veloren's **`Attack` pipeline, the five or six character-state kinds, the `MeleeConstructor` wedge, poise, block/parry and buffs** into a headless Java `combat` package driven by **JSON converted from Veloren `.ron`**.
   - Put a **Barony stat layer** on top: STR/DEX/INT scaling, proficiency rolls and gain, race×damage-kind tables, charge-to-double, backstab tied to DETECTION.md awareness, item status and degradation, MP spells.
   - Keep Veloren's **DR formula** (it scales cleanly) rather than Barony's subtractive AC.
   - Input: **tap LMB** = the weapon's Veloren primary (combo strike), **hold LMB** = its Veloren charged secondary, **RMB** = guard/shield block with a parry window, plus the optional Veloren roll.
   - **Hit detection in first person** is a crosshair-priority wedge: Veloren's wedge, with targets ranked by angle to the crosshair and LOS checked by block DDA. Projectiles are physical (gravity, sweep tests, headshots).
   - Monsters use Veloren **ability sets and animations**, but with Barony **attack cadence and readable windups**.
4. **Effort:** an MVP (melee, block/parry, backstab, damage numbers, two monster types) is about **10–13 dev-days**. The full system (ranged, thrown, magic, status effects, progression, degradation, monster roster, balance harness) is about **26–36 dev-days**. Both are on top of MOVEMENT M0–M6, VELOREN_ANIMATION P0–P5 and the DETECTION MVP. See §7.

---

## 1. Barony combat (BSD-2-Clause code; numbers and logic only)

### 1.1 Player melee input and HUD timing (`acthudweapon.cpp`)
- The HUD weapon is a state machine in `HUDWEAPON_CHOP` (`:379`).
  - Pressing attack enters **CHOP 1, the windup**: the weapon eases to `MOVEZ −6` at 0.65 per tick and to pitch −π/4 at 0.1 per tick, about 8–10 ticks (≈0.2 s).
  - While held, `HUDWEAPON_CHARGE` rises by 1 per tick up to `getMaxAttackCharge()` (`MAXCHARGE` = 30 → 0.6 s; flails charge at ¼ rate) (`:2082–2091`).
  - **On release**, `entity->attack(pose, HUDWEAPON_CHARGE)` is called **immediately** (`:2034`), and the swing (CHOP 2) and return (CHOP 3) animations play afterward, about 8–15 ticks. A full tap cycle is roughly **0.45–0.55 s**.
  - The camera shakes +6 on each swing (`:2079`).
- Poses: 1 = chop, 2 = stab (spears and staffs lunge, CHOP 7), 3 = alternate swing. There are also flail (24), whip and crossbow reload states, and the claymore overcharge power strike (CHOP 4/10–12).
- **Parry** with weapons that have no shield: tap Defend → `attack(MONSTER_POSE_PARRY, 35)` (`:2022`). This sets `stats->parrying`. While parrying, `AC()` adds `getParryingACBonus = skill/25 + weaponAtk + 5 + min(40,skill)/5` (`stat.cpp:1530`).
- **Shield defend**: hold Defend. Movement drops to half (MOVEMENT.md §1.3), you gain `getActiveShieldBonus = 5 + PRO_SHIELD/5` AC (`stat.cpp:1490`), and armour becomes **100% effective** (`getACEffectiveness`, `entity.cpp:8151`). There's also a passive `PRO_SHIELD/25` AC whenever a shield is worn.

### 1.2 Hit detection (`Entity::attack`, `entity.cpp:10097`)
- `dist = lineTrace(this, x, y, yaw, strikeRange, LINETRACE_ATK_CHECK_FRIENDLYFIRE)` (`:11315`), with `strikeRange = STRIKERANGE` (24 units = 1.5 tiles), or ×1.5 for whips and flails. It's a **single 2-D ray along yaw**: the first entity or wall hit wins, pitch is ignored, and there's no cone. Monsters use the same function with `target` passed in.

### 1.3 Damage formula
```
getAttack (entity.cpp:8220):
  attack = BASE_MELEE_DAMAGE (8)                      // unarmed players: 8 + PRO_UNARMED/20 (+gloves/ring)
         + weaponGetAttack()                          // items.cpp:4601: per-type base (bronze 4, iron 5, steel 6, crystal 10, …)
                                                      //   + beatitude + (status − 3)  [BROKEN 0 … EXCELLENT 4]
  players: attack *= 1 + STR·0.055   (rapier: DEX; whip: (STR+DEX)/2; sceptre: STR/2)
  monsters: attack += STR
  weakness effect: × (1 − 0.2…0.9)
getRangedAttack (:8377): (7 + quiver + weaponAtk)·(1 + DEX·0.055)       [monsters + DEX + PER + skill/20]
getThrownAttack (:8426): (6 + weaponAtk)·(1 + DEX/4·0.0625)·thrownSkillMult[skill/20]
In attack (:12770–13025):
  myAttack = getAttack·preMult + bonusOnTarget
  eff      = 0.75 + 0.025·Σblessings   (1.0 when defending with a shield or parrying)
  after    = max(0, myAttack·eff − AC) + (1−eff)·myAttack           // 25% always bypasses armour
  damage   = after · damagetables[race][weaponType]                  // monster.hpp:497; sword, mace, axe, polearm, ranged, magic, unarmed
  damage   = max(1, damage) unless armour is 100% effective
  axe +1; backstab/flank bonus (below)
  skill roll: damage ×= min(1, (40…60% + proficiency/2 %))           // so 50% average at skill 0, 100% from skill ≈ 80
  charge  : ×2 only at full charge (integer division), rapier +0.25
AC (entity.cpp:16915) = CON + Σ armorGetAC (helm, breastplate, gloves, boots, shield, cloak, ring, mask) + shield/parry bonuses
```
- `damagetables` (`monster.hpp:497`) give per-race resistances, for example skeleton `sword 0.5, mace 1.4, polearm 1.3, ranged 0.5`, slime `sword 1.4, mace 0.5`, lich ×2.5 vs melee. This is Barony's "use the right weapon" layer.

### 1.4 Backstab, flanking and crits (`entity.cpp:12866–12960`)
- Players only, against mobile non-mimic targets. `hitAngle = target.yawDifferenceFromEntity(attacker)` compares the **two facings**. `≤ 2π/3` after doubling means the attacker faces within ±60° of where the target faces, i.e. you're behind it.
- **Unaware** (the target was in `WAIT` or `PATH`, or `HUNT` without a current target, and isn't rooted): `+ (PRO_STEALTH/20 + 2)·2` flat (+4…+14; ×2 with the stealth capstone; +4…8 for the assassin hood). `HUNT` with a live target gives a reduced `+(stealth/20+1)`. 3-in-4 chance to raise Stealth.
- **Flanking** (the target is engaged): 50% chance of `+(stealth/20+1)`.
- Unarmed backstabs at high skill can paralyze (50 ticks) for +5 damage (`:14011`).
- There's **no random crit roll** in Barony. The variance is the 20% skill-roll band.

### 1.5 Degradation and proficiency
- **Weapon degrade on hit** (`:13380–13460`): `1/(4+skill/20)` when the hit does 0 damage, otherwise `1/(50 + 10·skill/20)`. Hardcore doubles these, goblin wielders get an extra 4/20, crystal weapons degrade faster, and artifacts or capstones never degrade. Each step lowers `status` by 1, which is −1 attack, and BROKEN destroys the item.
- **Armour degrade**: a random worn piece rolls per hit (`:13600–13690`). If none was chosen, the shield gets a chance instead, and a blocked hit gives a 1/(20+5·skill) chance (halved with perfect-block timing) to raise **Shield** skill.
- **Proficiency gain** (`:13148–13265`): weapon skill +1 with 1/10 chance per damaging hit and 1/8 per kill (crystal 1/6, goblins slower). Skills run 0–100 with tiers at 20/40/60/80/100 (`stat.hpp:195`). XP of 100 per level raises class-weighted stats (`entity.cpp:4065`). Player base HP and MP are 30 (`game.hpp:367`).

### 1.6 Ranged, thrown, magic
- **Bows and crossbows**: a fire input calls `attack(MONSTER_POSE_RANGED_SHOOT1/2)` and spawns `actArrow` at speed 7 units/tick (≈22 tiles/s ≈ 66 m/s at 3 m tiles) with an optional `arrowFallSpeed` drop. Quivers change damage and effects. Crossbows have reload states (`throwGimpTimer` 40).
- **Thrown** (daggers, chakrams, bolas, boomerang, potions): the item is consumed and becomes an `actThrown` projectile. Skill multipliers apply.
- **Magic** (`magic/*`): spells are lists of elements (propulsion + effect). Base damage is forcebolt 15, fireball 25 and lightning 25 (`setupSpells.cpp:60–90`). Cost is `spell->mana` (`spell.cpp:828`). INT bonus is `+INT/100`, doubled for Sorcery-school spells (`spell.cpp:939`). Spell `difficulty` versus the caster's skill gives a failure chance. MP regenerates on an interval (`getManaRegenInterval`, `entity.cpp:26680`). There are 226 spell IDs and 135 `EFF_*` status effects (asleep, poisoned, stunned, confused, drunk, invisible, blind, greasy, fast, slow, paralyzed, levitating, bleeding, fear, webbed, knockback, …).
- Casting uses its own HUD hand (`act_HandMagic.cpp`), which suits Veloren's floating hands well.

### 1.7 Monster attacks (`actmonster.cpp:10279`)
- A monster attacks when in range (`STRIKERANGE`, or ×1.5 for whips and flails, or the ranged weapon's effective distance). It ticks `monsterHitTime` and attacks at `HITRATE` (45 ticks = **0.9 s**) × a per-weapon factor (bows ×1.5–2, crossbow ×2, magic varies) × `monsterGlobalAttackTimeMultiplier`.
- Many races play **windup poses** (`MONSTER_POSE_MELEE_WINDUP1–3`) before the hit, which lets players react.
- Knockback or a stagger pushes `monsterHitTime` back (`:13954`).
- Monster damage uses the same `getAttack`, with `+STR` instead of the multiplier.

---

## 2. Veloren combat (GPL-3.0 code and assets)

### 2.1 Data model
- **`Attack`** (`combat.rs:126`): `damages: Vec<AttackDamage{Damage{kind, value}, target group, effects}>`, `effects: Vec<AttackEffect{CombatEffect, requirement}>`, `precision_multiplier`, `blockable`.
- **`DamageKind`** (`:1722`):
  - `Slashing`: 50% of applied damage also drains target energy, and the overflow becomes extra HP damage.
  - `Piercing`: ignores `0.75·dmg` of protection.
  - `Crushing`: damage absorbed by armour becomes poise damage, and hits on stunned targets convert poise into extra HP damage.
  - `Energy` (magic).
- **`CombatEffect`** (`:1245`): Heal, Buff (kind, dur, strength, chance), Knockback (Away/Towards/Up/TowardsUp, strength ×50), EnergyReward, Lifesteal, Poise, Combo, AdditionalDamage, RefreshBuff, SelfBuff, Energy, Transform, DebuffsVulnerable.
- **`CombatRequirement`** (`:1513`): AnyDamage, Energy(cost), Combo(n), TargetHasBuff, TargetPoised, **BehindTarget** (target look vs attack dir < 45°), TargetBlocking, TargetUnwielded, AttackSource, StageSection, …

### 2.2 Damage pipeline (`Attack::apply_attack`, `combat.rs:283`; `Damage::calculate_health_change`, `:1789`)
```
dmg   = value · strength_modifier · stats.attack_damage_modifier
prec  = max(flank, poise, headshot, conditional…)  // see below
dmg  += dmg · prec · (precision_power − 1)        // precision_power = 1 + 0.1 + Σarmor.precision_power (≈1.1 default)
dmg   = max(dmg − block_decrement, 0)
dmg  *= 1 − DR,  DR = 1 − (1 − p/(60+|p|))·(1 − stats_dr),  p = Σarmor.protection − piercing_pen
```
- **Precision sources**:
  - Melee flank (`precision_mult_from_flank`, `:2338`): back (< 45° between the target's look and the attack direction) = 0.75, side (< 135°) = 0.25. Abilities can invert or scale this (`hammer/breach` inverts, so frontal hits are precise).
  - A stunned target gives `0.5 × poise_state.damage_multiplier`.
  - Projectile headshots give 1.0 (top hits 0.5) (`systems/projectile.rs:652–728`).
  - The `Parried` buff overrides vulnerability to 0.75.
  - In practice a backstab is only **+7.5%** with no armour precision. That's far milder than Barony.
- **Block and parry** (`compute_block_damage_decrement`, `:186`): the target must be in a blocking state (`BasicBlock` or a PARRIES/BLOCKS capability), the attack must come from within `block_angle` of where the target looks, and the attack must be in `blocked_attacks`. The decrement is `block_strength·tool.power` (shield guard 10, power guard 12.5, weapon guard 3.3). In the **parry window** (`BasicBlock.parry_window.buildup = true` for every guard ability) the whole hit is negated. The attacker then gets **`Parried` for 2 s** (poise damage, precision vulnerability, slower recovery), and the defender is refunded `energy_regen` (`entity_manipulation.rs:2562–2640`). Blocks cost poise `min(1, dmg/strength)·25`.
- **Armour protection by set** (`material_stats_manifest.ron`, split over pieces with weights chest 3, shoulder 2, pants 2, hands 1, feet 1, belt 0.5, back 0.5): cloth 10–36, leather 30, plate 51.6, bronze 45, **iron 75 (55% DR)**, steel 102 (63%), orichalcum 135 (69%). Leather and other light sets also carry `stealth`, which multiplies the detection distance by `1/(1+Σ)` (DETECTION.md).
- **Poise** (`comp/poise.rs`): humanoid base 100, regen ramps to 10/s. Falling below **50/30/15/5** gives Interrupted 0.2 s, Stunned 0.35 s (+knockback), Dazed 0.75 s and KnockedDown 1.5 s, and hits taken in those states get extra precision. Armour `poise_resilience` reduces it with the same `x/(60+x)` curve.
- **Energy** (`comp/energy.rs`): 100 for humanoids. It's spent on rolls (10), guards (5–15), special abilities and beams (drain/s), and **earned back by landing hits** (`energy_regen` in every `MeleeConstructor`).
- **Durability** (`item/mod.rs:1004, 1471`): 12 steps, **lost only on death**. Stats start dropping after 9 lost, down to ×0.25.
- **Buffs** (`comp/buff.rs`, about 60 kinds): Burning (DoT 4/s ticks, immune to Frozen), Bleeding (DoT), Poisoned (energy drain), Crippled (slow + DoT), Frozen (move/attack slow, poise vulnerability), Chilled, Wet (low friction, no burning), Ensnared, Parried, Regeneration, Potion, ProtectingWard (DR), Hastened, Fortitude, Invulnerability, Cursed, Polymorphed, …

### 2.3 Character states used by combat (`common/src/states/`)
All of them share `StageSection` (Buildup, Charge, Action, Recover, Movement), a `timer`, `movement_modifier` (default 0.7 while attacking), `ori_modifier` and `handle_interrupts` (a roll can cancel). They're driven by `tick_attack_or_default` (attack-speed buffs scale dt).

| State | Count among 611 abilities | Mechanics |
|---|---|---|
| `ComboMelee2` | 148 | A list of `strikes`, each with buildup, swing, `hit_timing`, recover, a `MeleeConstructor` and `movement` (Forward(x)). Holding or pressing during the swing chains the next strike. |
| `BasicRanged` | 72 | buildup → spawns a `ProjectileConstructor` (`Simple`, `Explosive{radius}`, `Arcing`, …) at `projectile_speed` → recover |
| `BasicMelee` | 58 | buildup → Action (hit at `hit_timing·swing`) → recover. Re-presses re-enter. |
| `BasicBeam`, `Shockwave`, `DashMelee`, `SelfBuff`, `BasicSummon`, `RapidMelee`, `FinisherMelee`, `LeapMelee`, `BasicAura` | 11–38 each | specials and monster moves |
| `ChargedMelee` | 14 | `charge_duration`. Damage, poise, knockback and energy are `base + scaled·charge_frac` (`MeleeConstructor.scaled`). |
| `ChargedRanged` | 15 | the bow "broadhead": damage 0→25 and speed 50→150 over 1.4 s |
| `BasicBlock` | 8 | buildup (parry window) → hold (Action) → recover. Movement 0.4. |
| `Throw` | 9 | buildup/charge/throw/recover. Bombs and fireworks. |
| `Roll` | default ability | see MOVEMENT.md §2.4 (i-frames vs melee, not projectiles) |

**Weapon stats scale abilities** (`ability.rs:1509`, `melee.rs:577–700`, `combat.rs:1338`):
- Durations ÷ `speed`.
- Damage × `power`.
- Poise, knockback, heals and lifesteal × `effect_power`.
- Melee range and projectile speed × `range`.
- Energy cost ÷ `energy_efficiency`.
- Buff strength × `buff_strength`.
- Block strength × `power`.

Item stats (`assets/common/items/weapons/**`):
- **Starter sword or bow**: power 0.6, speed 0.8.
- **Shield**: power 1.0, equip 0.4 s.
- **Paper knife**: power 1.8, effect 2.0.
- **Modular weapons** (`items/modular/weapon/{primary,secondary}/{sword,axe,hammer,bow,staff,sceptre}`, 7 heads × 3 hilts each) multiply primary × secondary × material. Examples: longsword 1.0, long hilt (power 1.15, speed 0.8, range 1.1, equip 1.1 s), bronze ×0.75, iron 1.0, steel 1.2, orichalcum 1.5.

### 2.4 Melee hit shape (`systems/src/melee.rs:105–335`)
The `Melee` component is inserted on the hit frame and applied once (unless `sustained`). For each entity sorted by distance:
```
hit = d(pos_a,pos_b) < r_a + r_b + scale·range
   && z-band: target's [z, z+h] overlaps [mid_a − range, mid_a + range]
   && angle_xy(look, b − a) < max_angle + atan(r_b / d_xy)
   && !is_blocked_by_wall(9 rays between attacker/target AABBs, any ray reaching target ⇒ not blocked)
first hit only unless multi_target (Normal | Scaling(k) → strength 1 + n·k)
target_dodging = roll i-frames (Dodgeable::Roll) or airborne (Dodgeable::Jump)
```
Typical values: sword slash 3 m / 45°, thrust 4.5 m / 10°, axe chop 3 m / 30°, hammer smash 3 m / 30°, dagger 3.5 m / 20–50°, shield bash 1.5 m / 45°.

### 2.5 Starting ability sets (`ability_set_manifest.ron`)
| Weapon | Primary | Secondary | Guard | Specials |
|---|---|---|---|---|
| Sword | `basic_double_slash` (Combo 5 + 7 dmg, 0.3 + 0.4 s) | `basic_thrust` (Charged Stab 0→15, 0.5 s charge, 4.5 m, 10°) | `basic_guard` (3.3, 60°) | 81 files (5 stances × moves) |
| Axe | `triple_chop` (Combo 5.5/5.5/7) | `cleave` (Charged Slash 0→18) | `basic_guard` | 25 |
| Hammer | `solid_smash` (Bash 8, poise 12.5, kb 5) | `wide_wallop` (Charged Bash 0→40, poise 70, 1.3 s) | `basic_guard` | 26 (`breach` = unblockable + guard break) |
| Bow | `arrow_shot` (7 Piercing, 110 m/s, 0.4 s) | `broadhead` (charged 0→25) | — | 39 (elemental arrows, barrage, …) |
| Staff | `firebomb` (Explosive r4, 13 dmg, Burning 10%) | `flamethrower` (beam 5×3/s, 18 m) | — | 8 |
| Sceptre | `lifestealbeam` | `healingaura` | — | `wardingaura` |
| Dagger | `tempbasic` (Stab 5, 3.5 m / 20°) | same | — | none (placeholder) |
| Shield (off-hand) | `singlestrike` (Bash 6, parries during buildup) | `power_guard` (12.5, blocks projectiles) | `basic_guard` (10, 90°, blocks everything) | — |
| NPC "Simple" sets | `Sword Simple`, `Axe Simple`, `Dagger Simple`, `Bow Simple`, `Staff Simple`, `Hammer Simple`, `Simple Flying Melee` | | | plus about 170 `Custom(...)` monster sets (Husk, Gnarling *, Haniwa *, Adlet *, Cultist *, Beast Claws, Quad Med/Low *, Minotaur, Cyclops, Harvester, …) |

### 2.6 NPC combat AI (`server/agent/src/attack.rs`, 9077 lines)
- `attack_inner` (`action_nodes.rs:1240`) maps the main-hand item's `AbilitySpec::Custom` name (or `ToolKind`) to a `Tactic` (`data.rs:190`), aims (projectile lead via `aim_projectile`), then calls one of **86 `handle_*` functions**.
- `handle_simple_melee` (`attack.rs:54`): `if in_min_range && angle < 30° → press Primary, stop; else path toward the target`. Humanoids roll at 2% per tick when within 16 m.
- `handle_simple_backstab` (`:393`, used by `Gnarling Dagger`): strafes at 4.5 m and circles behind the target's facing before striking. **That's a ready-made "sneaky monster" behaviour.**
- Weapon tactics (`sword_attack`, `axe_attack`, `bow_attack`, …) pick specials by energy, distance and combo.
- Veloren monsters attack as fast as their states allow. **There's no Barony-style cooldown.**

### 2.7 Art: weapon models and attack animations
`.vox` counts under `assets/voxygen/voxel/weapon/` (`biped_weapon_manifest.ron` has 330 `Modular(...)` + 163 `Tool(...)` entries with grip offsets):

| Kind | Models | Third-person attack animations (`voxygen/anim/src/character/{basic,multi}.rs`, keyed by ability id) |
|---|---|---|
| Sword | 85 (+270 modular components: greatsword, katana, longsword, ornate, sabre, sawblade, zweihander × 6 metals × 1h/2h) | 82 ids (double_slash, thrust, guard, all stances) |
| Axe | 73 (axe, battleaxe, greataxe, jagged, labrys, ornate, poleaxe × metals) | 25 |
| Hammer | 81 (greathammer, greatmace, hammer, maul, ornate, spikedmace, warhammer) | 26 |
| Bow | 47 (bow, composite, greatbow, longbow, ornate, shortbow, warbow × 6 woods) | 39 |
| Staff | 48 | 7 (firebomb, flamethrower, fireshockwave, fire_breath, flame_cloak, pyroclasm, napalm) |
| Sceptre | 50 | 3 |
| Shield | **2** (`wood-0`, `wood-1`) | 3 (`shield.singlestrike`, `basic_guard`, `power_guard`) + wield/roll variants |
| Dagger | **3** (rusty, basic, cultist) + 4 biped_small daggers | **none for humanoids**. Only `biped_small/combomelee.rs` animates `daggersimple`. Needs custom keys or reuse of the sword `double_slash` branch. |
| Spear | 1 humanoid (`tidal_spear`) + 4 biped_small spears | biped_small only |
| Projectiles | 43 (`simple-arrow`, `heavy-arrow`, `fire-bolt-1`, `lightning_bolt`, `ice_bomb`, `nature-bolt`, `poison_ball`, `dart`, `harlequin-dagger`, `thorn-stake`, …) | — |
| NPC weapons | 34 under `weapon/biped_small/` (axes, blowguns, bows, daggers, spears, staffs, clubs). biped_large weapons are part of each species' figure. | `biped_small/{alpha,combomelee,shoot,beam,block,dash,leapmelee,ripostemelee,rapidmelee,…}.rs`, `biped_large/{alpha,beta,combomelee,chargemelee,shoot,spin,shockwave,leapmelee,…}.rs` |

Common states: `wield`, `equip`, `stunned`, `staggered`, `roll` (with shield variant), `sneakwield`, `throw`. Humanoid hands come from `humanoid_armor_hand_manifest.ron` (left/right per glove). Licence check: **no weapon, hand or projectile `.vox` is CC-BY-NC-SA**. The only NC items in `credits.ron` are the ingot and log sprites, the `vroom` glider and seven soundtrack `.ogg` files. Note that two of those are dungeon tracks (`a_hearts_ice-cold_protection`, `still_hearts_coffin`), so don't ship them as combat music.

---

## 3. Barony vs Veloren, and what to take

| Aspect | Barony | Veloren | **Crawler** |
|---|---|---|---|
| Input | Hold to wind up and charge, release hits. Defend key. | Primary/Secondary/Guard/abilities, combos, roll | **Tap LMB** = primary (combo strikes), **hold LMB ≥ 0.25 s** = charged secondary, **RMB** = guard/shield (parry on press), **Space/Shift** = roll (optional). Number keys for 2–3 unlockable specials later. |
| Hit test | 1 ray, yaw-only, 1.5 tiles | Wedge (range + angle + z band) + wall rays, first or multi target | **Wedge with crosshair priority** (§4.3) |
| Hit timing | Instantly on release | At `hit_timing` within the Action section | Veloren timing with **short buildups** (≤ 0.15 s for tap), so it feels as immediate as Barony |
| Damage scale | Small integers (HP 30 at L1) | Floats, HP 100 for humanoids, abilities 5–40 | **Veloren scale** (HP 100, ability numbers as-is), with Barony multipliers on top (§4.2) |
| Stats | STR/DEX/CON/INT/PER/CHR, level-ups | None (skill trees, gear only) | **Barony attributes** feed `power`, `speed`, DR, MP and detection |
| Armour | Subtractive AC with 25% bypass | `p/(60+p)` DR, piercing penetration | **Veloren DR**; Barony CON adds protection |
| Block | Defend = +AC, full armour efficiency | Subtract `strength·power`, parry window, `Parried` debuff | **Veloren block and parry**; Barony shield skill scales block strength and parry window |
| Variance | Skill roll 40–60% + skill/2 | None (deterministic), precision bonuses | **Barony skill roll** (seeded), narrowed: `min(1, 0.6 + 0.1·U(−1,1) + skill/250)` |
| Backstab | Big flat bonus on unaware targets | +7.5% flank | **Awareness-gated big multiplier** (§4.4) |
| Weapon types vs races | Damage table | Damage kinds + armour | **Both**: damage kind (Slash/Pierce/Crush/Energy) indexes a per-race resistance table |
| Stagger | Knockback effect, monster hit-time reset | Poise thresholds + Stunned/Staggered anims | **Veloren poise** (simplified to 2 states: Interrupted 0.2 s and Staggered 0.75 s) |
| Stamina | None (MP for magic) | Energy for roll, guard and specials, refilled by hits | **Keep energy** but only for roll, guard and charged or special attacks. Basic attacks are free (Barony feel). |
| Durability | Per-hit degrade chance, 5 statuses | Lost on death only | **Barony per-hit degradation** with statuses; repair later |
| Progression | Proficiency 0–100 from use; XP levels | SP skill trees per weapon | **Barony proficiencies** (sword/axe/mace/polearm/ranged/unarmed/shield/stealth/sorcery…); later, unlock Veloren stance abilities at 40/60/80 |
| Magic | Spellbooks, MP, difficulty, INT | Staff and sceptre abilities | **Barony spell list and MP rules**, rendered with Veloren projectiles, beams and auras (§4.7) |
| Monster AI | Approach, 0.9 s cadence, windup poses | 86 tactics, ability sets | **Veloren ability sets and anims**, run by 5–6 ported tactics with Barony cadence (§4.8) |

---

## 4. Design

### 4.1 Architecture (headless, deterministic)
Use the same rule as MOVEMENT.md: everything below lives in a module with **no `Gdx.*`** (`:sim`, or `core/src/main/java/io/github/jvmdc/combat/` until the module split), runs at a fixed 60 Hz and takes a seeded RNG.

```
io.github.jvmdc.combat
  data/      WeaponDef, ToolStats, AbilityDef (sealed: ComboMelee, BasicMelee, ChargedMelee, BasicBlock,
             BasicRanged, ChargedRanged, Throw, BasicBeam, SelfBuff), MeleeShape, ProjectileDef,
             AbilitySet, BuffDef, RaceResist, SpellDef               ← loaded from JSON
  core/      Attack, DamageInstance{kind, value, flags}, CombatEffect, Requirement,
             DamagePipeline (precision → block → DR → kind side-effects), Poise, Energy, Health, Mana
  state/     AttackState implements CharState (MOVEMENT §4.1) with StageSection timer; BlockState
  hit/       MeleeQuery (wedge + crosshair rank + wall DDA), ProjectileSystem (swept sphere vs capsules + DDA)
  status/    Buffs (tick, stack rules, immunities)
  rules/     BaronyRules (stat multipliers, proficiency roll/gain, degradation, backstab), Factions (DETECTION)
  events/    CombatEvent bus: Hit, Blocked, Parried, Killed, StatusApplied, ItemDegraded, SkillUp, Noise
io.github.jvmdc.render.combat (client): Viewmodel, DamageNumbers, HitFlash, HudBars, CombatLog
```

Data flow per tick:
```
Input → CharacterSim (state machine) → [hit frame] MeleeQuery / ProjectileSystem → Attack.apply →
  DamagePipeline → CombatEvents → {Health/Poise/Energy/Buffs, Perception.onDamaged (DETECTION),
  NoiseBus (DETECTION sound flood, combat loudness ≈10), Progression, Degradation, UI floaters}
```

### 4.2 Damage formula (one function, fully unit-tested)
```
// at ability start (Veloren adjusted_by_stats):
eff = ability scaled by tool stats (power × status_mult × durability), speed, range, effect_power
// Barony attribute layer folded into a per-attacker modifier (cached each tick):
statMult   = melee: 1 + 0.055·STR   ranged: 1 + 0.055·DEX   thrown: 1 + 0.0156·DEX   spell: 1 + INT/100 (×2 school)
speedMult  = 1 + 0.01·DEX  (cap 1.25)                      // Barony has no attack-speed stat; mild, optional
// on hit:
dmg  = eff.damage · chargeScale · statMult
dmg *= skillRoll = min(1, 0.6 + 0.1·U(−1,1) + prof/250)    // 0.5–0.7 at skill 0, 1.0 by ~100
dmg *= raceResist[targetRace][damageKind]                   // port Barony's columns: Slash←sword, Crush←mace, Pierce←polearm/ranged, Energy←magic
dmg *= 1 + backstabBonus(awareness, angle, PRO_STEALTH)     // §4.4 (replaces Veloren flank for players)
dmg += dmg · precision · (precisionPower − 1)               // keep Veloren for headshots and stunned targets
dmg  = max(0, dmg − blockDecrement)                         // §4.5
p    = Σarmor.protection·statusMult + 2·CON                 // Barony CON→AC folded into protection
dmg *= 1 − p'/(60+|p'|),  p' = p − (Piercing ? 0.75·dmg : 0)
// side-effects by kind: Slashing→energy drain 0.5·dmg; Crushing→poise += absorbed; Energy→none
dmg  = max(dmg, 1) unless fully blocked/parried or immune
```
- **Charge**: Veloren `ChargedMelee` already scales `base + scaled·frac`. To get Barony's "full charge ×2" feel, set `base = primary strike damage` and `scaled = primary damage` and add a **+25% bonus at frac = 1** (Barony's rapier rule) as a JSON flag, `fullChargeBonus`.
- **Item status** (Barony): `EXCELLENT/SERVICEABLE/WORN/DECREPIT/BROKEN → power ×1.1/1.0/0.9/0.75/broken`, which replaces Veloren's death-only durability.
- **Worked example**: a level-1 fighter with STR 5 and sword proficiency 20 uses an iron longsword (power 1.15) on the first `double_slash` strike (5) against an unaware gnarling (HP 50, Barony "goblin" resist, slash 0.9, no armour) from behind:
  - `5·1.15·1.275·0.68·0.9·(1+1.0) ≈ 9`.
  - The second strike awake is `7·1.15·1.275·0.68·0.9 ≈ 6.3`.
  - So about 6–7 hits to kill, or a 2–3-hit opener with the backstab. The balance targets in §5 bring this down for trash.

### 4.3 First-person hit detection
Barony's single ray makes small targets (rats, bats) frustrating in 3-D, and Veloren's server-side wedge ignores where you're aiming. Combine them in `MeleeQuery`:
1. **Candidates**: use the DETECTION.md uniform grid to find actors within `r_a + range + r_b_max`.
2. **Wedge filter (Veloren)**: horizontal angle `< max_angle + atan(r_b/d)`, using the **camera yaw**. Vertical uses **camera pitch**: accept the target if the aim ray at `±maxPitchSlack` (≈25°) hits the target's capsule height band. This replaces Veloren's mid-body z band, so you can hit rats at your feet by looking down.
3. **LOS**: Veloren's 9-ray `is_blocked_by_wall`, implemented with the DETECTION.md **Amanatides–Woo DDA** over block opacity. Doors and closed props count as solid.
4. **Rank** by angle to the crosshair, then distance. Single-target abilities hit only the best candidate (Barony feel: you hit what you aim at). `multi_target` abilities (axe sweeps, hammer wide_wallop) hit everyone in the wedge with Veloren's `Scaling` falloff.
5. **Dodge**: targets in roll i-frames are skipped for `Dodgeable.Roll` attacks. Jump-dodge is off (no tall jumps in dungeons).
6. **Props and terrain**: the crosshair ray (DDA) can hit breakable blocks or props (barrels, webs, doors), as with Barony's furniture hits. Use `break_block` for pick and shovel later.
7. Run on the **hit frame** inside the sim (deterministic). Clients predict only the viewmodel and the swing sound. The authoritative hit comes from the host, as in MOVEMENT §4.6.

Projectiles (bows, thrown, spells): use the Veloren `ProjectileConstructor` data (speed, gravity from body, lifetime, explosive radius). Integrate at 60 Hz as a **swept sphere** (r ≈ 0.1 m) against actor capsules (spatial grid) and the block DDA, so no Bullet is needed (DETECTION.md keeps Bullet for physics only). Hit-location precision: head band (top 15% of height) = 1.0, from above = 0.5. Arrows stick into blocks as props for about 10 s. Thrown weapons drop as items.

### 4.4 Backstab and sneak attacks tied to detection awareness
Use DETECTION.md's per-target `MemoryRecord.awareness` and alert state:
```
state(target→attacker) ∈ {UNAWARE, SUSPICIOUS, SEARCHING, ALERT}
behind = angle(target.look, attackDir_xy) < 60°          // Barony's ±60° facing rule (Veloren uses 45°)
bonus  = UNAWARE                → behind ? 1.0 + 0.1·stealthTier : 0.5     (assassinate-from-front is weaker)
         SUSPICIOUS / SEARCHING → behind ? 0.5 + 0.05·stealthTier : 0.25   (Barony's "HUNT" reduced case)
         ALERT (targeting you)  → behind && rng(0.5) ? 0.15 + 0.03·stealthTier : 0   (Barony flanking)
stealthTier = PRO_STEALTH/20 (0..5); capstone doubles
```
- Asleep or paralyzed targets count as UNAWARE and behind.
- Show a "sneak attack" damage colour and sound. Award Stealth proficiency with Barony's odds (3/4 when unaware, 1/20 when flanking), and only once per enemy (Barony's `allowedRaiseStealthAgainstEntity`).
- Any hit sets the victim's awareness to 1 and posts `ALLY_ALERT` (DETECTION.md §4.6). Kills and hits emit a combat **noise** (loudness ≈10 at the target; a quiet dagger kill ≈ 4) into the sound flood-fill, so stealth kills don't wake the whole level.
- Monsters get backstab too: `SimpleBackstab` gnarlings get the UNAWARE/behind bonus against sneaking players who don't know they're there. Use player-side "awareness" = the monster has been in the player's view cone in the last 3 s.

### 4.5 Block, parry, roll
- **Guard** (RMB): Veloren `BasicBlock`. The weapon guard (3.3·power, 60°, melee only) and the shield guard (10·power, 90°, blocks projectiles and explosions) come straight from JSON.
  - **Parry window** = buildup (0.25 s weapon, 0.45 s shield). A successful parry negates the hit, gives the attacker `Parried` for 2 s (+0.75 precision vulnerability → your next hit gets about +7.5–20%; for Barony feel, raise `MAX_BACK_FLANK`-style precision power for parried targets to 1.5 so ripostes hurt), refunds energy, and plays Veloren's parry outcome.
  - **Shield proficiency** scales `block_strength ×(1 + skill/100)`, widens the parry window by `+skill·1 ms`, and is gained with Barony odds per blocked hit.
  - Holding guard costs energy 5 on entry, movement ×0.4–0.5, and drains poise per block (Veloren `MAX_BLOCK_POISE_COST` 25 × dmg/strength). A guard broken by poise drops into the Interrupted state.
  - Barony's "defend makes armour 100% effective" is replaced by the subtractive block. Barony's flat +AC isn't needed.
- **Roll**: MOVEMENT M5 (energy 10, i-frames vs melee, beams and explosions, not projectiles). It can cancel buildup and recover for extra energy. It's optional in difficulty settings (Barony has no dodge).
- **Monsters blocking**: biped_small `block.rs` animation plus `BasicBlock` for shield monsters (Haniwa Guard, Sahagin) give player-readable shield enemies. Hammer-type crushing breaks guards (`breach`: `TargetBlocking → Poise(40)`, unblockable).

### 4.6 Ranged and thrown
- **Bows**: `arrow_shot` (tap, 0.4 s buildup, 7 dmg) and `broadhead` (hold, 1.4 s charge, 0→25) map neatly onto Barony's tap and hold. Use **speed ×0.6** (110 → 66 m/s ≈ Barony's 22 tiles/s at 3 m tiles) and keep gravity so arrows drop over long halls.
  - Ammo: Barony quivers become an `ammo` item adding `damage` and `effect` (fire/ice/poison/lightning using Veloren `burning_broadhead`… buffs and projectile models).
  - Crossbows: no Veloren model. Use `BasicRanged` with a 0.8 s recover as a "reload" and reuse a `bow/*` model until custom art exists.
- **Thrown**: Veloren `Throw` state (buildup 0.25, charge ≤1 s, speed 20 + 20·frac) with `harlequin-dagger`, `adlet-spear`, `iron_pike_bomb`, `bubble_bomb` and `ice_bomb` models. Daggers, potions (shatter → area buff) and bombs (explosive, friendly fire) are consumed from the stack, Barony-style.
- **Monster ranged**: `Bow Simple`, `Gnarling Blowgun`, `Haniwa Archer`, `Adlet Hunter`, `Sahagin sniper` and cultist turrets. Aim with Veloren's `aim_projectile` lead, and add Barony's accuracy ramp (`MonsterRangedAccuracy`, `stat.cpp:2187`: accuracy builds up against the same target and halves after 5 s) so the first shot misses more often.

### 4.7 Magic
- **Resource**: Barony MP (base 30, + INT growth, regen interval falling with INT and items). Veloren energy isn't used for spells.
- **Spells as data**: `SpellDef { id, school, mpCost, difficulty, castTime, ability: AbilityDef }`, where the ability reuses Veloren kinds:
  - **Forcebolt** (15): `BasicRanged`, `Simple`, `nature-bolt`/`spectral` model, Energy damage.
  - **Fireball** (25): `BasicRanged`, `Explosive r=2`, `fire-bolt-1`, Burning 25%.
  - **Lightning** (25): `BasicBeam` short, or a fast projectile with `lightning_bolt`.
  - **Cold**: projectile with Frozen. **Healing**: `SelfBuff`/`Heal`. **Light**: spawn a light entity.
  - **Slow**, **sleep**, **confuse**: Veloren buffs Chilled and Ensnared, plus custom `Asleep` and `Confused`.
- **Casting failure**: Barony difficulty versus `PRO_SORCERY`/`MYSTICISM`/`THAUMATURGY`. A failed cast wastes half the MP and plays a fizzle.
- **Staves and sceptres**: in Barony, magic staffs are charged items that cast a fixed spell, and sceptres are melee. Map **Veloren `staff` weapons to "magicstaff: cast spell X with charges"** using `staff.firebomb`/`flamethrower` and their animations, and Veloren **`sceptre` to a healer staff** (lifesteal beam, healing aura) for support classes and monsters such as cultist warlocks, Sea Bishop and gnarling shamans.
- **Viewmodel**: Veloren hand plus a particle orb (Barony `act_HandMagic`). The third-person cast uses `character/basic.rs` staff branches or `throw.rs`.

### 4.8 Status effects
Implement about 12 effects, with timers in seconds, stacking by max strength, and immunities as in Veloren:

| Effect | Source | Veloren art and anim | Rule |
|---|---|---|---|
| Burning | fire spells, staff, fire arrows, lava | `Burning` particles | DoT `strength`/s, ticks 0.25 s, removed by Wet or rolling |
| Bleeding | axe/sword crits (10% in `axesimple`) | `Bleeding` | DoT 0.5 s ticks; Barony also slows regen |
| Poisoned | spiders, blowguns | `Poisoned` (energy) | Barony poison is HP DoT, so make it HP DoT + energy drain |
| Frozen / Chilled | cold spell, frost arrows | `Frozen` | move and attack slow, +poise vulnerability |
| Stunned / Interrupted / KnockedDown | poise | `stunned.rs`, `staggered.rs` | from §2.2 thresholds, simplified |
| Parried | parry | — | precision vulnerability, slower recover |
| Slow / Fast | Barony potions and spells | `Ensnared` / `Hastened` | movement and attack speed |
| Asleep / Paralyzed | Barony spells, backstab capstone | `sleep.rs` / `stunned` | no actions; next hit is UNAWARE and wakes |
| Confused / Blind / Fear | Barony | (custom tint) | AI inputs randomized, perception range ×0.25, flee |
| Regeneration / Potion | potions, healing | `Regeneration` | HoT |
| Wet | water tiles | `Wet` | friction, immune to burning |
| Invisible | spell or ring | (shader dither) | DETECTION light cap |

### 4.9 Monsters: Veloren ability sets and animations, Barony pacing
- **Loadouts**: convert `assets/common/entity/dungeon/**` and `items/npc_weapons/**` to JSON. A monster then gets `{body, health (Veloren base_health), poise, ability set (from the weapon's ability_spec or ToolKind), tactic, raceResist row}`.
- **Tactics to port first** (all from `attack.rs`):
  - `SimpleMelee` (`:54`): 30° and in-range strike, else path.
  - `SimpleBackstab` (`:393`): circle behind.
  - `SwordSimple`, `AxeSimple`, `DaggerSimple`: combo + dash.
  - `Bow`/`ElevatedRanged`: keep distance, shoot, retreat.
  - `Staff`: firebomb at range, flamethrower close.
  - `QuadMedBasic`/`QuadLowBasic`: animals.
  - Bosses later (`minotaur_attack`, `cyclops_attack`, `harvester_attack`).
- **Barony cadence** on top: `attackCooldown = 0.9 s × tacticFactor` (bows 1.5×) between ability starts, and a mandatory **telegraph** (≥ 0.3 s buildup; use the ability's buildup or lengthen it ×1.5 for monsters). Hits while in buildup can Interrupt (poise ≤ 50), like Barony resetting `monsterHitTime` on knockback. Many Veloren monster buildups are 0.1–0.2 s, which reads as unfair in first person.
- **Damage scaling**: Veloren monster abilities are tuned against players with 55–65% armour DR, so multiply monster damage by a `difficulty` table (start ×0.6 at depth 1, rising to 1.0 by depth 10).
- **Animations** are driven by `(CharacterState kind, StageSection, progress)`, exactly as voxygen's figure picker does. The needed biped_small anims are `alpha`, `combomelee`, `shoot`, `block`, `dash`, `leapmelee`, `stunned` and `wield`. biped_large needs `alpha`, `beta`, `combomelee`, `chargemelee`, `shoot`, `spin` and `stunned`, plus quadruped attack anims. That's mostly 100–400 lines of Rust each, ported alongside VELOREN_ANIMATION P3.

### 4.10 Viewmodel timing (first person)
- One `ViewmodelTrack` JSON per `(weaponKind, abilityKind, strikeIndex, StageSection)` holding eased targets `{pos, yaw, pitch, roll}` over section progress. That's Barony's `HUDWEAPON_*` easing, made data-driven.
- Progress comes **from the same `AttackState` timer** that drives the third-person `basic.rs`/`multi.rs` animation others see, so both views agree on timing (MOVEMENT M6).
- Barony cues to keep:
  - Windup lowers and raises the weapon; full charge shows a shake or glow (Barony overcharge).
  - The hit frame gets camera kick +6 (scaled to about 0.5° pitch) and **hitstop** of 40–60 ms on connect (pause the attacker's state timer and the target's animation).
  - Recover eases back.
  - Guard raises the shield to centre-left. A parry flash plays during the window.
  - The bow draws back with the charge fraction.
- Tap versus hold: start primary buildup on press. If the button is still held at `tapThreshold` (0.18 s), switch to the charged secondary's Charge section, the way Veloren's `ChargedMelee` charge loop works. On release, enter Action.

### 4.11 Feedback and UI
- **Damage numbers**, Veloren `HpFloater` style (`voxygen/src/hud/mod.rs:1702–1730`): world-space numbers that accumulate per target for 1 s.
  - Colours: white normal, yellow precise or headshot, red sneak attack, grey blocked, cyan parried, green heal.
  - The player's own damage shows as a red vignette, as in Barony (no numbers on self).
  - Barony's `spawnDamageGib` uses the same idea.
- **Enemy health bar**: Barony-style top-centre bar for the last-hit target (name, HP, statuses), plus Veloren overhead bars optional.
- **Hit flash** (white tint, 80 ms), blood or spark particles by damage kind, Veloren `Outcome::Block` sparks, knockback impulse, death ragdoll-lite (tip over plus a dissolve).
- **Combat log** with Barony-style messages ("You hit the goblin!", "Your sword is damaged.", "You feel more skilled with swords.").
- **Sounds** via an `Outcome` event to SFX ID mapping. Check each Veloren SFX's licence in `credits.ron` before shipping.

### 4.12 Balance
- **Scale**: HP 100 for humanoids, armour DR curve `p/(60+p)`.
- **Targets** (tunable in `balance.json`):
  - **Time-to-kill** for an equal-depth fighter with a basic weapon at skill 20: trash 3–4 swings (≈2 s), elites 8–12, minibosses 25–40 with telegraphed specials.
  - **Time-to-die**: 6–10 trash hits at matching armour. A depth-1 player with cloth (DR ≈ 15%) takes 6–9 damage per gnarling hit.
  - **Backstab opener** ≈ 50–70% of a trash enemy's HP at Stealth 20, a one-shot at Stealth 60+.
  - **Skill curve**: average damage ×0.6 at 0, ×1.0 at about 100. Barony's curve is steeper (×0.5 → ×1.0 by 80) but keeps the "practice matters" feel.
- **Harness** (`CombatSim` CLI, headless): run N duels per pairing (player loadout × monster × depth), simulating swing cadence, block probability and roll. Report TTK, TTD and DPS distributions as CSV, and run it in CI as a regression check with ±10% tolerance bands.

### 4.13 Data pipeline (JSON from Veloren `.ron`)
- Reuse `Converter`/`RonToJsonVisitor` (already present) to dump:
  - `abilities/**`, `ability_set_manifest`
  - `items/weapons/**`, `items/modular/**`, `material_stats_manifest`
  - `items/npc_weapons/**`, `entity/dungeon/**`, `loadout/dungeons/**`
  - `biped_weapon_manifest`
- Then a **typed importer** (`AbilityImporter.java`) whitelists the ability kinds you support (§2.3 table). It maps externally tagged JSON to sealed `AbilityDef` records and fails loudly on unknown fields, mirroring Veloren's `deny_unknown_fields`. Keep the Veloren id (`common.abilities.sword.basic_double_slash`) as the key, because the animation picker needs it.
- Crawler-specific data lives beside it in `assets/data/combat/`:
  - `weapons.json`: Barony-style weapon list `{id, veloren_item, kind, proficiency, handed, baseAtkBonus, weight, value}` (Barony bronze/iron/steel/crystal tiers become Veloren materials bronze/iron/steel/cobalt/orichalcum).
  - `race_resist.json`: port Barony's table structure with your own values for Veloren species.
  - `spells.json`, `statuses.json`, `balance.json`.
- Example:
```json
{ "id": "iron_longsword", "item": "Modular:sword.longsword+iron+Two", "kind": "Sword",
  "proficiency": "SWORD", "stats": {"power": 1.15, "speed": 0.8, "range": 1.1, "effect_power": 1.1},
  "abilities": {"primary": "common.abilities.sword.basic_double_slash",
                "charged": "common.abilities.sword.basic_thrust",
                "guard":   "common.abilities.sword.basic_guard"},
  "fullChargeBonus": 0.25, "weight": 30, "status": "SERVICEABLE" }
```

### 4.14 Tests (JUnit, seeded RNG)
- **Formula golden tests**:
  - `Damage.computeDamageReduction` vs hand values (iron set 75 → 0.5556; piercing 20 dmg at p 75 → 60/(120) = 0.5).
  - Slashing energy drain and overflow.
  - Crushing poise from absorbed damage.
  - Block subtract.
  - Parry negation + `Parried` applied.
  - Precision math (`1 + prec·(power−1)`).
  - Barony getAttack (`8+5` at STR 10 → 20.15), skill roll bounds, race resist, charge ×2 + 25%.
  - Each test is pinned to the source line it reproduces.
- **State timing**: `ComboMelee2` with sword `basic_double_slash` produces exactly two hits. The first lands at 0.15 + 0.5·0.05 = 0.175 s, and the second at its own buildup + 0.5·swing after the chain point (verify the chain/recover order against `combo_melee2.rs:160–245` when writing the test). Both scale by `1/speed`, and a roll cancels them. `ChargedMelee` damage is linear in frac.
- **MeleeQuery**:
  - A target at 44° and 46° vs a 45° wedge, accounting for the `atan(r/d)` term.
  - A wall between them blocks.
  - A rat below the crosshair is hit when pitched down.
  - Single vs multi-target, and roll i-frames.
- **Projectiles**: no tunnelling at 110 m/s through 0.4 m capsules at 60 Hz, a headshot band, a block hit stops the arrow.
- **Backstab**: the awareness × angle matrix, and the stealth gain once per enemy.
- **Determinism**: the same seed and inputs give the same event log (needed for co-op).
- **Data**: every whitelisted ability JSON loads, and every referenced ability id exists in the importer and the animation picker.

---

## 5. What to port vs simplify

| Veloren piece | Port? | Notes |
|---|---|---|
| `combat.rs` `Attack`/`AttackDamage`/`Damage`/DR/block/knockback/requirements | **Port** (≈500 of 2440 lines) | Drop PvP, groups, auras, transforms, death effects and combo requirements beyond `Combo(n)` |
| `comp/melee.rs` `MeleeConstructor` (Slash/Stab/Bash/Hook; `scaled`) | **Port** | Skip NecroticVortex and SonicWave until needed |
| `systems/melee.rs` wedge + wall rays | **Port + modify** | Crosshair rank and pitch band (§4.3) |
| `states/{basic_melee,combo_melee2,charged_melee,basic_block,basic_ranged,charged_ranged,throw}` | **Port** (≈1.3k lines → ≈700 Java) | On the MOVEMENT M4 stage framework |
| `DashMelee`, `LeapMelee`, `Shockwave`, `BasicBeam`, `SelfBuff`, `BasicAura` | **Later** (monster specials, spells) | About 0.5 d each |
| Poise (5 states) | **Simplify** to Interrupted and Staggered (+KnockedDown for bosses) | |
| Energy | **Keep**, limited to roll, guard and charged/specials | |
| Buffs (60) | **Subset** (§4.8) | |
| Skill trees and SP | **Replace** with Barony proficiencies | Stance abilities can unlock at skill tiers |
| Combo counter | **Optional** (axe "execute at 30 combo") | |
| Durability | **Replace** with Barony per-hit statuses | |
| Agent tactics (86) | **Port 6** and write the rest per monster as needed | Barony cadence and telegraph wrapper |

| Barony piece | Take | Notes |
|---|---|---|
| `getAttack`/`getRangedAttack`/`getThrownAttack` stat multipliers | **Formula** | BSD notice if code is ported; numbers alone need no notice |
| Proficiency roll and gain odds, weapon/armour degradation odds | **Formula** | |
| Backstab/flank rules | **Adapt** to awareness | |
| `damagetables` | **Structure only** | Write your own values for Veloren species |
| HUD weapon easing and charge | **Technique** | Data-driven tracks |
| Spell list, MP, difficulty, INT bonus | **Rules** | Art from Veloren projectiles |
| Monster `HITRATE` cadence and windups | **Rule** | |

---

## 6. Licensing
- Veloren-derived Java files get the GPL header with source path, commit `ebfff94` and "Copyright (C) Veloren contributors, modified". Converted JSON data and `.vox` assets are GPL-3.0 via `credits.ron`; list them in NOTICE. None of the weapon, hand or projectile models are NC. **Don't ship the NC dungeon soundtracks** (`a_hearts_ice-cold_protection.ogg`, `still_hearts_coffin.ogg`) or the other NC items.
- Files that port Barony **code** (for example a literal `getAttack`/`AC` translation) must carry the **BSD-2-Clause notice, conditions and disclaimer** (header template in MOVEMENT.md §6), and the NOTICE file must reproduce it for binaries. Re-implementing the **formulas and tuning numbers** in your own code is fine without it, but credit is polite. **Never use Barony's data files** (monster stats, item sprites, sounds, `items.json`, spell icons).

---

## 7. Prioritized plan

Prerequisites (from earlier reports):
- MOVEMENT M0–M6: input, `CharacterSim`, camera, Barony movement rules, the stage framework (Melee, Block, Stunned), roll and energy, and the viewmodel pass.
- VELOREN_ANIMATION P0–P5: bone meshes, skeletons and the anim loop.
- NAV_COLLISION slices 0–3: block volume and capsule.
- DETECTION MVP: grid, awareness, alert states, noise.

| # | Slice | Effort | Key references |
|---|---|---|---|
| C0 | Data pipeline: run the converter on ability, item, modular, material, npc_weapon and entity dirs. Typed `AbilityImporter` for the 8 supported kinds, `WeaponDef`/`ToolStats` (modular multiply), `AbilitySet` lookup. `weapons.json`, `race_resist.json`, `balance.json`. | 1.5–2 d | `converter/Converter.java`, `RonToJsonVisitor.java`; `ability.rs:1509` (`adjusted_by_stats`), `ability_set_manifest.ron`, `material_stats_manifest.ron` |
| C1 | Combat core: `Attack`, `DamageKind`, effects and requirements, DR, block/parry, precision, poise (simplified), knockback impulse, energy, `CombatEvent` bus. Golden tests. | 2–3 d | `combat.rs:126–650, 1245–1900, 2288–2420`; `comp/poise.rs`; `entity_manipulation.rs:2562` |
| C2 | Barony rules layer: attributes → stat multipliers, skill roll, race resist, charge bonus, item status, `CON` → protection, backstab-by-awareness, proficiency gain, degradation. Tests. | 1.5–2 d | `entity.cpp:8151–8366, 12756–13460, 16915`; `stat.cpp:1490–1590`; `items.cpp:4601–4870` |
| C3 | Attack states on the M4 framework: `ComboMelee2`, `BasicMelee`, `ChargedMelee` (tap/hold), `BasicBlock` (parry window, guard energy), `BasicRanged`, `ChargedRanged`, `Throw`; stat scaling; roll cancel. | 2–2.5 d (M4 covers ≈1 d of this) | `states/*.rs` as listed; `utils.rs` `handle_interrupts` |
| C4 | `MeleeQuery`: grid candidates, wedge + pitch band, crosshair rank, 9-ray DDA wall test, multi-target scaling, i-frames, prop hits. | 1–1.5 d | `systems/melee.rs:105–419`; DETECTION §4 DDA |
| C5 | Projectiles: swept sphere vs capsules and blocks, gravity, headshot precision, sticking arrows, explosive radius/falloff, thrown item drop, ammo effects. | 1.5–2 d | `comp/projectile.rs`, `systems/projectile.rs:640–740`; Barony `actarrow.cpp:336–420` |
| C6 | Status effects (§4.8, about 12) with ticks, immunities, UI icons. | 1.5–2 d | `comp/buff.rs:385–600` |
| C7 | Feedback: damage floaters, enemy HP bar, hit flash, hitstop, camera kick, outcome → SFX hooks, combat log. | 1.5–2 d | `voxygen/src/hud/mod.rs:1702–1760`; Barony `spawnDamageGib`, `acthudweapon.cpp:2079` |
| C8 | Viewmodel combat tracks (on M6) for sword, axe, hammer/mace, dagger, shield guard/bash, bow draw, staff cast, synced to the state timer; charge glow and parry flash. | 3–4 d | `acthudweapon.cpp:1900–2700`; `character/{basic,multi}.rs` for timing |
| C9 | Monster combat: loadout import, 6 tactics, Barony cadence and telegraph wrapper, accuracy ramp, difficulty damage scale; biped_small/large anims `alpha`, `combomelee`, `shoot`, `block`, `dash`, `stunned`, `wield` + quadruped attacks. | 4–6 d | `attack.rs:54, 393, …`; `action_nodes.rs:1240–1406`; `voxygen/anim/src/biped_*`; Barony `actmonster.cpp:10279–10560` |
| C10 | Magic: MP and regen, `SpellDef` list (forcebolt, fireball, lightning, cold, healing, light, slow, sleep), cast failure, magicstaff charges, hand viewmodel, Veloren staff/sceptre anims for casters. | 3–4 d | Barony `magic/{setupSpells,spell,castSpell}.cpp`; Veloren `abilities/{staff,sceptre}`, `weapon/projectile/*` |
| C11 | Progression: XP and levels with class stat growth, proficiency tiers and notifications, Stealth/Shield skill gain, repair hook. | 1.5–2 d | Barony `entity.cpp:4040–4120`, `stat.hpp:172–200` |
| C12 | `CombatSim` balance harness + CSV + CI bands; first tuning pass. | 1.5–2 d | §4.12 |
| **Total** | | **≈ 26–36 d** | |

**MVP ("it feels like Barony with Veloren art")** is about 10–13 days:
- C0 (sword, axe, dagger, shield only)
- C1
- C2 without degradation
- C3 (combo, charged, block)
- C4
- C7 numbers + flash + hitstop
- C8 sword + shield
- C9 with `SimpleMelee` + `SimpleBackstab` (gnarling dagger/axe, husk)

Then C5 + bows, C6, C10, C11, C12.

Suggested order: C0 → C1 → C2 → C3 → C4 → C8 (alongside M6) → C7 → C9 → playtest → C5 → C6 → C10 → C11 → C12, then rebalance.

---

## 8. Open design decisions for you
1. **Dodge roll on or off?** Veloren's animations and i-frames are there (MOVEMENT M5), but Barony has no dodge. A setting would work, with on giving an "action" difficulty and off giving a "classic" one.
2. **Stamina for basic swings?** The recommendation is no (Barony), with energy only for guard, roll and charged or special attacks.
3. **Variance**: keep Barony's skill-roll band (variance and proficiency matter) or make it deterministic like Veloren?
4. **How big a backstab?** ×2 at Stealth 0 is very strong with Veloren's light hit counts. An alternative is the flat Barony bonus scaled ×5 to the Veloren HP scale.
5. **Daggers and spears** have thin Veloren humanoid art (3 dagger models, 1 spear, no humanoid dagger anim). Either reuse the sword one-hand animations or author a few `.vox` and anim branches.
6. **Shields**: only two humanoid shield models exist (`wood-0`, `wood-1`). Plan to author bronze, iron and tower shields in Veloren style if shield builds matter.
