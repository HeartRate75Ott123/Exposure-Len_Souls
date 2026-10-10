# Len Souls 数据包配置

## 目录结构

```
data/lensouls/
├── entity_weakness/           ← [被攻击实体] 元素弱点倍率
├── item_element_activity/     ← [武器] 元素活性等级
├── damage_type_element/       ← [伤害类型] → 元素映射
├── attacker_element/          ← [攻击者实体] → 元素映射
├── copysoul_filter/           ← [复制之魂] 掉落黑白名单
├── damage_type/               ← 自定义伤害类型定义
├── recipe/                    ← 合成配方
└── loot_tables/               ← 战利品表
```

所有 JSON 除 `damage_type/` 外均支持 `/reload` 热重载。

---

## copysoul_filter —— 复制之魂掉落 / 复制黑白名单

**路径：** `data/lensouls/copysoul_filter/` 下四个文件，均为实体/物品 ID 的 JSON 数组（也支持对象，键为 ID），支持 `/reload` 热重载：

| 文件 | 作用 |
|------|------|
| `drop_whitelist.json` | 哪些实体死亡掉落复制之魂（白名单） |
| `drop_blacklist.json` | 哪些实体永不掉落（黑名单） |
| `copy_whitelist.json` | 哪些物品可被复制之魂复制（白名单） |
| `copy_blacklist.json` | 哪些物品不可被复制（黑名单） |

每个文件里可以写三类条目：

| 写法 | 含义 |
|------|------|
| `"minecraft:oak_sapling"` | 点名一个具体 ID |
| `"#minecraft:saplings"` | **标签**，代表该标签下的全部成员（含子标签继承） |
| `"all"` | 通配，代表该领域全部内容 |

### 合并规则：谁更「具体」谁赢

对目标取白名单与黑名单各自命中的**最具体条目**，比「具体度」：

```
点名具体 ID          具体度 1000000
具名标签             具体度 = 路径层级 + 1（#a:group/child = 1 胜过 #a:group = 0）
"all"               具体度 -1（严格低于任何具名标签）
未命中              不参与比较
```

- 白名单更具体 → **允许**
- 黑名单更具体 → **禁止**
- **两边具体度相同 → 黑名单胜**（收紧）

推论（也就是这套规则的常见用法）：

| 配置 | 结果 |
|------|------|
| 黑名单 `["#minecraft:saplings"]` | 所有树苗物品不可复制 |
| 黑名单 `["#minecraft:saplings"]` + 白名单 `["minecraft:oak_sapling"]` | 只有橡树树苗可复制（白名单点名更具体） |
| 白名单 `["#minecraft:saplings"]` + 黑名单 `["minecraft:oak_sapling"]` | 除橡树树苗外都可复制（黑名单点名更具体） |
| 白名单 `["#minecraft:logs"]` + 黑名单 `["minecraft:oak_log"]` | 橡木原木不可复制，其余原木可复制（父标签只是在挂子标签，点名依然更具体） |
| 黑名单 `["all"]` + 白名单 `["minecraft:stone"]` | 只有石头可复制 |
| 黑名单 `["all"]` + 白名单 `["#minecraft:saplings"]` | 只有树苗可复制（标签比 all 具体） |
| 白名单 `["all"]` + 黑名单 `["minecraft:bedrock"]` | 除基岩外都可复制 |
| 白名单与黑名单**都含 `"all"`** | 全部禁止（同级 → 黑名单胜），要靠白名单点名/标签才能回加 |
| 黑名单 `[]` | 全部允许（默认形态，不做任何标签查表） |
| 白名单 `[]` | 等于「无白名单约束」= 默认放行 |

> **标签索引是从「物品自身」读的，不是读注册表的绑定表。** 这一点必须知道，因为有些模组
> （例如 gytrinket）的数据文件里标签是空的（`"values": []`），它在启动时遍历物品、把 TagKey
> **直接写进每个物品 Holder 的 tags 集合** —— 这种标签在注册表的「标签→成员」表里永远是空的，
> 只有在物品自身查得到。本名单逐物品读 `Holder#tags()`，与游戏里 `物品.is(标签)` 完全同源，
> 因此这类运行时补的标签照常生效。
>
> 展开结果按注册表建索引缓存，并在标签重载（`/reload`、客户端收包）时整体失效重建，
> 所以其它数据包往 `#minecraft:saplings` 里加树苗、或模组运行时补标签，本名单都会跟上。
> 标签不存在（模组没装或拼写错误）只记 WARN 并在日志里点名该条，不会让整份名单失效。

> **性能口径（`/reload` 不会因此卡顿）**：`/reload` 与标签重载时本过滤器**只丢缓存、不建索引**。
> 建索引是惰性的，推迟到「名单里真的写了 `#标签` 且第一次需要判定」时才做一次；
> 默认名单（黑名单为空）走早退，**一次都不建**。实测建一次索引（含逐个物品读其自身标签集合）：
> 3,000 物品约 **1.0 ms**、8,000 物品约 **3.7 ms**、20,000 物品的最坏情况约 **26 ms**；
> 建好之后每次判定只是 2 次哈希查表（约 7 ns），与名单条目数无关。
> 物品侧与实体侧各有一份独立索引，交替判定不会互相顶掉、不会反复重建。

掉落基础判定：实体最大生命值 **≥ 200**（`boss_entities/bosses.json` 内的首领豁免该门槛）。复制之魂本身默认不可复制（硬编码拒绝，名单无法覆盖）。

```json
// drop_whitelist.json（默认）
["all"]
// drop_blacklist.json（默认）
[]
// copy_whitelist.json（默认）
["all"]
// copy_blacklist.json（默认）
[]

// 示例：禁用整类树苗，但放行橡树树苗与金合欢树苗
// copy_blacklist.json
[ "#minecraft:saplings" ]
// copy_whitelist.json
[ "minecraft:oak_sapling", "minecraft:acacia_sapling" ]

// 示例：仅灾变/传奇怪物 BOSS 可掉，末影龙与整类袭击者除外
// drop_whitelist.json
[ "cataclysm:ignis", "legendary_monsters:posessed_paladin" ]
// drop_blacklist.json
[ "minecraft:ender_dragon", "#minecraft:raiders" ]
```

可用 ID 为各模组注册名（命名空间:路径），例如原版 `minecraft:wither`、灾变 `cataclysm:ignis`、传奇怪物 `legendary_monsters:posessed_paladin`、物品 `minecraft:netherite_block`。
可用标签用游戏内 `/lensouls copysoul tags` 列出（物品标签）。

### 游戏内核验（`/lensouls copysoul`）

| 指令 | 作用 |
|------|------|
| `/lensouls copysoul` | 查主手物品能否被复制（判定 + 双方命中条目 + 决定胜负的规则） |
| `/lensouls copysoul minecraft:oak_sapling` | 查指定物品 |
| `/lensouls copysoul #minecraft:saplings` | 对比该标签「索引内成员数」与「注册表绑定表成员数」，并列出成员 |
| `/lensouls copysoul tags` | 列出索引内全部物品标签及成员数（**含模组运行时补的标签**） |

`#标签` 那条会同时打出两个数字，两列不一致就说明该标签是模组运行时直接写进物品 Holder 的
（注册表绑定表是空的），本名单认「索引」那一列：

```
#gytrinket:random_build_pool：索引内成员 42 个，注册表绑定表 0 个｜整包物品标签 186 个
两列不一致 = 该标签由模组在运行时直接写进物品 Holder（如 gytrinket），本名单认索引这一列（与 物品.is(标签) 同源）
```

输出示例：

```
目标：minecraft:oak_sapling
白名单命中：minecraft:oak_sapling（点名白名单，具体度 1000000）  [具体度 点名]
黑名单命中：#minecraft:saplings（标签，含 12 个 ID，具体度 1）  [具体度 1]
裁决：白名单更具体 → 允许
✔ 允许复制
```

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
