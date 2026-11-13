# 镜魂描边重构方案 — 双 FBO 解耦

## 现状诊断（代码审计结果）

### 三套并行管线，仅两套活跃

| 管线 | FBO | 管理者 | 着色器来源 | 状态 |
|------|-----|--------|-----------|:----:|
| **冻结描边** (FrozenOutline) | `FrozenOutlineManager.maskTarget` | `FrozenOutlineManager` | `rendertype_gold_outline.fsh`（冰蓝） | ✅ 活跃 |
| **BOSS 描边** (PlayerOutline) | `BossOutlineManager.maskTarget` | `BossOutlineManager` | ← 借用 frozen 的 `goldOutlineShader`（`BossOutlineManager.java:92`） | ⚠️ 功能正常但耦合 |
| **旧镜魂管线** (SoulGlowFbo) | `SoulGlowFboRenderer.glowTarget` | `SoulGlowFboRenderer` | 从未被设置 → `composite()` 永远 return | ❌ 死代码 |

### 关键 Bug

#### 1. BOSS composite shader 被丢弃

```
LenSoulsClient.java:212 → BossOutlineManager.setCompositeShader(instance)
                        → BossOutlineManager.java:85 → {}  // 空函数
```

`soul_glow_composite.fsh`（含 inner glow + edge glow 双效）被注册到空函数，从不在 composite 中生效。`BossOutlineManager.composite()` 用 `FrozenOutlineManager.goldOutlineShader` 垫底。

#### 2. 着色器 uniform 帧间泄漏

两管线共用同一 `goldOutlineShader` 实例。`FrozenOutlineManager.compositeIfNeeded()` 不设 `BossGlowStrength`，若上一帧 BOSS 复合留下了 >0 的值，冻结描边会错误渲染 BOSS 彩色而非冰蓝色。

#### 3. 死文件残留

| 文件 | 注册状态 | 处理方式 |
|------|---------|---------|
| `mixin/client/BossBufferSourceMixin` | 不在 mixins.json | 删除 |
| `mixin/client/SoulGlowBufferSourceMixin` | 不在 mixins.json | 删除 |
| `mixin/client/LivingEntitySoulGlowMixin` | 不在 mixins.json | 删除 |
| `ability/client/SoulGlowFboRenderer` | 无任何调用方 | 删除 |
| `ability/client/SoulGlowMaskRenderTypes` | 仅被死 mixin 引用 | 删除 |
| `client/outline/SoulGlowMaskRenderTypes` | 0 引用 | 删除 |
| `client/outline/SoulOutlineRenderer` | 空壳 | 删除 |
| `client/outline/SoulOutlineShaderRegistry` | `setCompositeShader` 无调用方 | 部分保留：`setShader` 被注册，保留引用 |
| `FrozenOutlineManager.setBossColors()` | 仅被死 mixin 调用 | 删除 |
| `FrozenOutlineManager.markPureFrozen()` | 0 调用方 | 删除 |

#### 4. 命名冲突

存在两个 `SoulGlowMaskRenderTypes` 类：
- `ability/client/SoulGlowMaskRenderTypes.java`
- `client/outline/SoulGlowMaskRenderTypes.java`

两者同未命名，不同包，均可删除。

### 确认正确的路由

- `BufferSourceGetBufferMixin.java:35` ✅ 正确使用 `BossMaskRenderTypes`
- `IrisBufferSourceGetBufferMixin.java:35` ✅ 正确使用 `BossMaskRenderTypes`

## 重构步骤

### Step 1 — 删除死 mixin 文件

删除三个未注册的 mixin 文件：

```
rm mixin/client/BossBufferSourceMixin.java
rm mixin/client/SoulGlowBufferSourceMixin.java
rm mixin/client/LivingEntitySoulGlowMixin.java
```

**依据**：grep 确认 0 条 JSON 引用，仅供阅读的尸文件。

---

### Step 2 — 删除死 RenderType / FBO 文件

删除对应死管线的全部文件：

```
rm ability/client/SoulGlowFboRenderer.java
rm ability/client/SoulGlowMaskRenderTypes.java
rm client/outline/SoulGlowMaskRenderTypes.java
rm client/outline/SoulOutlineRenderer.java
rm client/outline/SoulOutlineShaderRegistry.java
```

**检查**：`SoulOutlineShaderRegistry.setShader()` 被 `LenSoulsClient.java:135` 调用，但方法体为空——删除后需同时清理该注册调用。

---

### Step 3 — 清理 FrozenOutlineManager 尸字段

删除以下字段和对应方法：

```java
// 删除 ↓
private static BossOutlineColors bossColors = null;
private static boolean hasPureFrozen = false;

public static void setBossColors(BossOutlineColors colors) { bossColors = colors; }
public static void markPureFrozen() { hasPureFrozen = true; }

// resetFrame() 中删除这两行
bossColors = null;
hasPureFrozen = false;

// compositeIfNeeded() 中删除
bossColors = null;
hasPureFrozen = false;
```

---

### Step 4 — 修复 BossOutlineManager 着色器断路

**4a** 添加 shader 字段并修复 `setCompositeShader`：

```java
private static ShaderInstance bossCompositeShader;

public static void setCompositeShader(ShaderInstance shader) {
    bossCompositeShader = shader;  // 原为空函数！
}
```

**4b** `composite()` 改用自有 shader：

```java
// 替换 ↓
var shader = FrozenOutlineManager.goldOutlineShader;
// 为 ↓
var shader = bossCompositeShader;
```

**4c** 补 uniform 映射（`soul_glow_composite.fsh` 使用 `Color1-4`/`GlowStrength`/`OutlineWidth`/`GameTime`，与 `gold_outline.fsh` 的 `BossColor1-4`/`BossGlowStrength` 不同）：

```java
if (shader.getUniform("Color1") != null)
    shader.getUniform("Color1").set(c1[0], c1[1], c1[2], 1f);
// ... 以此类推
if (shader.getUniform("GameTime") != null) shader.getUniform("GameTime").set(...);
```

**4d** 在 `composite()` 中对 `FrozenOutlineManager.goldOutlineShader` 显式归零 `BossGlowStrength` 以防泄漏：

```java
// 在 BOSS composite 结束时（或在 frozen composite 开始时皆可）：
if (FrozenOutlineManager.goldOutlineShader != null
    && FrozenOutlineManager.goldOutlineShader.getUniform("BossGlowStrength") != null) {
    FrozenOutlineManager.goldOutlineShader.getUniform("BossGlowStrength").set(0f);
}
```

建议放在 `FrozenOutlineManager.compositeIfNeeded()` 的开头，以保证每个 frozen 复合帧都从干净状态开始。

---

### Step 5 — 注册 BossOutlineManager 到 EVENT_BUS

在 `LenSoulsClient.java` 构建器中添加：

```java
NeoForge.EVENT_BUS.register(BossOutlineManager.class);
```

并给 `BossOutlineManager` 添加 `@SubscribeEvent onRenderLevelStage(AFTER_SKY)` 来清空 BOSS mask FBO（类似 `FrozenOutlineManager.onRenderLevelStage` 的 AFTER_SKY 逻辑）。

**所需新增方法：**

```java
@SubscribeEvent
public static void onRenderLevelStage(RenderLevelStageEvent event) {
    if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
        clearAndBind();  // 复用已有方法
    }
}
```

---

### Step 6 — composite 入口加 GUI/Screen 守卫

在所有 composite 入口加 `hideGui` + `screen` 检查。

**入口 1** — `FrozenOutlineManager.compositeIfNeeded()`：

```java
if (mc.options.hideGui().get() || mc.screen != null) return;
```

**入口 2** — `BossOutlineManager.composite()`：

```java
if (mc.options.hideGui().get() || mc.screen != null) return;
```

**依据**：AdorableArmory 模式，GUI 打开或界面隐藏时不应渲染后处理描边。

---

### Step 7 — 删除 FrozenEntityRenderMixin 的 GoldGlint 闪烁层

删除 `FrozenEntityRenderMixin.java:51`：

```java
// 删除 ↓
VertexConsumer consumer = bufferSource.getBuffer(GoldGlintRenderTypes.bodyGlint());
model.renderToBuffer(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY);
```

保留 `StunGlintRenderTypes.bodyGlint()` 调用（行 56-58）。

**理由**：冻结实体已有 FBO Sobel 边缘描边作为主要视觉效果，RenderLayer 表面闪烁层冗余且可能与 frozen mask 竞争渲染顺序。`GoldGlintRenderTypes` 类和 `GoldenPlayerGlintLayer` 仍保留（调试 K 键）。

---

### Step 8 — 修正 frozen composite uniform 泄漏

在 `FrozenOutlineManager.compositeIfNeeded()` 开头添加显式归零：

```java
// 防止上一帧 BOSS composite 留下的 BossGlowStrength 泄漏
if (goldOutlineShader != null && goldOutlineShader.getUniform("BossGlowStrength") != null) {
    goldOutlineShader.getUniform("BossGlowStrength").set(0f);
}
```

---

### Step 9 — 验证第一人称物品发光

`ItemInHandRendererMixin` 已在 `renderHandsWithItems` HEAD/RETURN 中正确捕获 BOSS mask 到 `BossOutlineManager` 的 FBO。组合管线：

```
第一人称渲染流程：
  renderHandsWithItems HEAD → 捕获 BOSS 物品到 mask FBO
  renderHandsWithItems RETURN → flushMask, endCapture
  renderItemInHand RETURN → FrozenOutlineManager.compositeIfNeeded()（冰蓝）
  GameRenderer.render RETURN → BossOutlineManager.composite()（BOSS 彩色）+ Frozen（已消费）
```

Step 4 修复后，`BossOutlineManager.composite()` 使用 `soul_glow_composite.fsh` 渲染 BOSS 彩色描边，第一人称物品发光自然恢复。

无需额外改动。如需验证，检查 `soul_glow_composite.fsh` 的 `GameTime` uniform 是否映射到 `level.getGameTime()`。

---

### Step 10 — 命名清理

| 原类名 | 实际效果 | 建议更名 |
|--------|---------|---------|
| `GoldGlintRenderTypes` | 冰蓝 glint（冻结）+ 金色 glint（调试） | 保持原名（金色调试层用，冻结用的 bodyGlint 方法即将删除） |
| `rendertype_gold_outline.fsh` | 输出冰蓝边缘描边 + BOSS 彩色描边 | 保持原名（两管线复用，代码注释注明即可） |

**命名改动建议暂缓**，待 Step 7 删除 bodyGlint 调用后再评估。

---

## 文件变更清单

### 删除（8 文件）

| 路径 | 原因 |
|------|------|
| `mixin/client/BossBufferSourceMixin.java` | 未注册 + 内含 bug |
| `mixin/client/SoulGlowBufferSourceMixin.java` | 未注册 |
| `mixin/client/LivingEntitySoulGlowMixin.java` | 未注册 |
| `ability/client/SoulGlowFboRenderer.java` | 死管线 |
| `ability/client/SoulGlowMaskRenderTypes.java` | 仅被死 mixin 引用 |
| `client/outline/SoulGlowMaskRenderTypes.java` | 0 引用 |
| `client/outline/SoulOutlineRenderer.java` | 空壳 |
| `client/outline/SoulOutlineShaderRegistry.java` | 可删除（`setShader` 空函数，需清理调用方） |

### 修改（7 文件）

| 文件 | 改动 |
|------|------|
| `FrozenOutlineManager.java` | 删除 `bossColors`/`hasPureFrozen`/`setBossColors`/`markPureFrozen`；`compositeIfNeeded()` 开头加 `BossGlowStrength` 归零 |
| `BossOutlineManager.java` | 添加 `bossCompositeShader` 字段；修正 `setCompositeShader`；`composite()` 改用自有 shader + 补 `soul_glow_composite.fsh` uniform 映射；添加 `@SubscribeEvent onRenderLevelStage(AFTER_SKY)` |
| `LenSonsClient.java` | 删除 `SoulOutlineShaderRegistry.setShader()` 调用；添加 `NeoForge.EVENT_BUS.register(BossOutlineManager.class)` |
| `GameRendererFrameEndMixin.java` | 在两个 composite 调用前加 `hideGui` + `screen` 守卫 |
| `FrozenEntityRenderMixin.java` | 删除 `GoldGlintRenderTypes.bodyGlint()` 调用 |
| `CompositeRenderTypes.java` | 考虑添加 `ShaderStateShard` 接受外部 shader 实例（可选） |
| `lensouls.mixins.json` | 无需修改（删除的文件本就不在 JSON 中） |

## 验证方法

1. **运行客户端**：`./gradlew runClient`
2. **BOSS 描边**：激活任一 BOSS 镜魂 → 玩家自身应有彩色边缘描边（穿墙可接受），手持物品在第一/三人称均有发光
3. **冻结描边**：时间定格怪物 → 冰蓝色边缘描边，无金色闪烁层
4. **GUI**：打开物品栏/暂停菜单 → 描边消失
5. **K 键**：切换 `GoldenPlayerGlintLayer` 可见性（独立于 FBO 描边，不应相互影响）
6. **Iris 兼容**：装入光影包 → 描边应在 AFTER_LEVEL 阶段正确渲染，不导致画面空白或着色器异常
