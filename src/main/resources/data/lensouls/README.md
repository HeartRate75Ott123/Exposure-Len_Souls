# Len Souls 数据包配置

## 目录结构

```
data/lensouls/
├── entity_weakness/           ← [被攻击实体] 元素弱点倍率
├── item_element_activity/     ← [武器] 元素活性等级
├── damage_type_element/       ← [伤害类型] → 元素映射
├── attacker_element/          ← [攻击者实体] → 元素映射
├── damage_type/               ← 自定义伤害类型定义
├── recipe/                    ← 合成配方
└── loot_tables/               ← 战利品表
```

所有 JSON 除 `damage_type/` 外均支持 `/reload` 热重载。

---

## entity_weakness —— 实体弱点

**路径：** `data/lensouls/entity_weakness/<任意文件名>.json`

定义被攻击实体对各种元素的弱点倍率。
倍率 > 1 = 弱该元素（追加增伤），< 1 = 抗该元素（减伤），0 = 免疫。
未配置的实体默认 0.1 倍（弹射物默认 0），但不会触发螺旋粒子。

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
  },
  "cataclysm:ignis": {
    "water": 2.5,
    "earth": 0.3
  }
}
```

可用元素：`fire`、`water`、`earth`、`ender`、`projectile`

---

## item_element_activity —— 武器元素活性

**路径：** `data/lensouls/item_element_activity/<任意文件名>.json`

指定武器的元素活性等级。等级 → 活性倍率：1→1.2、2→1.5、3→2.0、4→2.5、5→3.0
同一文件可配置多个物品 ID，支持跨模组命名空间。

```json
{
  "minecraft:diamond_sword": {
    "values": { "lensouls:ender": 2 }
  },
  "twilightforest:fiery_sword": {
    "values": { "lensouls:fire": 2 }
  },
  "irons_spellbooks:fire_staff": {
    "values": { "lensouls:fire": 3, "lensouls:earth": 1 }
  }
}
```

等级 0 = 不配置该元素。等级最高 5。

---

## damage_type_element —— 伤害类型 → 元素

**路径：** `data/lensouls/damage_type_element/<任意文件名>.json`

将任意 DamageType 映射到元素。同一文件可配置多个。

```json
{
  "minecraft:player_attack": { "element": "fire", "activity": 2.0 },
  "irons_spellbooks:fire_spell": { "element": "fire", "activity": 2.5 },
  "irons_spellbooks:ice_spell": { "element": "water", "activity": 2.0 }
}
```

`element` 可用值：`fire`、`water`、`earth`、`ender`
`activity` 在公式中与武器活性同位加算，取值任意。

---

## attacker_element —— 攻击者实体 → 元素

**路径：** `data/lensouls/attacker_element/<任意文件名>.json`

将攻击者实体类型映射到元素。用于其他模组的召唤物/原生生物非武器伤害。

```json
{
  "minecraft:blaze": { "element": "fire", "activity": 1.2 },
  "irons_spellbooks:fire_elemental": { "element": "fire", "activity": 1.5 },
  "cataclysm:ignis": { "element": "fire", "activity": 2.0 }
}
```

---

## 公式

```
追加伤害 = 原伤害 × (武器活性 + 药水活性 + damage_type活性 + 实体活性) × 目标弱点
最终伤害 = 原伤害 + 追加伤害
```

活性检测顺序：
1. 玩家灌注循环（ElementInfusionEffect）
2. 独立武器活性（跳过灌注已处理的元素）
3. 弹射物（IS_PROJECTILE 标签）
4. DamageType 映射
5. 攻击者实体映射

所有活性加算后乘目标弱点。仅服务端计算。

## 元素螺旋粒子

仅在 `entity_weakness/` 中**显式配置**了该实体该元素弱点时发射。
无配置的默认 0.1 倍率影响伤害数值，但不触发粒子。
