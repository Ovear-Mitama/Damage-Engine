# Damage Engine

![](https://cdn.modrinth.com/data/cached_images/49065a3a75ee433a521883aeccc277997b5393b5.gif)

![](https://cdn.modrinth.com/data/cached_images/2790e9f4439f9d099471217416ad36543a5b4e12_0.webp)

Damage Engine shows your combat in clear numbers: how much damage you deal, how many hits you chain, where each hit lands, and how the target is doing.

> Install it on **both client and server** for the full experience. If the server does not have it, the client can still show damage numbers in a limited client-only mode.

***

## Features

### Floating Damage Numbers

- Damage numbers appear in the world at the hit location, not on a fixed HUD corner.
- Separate colors and animations for normal hits, crits and kills, with custom kill text.
- Numbers scale with distance, so nearby hits are readable and far ones stay subtle.
- **Enhanced** and **cloud** animation modes, plus an in-game animation editor.
- Add particle effects to animations and preview them directly in the editor.
- Optional **merge** mode: fast hits on the same target combine into one number.
- Optional **entity tracking**: the number follows a moving target.
- Heal numbers and global damage numbers for hits dealt by other players.
- **Damage overflow** option: show the full damage dealt instead of stopping at the target's remaining health.

### HUD Modules

- **Total Damage** — damage dealt in the current fight, with configurable color tiers.
- **Combo Counter** — consecutive hits, for example `x5`.
- **Progress Bar** — shows when the current combo will reset.
- **Damage History** — recent hits, newest first, with smooth slide animation.
- **Target Info** — the last target's name, 3D entity preview, health values and an animated health bar with damage-tail and heal effects. A perception filter can hide targets you can neither see nor hear.
- **Rating** — an S / A / B / C / D grade based on combos, hits and crits.

### HUD Editor and Layout

- Drag, resize and arrange every module in-game, with undo and redo.
- HUD inertia: modules smoothly follow camera movement, with adjustable strength.
- Left/right alignment for the damage display.

### Quality of Life

- Number formatting: separators (`1,000`) and abbreviation (`1.5K`).
- Configurable decimal places for totals and floating numbers.
- F1 compatibility: optionally hide the HUD with the vanilla GUI.
- Multiple config profiles for different servers or playstyles.
- Debug options for raw damage info and live rating score.

***

## Client-Only Mode

When the server does not have Damage Engine installed, the client estimates damage from health changes.

- Works for most mobs and players without installing anything on the server.
- Limited accuracy: the attacker, crits and projectile hits cannot always be identified, and the number position is estimated.

***

## Configuration

- Open the config screen with the config key binding or from Mod Menu.
- Edit the HUD layout directly in-game through the HUD editor.
- Colors, timings, distances, animations and number formats are configurable in-game.

### Key Bindings

All keys are unbound by default and can be assigned in the controls menu:

- **Open Config** — open the configuration screen.
- **Toggle HUD** — show or hide the whole HUD.
- **Clear Damage** — reset the current session and remove floating numbers.

### Commands

- `/damage_engine clear` — clears current damage data.

***

## Links

- Source: <https://github.com/Ovear-Mitama/Damage-Engine>
- Issues and suggestions: <https://github.com/Ovear-Mitama/Damage-Engine/issues>

Licensed under the [MIT](https://opensource.org/license/MIT) license.
