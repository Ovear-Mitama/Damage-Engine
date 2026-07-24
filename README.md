# Damage Engine

![](https://cdn.modrinth.com/data/cached_images/49065a3a75ee433a521883aeccc277997b5393b5.gif)

![](https://cdn.modrinth.com/data/cached_images/2790e9f4439f9d099471217416ad36543a5b4e12_0.webp)

Damage Engine displays comprehensive combat information on your HUD — total damage, combo counter, damage history, target entity info, floating damage numbers, and a rating system.

**Requires installation on both client and server.**

---

### HUD Modules

- **Total Damage** — Accumulated damage in the current combat session, with configurable color thresholds (e.g., white → blue → magenta → gold at different damage milestones).
- **Combo Counter** — Shows how many hits you've landed in a row (e.g., "x5").
- **Progress Bar** — A bar that instantly refills on hit and smoothly drains over time, indicating when the combo/damage session will reset.
- **Damage History** — A scrolling list of recent individual damage values, newest on top, with slide-in and fade-out animations.
- **Target Info Panel** — Displays the last entity you hit: player avatar head (for players), entity name, HP values, and an animated health bar with damage tail and heal effects.
- **Floating Damage Indicators** — World-space numbers that pop up from hit targets or projectiles, with distinct animations for normal hits, crits, and kills ("Kill!"). Supports distance-based scaling.
- **Rating System** — Earn a grade (S / A / B / C / D) based on combo count, normal hits, and crits in each combat session. All scoring weights and grade thresholds are fully configurable.

### HUD Editor

Each module can be independently repositioned and resized via a drag-and-drop HUD editor, with undo/redo support.

---

### Configurable Options

- Show/hide each HUD module individually.
- Custom colors for progress bar, combo, history entries, normal/crit damage, health bars, floating indicators, and kill text.
- Configurable color thresholds for total damage (define color tiers at any damage value).
- Adjustable combo reset time, history entry limit (1–50), history display duration, and decimal places (0–10).
- F1 compatibility — optionally hide the HUD when the vanilla GUI is hidden.
- Debug mode — optionally print raw damage info to chat or show current rating score in the action bar.
- Full `config/damage-engine/config.json` for manual editing.

---

### Key Bindings

All keys are unbound by default and can be assigned in the controls menu:

- **Open Config** — Opens the configuration GUI.
- **Toggle HUD** — Show/hide the entire Damage Engine HUD.
- **Clear Damage** — Reset the current damage session and clear all floating indicators.

---

### Commands

- **`/damage_engine clear`** — Clears current damage data (same as the key binding).

---

### Compatibility

- **Mod Menu** — Full configuration GUI integration via the Mod Menu gear button.
- **FOV/Zoom Mods** — Correctly handles projection matrices for accurate floating indicator positioning.
- **Jade** — Listed as a suggested dependency.

---

### Feedback & License

- Issues and suggestions: [https://github.com/Ovear-Mitama/Damage-Engine/issues](https://github.com/Ovear-Mitama/Damage-Engine/issues)
- Licensed under the [MIT](https://opensource.org/license/MIT) license.
