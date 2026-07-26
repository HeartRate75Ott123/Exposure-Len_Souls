# Len Souls — 镜魂

**Exposure 摄影模组的灵魂扩展，融合元素伤害、BOSS 虚影幻灵与摄魂术能力系统。**

---

## 📖 介绍

Len Souls（镜魂）是 [Exposure](https://github.com/mortuusars/Exposure) 相机模组的功能扩展，新增一套完整的**元素伤害体系**、**BOSS 虚影幻灵演出**以及**摄魂术四能力系统**。通过拍照、镜魂道具与元素附魔，为战斗与探索带来全新的策略维度。

---

## ✨ 特性

### 🔥 元素伤害系统
- 四种元素：**火、水、土、末影**，每种有独立伤害倍率与实体弱点配置
- 通过数据包自定义实体对元素的弱点倍率（`data/lensouls/entity_weakness/`）
- 多元素额外伤害累加计算

### 🪞 镜魂道具
- **基础镜魂**：击杀怪物 10% 概率掉落，右键注入元素活性
- **BOSS 镜魂**：击杀对应 BOSS 必定掉落，额外触发特殊效果
- **铁砧升级**：两个同级镜魂合为下一级，最高 V 级
- 独立的物品冷却系统，支持跨游戏重启持久化

### 📷 摄魂术能力系统
通过相机附魔摄魂术解锁四大能力：

| 能力 | 效果 |
|------|------|
| **弱点透镜** | 拍照装入剑槽，对目标实体类型造成额外伤害 |
| **空间扭曲** | 在拍照位置展开远程交互范围圈 |
| **时空回溯** | 记录生命与位置快照，致命伤自动保命 |
| **时间定格** | 冻结视野内所有实体 5 秒 |
| **要害打击** | 右键相机对 BOSS 造成削韧伤害 |

### 👻 BOSS 虚影幻灵
安装可选前置模组后，激活 BOSS 镜魂触发 10 秒幻灵表演：
- 焰魔、末影守卫、下界合金巨兽（需 [L_Ender's Cataclysm 灾变](https://www.curseforge.com/minecraft/mc-mods/l-enders-cataclysm)）
- 云筑魔像、堕落圣骑、湮灭构造体（需 [Legendary Monsters 传奇怪物](https://www.curseforge.com/minecraft/mc-mods/legendary-monsters)）

### 🎯 武器系统
- **次元枪**：维度能量武器，蓄力射击，三种弹药类型随探索解锁
- **引力枪**：发射引力弹将实体牵引至面前
- 两把武器均通过合成台制作

### ⚙️ 整合包友好
- 所有获取方式可通过配置文件独立开关
- 弱点倍率通过数据包配置，支持 `/reload` 热重载
- JEI 集成：查看物品获取方式与合成配方

---

## 📦 依赖

### 必要
- [Exposure](https://www.curseforge.com/minecraft/mc-mods/exposure) — 核心相机模组

### 可选
- **ExposurePolaroid** — 拍立得相机支持
- **exposure-expanded** — 附加功能扩展
- **L_Ender's Cataclysm（灾变）** — 解锁焰魔/末影守卫/下界合金巨兽镜魂
- **Legendary Monsters（传奇怪物）** — 解锁云筑魔像/堕落圣骑/湮灭构造体镜魂
- **JEI** — 查看物品获取方式与合成配方

---

## 📦 数据包配置

### 实体弱点（entity_weakness）

路径：`data/lensouls/entity_weakness/<命名空间>.json`，通过 `/reload` 热重载。

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

**取值说明**：倍率 > 1 为弱该元素（增伤），< 1 为抗该元素（减伤），0 为免疫。`projectile` 为弹射物通用弱点。未配置的实体默认 0.1 倍（弹射物默认 0）。

### 物品元素活性（item_element_activity）

路径：`data/lensouls/item_element_activity/<物品注册名>.json`

```json
{
  "values": {
    "lensouls:fire": 2,
    "lensouls:water": 1
  }
}
```

**取值说明**：等级 1~5，分别对应活性倍率 1.2 / 1.5 / 2.0 / 2.5 / 3.0。未配置的武器活性为 0（不贡献元素伤害）。

## 🔧 配置

配置文件位于 `config/lensouls-common.toml`：

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `defaultDuration` | 30 | 镜魂灌注持续时间（秒） |
| `defaultCooldown` | 60 | 基础镜魂冷却时间（秒） |
| `bossCooldown` | 120 | BOSS 镜魂冷却时间（秒） |
| `photoBonus` | 1.2 | 照片增伤倍率 |
| `enableBasicSoulDrop` | true | 基础镜魂怪物掉落 |
| `enableBossSoulDrop` | true | BOSS 镜魂掉落 |
| `enableEnchantmentLoot` | true | 摄魂术战利品与村民交易 |
| `enableDimensionalGunRecipe` | true | 次元枪配方 |
| `enableGravityGunRecipe` | true | 引力枪配方 |
| `enableConverterRecipe` | true | 转换器配方 |
| `enableSkillBallBossLoot` | true | 能力球 BOSS 掉落 |

---

## 🎮 快速上手

1. 给相机附魔**摄魂术**
2. 击杀怪物收集**镜魂**，右键激活元素灌注
3. 对 BOSS 拍照削减韧性，破防后获得**BOSS 镜魂**
4. 制作**转换器**存放镜魂，按 G 键快速切换
5. 铁砧中合成同级镜魂提升等级

---

## 🌐 链接

- [GitHub](https://github.com/plumejade/lensouls)
- [Exposure](https://github.com/mortuusars/Exposure)

---

## 📜 许可

MIT License © 2024-2025 Plume Jade
