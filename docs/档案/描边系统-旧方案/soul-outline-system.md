# 镜魂描边系统架构

## 概述

基于平行 FBO + RenderType composite 的实体/物品发光描边方案，使用 Sobel 边缘检测 + cos² 四色渐变流动着色器输出描边效果。兼容 Iris、Player Animator、Better Combat。

## 架构总览

```
GameRenderer.render()
  ├─ render() HEAD → beginFrame() 清空 currentColors
  ├─ AFTER_SKY → 清空 mask FBO + 清空帧级去重集合 capturedThisFrame
  ├─ 实体渲染（第三人称）→ 捕获到 mask FBO
  │   └─ BossEntityRenderMixin @Inject HEAD/RETURN of LivingEntityRenderer.render()
  ├─ 手部渲染（第一人称）→ 捕获到 mask FBO
  │   └─ ItemInHandRendererMixin @Inject HEAD/RETURN of renderHandsWithItems()
  ├─ GameRenderer.render() RETURN → composite()
  │   └─ GameRendererFrameEndMixin @Inject RETURN
  └─ composite 着色器（boss_outline_composite / rendertype_gold_outline）
      └─ Sobel 边缘检测 + cos² 四色混合 → 全屏四边形渲染到主帧缓冲
```

## 关键组件

### 1. 平行 Mask FBO

两套独立的 mask FBO 系统，共享同一个 goldOutlineShader 着色器做 composite：

| 系统 | Mask FBO 管理器 | 用途 |
|------|----------------|------|
| BOSS 镜魂 | `BossOutlineManager` | 镜魂效果激活时的彩色渐变描边 |
| 定身（Frozen） | `FrozenOutlineManager` | 时间定格时的冰蓝单色描边 |

每帧 BEFORE `AFTER_SKY` 的事件中，shadow pass 会先往里写；`AFTER_SKY` 时清空 mask + 清空去重集合，丢弃 shadow pass 的写入结果；之后主 pass 重新写入，保留到帧末 composite。

### 2. Mask RenderType 双路径

`BufferSourceGetBufferMixin` 拦截 `MultiBufferSource$BufferSource.getBuffer()`，对 `NEW_ENTITY` 格式的 RenderType 做包裹。

```java
// BufferSourceGetBufferMixin 核心逻辑
@Inject(method = "getBuffer", at = @At("RETURN"), cancellable = true)
void lensouls$wrapGetBuffer(RenderType renderType, CallbackInfoReturnable<VertexConsumer> ci) {
    if (renderType.format() != DefaultVertexFormat.NEW_ENTITY) return;

    // BOSS 镜魂捕获
    if (isCapturing()) {
        boolean useItemMask = ItemRenderTracker.isRenderingItem();
        RenderType maskType = useItemMask ? MASK_TYPE_ITEM : MASK_TYPE;
        // 包裹：mask VertexConsumer + 原始 VertexConsumer
    }

    // 冻结描边捕获（独立路径）
    if (CaptureState.getCaptureEntityId() >= 0) {
        boolean useItemMask = ItemRenderTracker.isRenderingItem();
        RenderType maskType = useItemMask ? MASK_TYPE_ITEM : MASK_TYPE;
        // 包裹
    }
}
```

路由决策——`ItemRenderTracker.isRenderingItem()`：

| isRenderingItem() | Mask RenderType | 着色器 | alpha 处理 |
|---|---|---|---|
| `true` — 正在渲染物品 | **`MASK_TYPE_ITEM`** | `rendertype_item_outline_mask` | `texelFetch` 整数采样 + `alpha ≤ 0.01 → discard` |
| `false` — 实体身体/手臂 | **`MASK_TYPE`** | `rendertype_mask_entity` | 无纹理，直接 `vec4(1.0)` |

### 3. MASK_TYPE_ITEM alpha test 着色器

```glsl
// rendertype_item_outline_mask.fsh
uniform sampler2D Sampler0;
in vec2 texCoord0;

void main() {
    // 整数纹素采样：同一纹素的 alpha 值帧间不变，消除亚像素位移导致的 alpha 跳变
    ivec2 texSize = textureSize(Sampler0, 0);
    ivec2 texel = ivec2(texCoord0 * vec2(texSize));
    float alphaMask = texelFetch(Sampler0, texel, 0).a;
    if (alphaMask <= 0.01) {
        discard;
    }
    // 纯白输出，不依赖 ColorModulator
    fragColor = vec4(1.0);
}
```

关键设计：
- **`texelFetch` 替代 `texture`**：整数纹素坐标 → 同一纹素帧间 alpha 值不变 → 消除亚像素位移导致的闪烁
- **绑定块图集 `InventoryMenu.BLOCK_ATLAS`**：物品模型的 UV 坐标映射到方块图集，alpha 测试正常生效
- **alpha > 0.01 才写入 mask**：透明区域被 `discard`，mask 中只有不透明像素 → 边缘检测不产生方框噪点

### 4. 帧级去重机制

```java
// BossOutlineManager
private static final IntOpenHashSet capturedThisFrame = new IntOpenHashSet();

public static boolean tryStartCapture(int entityId) {
    if (!capturedThisFrame.add(entityId)) return false;  // 已捕获过 → 跳过
    captureEntityId.set(entityId);
    return true;
}
```

防止同一帧中实体渲染和手部渲染重复捕获同一个玩家。

PA 存在时：`renderHandsWithItems()` 被 PA 取消 → `ItemInHandRendererMixin` 不触发 → `tryStartCapture` 由 `BossEntityRenderMixin`（实体渲染路径）调用，PA 渲染的手持物品通过实体管线进入 mask。

无 PA 时：手部渲染先捕获 → 实体渲染的 `tryStartCapture` 返回 false → 不重复。

### 5. composite 着色器（rendertype_gold_outline.fsh）

在帧末以全屏四边形绘制，对 mask FBO 做 Sobel 边缘检测 + 着色：

```glsl
uniform sampler2D DiffuseSampler;  // mask FBO 颜色纹理
uniform vec4 BossColor1~4;         // 四色渐变
uniform float BossGlowStrength;    // 辉光强度
uniform float BossOutlineWidth;    // 描边宽度
uniform vec2 ScreenSize;
uniform float Time;

void main() {
    // Sobel 3×3 边缘检测
    vec2 texelSize = 1.0 / ScreenSize;
    // ... 9 纹素采样 → Gx, Gy → edge = length(vec2(Gx, Gy))
    if (edge < 0.04) discard;

    // BOSS 模式：cos² 四色渐变加权混合，Time 驱动流动
    float flow = texCoord.x * 3.2 + texCoord.y * 1.1 + Time * 1.2;
    // cos² 权重 → 四色混合输出

    // 冰蓝模式（BossGlowStrength = 0）：sin 相位双色混合
}
```

### 6. 第一人称空手跳过（防乱线）

最近的修改，解决空手手臂被 mask 捕获的问题。

原始问题：`renderHandsWithItems` 中空手的手臂模型（通过 `renderPlayerArm` 渲染）被 `MASK_TYPE_ITEM` 捕获——空手臂的 UV 映射到块图集 alpha test 产生随机通过/丢弃，composite 后呈现乱线。

修复：在 `renderArmWithItem` HEAD 检查 `stack.isEmpty()`，空手时临时 `endCapture()`，跳过该手的手臂 mask 写入，渲染完毕后 `startCapture()` 恢复。

```java
// ItemInHandRendererMixin
@Inject(method = "renderArmWithItem", at = @At("HEAD"))
void lensouls$beforeArmWithItem(..., ItemStack stack, ...) {
    if (stack.isEmpty() && isCapturing()) {
        endCapture();
        needResume = true;
    }
}

@Inject(method = "renderArmWithItem", at = @At("RETURN"))
void lensouls$afterArmWithItem() {
    if (needResume) {
        startCapture(playerId);
        needResume = false;
    }
}
```

`beginItemRender()` / `endItemRender()` 仍在 `renderHandsWithItems` HEAD/RETURN，全程 `MASK_TYPE_ITEM` 不变——非空手的手臂和物品都经过 alpha test 合理抠图。

### 7. RenderType 定义

BossMaskRenderTypes（BOSS 专用） vs MaskRenderTypes（冻结专用），核心区别：

| | BOSS MASK_TYPE | Frozen MASK_TYPE |
|---|---|---|
| depth test | `LESS`（GL_LESS, 513，反射创建） | `LEQUAL` |
| OutputStateShard | `BossOutlineManager::bindMaskTarget` | `FrozenOutlineManager::bindMaskTarget` |

`LESS` 深度测试：只通过 z 值严格小于已有深度值的片段，解决薄模型正反面 Z-fighting。

物品 mask 额外绑定了 `TextureStateShard(InventoryMenu.BLOCK_ATLAS)`，用于 alpha test 纹理采样。

### 8. Iris / PA / BC 兼容

| 模组 | 冲突点 | 处理方式 |
|------|--------|---------|
| **Iris** | ① `@Render` 冲突 ② shaderpack 覆盖着色器 ③ shadow pass 写入 mask | ① 全部用 `@Inject`，不用 `@Redirect`；② 通过 `RenderType` 管线走，Iris 识别并分阶段注入正确着色器；③ `AFTER_SKY` 清空 mask + 去重集合，丢弃 shadow pass 结果 |
| **Player Animator** | 取消 `renderHandsWithItems()`，手部走实体渲染管线 | `tryStartCapture` 帧级去重：PA 通过实体路径先捕获 → 手部 `tryStartCapture` 返回 false；无 PA 时手部先捕获 → 实体返回 false |
| **Better Combat** | 修改攻击距离属性 | 不涉及渲染管线，`getBuffer` 层面拦截不受影响 |

## 文件清单

| 文件 | 职责 |
|------|------|
| `ability/client/BossOutlineManager.java` | BOSS mask FBO 管理 + composite 驱动 + 帧级去重 |
| `ability/client/FrozenOutlineManager.java` | 冻结 mask FBO 管理 + 共享 goldOutlineShader |
| `ability/client/BossMaskRenderTypes.java` | BOSS mask RenderType 工厂（LESS depth test） |
| `ability/client/MaskRenderTypes.java` | 冻结 mask RenderType 工厂 |
| `ability/client/ItemRenderTracker.java` | 物品渲染追踪 ThreadLocal 标记 |
| `ability/client/BossSoulItemState.java` | BOSS 物品发光状态管理 |
| `mixin/client/BufferSourceGetBufferMixin.java` | 核心：`getBuffer` 拦截 + 路由 MASK_TYPE vs MASK_TYPE_ITEM |
| `mixin/client/BossEntityRenderMixin.java` | 第三人称实体 mask 捕获 |
| `mixin/client/ItemInHandRendererMixin.java` | 第一人称手部 mask 捕获（含空手跳过） |
| `mixin/client/GameRendererFrameEndMixin.java` | 帧末触发 composite |
| `mixin/client/ItemRendererMixin.java` | 物品 glow RenderType 包裹 |
| `mixin/client/ItemInHandLayerMixin.java` | 第三人称物品层 ItemRenderTracker 标记 |
| `shaders/core/rendertype_mask_entity.*` | 实体 mask 着色器（纯白输出） |
| `shaders/core/rendertype_item_outline_mask.*` | 物品 mask 着色器（texelFetch alpha test） |
| `shaders/core/rendertype_gold_outline.*` | composite 着色器（Sobel + cos²） |
| `shaders/core/boss_outline_composite.*` | BOSS composite 别名（POSITION_TEX 格式） |
| `client/outline/BossOutlineColors.java` | 六 BOSS 配色方案 |
