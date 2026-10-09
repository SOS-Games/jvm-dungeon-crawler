# Primitive NPC bots for jvm-dungeon-crawler: Barony followers + Veloren agents, one shared brain

This is research only. Nothing was changed, committed or opened as a PR in any repo.

**Sources read**
* **Barony** `TurningWheel/Barony` @ `962a5ce` (2026-02-20), BSD-2-Clause code with closed game data: `src/actmonster.cpp` (15.4k lines; `actMonster` runs from `:2652` to `:10277`), `src/monster.hpp`, `src/entity.cpp`, `src/entity.hpp`, `src/game.cpp`, `src/stat.hpp`, `src/game.hpp`, `src/interface/interface.cpp`, `src/actplayer.cpp`.
* **Veloren** `gitlab.com/veloren/veloren` @ `ebfff94` (2026-10-07), GPL-3.0: `common/src/comp/agent.rs`, `common/src/comp/pet.rs`, `server/src/sys/agent/{mod.rs,behavior_tree/mod.rs,behavior_tree/interaction.rs}`, `server/agent/src/{action_nodes,util,consts,attack}.rs`, `server/src/pet.rs`, `server/src/sys/pets.rs`, `server/src/events/{inventory_manip,interaction}.rs`, `rtsim/src/rule/simulate_npcs.rs`, `rtsim/src/data/sentiment.rs`, and the header of `rtsim/src/rule/npc_ai/mod.rs`.
* **Crawler** `SOS-Games/jvm-dungeon-crawler` @ `6093f3a`. It has no AI code yet. `core` already declares gdx-bullet and recast4j/detour 1.5.7, which are unused.
* **Prior reports** in `/workspace`: `MOVEMENT.md`, `DETECTION.md`, `COMBAT.md`, `NAV_COLLISION_PORT.md`, `LOOT.md`, `DUNGEON_GEN_PORT.md`, `BARONY_ENVIRONMENT.md`, `UI.md`.

**Units used below:** 1 Barony tile = 16 map units = 3 m (3×3 blocks, from BARONY_ENVIRONMENT). Barony runs at 50 ticks/s and the crawler sim at 60 Hz (MOVEMENT).

---

## 0. TL;DR

* **Barony's monster AI** is a single 7,600-line function (`actMonster`) built around five generic states: `WAIT` (idle, wander, scan, follow leader), `ATTACK` (chase, strafe, back off with ranged weapons, swing), `PATH` (path toward a target's last position), `HUNT` (walk a computed path) and `TALK` (shopkeeper or NPC conversation). Bosses add about 15 special states. **Followers are ordinary monsters with `leader_uid` set**, and everything else follows from that: factions, following, commands and XP. **Recruitment has no dice roll.** It's a set of deterministic gates: same-race or ally-race lists, whether the NPC is friendly toward you, a follower cap of `min(8, max(4, 2·⌊Leadership/20⌋))`, and for named NPCs `Leadership + CHR ≥ 60`. **Commands are gated by `Leadership + CHR`** (for example, attack-target needs 60, and attacking a friendly target needs 80). Followers keep **formation slots** behind the leader, re-check them once per second with a back-off when paths fail, travel with you between floors, get full XP from your kills, and **go "rabid" (revert to their race's hostility) when the leader dies**. Shopkeepers use a per-player **wanted level**. Hitting one makes every shopkeeper on the level hostile to you.
* **Veloren's agent** isn't really a behavior tree. It's a **priority list of `fn(&mut BehaviorData) -> bool`**, where the first rule that returns true wins. The lists are `root → target | idle`, `target → hostile | save allies | pet | pickup | idle`, and `pet → follow_if_far_away | attack_if_owner_hurt | idle`. The agent **writes into the same `Controller` the player uses**. Pets are `Alignment::Owned(uid)`. They're tamed with a collar within 5 m (max 3, quadrupeds, birds and crabs only), follow beyond 12.5 m, can be told to *stay* (10 m leash), avenge their owner within 5 s of the owner being hit, and teleport to the owner when more than 200 m away. Humanoids **menace** (wield and warn for 5 s) before attacking. `rtsim` adds **sentiments** from −1 to 1 with named thresholds (ENEMY −0.6, RIVAL −0.3, ALLY +0.3, FRIEND +0.6), raised or lowered by hurting, healing and rescuing.
* **What I'd build:** one `Brain` in plain Java, shared by enemies, neutrals, shopkeepers and followers. It's a **priority-list of reflex rules (Veloren) that selects one Mode from a small FSM (Barony's states, cleaned up)**, with **utility scoring only where a choice is genuinely needed**: target selection and whether to flee. Roles come from JSON. Disposition = faction relation (DETECTION) + personal sentiment (Veloren rtsim) + leader link (Barony). The brain outputs the **same `Input` record the player produces** (MOVEMENT M0), so NPCs reuse the character controller, combat states and animations for free. Pathing goes through a budgeted `PathService` on the recast4j navmesh. Followers add a roster, Barony's recruit gates and formation, a typed `Command` inbox, and a scene2d radial menu.
* **Why not a behavior-tree library or GOAP:** "primitive" bots need about 15 rules and 12 modes. A flat priority list is readable top to bottom, deterministic, allocation-free, trivially traceable ("rule #4 fired") and easy to unit-test. gdx-ai's BT (1.8.2, last release July 2019) or GOAP adds machinery without adding behavior. Keep the option open: a Mode can later be implemented with a gdx-ai tree if one gets complicated.
* **Effort:** **MVP is about 8–11 dev-days.** That covers enemies that wander, notice, fight, flee and search, plus recruitable followers with follow, wait, attack and move-to. **The full system is about 16–23 days**, adding disposition events, shopkeepers and wanted levels, all Barony commands with skill gates, equip/feed/give, the radial menu UI, level transfer, XP and leveling, the debug overlay and the scenario test suite. It sits on top of MOVEMENT M0–M1 + N1, NAV slices 0–5, the DETECTION MVP, and the COMBAT C1/C3/C9 subset.

---

## 1. Barony (BSD-2-Clause code; numbers and logic only)

### 1.1 Monster state machine (`monster.hpp:975–997`, `actmonster.cpp`)
| State | Line | What it does |
|---|---|---|
| `WAIT` (0) | `:5235` | Idle. Every 120 ticks it picks a random look direction. If `monsterMoveTime` hits 0 and it has **no leader (or is in DEFEND)**, it wanders to a random free tile (`:5905`). Perception and acquisition run on the 1 Hz "reflex" tick (DETECTION §1.1–1.2). **Followers run the follow-leader block here** (`:5596`, §1.4). Player allies also actively scan for enemies they can see (`:5799–5850`). |
| `ATTACK` (1) | `:6031` | Chase the target. Melee closes to strike range. Ranged units hold at `effectiveDistance − 10` and **strafe or back off** (`backupWithRangedWeapon`, `entity.cpp:26078`). It swings when `monsterHitTime ≥ HITRATE (45 ticks = 0.9 s)`. On losing sight it goes to `PATH`, or to `WAIT` if `shouldRetreat`. |
| `PATH` (2) | `:7007` | Build a tile path to `monsterTargetX/Y` (the last known position), then switch to `HUNT`. |
| `HUNT` (3) | `:7084` | Walk the path. Followers re-check the leader here as well, with `HUNT_FOLLOWDIST 64` (`:7478`). Ally move-to arrival and interact are handled here too (`:8164–8350`). |
| `TALK` (4) | `:8410` | Stand still and face the talker. Shopkeeper trade happens in this state, and leaving it closes the shop (`:10241`, `entity.cpp:24498`). |
| `LICH_*`, `DEVIL_*`, `GENERIC_DODGE/CHARGE` | `:8477–9858` | Boss scripts. Out of scope for "primitive" bots. |

Other mechanics worth copying:
* **Retreat** (`entity.cpp:25926 shouldRetreat`): flee when `HP ≤ MAXHP/4` (or `≤ MAXHP/8` if `MAXHP ≥ 100`) **and CHR ≥ −2**. Fear and pacify effects force it on. Many races are exempt (shopkeeper, vampire, shadow, mimic, summons, turrets). **Player followers never retreat ("brave leader", `:26056`).**
* **Move aside** (`monsterMoveAside`): a friendly NPC blocking the player steps out of the way when talked to or bumped.
* **Monster leaders:** the same `leader_uid` mechanism lets monster bosses lead squads, for example the goblin "potato king" with 3 followers (LOOT §1.5). Formations work for monster leaders too (`:867`).
* **Code shape to avoid:** one giant function with state in `skill[]`/`fskill[]` slots, plus special cases by race inline. That's why it can't be ported as-is. Port the rules, not the structure.

### 1.2 Recruitment (`makeFollower`, `actmonster.cpp:1597–2240`; talk dispatch `:4490–4640`)
Using "talk" (the interact key) on a mobile monster does the following:
1. If it's angry at you, it barks a threat ("En garde!"). If it's in `TALK` with someone else, it says "busy". Shopkeepers and traders open trade. NPCs with `STAT_FLAG_NPC` give chatter lines (`handleMonsterChatter`). **Everything else tries `makeFollower`.**
2. `makeFollower` gates, in order:
   * **Ring of conflict** → fail.
   * **Already has a leader** → greet, and if it's friendly and follows someone else, move aside.
   * **Normal path:** `tryAlly = checkFriend(player)`. That's the race ally matrix, so a human player can recruit friendly humans. **Named, non-generic, non-human NPCs refuse if `Leadership + CHR < 60`** (`:1869`). The cap is `allowedFollowers = min(8, max(4, 2·⌊Leadership/20⌋))`, so 4 up to Leadership 59, 6 at 60–79 and 8 at 80+. Tinkering bots don't count toward it.
   * **Race lists** (`:1906–2045`): the **player's race** decides which races join. Some examples: goblin→goblin, rat→rat, spider→spider/scarab/scorpion, skeleton player→ghoul, vampire→vampire, succubus/incubus→succubus/incubus (or a *drunk/confused* human), automaton→automaton/human, troll→troll. Humans can always recruit humans who are friendly to them.
   * **Leadership capstone (100)** (`:1670–1730`): up to 8 followers, and **it skips the friendliness check** for humans (human player), goblins, goatmen, insectoids and automatons.
   * **Overrides:** `MONSTER_FORCE_PLAYER_RECRUITABLE`, the Penance effect, and the Rose mask (incubus/succubus).
3. On success (`:2116–2240`): add the NPC to `stats[player]->FOLLOWERS`. Humans get a random name and a nametag. A dialogue tooltip says "I'll follow you" (for humans with INT > −2) or a generic "the X decides to follow you". Then: particles, **move aside**, **+Leadership skill**, `monsterState = WAIT`, `leader_uid = player`, `monsterAllyIndex = player`, tint changed to ally colours (`USERFLAG2`), and **every other follower drops this NPC as a target**.
4. There's no random roll anywhere. **"Charisma" matters through the `Leadership + CHR` sum** (named NPCs, command gates) and through Leadership for the cap.
5. **Other ways to recruit:** `forceFollower` (`:11143`) for charm or command spells, summons, thrown items, tinkering bots, leashes (`actitem.cpp:287`). It moves the follower between leaders' lists.

### 1.3 Follower command menu (`monster.hpp:598–760`, `monsterAllySendCommand` `actmonster.cpp:12435–13108`, `FollowerRadialMenu` `interface/interface.cpp:4402–4830`)
`skillLVL = Leadership + CHR` (Tinkering + PER for bots; Mysticism + CHR under the Command spell). The command is refused if the follower is targeting its own leader ("angry at owner") or is immobile (asleep wakes from Rest on move/attack/move-aside).

| Command | Skill gate (`AllyNPCSkillRequirements`) | Behavior |
|---|---|---|
| **Follow** | 1 | `monsterAllyState = DEFAULT`, rejoin formation |
| **Wait / Defend** | 1 | `ALLY_STATE_DEFEND`, `createPathBoundariesNPC(5)`, **leashed within 5 tiles** of the spot, still fights. Turrets store a look direction. |
| **Move to** (select → confirm) | 20 | Path to a tile (`GENERATE_PATH_PLAYER_ALLY_MOVETO`). Barks begin, end, fail and repath. If the target was an **entity** (lever, item, gold, teleporter), it **interacts** on arrival within 24 units (1.5 tiles): `monsterAllySetInteract` → `FollowerMenu.entityToInteractWith`. At a teleporter it then switches to Defend. |
| **Attack** (select → confirm) | 20 to select, **60 to confirm**. Creature tiers: tier 2 (imp, demon, kobold, goatman, insectoid…) needs **80**, tier 3 (golem, vampire, cockatrice, shadow) needs **100** | `monsterAcquireAttackTarget(target, ATTACK)`. A **friendly target** is only accepted at ≥80 **or** for non-human followers, and only if friendly fire is on (or the target isn't FF-protected). Otherwise it barks `ATTACK_FRIENDLY_FIRE`. If the selection isn't a creature, it **moves to** it. |
| **Pick-up toggle** | 20 | Cycles `NONPLAYER (default) → NONE → ALL`. By default followers **don't take items the player dropped** (or anything dropped in the last 5 s) (`entity.cpp:24601, 24861`) |
| **Class toggle** | 40 | `MIXED → MELEE → RANGED` weapon preference. Only humans, goblins, automatons and goatmen. |
| **Drop equipment** | 40 | Below 80 it drops the weapon only. At 80+ it drops armour (helm, breastplate, boots, shield, cloak). At 100+ also ring and amulet. Named special humans refuse. Dropped items record `itemOriginalOwner`. |
| **Special: Rest** | 40 | 60 s asleep + HP regen, then locked until the next floor. 1/3 chance of +Leadership. |
| **Move aside** | 0 | Steps out of the leader's way |
| **Return soul / bot commands** | 0 | Summons and tinkering bots only |

UI: a radial menu around the crosshair, opened on a follower (or on the most recent follower). "Select" modes then take a world click: a tile, a creature or an interactable. Disabled options show their required level (`optionDisabledForCreature` returns the requirement, or −1 if the creature can't do it).

### 1.4 Following the leader (`actmonster.cpp:5596–5760`, `MonsterAllyFormation_t` `monster.hpp:1386`, `:820–1180`)
* **Check rate:** once per second, per follower, staggered by UID. `getFollowerChaseLeaderInterval` stretches it by `pathingDelay` seconds.
* **Formation slots** (`formationShape`): two slots beside the leader (±2 tiles lateral), then alternating rows behind it, `(-1,-i),(1,-i)` for odd i and `(-2,-i),(2,-i),(0,-i)` for even i. Melee units fill slots first and **ranged units are listed separately** (assigned after, so further back). A slot is only accepted if a **line trace from the slot reaches the leader**. Slots are recomputed at most once a second per leader.
* **Trigger:** follow if `dist > WAIT_FOLLOWDIST (48 units = 3 tiles = 9 m)`, or `HUNT_FOLLOWDIST 64 = 12 m` while hunting, **or the leader isn't visible**. It won't break off while `ATTACK`ing or within `2·TOUCHRANGE` of its target (`isFollowerFreeToPathToPlayer`, `:14716`).
* **Back-off:** each failed path adds `pathingDelay = min(10, +1)`, and at 5 it tries an extended search (`updateOnPathFail` `:860–890`). This is what keeps 8 followers from re-pathing every tick in a blocked corridor.
* **Small fliers** (gyrobot, duck, fire moth) instead follow **ahead** of the leader, raycasting along the leader's yaw up to the follow distance.
* **Between floors:** **all followers** are copied with their stats and respawned around the player on the next level, keeping class and pickup settings (`game.cpp:2014–2055, 2470–2510`). There's no in-level teleport for normal followers. They path, or get left behind until the stairs.

### 1.5 Followers: XP, leveling, equipment
* **The player kills something:** every follower within `XPSHARERANGE` (99999, so the whole level) gets the **full** XP amount, not a split (`entity.cpp:18020–18090`). Summons, bots and elementals are excluded. "Inspiration" (Charisma-related) multiplies it.
* **A follower kills something:** the leader gets XP (`leader->awardXP`, `:18671`) and **+Leadership**.
* **Monster level-up:** at 100 EXP, `LVL+1`, **+1 to three distinct random stats** (`monsterRollLevelUpStats` `:3792`), `MAXHP += HP_MOD (5)` (`entity.cpp:4065–4300`).
* **Equipment:** monsters with hands pick up items within range and **equip if better** (`monsterAddNearbyItemToInventory` `:24671`, `monsterWantsItem` `:25224`, `shouldMonsterEquipThisWeapon` `:25158`). To arm a follower, you drop gear and either set pickup to ALL or use **Move to → interact** on the item (that sets `interactNPCUid` so the follower may take it). Food interactions have barks for good, bad, rotten and full, which is how you feed them.

### 1.6 Friendly fire, betrayal, leader death
* Server flag `SV_FLAG_FRIENDLYFIRE`. With it off, a player's hits on friends that are FF-protected (players and player-led monsters, `friendlyFireProtection` `entity.cpp:19394`) are **ignored entirely** (`entity.cpp:11551–11606`, plus arrows, thrown items and spells).
* **Ally-on-ally hits** between two player allies don't trigger aggro back (`monsterAlertBeforeHit` `actmonster.cpp:721`), so splash damage doesn't start civil wars.
* With FF on, hitting your follower goes through the normal "hit → acquire attacker" path, and the follower then refuses commands while targeting you. I didn't find code that clears `leader_uid` on a hit, so **betrayal in Barony is temporary anger, not leaving**. Treat that as unverified at runtime.
* **Leader dies:** followers are removed from the list, `leader_uid = 0`, and the tint is reset ("now rabid", `actplayer.cpp:10985–11010`). They fall back to their race's default relations, which for most races means hostile to players.

### 1.7 Shopkeepers and neutral hostility (`ShopkeeperPlayerHostility_t`, `monster.hpp:1323`, `actmonster.cpp:326–719`)
* Each player (per race) has a **wanted level**: `NO_WANTED_LEVEL < FAILURE_TO_IDENTIFY < WANTED_FOR_AGGRESSION_SHOPKEEP_INITIATED < WANTED_FOR_ACCESSORY < WANTED_FOR_AGGRESSION < WANTED_FOR_KILL`. It only ever increases (`setWantedLevel`).
* **Hitting** a shopkeeper sets `WANTED_FOR_AGGRESSION`. **Killing** one sets `WANTED_FOR_KILL`. Nearby co-op players become `WANTED_FOR_ACCESSORY`. A follower killing a shopkeeper counts against its leader.
* `checkEnemy` consults `isPlayerEnemy(player)` (`entity.cpp:19049`), so **every shopkeeper on the level** is hostile to a wanted player, not just the one that was hit.
* Other neutrals (friendly humans) are just race-friendly monsters. When hit they acquire the attacker and alert visible friends (`alertAlliesOnBeingHit`, DETECTION §1.6).

---

## 2. Veloren (GPL-3.0)

### 2.1 Data (`common/src/comp/agent.rs`)
* `Alignment = Wild | Enemy | Npc | Tame | Owned(Uid) | Passive` (`:47`) with pairwise `hostile_towards / passive_towards / friendly_towards` (DETECTION §2.6). Owned agents use their **owner's** alignment for hostility.
* `Behavior { capabilities: SPEAK|TRADE, state: TRADING|TRADING_ISSUER, trading_behavior: None | RequireBalanced{site} | AcceptFood }` (`:133–245`). Data-driven flags decide what an agent can do. **`AcceptFood` lets pets "trade" only with their owner, which is how you feed them.**
* `Psyche { flee_health, sight_dist, listen_dist, aggro_dist, aggro_range_multiplier, idle_wander_factor, should_stop_pursuing }` per body.
* `Agent { target: Option<Target{target, hostile, selected_at, aggro_on, last_known_pos}>, patrol_origin, stay_pos, chaser (path cache), inbox: VecDeque<AgentEvent>, awareness, timers, flee_from_pos, rtsim_controller }` (`:632`).

### 2.2 The "behavior tree" (`server/src/sys/agent/behavior_tree/mod.rs`)
Each node is `fn(&mut BehaviorData) -> bool`, and `run()` stops at the first one that returns `true` (`:176`):
```text
root:     maintain_if_gliding, react_on_dangerous_fall, react_if_on_fire,
          target_if_attacked, process_inbox_sound_and_hurt, process_inbox_interaction,
          do_target_tree_if_target_else_do_idle_tree
target:   update_last_known_pos, untarget_if_dead, update_target_awareness,
          search_last_known_pos_if_not_alert, do_hostile_tree_if_hostile_and_aware,
          do_save_allies, do_pet_tree_if_owned, do_pickup_loot, do_idle_tree
hostile:  heal_self_if_hurt, hurt_utterance, do_combat
pet:      follow_if_far_away, attack_if_owner_hurt, do_idle_tree
idle:     set_owner_if_no_target, handle_rtsim_actions, handle_timed_events
interaction (if SPEAK/TRADE and not in combat): talk, dialogue, trade invite/accept/finish
```
* Agents run **every tick** in parallel (`par_join`, `sys/agent/mod.rs`). Nothing is staggered except choosing a target at 10% per tick and the retarget threshold. The output is a **`Controller`** (`move_dir`, `look_dir`, actions such as Wield/Sit/Stand/Talk, ability inputs). That's the same component a client fills from the keyboard, so **NPCs run on the player's character states**.
* `target_if_attacked` (`:274`): damage within `DAMAGE_MEMORY_DURATION 0.25 s` → awareness +1, utter Angry, and **switch target if the attacker is more dangerous** (`is_more_dangerous_than_target`). Note that it returns `false`, so the tree keeps running.
* `do_combat` (`:852`): flee if `below_flee_health` or the agent has a `stay_pos` (for 3 s `FLEE_DURATION`, directly away within 20 m `NORMAL_FLEE_DIR_DIST`, otherwise from a random point, at 0.65 speed). Otherwise **give up** if `stop_pursuing(...)` (`util.rs:66`). If `aggro_on`, it calls `attack()` (`attack.rs` tactics, COMBAT §2.6). If not aggro'd yet, it calls `menacing()`.
* `stop_pursuing`: `escape = dist_home² / own_hp_frac · t_since_selected · 0.005` versus `pursue = 100·(1/max(flee_health,0.25))·aggro_dist·sight_dist / (dist_target² · target_hp_frac)`. It gives up when escape > pursue. This is a neat, **one-line utility score for leashing**.
* `menacing()` (`action_nodes.rs:2392`): look at the target, wield, cap movement at 0.5, utter Angry and say a warning line every **5 s** (`TimerAction::Warn`), and walk toward the target at 0.4 speed. That's the "back off!" behavior for neutrals that have `aggro_dist`.
* `idle()` (`:216`): awareness decays by `0.05/s`. It heals with consumables, follows rtsim activities (Goto/Gather/…), and otherwise patrols around `patrol_origin` within `MAX_PATROL_DIST 50 m × idle_wander_factor`.

### 2.3 Pets (`server/src/pet.rs`, `sys/pets.rs`, `events/inventory_manip.rs:613–660`, `comp/pet.rs`)
* **Taming:** use a **Collar** item. The nearest `Alignment::Wild` creature within **5 m** that `is_tameable` (all quadruped_small/low, bird_medium and crustaceans, plus quadruped_medium except mammoth, elephant, catoblepas and hirdrasil) is tamed. **`MAX_PETS = 3`**. The item is returned if taming fails.
* `tame_pet_internal`: refuses if the pet already has another owner, the owner is itself a pet, or the pet owns pets. Sets `Alignment::Owned(owner)`, anchors the pet to the owner (so it doesn't despawn), creates a fresh `Agent` with `TRADE` + `AcceptFood`, `idle_wander_factor = 0.25`, `aggro_range_multiplier = 0.25`, no patrol origin, and **joins the owner's group** (group XP and UI).
* **Pet tree:** `set_owner_if_no_target` (10%/tick, sets the owner as a non-hostile target and makes the pet maximally aware) → `follow_if_far_away` (the owner is further than `50·0.25 = 12.5 m` → chase to `AVG_FOLLOW_DIST 6 m`, speed easing `min(speed, 0.2 + (d² − 6²)/8)`). With `stay_pos`, it **sits** and only walks back if more than **10 m** (`MAX_STAY_DISTANCE`) from the spot. → `attack_if_owner_hurt` (the owner lost HP in the last **5 s** and the pet isn't staying → attack the owner's attacker).
* **Lost pet:** if the pet is more than **200 m** from its owner, the owner is on the ground, and the pet isn't staying → snap it to the ground at the owner's position (`sys/pets.rs`).
* **Stay command:** `SetPetStayEvent(giver, pet, stay)`, only accepted from the owner (`events/interaction.rs:238`).

### 2.4 rtsim sentiments (`rtsim/src/data/sentiment.rs`, `rule/simulate_npcs.rs:51–85`)
* Each NPC holds a sentiment per actor or faction in [−1, 1]. Thresholds: `VILLAIN −0.8, ENEMY −0.6, RIVAL −0.3, NEGATIVE −0.1, POSITIVE 0.1, ALLY 0.3, FRIEND 0.6, HERO 0.8`, each with a doc comment explaining what behavior it should unlock (FRIEND means "may join the actor as a companion").
* `change_by(delta, cap)` moves toward a cap without passing it. **Hurt → −0.1 capped at ENEMY. Healed → +0.05 capped at POSITIVE. Rescued from downed → +0.3 capped at FRIEND.** It decays slowly back toward neutral.
* rtsim's NPC brain (`npc_ai/mod.rs`, 1,781 lines) uses a combinator "Action" API (`seq`, `choose`, `until`, `just`, `now`) for long-running jobs like travel, trade and quests. **A single dungeon level doesn't need this.** The sentiment model is what to take from it.

### 2.5 What to take from each

| Topic | Barony | Veloren | Take |
|---|---|---|---|
| Decision structure | 5 states in one function | priority list of predicate→action rules | **Veloren's priority list choosing Barony-like modes** |
| Output | direct position/velocity writes | `Controller` = same as player input | **Veloren**: AI fills `Input` |
| Factions | race matrices + leader + effects | alignment pairs + owner | DETECTION `FactionTable` + **leader link** + **sentiment** |
| Recruit | gates: race list, friendliness, Leadership cap, Leadership+CHR ≥ 60 named | collar, 5 m, max 3, tameable bodies | **Barony talk-recruit for humanoids**, **Veloren tame-with-item for beasts** |
| Following | formation slots + LOS check + 1 Hz + back-off | chase if > 12.5 m to 6 m, eased speed | **Barony slots + back-off**, **Veloren eased arrival** |
| Commands | 10+ commands gated by Leadership+CHR | stay/follow only | **Barony set and gates**, simplified |
| Hold position | 5-tile leash, still fights | `stay_pos`, 10 m, sits, flees if attacked | **Barony** (defenders fight) |
| Defend leader | none explicit (followers scan) | `attack_if_owner_hurt` (5 s) | **Veloren** + Barony ally scan |
| Teleport | between floors only | > 200 m in world | Floors (Barony) + **hidden** in-level catch-up |
| Flee | HP ≤ 25% (12.5% big), CHR ≥ −2, followers never | `flee_health`, 3 s bursts | **Barony thresholds** in data, Veloren burst timing |
| Leash / give up | none (bosses never) | `stop_pursuing` score | **Veloren score** (simple utility) |
| Neutral warning | threat barks | `menacing` 5 s | **Veloren** |
| Shop crime | per-player wanted level, global for shopkeepers | trade only | **Barony** |
| Leveling | 100 XP, +3 random stats, +5 HP; full share | group XP | **Barony** |

---

## 3. Which decision model for "primitive" bots

| Option | Pros | Cons for this project |
|---|---|---|
| **Flat FSM (Barony)** | Obvious, cheap | Transitions sprawl. Barony's became 7.6k lines because every interrupt (hit, fear, command, leader) had to be checked in every state. |
| **Behavior tree (gdx-ai `btree`)** | Standard, has an editor-friendly text format | gdx-ai 1.8.2 is the latest release (July 2019). It brings pooling and `Task` cloning to manage, and running/decorator semantics that a simple bot doesn't need. Harder to answer "why did it do that?" without a tracer. |
| **Utility AI (score every action)** | Smooth, emergent | Tuning curves for 12 behaviors is real work, and it's harder to make predictable for players (Barony's charm is predictability). |
| **GOAP / HTN** | Planning | Overkill. Dungeon bots don't plan multi-step goals. |
| **Priority rules + modes + local utility (recommended)** | One ordered list of ~15 `Rule`s, where the first that fires sets or keeps a **Mode** (a small state object with enter/tick/exit). Interrupts like hit, fear, command or leader-far are just rules **above** the mode, so they're written once. Utility is used only for (a) target choice and (b) the give-up/flee score. | Less "smart". That's the point: allies and enemies read clearly, it's deterministic with a seeded RNG, a decision trace is one int per tick, and each rule is unit-testable. |

This is Veloren's structure (`BehaviorTree::run`) with Barony's states as modes. The Mode interface stays open: if one mode, say a boss, gets complicated, its `tick()` can host a gdx-ai tree or a script without touching the rest.

---

## 4. Shared Java architecture

### 4.1 Packages (all in the headless `:sim` side, no `Gdx.*`, fixed 60 Hz, seeded RNG; matches MOVEMENT §4.1 and COMBAT §4.1)
```text
io.github.jvmdc.ai
  brain/
    Brain              per-agent: role, mode, rules (shared static list per role), memory ref, rng, trace
    BrainContext       read-only snapshot view: self Actor, Perception (DETECTION), Social, PathService,
                       World queries (items, interactables), time; plus the Input to write
    Rule               @FunctionalInterface boolean fire(BrainContext c)   // Veloren BehaviorFn
    RuleSet            ordered Rule[] built from RoleDef; run() returns index of rule that fired
    Mode (sealed)      Idle, Wander, Patrol, Guard(anchor, leash), Follow(leader), MoveTo(pos, then),
                       Interact(target), Investigate(pos), Search(lastSeen), Engage(target, tactic),
                       Flee(from, until), Rest(until), Talk(with), Trade(with), Menace(target, warnT)
                       each: enter(c) / Status tick(c) / exit(c)
  memory/
    AgentMemory        DETECTION MemoryRecord[8] + lastAttackedBy/time + ownerHurtTime + pathFailCount
  social/
    FactionTable       (DETECTION §4.6) faction×faction H/N/F + species overrides + effects
    Sentiments         small open-addressing map actorId → byte (−126..126 ≈ −1..1), change_by(delta, cap), decay
    Disposition        relation(a,b) = leader/effect override ▸ crime (WantedLevels) ▸ sentiment band ▸ faction
    Leadership         per leader: FollowerRoster (ids, slots), cap(), commandSkill() = Leadership+CHR
    RecruitRules       data-driven gates (§4.6)
    WantedLevels       per player: NONE..KILL (Barony enum) consulted by shopkeeper/guard roles
  action/
    ActionLayer        each sim tick: turns Mode intents into Input (moveDir, yaw/pitch, sneak, attack/block)
    PathFollower       cached corridor from PathService, arrive radius, eased speed (Veloren follow), stuck detect
    PathService        recast4j NavMeshQuery wrapper; request queue with per-frame budget; async-safe results
    Tactics            ports of Veloren attack.rs: SimpleMelee, SimpleBackstab, RangedKite (Barony backup),
                       Caster; + Barony cadence/telegraph wrapper (COMBAT §4.9)
    Formation          Barony formationShape + LOS check (VoxelRay) + navmesh snap + back-off
  command/
    Command (sealed)   Follow, Hold(pos), MoveTo(pos), Interact(entityId), Attack(entityId), SetPickup(mode),
                       SetStyle(MIXED|MELEE|RANGED), Rest, DropEquipment, MoveAside, Dismiss
    CommandGate        Barony requirement table + creature tier → Allowed | NeedsSkill(n) | CreatureCant
  interact/
    TalkService        talk → role handler: recruit / chatter / trade / threat bark (Barony §1.2 order)
    GiveService        hand item to follower (direct), feed, equip-if-better (Barony monsterWantsItem rules)
  sched/
    AiScheduler        think ticks staggered (5 Hz idle, 10 Hz engaged, 1 Hz far LOD), path/LOS budgets
  data/                RoleDef, AiProfile, RecruitDef, BarkTable (JSON via existing Converter/Json)
io.github.jvmdc.render.debug.AiDebugDraw    (client) overlay, §4.10
io.github.jvmdc.ui.follower                 (client) radial menu, follower panel, §4.9
```

### 4.2 The tick
```text
60 Hz sim tick
  PerceptionSystem.tick()                 // DETECTION: awareness, memories, AiIntents (staggered)
  for agent due a THINK tick (AiScheduler):
       idx = role.rules.run(ctx)          // first rule that fires; may switch Mode
       brain.trace.push(tick, idx, mode)  // ring buffer for the overlay and tests
  for every agent (cheap, every tick):
       mode.tickMotor(ctx) → ActionLayer → Input   // follow path, face, press attack per Tactic timers
  CharacterSim.tick(actor, input, dt)     // MOVEMENT: the same code path as the player
  Combat/Projectiles/Stimuli …            // COMBAT, DETECTION StimulusBus
```
**Rule of thumb:** decisions happen at 5–10 Hz, motor work at 60 Hz. The motor never chooses, it only executes the current Mode's last decision. That's what keeps 200 agents cheap.

### 4.3 Default rule order (one list, roles switch rules on or off)
```text
 1 Dead/Stunned/Asleep          → no-op (CharacterSim handles state)
 2 HandleCommandInbox           → followers only; may set Mode (Barony monsterAllySendCommand)
 3 ReactIfHit                   → Veloren target_if_attacked: awareness=1, retarget if more dangerous;
                                  Disposition.onHurt(attacker); never returns true by itself
 4 FleeIfLowHealth              → role.flee (Barony HP ≤ 25% / 12.5% if maxHP ≥ 100; never if has leader or brave)
 5 ReturnToLeaderIfFar          → follower: leader dist > followFar or no LOS, and !engaged-in-melee (Barony
                                  isFollowerFreeToPathToPlayer); else hidden-teleport rule (§4.7)
 6 DefendLeader                 → follower: leader hurt in last 5 s → Engage(leader.lastAttacker) (Veloren)
 7 EngageHostileTarget          → aware(≥ALERT) of hostile target → Engage; Veloren stop_pursuing → drop
 8 MenaceIfProvoked             → neutral role with sentiment ≤ RIVAL and target within aggroDist → Menace (5 s warn)
 9 InvestigateOrSearch          → DETECTION intents: Investigate(heard) / Search(lastSeen) below ALERT
10 ContinueCommandedMode        → Hold / MoveTo / Interact / Rest still running
11 TalkOrTrade                  → in TALK/TRADE session (shopkeeper, chatter), face partner, stand still
12 PickupWanted                 → item in LOS and PickupPolicy allows (Barony NONPLAYER/NONE/ALL; Veloren humanoid)
13 FollowFormationSlot          → follower default
14 RoleIdle                     → Guard(post) | Patrol(route) | Wander(room) | Idle
```
Rules 2, 5, 6 and 13 only exist in the follower role. Rule 8 only exists in neutral roles. This one list is the whole "brain".

### 4.4 Roles as data (`assets/data/ai/roles.json`)
```json
{
  "enemy_melee":   { "faction": "cultist", "idle": "patrol", "tactic": "SimpleMelee",
                     "flee": {"hpFrac": 0.25, "bigHpFrac": 0.125, "bigHp": 100, "minChr": -2},
                     "pickup": "veloren_humanoid", "alertsAllies": true, "giveUp": "veloren_score" },
  "enemy_beast":   { "faction": "beast", "idle": "wander", "tactic": "QuadMedBasic", "flee": {"hpFrac": 0.3},
                     "pickup": "none", "tameable": true },
  "neutral_human": { "faction": "neutral", "idle": "wander", "talk": "chatter", "aggroDist": 4.0,
                     "menaceSecs": 5, "recruitable": true, "onHitSentiment": -0.7, "fleeFromThreatSounds": true },
  "shopkeeper":    { "faction": "neutral", "idle": "guard", "talk": "trade", "leash": 6, "wantedAware": true,
                     "flee": null, "dropsHalfStock": true },
  "guard":         { "faction": "town", "idle": "guard", "wantedAware": true, "investigateCrimes": true },
  "follower":      { "inherits": "<original role>", "rules": "+follower", "flee": null,
                     "followNear": 6.0, "followFar": 9.0, "holdLeash": 15.0, "pickupDefault": "nonplayer" },
  "pet":           { "inherits": "<original role>", "rules": "+follower", "commands": ["Follow","Hold","Attack","Dismiss"] }
}
```
* Recruiting doesn't replace the role. It **overlays** `follower` (`inherits`), so a recruited archer keeps its tactic, animations and loadout.
* `AiProfile` per species (DETECTION `PerceptionProfile` plus speeds, preferred range, `canOpenDoors`, `canUseItems`, creature `tier` for the command gates).

### 4.5 Disposition: one function decides "do I attack you?"
```text
relation(me, you):
  if effects (charm/command/conflict/fear)      → effect result                 (Barony overrides)
  L_me = leaderOf(me) ?: me ; L_you = leaderOf(you) ?: you                       (Barony leader_uid / Veloren Owned)
  if L_me == L_you                              → FRIENDLY  (followers ↔ leader ↔ co-followers)
  if role(me).wantedAware && wanted(L_you) ≥ AGGRESSION_SHOPKEEP_INITIATED → HOSTILE (Barony)
  s = sentiment(me → L_you)                                                      (Veloren rtsim)
  if s ≤ ENEMY(−0.6) → HOSTILE ; if s ≤ RIVAL(−0.3) → WARY (menace) ; if s ≥ ALLY(0.3) → FRIENDLY
  else                                          → FactionTable.relation(faction(L_me), faction(L_you))
```
**Events that change sentiment** (each a `StimulusBus` record, applied to the victim and to witnesses who can see it, scaled down for witnesses):

| Event | Delta (cap) | Source |
|---|---|---|
| Damaged by X | −0.1 (ENEMY) for most roles; `neutral_human` and shopkeeper set **−0.7** at once (Barony: hit = fight) | rtsim `on_health_changed`, Barony |
| Healed by X | +0.05 (POSITIVE) | rtsim |
| Rescued, or a threat to it killed | +0.3 (FRIEND) | rtsim `on_helped` |
| Gift (`GiveService`) | +value-scaled 0.05–0.2 (ALLY) | new; Veloren AcceptFood |
| Theft from a shop / attacked shopkeeper | wanted level up | Barony |
| Leader hits own follower (FF on) | −0.25 (RIVAL). Two quick hits → **leave** (`Dismiss` + hostile if ≤ ENEMY) | design choice: Barony only gets angry (§1.6). Leaving is a stronger "betray" you may want |

Leader death: clear the leader link, `sentiment(me → deadLeader's party)` unchanged. Falls back to faction, which for enemies-turned-followers means **hostile again** (Barony "rabid"). Make this per-role (`onLeaderDeath: revert | stay_neutral | flee`).

### 4.6 Recruitment (`RecruitRules`, `assets/data/ai/recruit.json`)
```json
{ "cap": {"base": 4, "perLeadership20": 2, "max": 8, "capstoneMax": 8},
  "namedRequiresLeadershipPlusChr": 60,
  "byPlayerRace": {
    "human":  {"always": ["humanoid.*"], "requireFriendly": true,
               "capstone": ["biped_small.gnarling", "biped_small.adlet", "biped_small.boreal", "golem.*"]},
    "gnarling": {"always": ["biped_small.gnarling"]}
  },
  "tame": {"item": "collar", "range": 5.0, "maxPets": 3,
           "bodies": ["quadruped_small.*", "quadruped_low.*", "bird_medium.*", "crustacean.*", "quadruped_medium.*"],
           "excludeSpecies": ["mammoth", "elephant", "catoblepas", "hirdrasil"]},
  "optionalRoll": null }
```
* **Talk-recruit (Barony):** `TalkService.talk(player, npc)` runs Barony's dispatch order (§1.2), then the gates. **No roll by default**, which matches Barony and keeps it readable. If you want "charisma checks", set `optionalRoll` to something like `p = clamp(0.35 + 0.03·(CHR + Leadership/10 − npc.LVL), 0.05, 0.95)`, rolled once per NPC per floor so you can't spam it.
* **Tame (Veloren):** for animal bodies, use the item within range. It counts against a separate pet cap, or the same cap (your choice).
* **On success:** `Leadership.add(npc)`, overlay the follower role, `leader = player`, `sentiment(npc → player) = max(s, ALLY)`, clear targets that other followers had on it, nametag, bark, +Leadership skill XP. All Barony.

### 4.7 Followers
* **Formation** (port of `MonsterAllyFormation_t`): `slots = [(±2,0), then rows behind: odd i → (±1,−i), even i → (±2,−i),(0,−i)]` in **tile units × 3 m**, melee first, ranged after. Accept a slot if `VoxelRay.los(slotEye, leaderEye)` and `PathService.nearestPoly(slot)` both succeed. Recompute at most 1 Hz per leader, or when the leader moves more than 2 m.
* **Follow trigger:** `dist(slot) > followFar (9 m)` or no LOS to the leader → path to the slot. Arrive with Veloren easing `speed = min(max, 0.2 + (d² − near²)/8)` so they don't sprint and stop. Don't break off while in melee or within 2 tiles of the current target (Barony).
* **Back-off:** on path failure, `retryDelay = min(10 s, +1 s)`, and at 5 failures try a wider `findNearestPoly` extent (Barony `tryExtendPath`).
* **Catch-up teleport:** if there's no path, or the path is more than 40 m, for 10 s, **and** neither the follower's nor the destination slot's position is visible to any player, place it at the slot. That's Veloren's lost-pet teleport made invisible. It's never used while holding.
* **Floors:** carry the roster through stairs (Barony copies everyone). Spawn followers on free navmesh points around the arrival point and keep their settings (pickup, style, level, gear).
* **XP:** the leader's kills give every follower on the level the full XP. A follower's kill gives XP to the leader + Leadership. At 100 XP a follower levels up: +1 to 3 distinct random stats, +5 max HP (§1.5). Reuse COMBAT C11's progression code.
* **Equipment:** `GiveService.give(player, follower, item)` is a direct hand-over (a UI improvement on Barony's drop-and-pickup), with Barony's rules: humanoids equip if better, beasts only accept food. `PickupPolicy` NONPLAYER, NONE or ALL. `DropEquipment` gated as in §1.3. Equipped gear renders on the Veloren figure through LOOT L8.

### 4.8 Command system (Barony set, data-gated)
* `Command` records go into `Brain.inbox` from the UI, from network messages later, or from tests. Rule 2 consumes one per think tick. Every command produces a **bark** (`BarkTable`, Barony `AllyNPCChatter` events: begin, end, fail, attack-refused-friendly, drop-failed…) so the player always gets feedback.
* `CommandGate.check(leader, follower, command)` returns `Allowed | NeedsSkill(n) | CreatureCant | AngryAtYou`. Gates are in `commands.json`:

| Command | Gate (Leadership+CHR) | Mode set |
|---|---|---|
| Follow | 0 | `Follow` |
| Hold here | 0 | `Guard(pos, leash 15 m ≈ 5 tiles)`, still engages within the leash |
| Move to (tile) | 20 | `MoveTo(pos) → Guard(pos)` |
| Interact (lever, door, item, chest) | 20 | `MoveTo → Interact(id)` (use, or pick up if allowed, at 1.5 m) |
| Attack target | 20 to open, 60 to confirm. Tier 2 creatures need 80, tier 3 need 100. A friendly target needs 80 + FF on | `Engage(id)` |
| Pickup mode | 20 | — |
| Style (mixed, melee, ranged) | 40 (humanoids only) | affects `Tactics` weapon choice |
| Rest | 40 | `Rest(60 s)`, regen, once per floor |
| Drop equipment | 40 (weapon), 80 (armor), 100 (jewelry) | — |
| Move aside | 0 | short sidestep |
| Dismiss | 0 | clear leader, keep sentiment ALLY (stays neutral) |

### 4.9 UI hooks (UI.md stack: scene2d + Veloren skin)
* **Interact prompt:** looking at an NPC within 2.5 m shows "Talk" / "Trade" / "Command". `TalkService` returns a `TalkResult` (`Recruited`, `Chatter(lineKey)`, `OpenTrade`, `Refused(reasonKey)`, `Threat`), and the UI shows it as a Barony-style **dialogue bubble** (world-space tooltip) using Veloren's speech-bubble art if present, or a 9-patch frame.
* **Radial menu** (`ui.follower.FollowerRadial`): hold the command key on a follower (or tap it for the last-commanded one). Up to 10 wedges with Veloren icons. Disabled wedges show `"Needs Leadership 60"` or `"Can't"`. "Select" commands enter **target mode**: crosshair ray (`VoxelRay` + entity pick) highlights a tile, creature or interactable, and click confirms. Gamepad: stick selects the wedge.
* **Follower panel:** Barony's left-side list. Portrait (rendered with the icon FBO, UI.md §3), name, level, HP bar, current mode icon. Click to select as the command target.
* **Nametag + ally tint** above recruited NPCs (Barony `USERFLAG2`). In the shader plan this is a palette tint uniform.
* **Shop:** `Trade(with)` mode opens LOOT L10/L11's shop window. The shopkeeper stays in `Trade` until the window closes or the player is more than 3 m away.

### 4.10 Debug overlay (F9; matches F6 navmesh, F7 colliders, F8 perception)
* Above each head: mode letter, rule index that fired, disposition toward the player (colour), and leader link lines.
* Path polylines (current corridor), formation slots (numbered, green if accepted, red if LOS or poly failed), hold leash circle, `MoveTo` target.
* Command inbox and last result. Retry/back-off timers. Teleport candidates.
* Selected-agent panel (imgui, UI.md item 9): role, mode history (last 20 with tick and rule), sentiments table, memory records, tactic timers, path budget usage this frame.
* **Decision trace export**: `brain.trace` as CSV for a selected agent, which is handy for bug reports.

### 4.11 Tick budgeting
| Work | Rate | Budget |
|---|---|---|
| Rule list (think) | 5 Hz idle, 10 Hz engaged, 1 Hz far LOD, staggered by `id % 12` | ~1–3 µs per agent per think → 200 agents ≈ 30–60 µs per frame |
| Motor (path follow, face, press) | 60 Hz | ~0.3 µs per agent → 60 µs |
| `findPath` / `findStraightPath` | queued | **max 6 requests per frame**, FIFO with priority (commands > engage > follow > wander). recast4j path on a dungeon is ~20–80 µs → ≤ 0.5 ms worst case |
| Formation recompute | ≤ 1 Hz per leader | 10 slots × (LOS ray + nearestPoly) ≈ 20 µs |
| Perception | DETECTION scheduler | 0.1–0.3 ms |
| **Total AI** at 200 agents | | **≈ 0.3–0.8 ms per frame** (estimate, not benchmarked) |

* Re-path triggers are rate-limited: the target moved more than 1.5 m from the corridor end, the corridor is invalid, or the path is stuck (no progress for 1 s). Stuck handling: try `moveAlongSurface`, then a small random sidestep, then re-path, then give up the mode (Veloren `unstuck_if`).
* Later, use `detour-crowd` (NAV slice 6) for separation when more than ~6 agents share a corridor. PathFollower can feed crowd agent targets instead of steering directly.
* Determinism: `SplittableRandom(worldSeed ^ actorId)` per brain, no wall-clock reads, and modes iterate in a fixed order. That makes scenario tests reproducible and opens the door to lockstep co-op.

---

## 5. Tests (JUnit 5, headless, CI)

**Harness:** `AiTestWorld` builds a `TileGrid`/`OpacityGrid` from ASCII art, with a grid A* `PathService` stub (or the real recast4j bake on the same blocks for integration tests), a kinematic `Mover` stub (MOVEMENT's `Mover` interface), real `PerceptionSystem`, `FactionTable` and `Brain`, and a fake `Combat` that applies fixed damage. `world.run(seconds)` steps 60 Hz and returns traces.

| # | Scenario | Pass condition |
|---|---|---|
| 1 | Follower follows the leader through an L corridor and a door | within 1.5 m of its slot ≤ 3 s after the leader stops. No more than 1 path request per second on average |
| 2 | 8 followers in a 1-tile corridor | no deadlock in 10 s. Back-off keeps path requests ≤ 2/s total |
| 3 | Hold here, enemy enters leash | follower engages, never leaves 15 m, returns to the spot after the kill |
| 4 | Attack command gates | skill 59 → `NeedsSkill(60)`. Friendly target at 79 → refused + bark. Tier-3 creature at 99 → refused |
| 5 | Recruit gates | cap 4/6/8 by Leadership. Named NPC with L+CHR 59 refuses. Hostile goblin refuses without the capstone and joins with it. Already-led NPC refuses |
| 6 | Leader dies | followers revert per `onLeaderDeath` and attack the next player (enemy-turned-follower) |
| 7 | Shopkeeper hit | wanted = AGGRESSION. A second shopkeeper across the level is hostile. Trade refused. Co-op partner nearby = ACCESSORY |
| 8 | Neutral provoked | Menace for 5 s with barks, then Engage. Player backs off past aggroDist → Menace ends |
| 9 | Low HP | enemy flees at 25% HP. Follower doesn't. Fear effect forces flee |
| 10 | Defend leader | the leader is hit by an unseen archer → follower engages within 1 think tick + path time |
| 11 | Pickup modes | NONPLAYER ignores the player's dropped sword. ALL takes it and equips if better. Direct give equips |
| 12 | Floor transition | roster carried, settings preserved, spawn on valid polys |
| 13 | Catch-up teleport | never fires while the player can see either point |
| 14 | Determinism | same seed → identical trace hash over 60 s with 50 agents |
| 15 | Budget (not in CI) | 200 agents on a 150×150-tile map, AI < 1 ms/frame average, path queue never > 6/frame |

---

## 6. Prioritized plan

**Prerequisites from earlier reports:** MOVEMENT M0–M1 (the `Input` record + `CharacterSim`) and N1 (AI fills `Input`); NAV slices 0–5 (block volume, capsule, navmesh, Detour queries); DETECTION MVP (slices 1–4 + minimal 5); COMBAT C1 + C3 + the `SimpleMelee` part of C9; for equipment, LOOT L1; for UI, UI.md slice 0.

| # | Slice | Effort | Key references |
|---|---|---|---|
| A0 | **Brain core:** `Rule`/`RuleSet`, sealed `Mode` with enter/tick/exit, `BrainContext`, seeded RNG, decision trace, `AiScheduler` (stagger, LOD), Input bridge | 1.5–2 d | Veloren `behavior_tree/mod.rs:50–184`; MOVEMENT M0 |
| A1 | **Action layer:** `PathService` (budgeted queue over `NavMeshQuery`), `PathFollower` (arrive easing, stuck/unstuck, re-path rules), look/aim, door use via area flags | 1.5–2 d | NAV §3.4 / slice 5; Veloren `action_nodes.rs:94–215, 806–835`; DETECTION §4.8 |
| A2 | **Enemy behaviors:** Idle/Wander/Patrol/Guard from `RoleDef`, Engage with `Tactics.SimpleMelee` + `RangedKite` (Barony `backupWithRangedWeapon`) + Barony cadence wrapper, Flee rule (Barony thresholds, Veloren 3 s bursts), `stop_pursuing` score, Investigate/Search from DETECTION intents | 2–2.5 d | `actmonster.cpp:5235–7200`; `entity.cpp:25926, 26078`; Veloren `mod.rs:852–1009`, `util.rs:66–103`, `attack.rs:54, 393` |
| A3 | **Social:** `Sentiments` (change_by with caps, decay), `Disposition.relation`, leader inheritance, `WantedLevels`, Menace mode, witness propagation via `StimulusBus` | 1–1.5 d | rtsim `sentiment.rs`, `simulate_npcs.rs:51–85`; Barony `actmonster.cpp:326–719`; Veloren `action_nodes.rs:2392` |
| A4 | **Followers:** `Leadership` roster, `RecruitRules` (JSON gates + optional roll), Tame-with-item path, `Formation` (slots + LOS + navmesh + back-off), follow/defend-leader rules, catch-up teleport, floor carry-over, leader-death handling, XP share + level-up | 2.5–3 d | `actmonster.cpp:820–1180, 1597–2240, 5596–5760, 11143`; `entity.cpp:3792, 4065–4300, 18020–18090`; `game.cpp:2014–2510`; Veloren `pet.rs`, `sys/pets.rs`, `mod.rs:519–597` |
| A5 | **Commands:** sealed `Command`, `CommandGate` + `commands.json`, inbox rule, all §4.8 commands, `BarkTable` (Barony event list, your own text) | 1.5–2 d | `monster.hpp:598–700`; `actmonster.cpp:12435–13108`; `interface.cpp:4402–4830` |
| A6 | **Interactions:** `TalkService` (Barony dispatch order), recruit flow, chatter, `GiveService` (give, feed, equip-if-better port), shopkeeper Trade mode hooks into LOOT L10–L11 | 2–2.5 d | `actmonster.cpp:4490–4640`; `entity.cpp:24671–25400`; Veloren `TradingBehavior::AcceptFood` |
| A7 | **UI:** interact prompt + dialogue bubble, radial menu with target mode, follower side panel, nametags/tint | 2–3 d | UI.md slices 0–1; Barony `FollowerRadialMenu` |
| A8 | **Debug overlay** (F9 + imgui panel + trace CSV) | 1–1.5 d | DETECTION §4.9; UI.md item 9 |
| A9 | **Test harness + 15 scenarios + benchmark** | 1.5–2 d | §5 |
| **Total** | | **≈ 16.5–22 d** (call it 16–23) | |

**MVP (about 8–11 days):**
* A0, A1.
* A2 with melee only and no `stop_pursuing`.
* A3 with the faction and leader link only.
* A4 with talk-recruit, cap, formation follow and defend-leader (no teleport, XP or taming).
* A5 with Follow, Hold, Move-to and Attack, through a temporary 4-key hotkey instead of the radial menu.
* A8 with mode letters and paths only.
* A9 scenarios 1, 3, 4, 5, 9 and 14.

That gives you enemies that wander, notice you, fight, flee and search, and humanoids you can talk into following you and order around. All of them use one brain and the player's own movement and combat code.

**Suggested order:** A0 → A1 → A2 (enemies first, since they're the most visible) → A3 → A4 → A5 → A9 alongside → A8 → A6 (once LOOT items exist) → A7 (once the UI skin exists).

---

## 7. Licensing
* Code that ports Barony's **formation table, recruit gates, follower cap formula, command gate table, retreat thresholds, XP/level-up rules or wanted-level logic** should carry the **BSD-2-Clause notice** (© Turning Wheel LLC; template in MOVEMENT.md §6) and be listed in `NOTICE`. Barony's text strings (Language entries) are part of its data. **Write your own barks.**
* Code that ports Veloren's **rule order, `stop_pursuing`, follow easing, sentiment constants/`change_by`, pet rules, menacing timing or attack tactics** needs a GPL-3.0 attribution header naming the source file. That's fine in your GPL-3.0 repo.
* The design (priority list over modes) isn't copyrightable. Only literal ports need headers.

---

## 8. Decisions for you
1. **Charisma roll or Barony gates?** I'd keep Barony's deterministic gates and leave `optionalRoll` off.
2. **Betrayal:** Barony only gets angry when you hit a follower. Do you want followers to **leave or turn** after repeated friendly fire, or on low sentiment (gifts and neglect)?
3. **Taming beasts:** add Veloren's collar or food taming for animals (wolves, boars, birds), or keep recruitment to humanoids like Barony?
4. **Follower cap and pets:** shared cap (Barony 4–8) or a separate pet cap (Veloren 3)?
5. **Catch-up teleport** inside a level: allow it (hidden) or never, like Barony?
6. **Giving items:** a direct "give" window (easier) or Barony's drop-and-command flow (more Barony)?
7. **Neutrals:** instant hostility when hit (Barony), or the Veloren warn → fight ladder with sentiment?

---

## 9. Constants quick reference
| Item | Barony | Veloren |
|---|---|---|
| Think rate | 1 Hz reflex, follower check 1 Hz (+back-off) | every tick; target choice 10%/tick; retarget 10 s |
| Follow distances | 48 units (3 tiles) wait, 64 (4 tiles) hunt | follow if > 12.5 m (50 × 0.25), to 6 m |
| Hold | 5-tile leash, fights | stay 10 m, sits, flees if attacked |
| Defend owner | allies scan for visible enemies | owner hurt in last 5 s → attack attacker |
| Teleport | floors only, all followers | > 200 m, owner on ground |
| Recruit | gates; cap `min(8, max(4, 2·⌊L/20⌋))`; named L+CHR ≥ 60; capstone 100 | collar, 5 m, Wild + tameable, max 3 |
| Command gates | L+CHR: 0/1, 20, 40, 60, 80, 100 by command and tier | owner only |
| Flee | HP ≤ 25% (≤ 12.5% if maxHP ≥ 100), CHR ≥ −2; followers never | `flee_health`, 3 s bursts, 0.65 speed |
| Give up | never (bosses chase) | `stop_pursuing` score |
| Warn | threat bark | menace: 5 s warn timer, 0.4 speed approach |
| XP | full share to followers; leader gets follower kills; 100 XP/level, +3 stats, +5 HP | group XP |
| Shop crime | wanted levels NONE…KILL, all shopkeepers hostile | n/a |
| Sentiment | n/a | ENEMY −0.6, RIVAL −0.3, ALLY 0.3, FRIEND 0.6; hurt −0.1, heal +0.05, rescue +0.3 |
