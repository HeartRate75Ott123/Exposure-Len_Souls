# 定身法橙黄色渐变流动描边系统

## 概述

本系统实现黑神话悟空定身法风格的效果：被冻结的实体周围渲染**橙黄色渐变流动描边 + 辉光发光**。

## 渲染管线

```
AFTER_SKY (清空蒙版 FBO)
  ↓
LivingEntityRenderer.render() HEAD
  └─ FrozenEntityRenderMixin @Inject HEAD
       └─ ClientFreezeCache.isFrozen(entityId)?
           → CaptureState.startCapture(entityId)
  ↓
实体渲染过程中的所有 bufferSource.getBuffer() 调用
  └─ BufferSourceGetBufferMixin @Inject RETURN (cancellable)
       ├─ 身体/盔甲 → 写入 MASK_TYPE（纯白蒙版，无纹理）
       ├─ 手持物品  → 写入 MASK_TYPE_ITEM（纹理采样 + alpha 测试）
       └─ VertexMultiConsumer.create(mask, main) → 同时写入主 FBO + 蒙版 FBO
  ↓
LivingEntityRenderer.render() RETURN
  └─ CaptureState.flushMask() → 蒙版缓冲刷新到 GPU
  ↓
AFTER_LEVEL (合成)
  └─ FrozenOutlineManager.onAfterLevel()
       └─ 自定义着色器 rendertype_gold_outline
            ├─ Sobel 3×3 边缘检测（2px 间距，自然加粗）
            ├─ 内部辉光分层（核心线 + 外发光 + 高光闪烁）
            ├─ 橙黄渐变流动（切线方向，Time uniform 驱动动画）
            └─ 最终合成到主帧缓冲
```

## 双蒙版策略

| 部位 | 蒙版类型 | 纹理 | alpha 测试 | 解决的问题 |
|------|---------|------|-----------|-----------|
| 身体 + 盔甲 | `MASK_TYPE`（纯白） | ❌ 无 | ❌ 无 | 实体整体轮廓 |
| 手持物品 | `MASK_TYPE_ITEM`（纹理采样） | ✅ BLOCK_ATLAS | ✅ 丢弃 alpha<0.01 | 透明纹理方框 |

**为什么需要双蒙版**：物品的透明纹理区域（如剑柄、工具镂空）在纯白蒙版中被涂为实心，边缘检测产生方框。带纹理采样的蒙版通过 alpha 测试丢弃这些透明片元，只保留可见像素的轮廓。

**关键代码**：`MaskRenderTypes.java` 中 `MASK_TYPE` 和 `MASK_TYPE_ITEM` 两个 RenderType。

## 触发方式

时间定格触发后自动生效：

```
按下快门（TimeStop 能力已激活）
  → TimeFreezeHandler（反射监听 Exposure FrameAddedEvent）
  → FreezeTracker.freeze(player, targets, 100ticks)
  → 发送 FreezeSyncPacket(true, ids) S2C
  → ClientFreezeCache.freezeAll(ids)
  → isFrozen(frozenEntity.id) 返回 true
  → 描边渲染自动触发 ✓
```

解冻时流程反向：`FreezeTracker` 到期 → `FreezeSyncPacket(false, ids)` S2C → `ClientFreezeCache.unfreezeAll(ids)` → 描边消失。渲染管线自动响应 `ClientFreezeCache` 的状态变化。

## 着色器

文件：`assets/lensouls/shaders/core/rendertype_gold_outline.*`

| 参数 | 值 | 说明 |
|------|-----|------|
| 边缘检测 | Sobel 3×3，2px 间距 | 单 Pass 自然加粗，无双线 |
| 颜色 | #ffb527(1,0.71,0.15) → #ffec81(1,0.925,0.506) | 纯橙黄渐变，无白色混入 |
| 动画 | `Time = gameTime * 0.05`（秒） | 自定义 uniform，避免 apply() 覆盖 |
| 流动方向 | 沿边缘切线 | `tangent = (-gy, gx) / |g|` |
| 辉光 | core×0.5 + glow×0.3 + spark | 三层合成，控制亮度 0.5~1.2 |
| 闪烁 | `sin(Time*3 + uv*seed)` | 沿边缘随机高光 |
| 阈值 | edge < 0.04 discard | 丢弃弱边缘 |

## 关键文件

| 文件 | 作用 |
|------|------|
| `mixin/client/FrozenEntityRenderMixin.java` | HEAD/RETURN 注入 LivingEntityRenderer.render() |
| `mixin/client/BufferSourceGetBufferMixin.java` | @Inject RETURN 拦截 BufferSource.getBuffer() |
| `mixin/client/ItemInHandLayerMixin.java` | 标记手持物品渲染状态 |
| `ability/client/CaptureState.java` | 捕获状态管理（ThreadLocal） |
| `ability/client/ClientFreezeCache.java` | 客户端冻结 ID 缓存 + 测试模式 |
| `ability/client/MaskRenderTypes.java` | 双蒙版 RenderType 工厂 |
| `ability/client/FrozenOutlineManager.java` | 蒙版 FBO 管理 + AFTER_LEVEL 合成 |
| `ability/client/ItemRenderTracker.java` | 手持物品渲染标记 |
| `ability/util/FreezeTracker.java` | 服务端冻结追踪器 |
| `ability/network/FreezeSyncPacket.java` | 冻结状态 S2C 同步 |
| `shaders/core/rendertype_gold_outline.*` | 橙黄渐变流动描接着色器 |
| `shaders/core/rendertype_mask_entity.*` | 纯白蒙版着色器 |
| `shaders/core/rendertype_item_outline_mask.*` | 纹理 alpha 测试蒙版着色器（复用镜魂系统） |

## 已知限制

- 手持物品的蒙版绑定 `InventoryMenu.BLOCK_ATLAS`（方块/物品纹理图集），对于使用非图集纹理的特殊物品，alpha 测试可能不精确
- 第一人称看不到自己的描边（玩家模型不渲染），按 F5 切第三人称
- 每帧蒙版 FBO 清除 + 合成 ≈ 两个全屏四边形绘制，性能开销可忽略
