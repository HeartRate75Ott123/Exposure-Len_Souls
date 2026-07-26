# 镜魂物品发光描边 — FBO 后处理管线

## 目标

玩家激活 BOSS 镜魂后获得 `ElementInfusionEffect`，手持物品应出现 BOSS 配色的彩色发光描边。

## 架构总览

```
GameRenderer.render() HEAD
  ├→ beginFrame()        清空 hasGlowContent
  │
  ├→ ItemRenderer.render() (物品渲染)
  │   └→ captureGlowMask() @Return
  │       ├→ setGlowFor()  标记颜色+hasGlowContent=true
  │       ├→ bindAndClear() 绑定 FBO、清空
  │       ├→ renderModelLists() 记录顶点到 BufferBuilder
  │       ├→ drawBuiltBuffer()  → maskShader 画到 FBO
  │       └→ restoreMain()
  │
  ├→ ... 世界渲染、GUI 渲染 ...
  │
  └→ GameRenderer.render() RETURN
      └→ composite()      读取 FBO → 全屏 quad → 主缓冲
```

## 关键文件

| 文件 | 角色 |
|------|------|
| `ability/client/SoulGlowFboRenderer.java` | FBO 管理、mask 着色器绘制、帧末合成 |
| `mixin/client/ItemRendererMixin.java` | @Return 拦截物品渲染，构建 mask |
| `mixin/client/GameRendererFrameEndMixin.java` | @HEAD beginFrame / @Return composite |
| `client/outline/BossOutlineColors.java` | BOSS 配色枚举 + fromEntity() 匹配 |
| `shaders/core/rendertype_soul_glow_mask.*` | mask 着色器（输出纯白） |
| `shaders/core/soul_glow_composite.*` | composite 着色器（边缘检测+渐变） |

## 已修复的 Bug（仍无效果）

1. **`hasGlowContent` 始终 false** → `setGlowFor()` 末尾加 `hasGlowContent = true`
2. **`bindAndClear()` 清不掉 FBO** → `RenderSystem.clear(0, ...)` → 改为 `glowTarget.setClearColor(0,0,0,0); glowTarget.clear(Minecraft.ON_OSX)`
3. **纹理绑定方式错误** → `glowTarget.bindRead()` → 改为 `compositeShader.setSampler("DiffuseSampler", glowTarget.getColorTextureId())`

## 通过日志确认的事实

- `BossOutlineColors.fromEntity()` 正常返回颜色值（`glowStrength=1.0, outlineWidth=2.8`）
- Mixin 每帧触发数百次（`@At("RETURN")` 对所有 `ItemRenderer.render` 调用触发）
- **`composite()` 入口是否执行尚未确认**（日志 `[composite] 进入合成` 在 20260721 凌晨版本刚加入，待下次 runClient 验证）

## 推测的残余问题（按概率排序）

### 1. composite() 根本未执行
入口条件 `hasGlowContent && compositeShader != null && glowTarget != null`。如果 `beginFrame()` 在 `composite()` 之后执行（顺序颠倒），`hasGlowContent` 可能在 composite 之前被清空。

**验证**：看 `[composite] 进入合成` 日志有无输出。

### 2. mask 被渲染到 FBO 的错误位置
`captureGlowMask` 中 `renderModelLists()` 用自己构建的 `poseStack` 记录顶点变换。但 `drawBuiltBuffer()` 渲染 mask 时，`RenderSystem` 中的 `ModelViewMat`/`ProjMat` 可能因调用时机（`@Return`）不再是物品渲染时的矩阵。

**检查方法**：在 FBO 中直接写入亮色而不是 mask 的四芒星，看 composite 输出位置是否正确。

### 3. composite 结果被后续渲染覆盖
Minecraft 的渲染流程：物品 → 世界 → GUI → HUD。即使 `composite()` 正确输出到主缓冲，后续 GUI/HUD 渲染可能覆盖了 glow 像素。

**缓解方案**：将 composite 移到最后一步（所有渲染完成后，在 `Minecraft.window` 交换缓冲前执行）。

### 4. 着色器 uniform 未被上传到 GPU
1.21.1 `ShaderInstance.apply()` 后可能只上传标准 uniform，自定义 uniform（`OutlineColor`、`GameTime` 等）未调用 `uniform.upload()` 发送到 GPU。

**修复方法**：设置自定义 uniform 后显式调用 `uniform.upload()` 或 `shader.apply()` 重新应用。

### 5. 纹理单元冲突
`setSampler()` 将 `DiffuseSampler` 绑定到某个纹理单元，但 `BufferUploader.drawWithShader()` 内部可能重置了纹理绑定。

**缓解方案**：将 `setSampler` 调用移动到 `tess.begin()` 之前、紧挨着最终 draw 调用。

## 参考：FrozenOutlineManager 已验证的合成模式

`FrozenOutlineManager.compositeIfNeeded()` 已被证实可用（定身法描边），其关键差异：

```java
// FrozenOutlineManager（可用）
main.bindWrite(true);
// ... blend state ...
RenderSystem.setShader(() -> shader);
shader.setSampler("DiffuseSampler", maskTarget.getColorTextureId());  // setSampler 在 setShader 之后
// ... 其他 uniform ...
tess.begin(QUADS, POSITION_TEX);
// ...
BufferUploader.drawWithShader(mesh);
```

当前 `SoulGlowFboRenderer.composite()` 已经改为与此一致。

## 快速诊断清单

- [ ] `[composite] 进入合成` 日志是否输出
- [ ] FBO 的 color texture 是否有内容（可以在 composite 中读回 pixel 验证）
- [ ] mask 着色器是否产出有效 mask（临时改为纯红可视觉验证）
- [ ] composite 全屏 quad 是否正确（临时改为纯绿可视觉验证）

## 绕行方案

如果 FBO 方案持续无效且原因难以定位，建议切换到已搁置的 `RenderLayer` 方案：

- `SoulGlowLayer` — 使用 `rendertype_entity_glint` 的 RenderType
- `SoulGlowRenderTypes` — 已定义但无效果（搁置原因是缺少正确的纹理状态配置）
- 或使用 `shouldEntityAppearGlowing()` + OutlineBuffer 改色（方案 X）
