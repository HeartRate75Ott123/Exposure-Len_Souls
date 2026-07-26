# 镜魂幻灵系统 — 借真身驱动技术路线

## 概述

镜魂幻灵系统（简称**借真身驱动**）的核心思想：不手写幻灵实体、不手写粒子、不配音效帧。直接借用灾变/传奇怪物的**真实 BOSS 实体**，保持其 AI 运行，让 `tick()` 自然驱动动画/粒子/音效。我们在渲染层叠加半透明元素色覆盖。

### 架构分层

| 层 | 负责 | 实现 |
|----|------|------|
| **动画逻辑**（骨骼运动） | 原模组 | AI 正常跑，打连招、喷粒子、播音效 |
| **伤害** | 我们 + 原模组 | 召唤瞬间径向 AOE + 实体原生 AreaAttack |
| **像素绘制**（幻灵特效） | 我们 | `PhantomVertexConsumer` 拦截顶点颜色，替换为元素色+半透明 alpha |

### 核心优势

- 零手写动画：BOSS 原生 AI 驱动完整连招
- 零手写粒子：灾难/传奇怪物的 `aiStep()` 原生产粒子
- 零手写音效：原生 `playSound` 调用
- 模型精确：原生模型 + 原生 `setupAnim()`，骨骼 100% 还原
- 新增 BOSS 只需配置元数据，不写 Java 逻辑

---

## 系统组件

### 关键文件

| 文件 | 职责 |
|------|------|
| `entity/BossPhantomType.java` | **BOSS→幻灵映射枚举**，所有元数据在这里 |
| `entity/BossPhantomManager.java` | **服务端序列管理器**，启动/tick/结束/断线清理 |
| `entity/PhantomDamageHandler.java` | 幻灵伤害补偿 + 元素标签 |
| `mixin/client/LivingEntityPhantomMixin.java` | 客户端渲染拦截 |
| `client/phantom/PhantomVertexConsumer.java` | 顶点颜色替换包装器 |
| `client/phantom/ClientPhantomHandler.java` | 客户端幻灵 ID 集 + 降级粒子 |
| `network/PhantomStartPacket.java` | S2C 启动包 |
| `network/PhantomStopPacket.java` | S2C 停止包 |

### 数据流

```
玩家使用镜魂物品
  → BossPhantomManager.startPhantom()
    → startBorrowedEntity()
      → 反射构造真实 BOSS 实体
      → entity.getPersistentData().put("lensouls:phantom", true)
      → setTarget(findNearestEnemy)
      → level.addFreshEntity(entity)
      → PhantomStartPacket (含 entityId)
        → ClientPhantomHandler.addPhantomEntity(entityId)
          → LivingEntityPhantomMixin 检测到→替换渲染

每 tick:
  Server: BossPhantomManager.tick()
    → target 重新指派（丢失时）
    → 强制面向目标
    → 类型特定冷却清零（下界合金巨兽）
    → 200 tick 后 endPhantom()

Client 渲染:
  LivingEntityRenderer.render()
    → @WrapOperation model.renderToBuffer()
      → isPhantom? → PhantomVertexConsumer 包装
        → model.renderToBuffer(ps, wrappedVC, light, overlay, 0xFFFFFFFF)
          → 模型写入顶点→包装器拦截 setColor→替换为元素色×alpha
```

---

## 添加新 BOSS 镜魂 — 步骤指南

### 前置条件

1. 目标 BOSS 模组是 compileOnly 依赖
2. BOSS 实体有 `(EntityType, Level)` 构造器
3. BOSS 的 `ModEntities` 中有 `Supplier<EntityType<?>>` 字段

### Step 1: BossPhantomType 新增枚举值

打开 `entity/BossPhantomType.java`，在枚举列表末尾添加新值：

```java
NEW_BOSS("modid", "registry_name", ElementDamage.FIRE, 2.0f, false, 0xFF4500,
        30, 7.5, 8.5, 8.0f, 6.0f,
        "com.example.mod.entity.NewBossEntity",         // className
        "com.example.mod.init.ModEntities",               // modEntitiesClass
        "NEW_BOSS",                                       // entityTypeFieldName
        "modid:textures/entity/new_boss.png"),            // texturePath
```

参数说明：

| # | 参数 | 示例 | 说明 |
|---|------|------|------|
| 1 | `modId` | `"cataclysm"` | 模组 ID，用于 `isModLoaded()` 检测 |
| 2 | `entityRegistryName` | `"ignis"` | 实体注册名（仅日志用） |
| 3 | `element` | `ElementDamage.FIRE` | 元素类型 |
| 4 | `damageMultiplier` | `2.0f` | 物品倍率。**影响伤害补偿**：`补偿 = dmgMult × 1.25` |
| 5 | `applySlowness` | `false` | 是否应用减速 |
| 6 | `color` | `0xFF4500` | RGB 主色调，用于渲染 + 描边 |
| 7 | `skillTick` | `30` | 旧路径遗留，新路径填任意值 |
| 8-9 | `spectatorBack/Up` | `7.5, 8.5` | 旧路径遗留，新路径填任意值 |
| 10-11 | `skillDamage/Radius` | `8.0, 6.0` | 召唤 AOE 伤害和范围 |
| 12 | `className` | 实体全限定名 | **Mixin 检测用**，必须准确 |
| 13 | `modEntitiesClass` | ModEntities 全限定名 | 反射获取 EntityType |
| 14 | `entityTypeFieldName` | `"NEW_BOSS"` | ModEntities 中的字段名 |
| 15 | `texturePath` | 资源路径 | 客户端半透明渲染用 |

### Step 2: 验证元数据正确性

确保：
- `className` 与实际实体类完全一致（含内部类路径）
- `entityTypeFieldName` 与 `ModEntities` 中的字段名完全一致（大小写敏感）
- 构造器签名是 `(EntityType, Level)`

#### 常见 ModEntities 字段命名风格

| 模组 | 风格 | 示例 |
|------|------|------|
| Cataclysm | `UPPER_SNAKE` | `IGNIS`, `ENDER_GUARDIAN` |
| Legendary Monsters | `Pascal_Snake` | `Cloud_golem`, `Posessed_Paladin` |
| Legendary Monsters (特殊) | `UPPER_SNAKE` | `THE_OBLITERATOR` |

### Step 3: 构建测试

```bash
./gradlew build
```

无编译错误即接入完成。**不需要改任何 Java 逻辑**——泛化借体方法、泛化 Mixin、泛化伤害处理器已经覆盖所有 `BossPhantomType` 枚举值。

### Step 4: （可选）特殊初始化

如果新 BOSS 需要像 Ignis 那样跳过某些就绪阀门（如 `blockingProgress = 10`），在 `BossPhantomType.initEntity()` 中添加分支：

```java
public void initEntity(Entity entity, ServerLevel level) {
    if (this == IGNIS) { initIgnis(entity); }
    if (this == NEW_BOSS) { initNewBoss(entity); }
}
```

### Step 5: （可选）冷却清零

如果新 BOSS 有内置攻击冷却，在 `BossPhantomManager.tick()` 中添加清零逻辑（参考 `resetNetheriteCooldowns` 模式）。

---

## 伤害补偿系统

### 倍率公式

```
补偿倍率 = type.getDamageMultiplier() × 1.25
元素弱点 = DataPackLoader.getWeakness(targetId, type.getElement())
总倍率 = 补偿倍率 + 元素弱点

newDamage = originalDamage × max(1.0, 总倍率)
```

### 当前倍率表

| BOSS | itemMultiplier | 补偿倍率 | 元素 |
|------|---------------|---------|------|
| Ignis | 2.0 | 2.5x | Fire |
| Cloud Golem | 1.2 | 1.5x | Water |
| Possessed Paladin | 1.5 | 1.875x ≈ 1.9x | Earth |
| Ender Guardian | 1.5 | 1.875x ≈ 1.9x | Ender |
| Obliterator | 2.0 | 2.5x | Ender |
| Netherite Monstrosity | 2.0 | 2.5x | Earth |

---

## 渲染管线详解

### 为什么需要 VertexConsumer 包装器

标准 Ignis 的模型（`Ignis_Model`）在 `renderToBuffer()` 中正确传递了 5-参数中的 `color`，所以直接调用 `model.renderToBuffer(ps, buf, light, overlay, color)` 就能用上 alpha。

但部分模型（如 `Cloud_GolemModel`、`TheObliteratorModel`）的 `renderToBuffer()` 内部调用了 `root.render(ps, buf, light, overlay)`（**4-参数**），丢弃了传递进来的 `color` 参数。这导致直接设置 alpha 无效。

**解决方案**：`PhantomVertexConsumer` 在 `VertexConsumer` 层级拦截所有 `setColor()` 调用，无论模型是否传递 color，顶点写入时都被替换为元素色 + 幻灵 alpha。

### 渲染链路

```
LivingEntityRenderer.render()
  → @WrapOperation model.renderToBuffer(ps, vc, light, overlay, color)
    → isPhantom()? → phantom() :
      → vc = buf.getBuffer(RenderType.entityTranslucent(tex))
      → wrapped = new PhantomVertexConsumer(vc, elemColor, alpha)
      → model.renderToBuffer(ps, wrapped, light, overlay, 0xFFFFFFFF)
        → model 内部调用 wrapped.setColor(r, g, b, a)
          → PhantomVertexConsumer 替换为 (elemR, elemG, elemB, alpha)
```

### calcAlpha 淡入淡出曲线

```
tick 0-20:   线性从 0 升至 153 (alpha 0→0.6)
tick 20-180: 保持 alpha=153 (0.6)
tick 180-200:线性从 153 降至 0
```

### 元素色映射

元素色存储在每个 `BossPhantomType` 的 `color` 字段中，通过 `getColorForClass(className)` 在渲染时动态查询。

---

## 实体配置元数据表

当前已注册的 6 个 BOSS 实体元数据：

### Cataclysm

| Type | className | ModEntities 字段 | 纹理 | 元素色 |
|------|-----------|-----------------|------|--------|
| IGNIS | `com.github.L_Ender.cataclysm.entity.AnimationMonster.BossMonsters.Ignis_Entity` | `IGNIS` | `cataclysm:textures/entity/ignis/ignis_idle_0.png` | `0xFF4500` |
| ENDER_GUARDIAN | `cataclysm:...Ender_Guardian_Entity` | `ENDER_GUARDIAN` | `cataclysm:textures/entity/ender_guardian/new_ender_guardian.png` | `0x660099` |
| NETHERITE_MONSTROSITY | `cataclysm:...Netherite_Monstrosity_Entity` | `NETHERITE_MONSTROSITY` | `cataclysm:textures/entity/monstrosity/netherite_monstrosity.png` | `0xCC6600` |

### Legendary Monsters

| Type | className | ModEntities 字段 | 纹理 | 元素色 |
|------|-----------|-----------------|------|--------|
| CLOUD_GOLEM | `legendary_monsters:...Cloud_GolemEntity` | `Cloud_golem` | `legendary_monsters:textures/entity/cloud_golem/cloud_golem.png` | `0x87CEEB` |
| POSSESSED_PALADIN | `legendary_monsters:...PossessedPaladinEntity` | `Posessed_Paladin` | `legendary_monsters:textures/entity/posessed_paladin/new_posessed_paladin.png` | `0x8B4513` |
| OBLITERATOR | `legendary_monsters:...TheObliteratorEntity` | `THE_OBLITERATOR` | `legendary_monsters:textures/entity/the_warped_one/the_warped_one.png` | `0x9933CC` |

---

## 断线保护

### 服务端

```java
// onPlayerLogout — 清理活跃幻灵实体
getEntity(d.phantomEntityId()).discard();
activePhantoms.remove(uuid);

// onPlayerLogin — 恢复原始游戏模式 + 清理残留
if (persistentData.contains("lensouls:originalGameType")) {
    GameType original = GameType.byId(persistentData.getInt(...));
    player.setGameMode(original);
}
```

原始游戏模式在 `startBorrowedEntity()` 中写入 NBT，`endPhantom()` 中清理。

---

## 常见问题排查

### 幻灵渲染为不透明（无半透明效果）

**原因**：模型的 `renderToBuffer()` 丢弃了 color 参数。
**验证**：检查模型是否调用了 `root.render(ps, buf, light, overlay)` 4-参数版本。
**修复**：已由 `PhantomVertexConsumer` 自动处理。如果仍有问题，检查 `BossPhantomType.getTextureForClass()` 是否返回了正确的纹理路径。

### 幻灵不被 mixin 拦截

**原因**：类名不匹配或实体 ID 未注册。
**验证**：
1. 确认 `BossPhantomType.isPhantomClassName(className)` 返回 true
2. 确认 `ClientPhantomHandler.isPhantomEntity(entityId)` 返回 true
3. 检查网络包 `PhantomStartPacket` 是否成功发送到客户端

### 幻灵不攻击

**原因**：没有 target。
**验证**：`BossPhantomManager.tick()` 中每 tick 重新指派 target，确认 `findNearestEnemy()` 在范围内找到了有效目标。

### 下界合金巨兽打完一招后发呆

**原因**：原生 AI 有内置冷却字段（`shoot_cooldown` 等），攻击后设 120 tick 冷却。
**修复**：`resetNetheriteCooldowns()` 每 tick 将所有冷却清零。

### 断线重连后还是旁观模式

**原因**：`endPhantom()` 未触发，`originalGameTypes` 内存数据丢失。
**修复**：游戏模式信息已持久化到玩家 NBT，`onPlayerLogin()` 自动读取恢复。

---

## 文件变更清单（新增 BOSS 时）

| 文件 | 必须改？ | 改动 |
|------|---------|------|
| `entity/BossPhantomType.java` | ✅ 必须 | 添加枚举值 + 15 个构造参数 |
| 其他 Java 文件 | ❌ 不需要 | 泛化代码已自动适配所有枚举值 |
| 模组物品 / 翻译 | ✅ 必须 | 注册镜魂物品 + 语言文件 |
