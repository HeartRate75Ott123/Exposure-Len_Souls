# Len Souls

An **Exposure** camera mod addon that adds an elemental damage system, boss phantom spectacles, and a Soul Photography ability system.

---

## 📖 Overview

Len Souls is a feature expansion for the [Exposure](https://github.com/mortuusars/Exposure) camera mod. It introduces a complete **elemental damage system**, **boss phantom spectacles**, and **Soul Photography abilities** driven by in-game photography and soul items.

---

## ✨ Features

### 🔥 Elemental Damage
- Four elements: **Fire**, **Water**, **Earth**, **Ender** — each with configurable damage multipliers and entity weakness values
- Customizable via data packs (`data/lensouls/entity_weakness/`) — reload with `/reload`
- Multiple elements stack additively for bonus damage

**Entity Weakness** (`data/lensouls/entity_weakness/<namespace>.json`):
```json
{
  "minecraft:zombie": {
    "fire": 1.5,
    "water": 0.5,
    "earth": 1.0,
    "projectile": 1.0
  },
  "minecraft:blaze": {
    "fire": 0.0,
    "water": 3.0
  }
}
```
Values: > 1 = weak (takes bonus damage), < 1 = resistant, 0 = immune. `projectile` is a general weakness for projectile damage. Unconfigured entities default to 0.1x.

**Item Activity** (`data/lensouls/item_element_activity/<item_id>.json`):
```json
{
  "values": {
    "lensouls:fire": 2,
    "lensouls:water": 1
  }
}
```
Levels 1-5 map to activity multipliers 1.2 / 1.5 / 2.0 / 2.5 / 3.0.

### 🪞 Soul Items
- **Basic souls**: 10% chance to drop from monsters — right-click to infuse elemental activity
- **Boss souls**: 100% drop from corresponding bosses — also triggers a special effect
- **Anvil upgrade**: Combine two same-level souls to reach the next level (up to level V)
- Per-item cooldown persists across game restarts

### 📷 Soul Photography Abilities
Enchant your camera with **Soul Photography** to unlock abilities:

| Ability | Effect |
|---------|--------|
| **Weakness Lens** | Photos mount on swords for bonus damage against the captured entity type |
| **Spatial Warp** | Create an interaction zone at the photo's capture location |
| **Temporal Recall** | Snapshot health and position — auto-triggers on fatal damage |
| **Time Stop** | Freeze all visible entities for 5 seconds |
| **Vital Strike** | Right-click the camera to deal 1 toughness damage to bosses |

### 👻 Boss Phantoms
Activating a boss soul triggers a 10-second cinematic phantom sequence (requires optional mods):

| Mod | Bosses |
|-----|--------|
| [L_Ender's Cataclysm](https://www.curseforge.com/minecraft/mc-mods/l-enders-cataclysm) | Ignis, Ender Guardian, Netherite Monstrosity |
| [Legendary Monsters](https://www.curseforge.com/minecraft/mc-mods/legendary-monsters) | Cloud Golem, Possessed Paladin, The Obliterator |

### 🎯 Weapons
- **Dimensional Gun**: Energy weapon with charge shots and 3 ammo types unlocked by exploring dimensions
- **Gravity Gun**: Fire gravity bullets to pull entities toward you
- Both crafted at the crafting table (vanilla materials)

### ⚙️ Pack-Maker Friendly
- All acquisition methods toggleable via config
- Entity weakness values configurable via data packs with `/reload` support
- JEI integration for item info and recipes

---

## 📦 Dependencies

### Required
- [Exposure](https://www.curseforge.com/minecraft/mc-mods/exposure)

### Optional
- **ExposurePolaroid** — Instant camera support
- **exposure-expanded** — Extra features
- **L_Ender's Cataclysm** — Ignis, Ender Guardian & Netherite Monstrosity souls
- **Legendary Monsters** — Cloud Golem, Possessed Paladin & The Obliterator souls
- **JEI** — View item acquisition info and recipes

---

## 🔧 Configuration

File: `config/lensouls-common.toml`

| Option | Default | Description |
|--------|---------|-------------|
| `defaultDuration` | 30 | Soul infusion duration (seconds) |
| `defaultCooldown` | 60 | Basic soul cooldown (seconds) |
| `bossCooldown` | 120 | Boss soul cooldown (seconds) |
| `photoBonus` | 1.2 | Photo damage bonus multiplier |
| `enableBasicSoulDrop` | true | Basic soul drops from mobs |
| `enableBossSoulDrop` | true | Boss soul drops |
| `enableEnchantmentLoot` | true | Soul Photography in dungeon loot & villager trades |
| `enableDimensionalGunRecipe` | true | Dimensional Gun recipe |
| `enableGravityGunRecipe` | true | Gravity Gun recipe |
| `enableConverterRecipe` | true | Converter recipe |
| `enableSkillBallBossLoot` | true | Skill Ball boss drops |

---

## 🎮 Quick Start

1. Enchant a camera with **Soul Photography**
2. Kill monsters to collect **souls** — right-click to activate elemental infusion
3. Photograph bosses to reduce their toughness, then claim **boss souls**
4. Craft a **Converter** to store souls — press the converter hotkey (default G) to activate
5. Combine same-level souls on an anvil to increase power

---

## 🌐 Links

- [GitHub](https://github.com/plumejade/lensouls)
- [Exposure](https://github.com/mortuusars/Exposure)

---

## 📜 License

MIT License © 2024-2025 Plume Jade
