# Reusing Veloren's sounds and music in jvm-dungeon-crawler

Research only. No changes to any repo. Veloren checked at `master` @ `ebfff94` (2026-10-06 18:56 ET).
Sparse clone at `/workspace/veloren_audio`. Of the 639 files, 200 SFX/ambience files were pulled from LFS and probed with ffprobe, plus 7 music files fetched separately.
Per-file license table: **`/workspace/SOUNDS_licenses.csv`** (all 639 `.ogg` files: path, author, license, status).

## TL;DR

**Yes, mostly.** 632 of Veloren's 639 audio files are GPL-3.0 or CC BY 4.0. Both work in a GPL-3.0 project as long as you give attribution.
**Exclude 7 music tracks by GeekyGami.** They're CC-BY-NC-SA 4.0 and come with an added restriction: *"Should not be used in other games than Veloren unless it is parody."*
None of Veloren's audio is ND, and none of the SFX are NC.

What carries over well:
- footsteps (stone/dirt/grass/snow/wood/water)
- sword/axe/hammer/dagger swings, bow shots and arrow hit/miss
- block/parry/hurt/death/stagger
- fire/ice/heal spell sounds
- monster vocalizations (ogre, wolf, bat, wendigo, adlet, saurok, etc.)
- inventory/pickup/consume/UI click sounds
- a 75 s cave ambience loop
- 7 usable dungeon tracks, 4 usable cave tracks, and 3 start/loop/end combat tracks

**What's missing:** doors, chests, traps, levers, coins, and the classic dungeon monsters (skeleton, goblin, rat, spider, zombie). These will need CC0 or CC BY sounds from somewhere else.

Veloren's playback engine is Rust (`kira 0.12` + symphonia), so port the **data** (`sfx.ron`, `soundtrack.ron`, `ambience.ron`, `music_transition_manifest.ron`) and the **logic**, not the code.
Estimate: **5–7 days** for a libGDX-only implementation, or **6.5–9 days** using `gdx-miniaudio` for real 3D, low-pass muffling and gapless loops.

---

## 1. Inventory (`assets/voxygen/audio/`)

All 639 files are **Ogg Vorbis**. There are no WAVs, even though `.gitattributes` LFS-tracks `*.wav` and `*.ogg`.
Total size is about 266 MB, and music is about 259 MB of that. Every audio file is in **Git LFS**. To fetch only what you need, use `git lfs pull --include=...`.
Avoid `git lfs ls-files` on a `--filter=blob:none` clone: it lazily fetches blobs and takes a very long time.

| Folder | Files | Size | Notes |
|---|---|---|---|
| `sfx/` | 548 | ~11 MB | 252 are instrument notes (not relevant) |
| `soundtrack/` | 83 | ~259 MB | overworld 46, town 13, dungeon 9, cave 5, combat 9 (3 tracks × start/loop/end), title 1 |
| `ambience/` | 7 | 2.4 MB | stereo loops: cave, wind, rain, thunder, leaves, river loud/quiet |
| `null.ogg` | 1 | – | silence placeholder |
| `.ron` configs | 6 | – | `sfx.ron` (2318 lines, 256 events), `soundtrack.ron`, `ambience.ron`, `music_transition_manifest.ron`, `calendar/{christmas,halloween}/soundtrack.ron` |

**Format details from the probe of 200 SFX/ambience files:**
- Sample rates: 44.1 kHz is most common, then 48 kHz, plus 9 ability files at 24 kHz.
- Channels are mixed. **All 37 footsteps are stereo.** Utterances are almost all mono. About half of abilities/character are stereo.
- Music is stereo 44.1 kHz, except `cobbled_halls` at **96 kHz**, which is worth resampling.
- Ogg tags carry artist and title, but no license info.

### Categories relevant to a dungeon crawler

| Need | Veloren files | Count | Notes |
|---|---|---|---|
| Footsteps: stone | `footsteps/stone_step_1..12` | 12 | the main dungeon floor sound (alfy, GPL) |
| Footsteps: dirt/grass/snow | `stepdirt_1..5`, `stepgrass_1..6`, `snow_step_1..3` | 14 | |
| Footsteps: wood | `wood_step_1..4` | 4 | **present but unmapped in Veloren**: `BlockKind::Wood` falls through to Grass. Use them for bridges and floors. |
| Water | `water_splash_1..4` (swim), `splash_small/medium/big` (falling into water) | 7 | splashes are CC BY (DaforLynx) |
| Sword/dagger/spear swing | `abilities/swing_sword`, `sword_dash`, `separated_second_swing`, `separated_third_swing` | 4 | the separated swings are unused in sfx.ron but good for combo stages |
| Axe/hammer/shield swing | `abilities/swing` | 1 | one file shared by all heavy weapons, so add pitch variation |
| Generic whooshes | `whoosh`, `swoosh`, `yeet` (throw) | 3 | |
| Draw/sheathe | `weapon/sword_in/out`, `dagger_in/out`, `staff_in/out`, `shield_out`, `weapon_in/out` | 9 | |
| Bow | `abilities/arrow_shot_1..4`, `character/arrow_hit`, `arrow_miss` | 6 | |
| Hit / hurt / death | `character/hit_1..4`, `death_1..3` | 7 | `hit_*` is the generic "Damage" event |
| Block / parry | `character/block_1..3`, `parry_1..2` | 5 | |
| Stagger (poise) | `interrupted_1..4`, `stunned_1..3`, `dazed_1..3`, `knockeddown_1..2` | 12 | CC BY (DaforLynx) |
| Dodge roll | `dive_roll_1..2`, `dive_roll_cancel_1..2` | 4 | |
| Fire magic | `fire_shot_1..2`, `fire_breath`, `fire_dash`, `flame_cloak`, `flame_thrower`, `napalm_strike/pool`, `pyroclasm_bolt/charge`, `explosion`, `fuse` | 13 | riccifl0w's 7 are CC BY |
| Ice / other magic | `ice_crack`, `steam`, `heal`, `heal_bomb`, `sceptre_aura`, `sceptre_channeling`, `laser_beam`, `portal_1/2`, `bleep` | 10 | `heal` works for healing spells, `portal_*` for teleport/level exit |
| Heavy impacts | `minotaur_smash_1..2`, `klonk`, `smashklonk`, `cyclops_charge`, `gigas_frost_roar` | 6 | boss slams and thrown-object hits |
| Boss voices | `deep_laugh`, `hiss`, `long_hiss`, `strigoi_head`, `bloodmoon_heiress_summon` | 5 | |
| Creature vocals | `utterance/*` | 89 | species with angry/hurt/calm sets: **ogre** (Veloren maps all BipedLarge, e.g. trolls/minotaur, to these), **wolf** (6 angry/5 hurt), **wendigo** (6/5 calm), **vampire_bat**, **adlet**, **saurok**, **asp**, **alligator/sea_crocodile**, **dagon**, **mindflayer**, **mandragora**, **maneater**, **phoenix**, **wyvern**, **lion**, plus farm animals, `humanmale_hurt1`, `humanmale/female_greeting1`, and 15 `legoom_*` |
| Inventory / items | `inventory/add_item`, `add_failed`, `drop_item`, `open_inventory`, `pickup_sword`, `pickup_staff`, `collect_gemstone`, `consumable/apple`, `food`, `liquid` (potions) | 10 | potion drinking is `liquid` |
| UI | `ui/click1`, `click2`, `character/experience_gained_1..3`, `level_up_sound_-_shorter_wind_up` | 6 | level up and XP |
| Mining / breaking | `pickaxe_damage*`, `shovel` | 4 | breakable walls |
| Ambience loop | `ambience/cave.ogg` (75.5 s, loop samples 3394–3329488) | 1 | **the dungeon bed** |
| Spot ambience | `ambient/fire` (torches/campfire), `running_water-001..027` (streams; Veloren reuses 6 for lava), `owl_1`, `frog_croak_1`, `bees_1`, `crickets_1..3` | | fire and water fit dungeon dressing |
| Dungeon music | `soundtrack/dungeon/*` | 9 | **7 usable** (see exclusions) |
| Cave music | `soundtrack/cave/*` | 5 | **4 usable** |
| Combat music | `soundtrack/combat/{barred_paths,reversal,clash}-{start,loop,end}` | 9 | **commented out in `soundtrack.ron` on master** (disabled in game), but the files ship and the code path works |

### Gaps (no Veloren sound exists)
- **Doors** opening and closing, **portcullis**, **chest open**, **lever**, **pressure plate click**, **spike trap**, **arrow trap** (`arrow_shot_*` plus `klonk` is a usable stand-in), **boulder roll**.
- **Coins/gold pickup** (`collect_gemstone` is the closest), **lockpick**, **book/scroll**.
- **Dungeon monsters:** skeleton (bone rattle), goblin, rat, spider (Veloren's arthropods are silent), zombie/ghoul, slime.
- **Lightning spell** (`ambient/lightning_1` is a distant thunder clap, not a bolt), **magic missile**, generic **"cast"**.
- Player **grunts** beyond `humanmale_hurt1`. There's no female hurt sound.

You can mix in sounds from CC0 sources such as Kenney audio packs, OpenGameArt CC0 and freesound CC0. Track their licenses in the same credits file.

---

## 2. How Veloren triggers sounds (`voxygen/src/audio/`)

`mod.rs` (`AudioFrontend`, 1013 lines), `channel.rs`, `sfx/mod.rs` (1162), `sfx/event_mapper/{movement,combat,block,campfire,vehicle}`, `music.rs` (843), `ambience.rs` (402) and `soundcache.rs`. The audio library is **kira 0.12** with symphonia decoding.

### SFX (`sfx.ron` → `SfxTriggers`)
- **`sfx.ron` format:** a map from `SfxEvent` to `(files: [...], threshold: f32, subtitle: Option<String>)`.
  - When `files` has more than one entry, one is picked **at random** each play.
  - `threshold` is a per-entity cooldown in seconds. For movement it gates repeats; for one-shots it's also used as the subtitle duration.
- **The event enum** (`SfxEvent` in `sfx/mod.rs`) includes:
  - movement: `Run(BlockKind)`, `QuadRun(..)`, `OctoRun(..)`, `Roll`, `Climb`, `Swim`, `Glide`
  - weapons: `Wield/Unwield(ToolKind)`, `Attack(CharacterAbilityType, ToolKind)` (e.g. `Attack(ComboMelee2(Action), Sword)`)
  - `Inventory(SfxInventoryEvent)`
  - one-shot outcomes: `Damage`, `Death`, `Block`, `Parry`, `PoiseChange(state)`, `ArrowShot/Hit/Miss`, `Explosion`
  - `Utterance(UtteranceKind{Calm,Angry,Hurt,Greeting}, VoiceKind)`
- **Three kinds of trigger:**
  1. **Polling mappers.** Each frame they compare every entity's `CharacterState`/physics with its previous state, for entities within `SFX_DIST_LIMIT` of the camera.
     - `movement/mod.rs` maps the block underfoot to a surface: Snow; Rock/WeakRock/Glowing/Ice → Rock; Earth; Air → idle; **everything else → Grass**.
     - Footsteps fire on the animation's foot-contact flag (`Footsteps::is_any_stepping()`) or on landing. The time threshold is only a fallback.
     - `combat/mod.rs` fires `Wield/Unwield` and `Attack(ability, tool)` when the ability's stage changes to Action.
     - `block.rs` scans nearby blocks and sprites for spot ambience (birds, crickets, water, lava, frogs).
     - `campfire.rs` handles fire.
  2. **Server `Outcome`s** in `handle_outcome`: explosion (volume scales with power), projectile shot/hit/miss per projectile body, damage, death, block, parry, poise change, ground slam, utterances, portal, etc.
     - Arrow hits on targets you fired are played at **your** position so you always hear confirmation.
     - Trap projectiles (`AdletTrap`, `BorealTrap`, `Mine`) use `Yeet` when fired and `Klonk`/`SmashKlonk` on impact.
  3. **UI/inventory events**, played non-positionally on UI channels.
- **Positional audio:**
  - The listener sits at the **camera** position and direction.
  - Each SFX goes into a kira `SpatialTrack` with distances `(1.0, 256.0)` and attenuation easing `OutPowf(0.66)`. It also gets an extra linear "player attenuation" of `1 - dist(player, emitter)/256`, and volume is converted with `to_decibels(vol*5*ratio)`.
  - The voice pool has **16/32/64 channels** (Low/Medium/High). When it's full, a new sound steals the farthest channel with equal or lower priority (`SfxTag`).
  - When the camera is inside liquid, a master **low-pass** drops to 888 Hz (20 kHz normally).
  - Subtitles are emitted alongside sounds.
- Thunder is delayed by `distance/340` seconds (speed of sound), which is a nice touch to copy for distant explosions.

### Music (`soundtrack.ron`, `music.rs`, `music_transition_manifest.ron`)
- **Each track is either `Individual(...)` or `Segmented(...)`.**
  - `Individual` fields: `title`, `path`, `length` in seconds (used instead of probing the file), `timing` (Day/Night/None), `weather`, `biomes` (weighted), `sites` (`Void`, `Cave`, `Settlement(..)`, `Dungeon(Cultist|Myrmidon|VampireCastle|Adlet|Haniwa|DwarvenMine|SeaChapel|Gnarling|Terracotta|Sahagin|Old)`), `music_state` (`Activity(Explore|Combat(Low|High))` or `Transition(from,to)`), `artist`.
  - `Segmented` adds a list of `(path, length, state, override)`: a **start** segment (`Transition(Explore, Combat(High))`), a **loop** (`Activity(Combat(High))`), and an **end** (`Transition(Combat(High), Explore)`).
- **Track selection:** filter by current site, then by biome, day period and weather. Don't repeat the last track, then pick a random one.
- **Combat detection:** count hostile entities within `combat_nearby_radius` (25 m). Each enemy counts `ceil(maxHP / combat_health_factor (70))` times. Reaching ≥4 (`combat_nearby_high_thresh`) means Combat(High). Combat is overridden to Explore when the player is dead.
- **Interrupts:** state changes are checked every `interrupt_delay` (5 s).
- **Crossfades** from `fade_timings`:
  - Explore→Combat: 1.0 s out / 0.5 s in
  - Combat→Explore: 2 s / 5 s
  - Explore→Explore: 4 s / 0 s
- **Silence between explore tracks:** **10–20 s in dungeons**, 20–40 s in caves, 120–180 s in towns, 120–240 s elsewhere. All are multiplied by a user "spacing" setting. There's no gap in combat.
- Calendar overrides (Christmas/Halloween) are separate track lists.

### Ambience (`ambience.rs`, `ambience.ron`)
- Seven looping channels (wind, rain, thunder, leaves, **cave**, river loud/quiet). Each has **sample-accurate loop points** in `ambience.ron`.
- Target volumes are recomputed every frame and tweened.
  - Cave volume rises with depth below the terrain surface (`-(cam.z - terrain_alt)/100`) and is full when the site is `Cave`.
  - `is_indoors()` casts 5 rays (±X, ±Y, +Z) up to 50 blocks. If all hit solid blocks, you're indoors, which cuts rain and wind.

---

## 3. Licensing

Sources:
- `assets/credits.ron`, sections `music` and `sounds`.
- `credits.ron`'s own format notes: **license omitted ⇒ GPL-3.0 assumed**.
- Veloren's repo `LICENSE` is GPL-3.0, and the README FAQ says the game is GPL-3.0.

Your crawler is GPL-3.0, so:

| Class | Files | Usable? | Obligations |
|---|---|---|---|
| GPL-3.0, credited author (Ellinia, Oolnokk, Eden, Zesterer, GeekyGami *SFX*, Samstler, Tjin, Corvella, aylana, Flashbang, floppy, Pfau, Robotnik, alfy, Hannibal) | 353 | Yes | keep in a GPL project; credit is good practice |
| GPL-3.0 by default, author **"Unknown"** (credits.ron: *"These files will be categorized with known authors eventually"*) | 103 | Yes, under Veloren's stated default | **provenance unverified**: includes `death_*`, `parry_*`, `stepgrass_*`, `wood_step_*`, `weapon_in/out`, all `inventory/*`, `heal`, `ice_crack`, `whoosh`, `swoosh`, `hiss`, `deep_laugh`, pig/cat/phoenix/wyvern/vampire_bat/dagon/saurok/sea_crocodile utterances |
| GPL-3.0 by default, music with **no credits.ron entry** (artist only in `soundtrack.ron`: Aeronic, DaforLynx, badbbad, Oolnokk, Crow, Tiny, Jeremiah Sweeney, Flashbang, DragonDee, Alfredo Pompa D & Rodrigo Plata, …) | 76 | Yes, by the GPL default | **credit the artist** (Veloren shows title and artist in-game); consider confirming licenses with Veloren or the artists for the tracks you ship |
| Ambience loops + `null.ogg`, **not in credits.ron at all** | 8 | Yes, by the GPL default | author unknown |
| **CC BY 4.0**: DaforLynx (85: bird calls, crickets, all river/running-water, splashes, glider air, interrupted/stunned/dazed/knockeddown, minotaur_smash, sceptre_aura, viola, train steam) and riccifl0w (7 fire abilities) | 92 | Yes (CC BY 4.0 → GPL-3.0 is allowed) | **attribution required**: author, title/file, license link, and a note of any changes. DaforLynx's entry also cites freesound sources (Pfannkuchn, katfolker, poorenglishjuggler, pillwave, JonnyRuss01, PelicanPolice, leonelmail). Copy that note verbatim. |
| **CC-BY-NC-SA 4.0 + "Should not be used in other games than Veloren unless it is parody"** | **7** | **No: exclude** | see below |
| CC0 / CC BY-SA / ND | 0 | – | none in audio (CC-BY-SA appears only for README art and some fonts or models) |

### Exclusion list (all music by GeekyGami)
1. `soundtrack/dungeon/a_hearts_ice-cold_protection.ogg` (Adlet dungeon)
2. `soundtrack/dungeon/still_hearts_coffin.ogg` (Vampire castle)
3. `soundtrack/cave/cavernous_hollow.ogg` (listed twice in credits.ron)
4. `soundtrack/overworld/moonlit_canopy.ogg`
5. `soundtrack/overworld/stargazing.ogg`
6. `soundtrack/overworld/ballerine_macabre.ogg`
7. `soundtrack/town/life_of_a_background_mob.ogg`

GeekyGami's **SFX** (wendigo, wolf and legoom utterances and others in the `sounds` section) carry **no** license field, so they default to GPL-3.0 and are fine. Because his music is restricted, you could politely confirm with him if you want to be certain.

**Practical rule for the import script:** whitelist only files whose credits.ron license is empty (GPL) or `CC BY 4.0`. Hard-fail on any license containing `NC` or `ND`. Generate `assets/audio/CREDITS.md` from credits.ron and soundtrack.ron artists. Code ported from `voxygen/src/audio` is GPL-3.0, so keep a "ported from Veloren (GPL-3.0)" header on those files.

---

## 4. libGDX playback plan (libGDX 1.14, lwjgl3 backend; the crawler has no audio code yet)

### Engine choice
- **libGDX built-in (OpenAL Soft via LWJGL3):**
  - `Sound` for short SFX. It's fully decoded into an OpenAL buffer. `play(volume, pitch, pan)` returns an id, and you can call `setPan/setPitch/setVolume/setLooping(id)`.
  - `Music` for streamed tracks and long ambience. It has `setOnCompletionListener`, `setPosition`, `setLooping`, and per-instance volume.
  - Ogg Vorbis is supported on desktop.
  - **`setPan` does not work on stereo files**, and all Veloren footsteps plus about half the combat SFX are stereo. **Downmix SFX to mono at import** (`ffmpeg -ac 1`).
  - Voice count is `Lwjgl3ApplicationConfiguration.setAudioConfig(simultaneousSources, bufferSize, bufferCount)`. The default is 16 sources; raise it to 32–64 to match Veloren.
  - There's **no 3D, no low-pass, and no sample-accurate loop points** (`Music.setLooping` loops the whole file).
- **`gdx-miniaudio`** (`games.rednblack.miniaudio:miniaudio`, Apache-2.0, built against libGDX 1.14):
  - real 3D listener and sound position/direction/cone, attenuation model and rolloff
  - low-pass and other filter nodes
  - fades
  - **gapless chaining** (good for combat start→loop→end)
  - sound groups (SFX/music/ambience buses)
  - an AssetManager loader
  - It's a closer match to kira's feature set. Costs: an extra native dependency, and on desktop it loads classpath assets from memory.
- Raw OpenAL through LWJGL is possible but means poking at backend internals. Not recommended.

**Recommendation:** start with the built-in `Sound`/`Music` plus a small manual spatializer, since that's enough for a first-person crawler. Move to `gdx-miniaudio` if you want true 3D, muffling through doors, or seamless combat loops.

### Manual spatializer (built-in path)
For each emitter, relative to the camera listener:
- `d = |emitter - listener|`. Skip if `d > maxDist`. For 3 m tiles use about 40 m, not Veloren's 256 m.
- `gain = base * (1 - (d-1)/(maxDist-1))^0.66`, which mirrors kira `OutPowf(0.66)`.
- `pan = clamp(dot(normalize(emitter - listener), cameraRight), -1, 1) * 0.8`. Sounds behind you don't flip.
- **Occlusion:** multiply by the sound-propagation attenuation from the tile grid planned in DETECTION.md. Walls block and doors muffle, so one system serves both AI hearing and player audio.
- Update gain and pan on long or looping instances each frame.
- **Voice limiting:** cap at N playing SFX. Replace the farthest one with lower or equal priority, as Veloren does with `SfxTag`.

### Event → sound table (port of `sfx.ron`)
- Convert the relevant `sfx.ron` entries to `assets/audio/sfx.json`, for example `{"event":"Footstep.Stone","files":[...],"cooldown":0.3,"subtitle":"..."}`. A short Python script that parses the RON and filters to whitelisted files is enough.
- Java side:
  - `SfxEvent` keys as strings or an enum: `Footstep(surface)`, `Swing(weaponKind, stage)`, `Wield/Unwield(weaponKind)`, `Bow.Shot/Hit/Miss`, `Damage`, `Death`, `Block`, `Parry`, `Stagger(level)`, `Utterance(kind, voice)`, `Spell(id)`, `Item(event)`, `Ui(click)`, plus new keys for gaps (`Door.Open`, `Chest.Open`, `Trap.Spike`, …).
  - Pick a random file, add **±5–8% pitch jitter**, which matters because axe and hammer share one `swing` file.
  - Apply a per-entity cooldown from `threshold`.
- **Surface mapping:**
  - stone/brick/cultist rock → `stone_step_*`
  - wood → `wood_step_*`
  - dirt → `stepdirt_*`
  - water → `water_splash_*`
  - moss/grass → `stepgrass_*`
- **Footstep timing:** fire on the foot-contact phase of the walk cycle from the planned skeleton animation, as Veloren's `Footsteps` component does. For the first-person player, fire every ~1.6 m travelled. Sneaking plays quieter (×0.4) and also emits a smaller noise radius for AI.
- **Combat hooks:** play swing at the ability's Action stage, hit or block on resolved damage, and utterance(Hurt) on damage. Throttle utterance(Angry) to once per alert transition from DETECTION.md.

### Music state machine (port of `soundtrack.ron` + `music.rs`)
- `assets/audio/soundtrack.json` holds tracks with `pool` (`dungeon:<theme>`, `cave`, `combat`, `title`), `length`, `artist` and optional `segments`.
- **States:**
  - `Explore`: play a random track from the current floor's pool, never the last one played, then stay silent 10–20 s.
  - `Combat` (≥ threshold of awake hostiles within ~25 m, weighted by maxHP/70): fade explore out over 1 s, play combat `start`, then `loop` on completion, looping.
  - Back to Explore after 5 s with no combat: play `end`, then fade explore in over 5 s.
  - Re-check state every 5 s, as `interrupt_delay` does.
- With libGDX `Music`, the start→loop handoff uses `OnCompletionListener` and may have a small gap (OpenAL streaming). Pre-trimming files or using miniaudio chaining makes it gapless.
- **Suggested pools after exclusions:**
  - Dungeon: Dank Dungeon, Ruination, Vast Onslaught, Sacred Temple (Aeronic), Cobbled Halls (Crow), Mysty Temple (Tiny), Thrumming Waters (Jeremiah Sweeney)
  - Cave: Stars Like Stalactites, Saturated Hallows, Mineral Deposits, Down the Rabbit Hole
  - Combat: Barred Paths, Reversal (DaforLynx), Clash (Pompa/Plata)
  - About 49 MB of Ogg total. Resample `cobbled_halls` from 96 kHz.

### Ambience
- Loop `ambience/cave.ogg` as a constant dungeon bed.
  - Pre-trim it to its loop points (samples 3394–3329488, via `ffmpeg atrim=start_sample=…:end_sample=…`) so `Music.setLooping(true)` loops cleanly.
- Add positional spot loops: `ambient/fire` on torches and braziers, `running_water-*` on water channels and lava (Veloren reuses them for lava).
- A Veloren-style `is_indoors` raycast isn't needed, since the whole level is indoors.

### Effort estimate

| Task | Days |
|---|---|
| Import script: `git lfs pull` whitelist, license filter (NC/ND hard-fail), mono downmix, resample, ambience loop trim, generate `CREDITS.md` | 0.5–1 |
| `sfx.json` port + `SfxTable` loader (random pick, pitch jitter, cooldown) | 0.75–1 |
| `AudioSystem`: voice pool + priority stealing, manual spatializer (gain/pan/occlusion), volume buses + settings | 1–1.5 |
| Gameplay hooks: footsteps (surface + gait), combat/bow/spell/stagger, utterances, items/UI | 1–1.5 |
| Music state machine (explore/combat, segmented tracks, fades, gaps, per-theme pools) | 1–1.5 |
| Ambience bed + positional spot loops | 0.5 |
| Fill gaps from CC0 sources (doors, chests, traps, skeleton/goblin/rat/spider) | 0.5–1 (sourcing only) |
| **Total (libGDX built-in)** | **5–7** |
| Option: switch to `gdx-miniaudio` for real 3D, low-pass muffling, gapless loops | +1.5–2 |

**Dependencies:** occlusion uses the sound-propagation grid (DETECTION.md). Footstep timing uses the skeleton animation (VELOREN_ANIMATION.md), with a distance-based fallback until it exists. Combat hooks use the combat state machine (MOVEMENT.md and the combat plan).

### Gotchas
- Fetch only the files you use with LFS. The full audio tree is about 266 MB, mostly overworld and town music you don't need.
- Pin the Veloren commit in the import script and re-check credits.ron on every update. Licenses can change, as they did for the NC music.
- Don't ship `null.ogg` or instrument and vehicle sounds.
- Veloren's `sfx.ron` sometimes points several events at one file (`swing` for axe, hammer and shield). Expect repetition, and use pitch jitter.
