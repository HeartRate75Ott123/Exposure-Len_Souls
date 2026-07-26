# Minecraft NeoForge 渲染技术体系参考文档

> 基于 7 个模组源码深度研究，覆盖从 CoreShader 到后处理全管线。
> 持续更新，最后更新: 2026-07-19

---

## 目录

1. [CoreShader 系统](#1-coreshader-系统)
2. [RenderType 搭建](#2-rendertype-搭建)
3. [RenderLayer 实现模式](#3-renderlayer-实现模式)
4. [FBO 遮罩 + 后处理管线](#4-fbo-遮罩--后处理管线)
5. [OutputStateShard 分层渲染](#5-outputstateshard-分层渲染)
6. [全局实体效果层](#6-全局实体效果层)
7. [着色器动画模式](#7-着色器动画模式)
8. [常用图案代码](#8-常用图案代码)
9. [项目技术特色索引](#9-项目技术特色索引)

---

## 1. CoreShader 系统

### 1.1 注册流程

所有 CoreShader 通过 `RegisterShadersEvent` 注册（NeoForge mod 事件总线）。

```java
// LenSoulsClient.java 或专用 ShaderRegistry 类
@SubscribeEvent
public static void registerShaders(RegisterShadersEvent event) {
    ResourceProvider provider = event.getResourceProvider();
    
    event.registerShader(
        new ShaderInstance(provider,
            ResourceLocation.fromNamespaceAndPath(MODID, "my_shader"),
            DefaultVertexFormat.NEW_ENTITY),  // ← 必须匹配 .json 中的 attributes
        instance -> {
            MyShaderHolder.shader = instance;
            LOGGER.info("着色器加载成功");
        }
    );
}
```

### 1.2 Shader 文件结构

每个 CoreShader 需要三个文件，放在 `assets/<modid>/shaders/core/` 下：

```
assets/<modid>/shaders/core/
  my_shader.json       # 程序定义（顶点格式、采样器、uniform、混合模式）
  my_shader.fsh        # 片段着色器（GLSL 150 core）
  my_shader.vsh        # 顶点着色器（GLSL 150 core）
```

### 1.3 .json 定义关键要素

```json
{
  "vertex": "rendertype_entity_translucent",  // 复用原版顶点着色器
  "fragment": "mymod:my_shader",             // 自定义片段着色器
  "samplers": [
    { "name": "Sampler0" }                    // 纹理采样器
  ],
  "attributes": [ "Position", "Color", "UV0", "UV1", "UV2", "Normal" ],
  "uniforms": [
    { "name": "ModelViewMat", "type": "matrix4x4", "count": 16, "values": [...] },
    { "name": "ColorModulator", "type": "float", "count": 4, "values": [1,1,1,1] },
    { "name": "GameTime", "type": "float", "count": 1, "values": [0.0] }
  ]
}
```

**关键规则：**
- `attributes` 必须匹配顶点格式（`DefaultVertexFormat.NEW_ENTITY` → `Position,Color,UV0,UV1,UV2,Normal`）
- `uniforms` 必须包含 Minecraft 核心系统要求的 uniform（ModelViewMat, ProjMat, ColorModulator 等），即使着色器没使用也要声明
- `.json` 文件名 = 注册时的路径名（`mymod:my_shader` → `my_shader.json`）

### 1.4 可复用顶点着色器

| 原版顶点着色器 | 对应格式 | 适用场景 |
|---|---|---|
| `rendertype_entity_translucent` | NEW_ENTITY | 实体渲染（标准光照+雾） |
| `rendertype_entity_cutout` | NEW_ENTITY | 实体渲染（无透明混合） |
| `rendertype_entity_translucent_emissive` | NEW_ENTITY | 自发光实体（跳过光图减暗） |
| `position` | POSITION | 纯位置（无纹理无颜色） |
| `position_color` | POSITION_COLOR | 纯几何体（线框、球体） |
| `position_color_tex` | POSITION_COLOR_TEX | 纹理+颜色（发光四边形） |
| `position_tex` | POSITION_TEX | 全屏四边形（后处理） |
| `rendertype_text` | POSITION_COLOR_TEX_LIGHTMAP | 文本渲染 |
| `rendertype_beacon_beam` | POSITION_COLOR_TEX | 信标光束效果 |

**引用方式**：`.json` 中 `"vertex": "rendertype_entity_translucent"`（不指定命名空间时默认 `minecraft:`）。

> **深度参考**：
> - Malum `soulless_creature_outline.vsh` — 自定义顶点着色器传递 Normal/Tangent/Bitangent
> - Alex's Caves `rendertype_bubbled.vsh` — 顶点位移（wobble 气泡效果）
> - ShaderTest `black_hole.vsh` — 规范化位置用于射线求交

---

## 2. RenderType 搭建

### 2.1 标准模式（Alex's Caves 风格）

```java
public class MyRenderTypes {
    
    // 1. 定义 ShaderStateShard
    private static final RenderStateShard.ShaderStateShard MY_SHADER =
        new RenderStateShard.ShaderStateShard(() -> MyShaderHolder.shader);
    
    // 2. 构建完整的 CompositeState
    private static RenderType create() {
        RenderType.CompositeState state = RenderType.CompositeState.builder()
            .setShaderState(MY_SHADER)
            .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
            .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
            .setCullState(RenderStateShard.CULL)
            .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
            .setOutputState(RenderStateShard.MAIN_TARGET)
            .setLightmapState(RenderStateShard.LIGHTMAP)
            .setOverlayState(RenderStateShard.NO_OVERLAY)
            .createCompositeState(true);  // true = outlineable（可被描边）
        return RenderType.create("lensouls_my_type",
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS,
            256, false, true,  // bufferSize, affectsCrumbling, translucent
            state);
    }
    
    // 3. 缓存
    private static RenderType cached;
    public static RenderType get() {
        RenderType rt = cached;
        if (rt == null) { rt = create(); cached = rt; }
        return rt;
    }
}
```

### 2.2 RenderType.create() 构造函数参数

```java
RenderType.create(
    "name",                // 名称（必须唯一）
    VertexFormat format,   // 如 DefaultVertexFormat.NEW_ENTITY
    VertexFormat.Mode mode,// QUADS, TRIANGLES, LINES 等
    int bufferSize,        // 256 通常足够
    boolean affectsCrumbling, // 是否受破坏进度影响
    boolean translucent,   // 是否透明（影响排序）
    CompositeState state   // 上面构建的 state
);
```

### 2.3 常用 StateShard 组合

| 效果 | Transparency | WriteMask | Cull | DepthTest | Output |
|---|---|---|---|---|---|
| 实心不透明 | `NO_TRANSPARENCY` | `COLOR_DEPTH_WRITE` | `CULL` | `LEQUAL` | `MAIN_TARGET` |
| 半透明 | `TRANSLUCENT_TRANSPARENCY` | `COLOR_DEPTH_WRITE` | `CULL` | `LEQUAL` | `MAIN_TARGET` |
| 叠加（发光） | `TRANSLUCENT_TRANSPARENCY` | `COLOR_WRITE` | `NO_CULL` | `LEQUAL` | `MAIN_TARGET` |
| 纯深度写入 | - | `DEPTH_WRITE` | `CULL` | `LEQUAL` | - |
| 全屏后处理 | `NO_TRANSPARENCY` | `COLOR_WRITE` | - | `NO_DEPTH_TEST` | `MAIN_TARGET` |
| 无深度测试发光 | `ADDITIVE_TRANSPARENCY` | `COLOR_WRITE` | `NO_CULL` | `NO_DEPTH_TEST` | `MAIN_TARGET` |

### 2.4 反射补全保护字段

`RenderStateShard` 的某些子类（`LineStateShard`, `LayeringStateShard`）是 `protected` 的，需要通过反射访问：

```java
// LineStateShard（控制 glLineWidth）
Constructor<RenderStateShard.LineStateShard> lineCtor =
    RenderStateShard.LineStateShard.class.getDeclaredConstructor(OptionalDouble.class);
lineCtor.setAccessible(true);
LINE_STATE = lineCtor.newInstance(OptionalDouble.of(2.0));

// VIEW_OFFSET_Z_LAYERING（防深度冲突）
var layeringField = RenderStateShard.class.getDeclaredField("VIEW_OFFSET_Z_LAYERING");
layeringField.setAccessible(true);
VIEW_OFFSET_Z_LAYERING = (RenderStateShard.LayeringStateShard) layeringField.get(null);
```

> **深度参考**：
> - lensouls `WireframeRenderTypes.java` — 空间扭曲球体线框（已验证可用的反射模式）
> - Malum `MalumRenderTypes.java` — 自定义 `SUBTRACTIVE_TEXT_TRANSPARENCY`（GL_FUNC_SUBTRACT）
> - AdorableArmory `AdorableArmoryShaders.java` — 18 个自定义 RenderType（COSMIC/SKY 系列）
> - Alex's Caves `ACRenderTypes.java` — 20+ 个自定义 RenderType，含 OutputStateShard

---

## 3. RenderLayer 实现模式

### 3.1 简单叠加层（CultistEmpowermentLayer 模式）

最可靠的实体效果叠加方式。原理：在实体模型上 inflate 后叠加半透明渲染，inflate 部分超出原始模型轮廓形成可见光晕。

```java
@Override
public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                   Entity entity, float limbSwing, float limbSwingAmount,
                   float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
    if (!shouldRender(entity)) return;
    
    M model = getParentModel();
    float visibility = getVisibility(entity);
    if (visibility <= 0) return;
    
    // ① 动画 UV（滚动纹理）
    float delta = (float) entity.tickCount + partialTicks;
    float u = (delta * 0.02f) % 1.0F;
    float v = (delta * 0.01F) % 1.0F;
    
    // ② 获取 RenderType（energySwirl 自带半透明混合）
    VertexConsumer consumer = buffer.getBuffer(RenderType.energySwirl(texture, u, v));
    
    // ③ inflate 模型
    model.grow(0.25f);  // 或 poseStack.scale(1.03f, 1.03f, 1.03f)
    
    // ④ 渲染叠加层
    model.renderToBuffer(poseStack, consumer, packedLight,
        OverlayTexture.NO_OVERLAY, animatedColor);
    
    // ⑤ 恢复
    model.grow(-0.25f);
}
```

**关键要点：**
- **不要用 GL_FRONT 剔除** — 这会剔除正面只留背面，背面的深度在原模型之后导致 LEQUAL 全部失败
- **不要做 depth pre-pass** — 父渲染器已经渲染了原始模型到深度缓冲
- 使用 `LEQUAL_DEPTH_TEST` — inflate 后的正面部分超出原始模型，位于原始模型深度之前，通过 LEQUAL 测试
- 使用半透明混合 — `TRANSLUCENT_TRANSPARENCY` 或 `energySwirl` 自带的混合

**为什么不直接 scale 不行？** 因为 `poseStack.scale()` 会同时影响位置。Minecraft 的实体已通过 `scale(-1,-1,1)` 翻转 Y 轴，在此基础上再 scale 会产生偏移。正确做法：用 `model.grow()`（直接膨胀顶点）或确保在正确的变换栈中。

### 3.2 自定义着色器叠加层（Ars Nouveau 风格）

按实体名称或条件应用自定义 CoreShader：

```java
// 在渲染器中条件选择 RenderType
@Override
public ResourceLocation getTextureLocation(T entity) {
    return TEXTURE;  // 基础纹理
}

@Override
protected RenderType getRenderType(T entity, boolean bodyVisible,
                                    boolean translucent, boolean glow) {
    String name = entity.getDisplayName().getString();
    if ("Splonk".equals(name)) {
        return ShaderRegistry.blamed(texture, true);
    }
    if ("Bailey".equals(name)) {
        return ShaderRegistry.rainbowEntity(texture, maskTexture, true);
    }
    return super.getRenderType(entity, bodyVisible, translucent, glow);
}
```

### 3.3 全局 Layer 注册

对所有 LivingEntityRenderer 统一添加 RenderLayer：

```java
// 在 EntityRenderersEvent.AddLayers 中遍历所有渲染器
@SubscribeEvent
public static void addLayers(EntityRenderersEvent.AddLayers event) {
    // 遍历所有实体类型
    for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
        if (type.getCategory() == MobCategory.MISC) continue;
        var renderer = event.getRenderer(type);
        if (renderer instanceof LivingEntityRenderer<?, ?> ler) {
            ler.addLayer(new MyGlobalEffectLayer<>(ler));
        }
    }
    // 单独处理玩家
    addLayerForSkin(event, "default", MyGlobalEffectLayer::new);
    addLayerForSkin(event, "slim", MyGlobalEffectLayer::new);
}

private static void addLayerForSkin(EntityRenderersEvent.AddLayers event,
                                     String skin, Function<LivingEntityRenderer, RenderLayer> factory) {
    var player = event.getSkin(skin);
    if (player instanceof LivingEntityRenderer<?, ?> ler) {
        ler.addLayer(factory.apply(ler));
    }
}
```

> **深度参考**：
> - Malum `CultistEmpowermentLayer.java` — 最简单的 inflate+energySwirl 叠加层
> - Alex's Caves `ACPotionEffectLayer.java` — 全局实体效果层 + OutputStateShard
> - Alex's Caves `ClientLayerRegistry.java` — 遍历所有实体类型注册 Layer
> - Ars Nouveau `StarbuncleRenderer.java` — 按名称动态选择着色器

---

## 4. FBO 遮罩 + 后处理管线

### 4.1 两遍架构（ItemGlint / AdorableArmory 模式）

**第一遍 — 遮罩捕获**：将目标渲染到独立的 FBO（白色/有色填充，背景透明黑色）

```
主 FBO → 切换到 maskTarget（带深度）
  → 清除为黑色
  → 渲染目标对象（使用 mask 着色器输出纯色或编码颜色）
  → 切换回主 FBO
```

**第二遍 — 后处理合成**：用全屏四边形对遮罩应用边缘检测着色器

```
主 FBO → 绑定 maskTarget 的颜色纹理到采样器
  → 禁用深度测试
  → 绘制全屏四边形（POSITION_TEX）
  → 着色器对 mask alpha 进行边缘检测 → 输出轮廓效果
  → 启用深度测试
```

### 4.2 FBO 管理

```java
private RenderTarget maskTarget;

private void ensureTarget(RenderTarget mainTarget) {
    if (maskTarget == null) {
        maskTarget = new TextureTarget(
            mainTarget.width, mainTarget.height,
            true,          // useDepth — 深度附件
            Minecraft.ON_OSX
        );
    } else if (maskTarget.width != mainTarget.width
            || maskTarget.height != mainTarget.height) {
        maskTarget.resize(mainTarget.width, mainTarget.height, Minecraft.ON_OSX);
    }
}

private void clearMask() {
    maskTarget.bindWrite(true);
    RenderSystem.clearColor(0, 0, 0, 0);
    RenderSystem.clear(16640, Minecraft.ON_OSX);  // COLOR | DEPTH
}

private void restoreMain() {
    Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
}
```

### 4.3 全屏合成四边形

```java
private static void drawFullscreenQuad(ShaderInstance shader) {
    RenderSystem.setShader(() -> shader);
    var builder = Tesselator.getInstance()
        .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
    builder.addVertex(-1.0f, -1.0f, 0.0f).setUv(0.0f, 0.0f);
    builder.addVertex( 1.0f, -1.0f, 0.0f).setUv(1.0f, 0.0f);
    builder.addVertex( 1.0f,  1.0f, 0.0f).setUv(1.0f, 1.0f);
    builder.addVertex(-1.0f,  1.0f, 0.0f).setUv(0.0f, 1.0f);
    BufferUploader.drawWithShader(builder.build());
}
```

### 4.4 边缘检测着色器模板（搜索式）

```glsl
#version 150

uniform sampler2D DiffuseSampler;    // 遮罩纹理
uniform sampler2D DepthSampler;      // 可选深度纹理
uniform vec2 ScreenSize;
uniform float OutlineWidth;          // 搜索半径（像素）
uniform float AlphaThreshold;        // 通常 0.01~0.1

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 mask = texture(DiffuseSampler, texCoord);
    
    // 如果当前像素本身在遮罩上 → 跳过（不描边）
    if (mask.a > AlphaThreshold) {
        discard;
        return;
    }
    
    // 多方向搜索最近的遮罩像素
    float closestDist = OutlineWidth * OutlineWidth;
    vec3 foundColor = vec3(0.0);
    
    for (int i = 0; i < 16; i++) {
        vec2 offset = precomputedOffsets[i] / ScreenSize;
        vec4 sample = texture(DiffuseSampler, texCoord + offset);
        if (sample.a > AlphaThreshold) {
            float dist = dot(offset * ScreenSize, offset * ScreenSize);
            if (dist < closestDist) {
                closestDist = dist;
                foundColor = sample.rgb;
            }
        }
    }
    
    if (closestDist < OutlineWidth * OutlineWidth) {
        float edge = 1.0 - sqrt(closestDist) / OutlineWidth;
        fragColor = vec4(foundColor, edge * 0.8);
    } else {
        discard;
    }
}
```

### 4.5 可选：深度感知边缘检测

在遮罩 FBO 中捕获深度后，比较当前像素深度与附近像素深度，在深度不连续处增强边缘：

```glsl
// 在与遮罩相同 uv 处采样深度
float centerDepth = texture(DepthSampler, texCoord).r;
float nearDepth = texture(DepthSampler, texCoord + vec2(0, 1.0/ScreenSize.y)).r;
float depthContrast = abs(centerDepth - nearDepth);
// 深度差大 → 边缘
edgeStrength = max(edgeStrength, smoothstep(0.001, 0.01, depthContrast));
```

> **深度参考**：
> - lensouls `SoulOutlineRenderer.java` — 镜魂描边 FBO + 全屏合成（已验证可工作）
> - ItemGlint `HeldItemOutlineRenderer.java` — 完整 FBO 遮罩 + 边缘检测 + 辉光 + 剪刀矩形优化
> - AdorableArmory `ItemOutlinePostProcessor.java` — 1148 行的完整描边引擎（搜索半径 + 深度遮挡 + 批次处理）
> - ShaderTest `ExplosionMagicPostRenderer.java` / `SkyStrikePostRenderer.java` — FBO blit 后处理
> - ShaderTest `BlackHoleWorldRenderer.java` — 场景色彩 + 深度双采样

---

## 5. OutputStateShard 分层渲染

### 5.1 架构原理（Alex's Caves 核心模式）

Minecraft 的 RenderType 管线允许通过 `OutputStateShard` 将几何体重定向到特定的 FBO。结合 `PostEffectRegistry`，可以实现：

1. 用自定义 RenderType 渲染实体 → 写入**效果专属 FBO**
2. 帧末对 FBO 运行后处理链（模糊、发光、扭曲）
3. 将结果混合回主帧缓冲

### 5.2 OutputStateShard 实现

```java
// 持有效果专属 FBO
public class EffectFboManager {
    public static RenderTarget target;
    
    public static RenderStateShard.OutputStateShard createOutput() {
        return new RenderStateShard.OutputStateShard("effect_output", 
            () -> {
                // setup: 绑定 FBO，从主目标拷贝深度
                target.bindWrite(false);
                target.copyDepthFrom(
                    Minecraft.getInstance().getMainRenderTarget());
            },
            () -> {
                // cleanup: 恢复主目标
                Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
            }
        );
    }
}
```

### 5.3 在 RenderType 中使用

```java
public static RenderType getEffectRenderType(ResourceLocation texture) {
    return RenderType.create("lensouls_effect",
        DefaultVertexFormat.NEW_ENTITY,
        VertexFormat.Mode.QUADS, 256, false, true,
        RenderType.CompositeState.builder()
            .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
            .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
            .setOutputState(EffectFboManager.createOutput())  // ← 重定向到 FBO
            .setWriteMaskState(RenderStateShard.COLOR_WRITE)
            .setCullState(RenderStateShard.CULL)
            .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
            .createCompositeState(true));
}
```

### 5.4 后处理链

```json
// post/effect.json — Minecraft PostChain 格式
{
    "targets": [
        "swap",
        "final"
    ],
    "passes": [
        { "name": "blit", "intarget": "final", "outtarget": "swap" },
        { "name": "bloom", "intarget": "swap", "outtarget": "final" },
        { "name": "blit", "intarget": "final", "outtarget": "minecraft:main" }
    ]
}
```

```java
// 在客户端初始化时注册效果
PostEffectRegistry.registerEffect(
    ResourceLocation.fromNamespaceAndPath(MODID, "shaders/post/effect.json"));

// 在 RenderLayer 中激活动画
PostEffectRegistry.renderEffectForNextTick(effectResourceLocation);
```

> **深度参考**：
> - Alex's Caves `ACRenderTypes.java` — `IRRADIATED_OUTPUT`、`HOLOGRAM_OUTPUT`、`PURPLE_WITCH_OUTPUT`
> - Alex's Caves `ACPotionEffectLayer.java` — 先 queue 效果再渲染实体到对应 FBO
> - Alex's Caves `ClientProxy.java` — PostEffectRegistry.registerEffect + post chain JSON
> - lensouls `SoulOutlineRenderer.java` — 等效的 OutputStateShard 模式（手动绑定/恢复 FBO）
> - Malum `ParallelWorldRenderer.java` — 自定义 RenderTarget + OutputStateShard

---

## 6. 全局实体效果层

### 6.1 ACPotionEffectLayer 模式（Alex's Caves）

对所有 `LivingEntityRenderer` 添加一个 Layer，在 render() 中检测实体效果状态：

```java
public class MyGlobalEffectLayer<T extends LivingEntity, M extends EntityModel<T>>
        extends RenderLayer<T, M> {

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer,
                       int packedLight, T entity, ...) {
        
        // 检测效果状态
        var effect = entity.getEffect(ModEffects.FROZEN);
        if (effect == null) return;
        
        int amplifier = effect.getAmplifier();
        
        // ① 激活动画后处理效果（帧末执行）
        PostEffectRegistry.renderEffectForNextTick(FROZEN_OUTLINE_EFFECT);
        
        // ② 将实体渲染到效果 FBO
        M model = getParentModel();
        VertexConsumer vc = buffer.getBuffer(
            MyRenderTypes.getFrozenOutline(texture));
        model.renderToBuffer(poseStack, vc, packedLight,
            OverlayTexture.NO_OVERLAY, animatedColor);
        
        // ③ 另一个 pass（可选，叠加效果层）
        VertexConsumer vc2 = buffer.getBuffer(
            RenderType.entityTranslucent(texture));
        model.renderToBuffer(poseStack, vc2, packedLight,
            OverlayTexture.NO_OVERLAY, -1);
    }
}
```

### 6.2 注册到所有渲染器

```java
@SubscribeEvent
public static void addLayers(EntityRenderersEvent.AddLayers event) {
    for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
        if (type.getCategory() == MobCategory.MISC) continue;
        var renderer = event.getRenderer(type);
        if (renderer instanceof LivingEntityRenderer<?, ?> ler) {
            ler.addLayer(new MyGlobalEffectLayer<>(ler));
        }
    }
    // 玩家皮肤
    for (String skin : new String[]{"default", "slim"}) {
        var player = event.getSkin(skin);
        if (player instanceof LivingEntityRenderer<?, ?> ler) {
            ler.addLayer(new MyGlobalEffectLayer<>(ler));
        }
    }
}
```

### 6.3 注意事项

- 每个 entity 在每一帧的 `render()` 中都会被调用，注意性能
- 仅当效果激活时才做额外渲染，通过 `if (effect == null) return;` 提前退出
- 如果使用 OutputStateShard + PostEffectRegistry，只需 queue 一次（在渲染前调用 `renderEffectForNextTick`）
- 颜色动画可在 Java 侧用 `GameTime` 计算后传入 VertexConsumer

> **深度参考**：
> - Alex's Caves `ACPotionEffectLayer.java` — 四效果合一全局层（irradiated/bubbled/darkness/sugar_rush）
> - Alex's Caves `LicowitchPossessionLayer.java` — 双 pass 渲染（普通 translucent + 紫色女巫输出）
> - Alex's Caves `ClientLayerRegistry.java` — 全局注册代码

---

## 7. 着色器动画模式

### 7.1 GameTime 驱动动画

```glsl
uniform float GameTime;

void main() {
    // 呼吸脉冲
    float breathe = sin(GameTime * 0.15) * 0.5 + 0.5;
    
    // 流动 UV
    vec2 uv = texCoord0 + vec2(GameTime * 0.02, GameTime * 0.01);
    
    // 分块/像素化
    float blockSize = 64.0;
    uv = floor(uv * blockSize) / blockSize;
    
    // 彩虹循环
    float hue = fract(GameTime * 0.05);
    vec3 rainbow = 0.5 + 0.5 * cos(TAU * (hue + vec3(0.0, 0.33, 0.67)));
}
```

### 7.2 Java 侧颜色计算

在 RenderLayer 中用 Java 计算后再传入，比在着色器中计算更灵活：

```java
long gt = entity.level().getGameTime();
float pulse = (float) (Math.sin(gt * 0.3) * 0.5 + 0.5);
float breathe = (float) (Math.sin(gt * 0.15) * 0.2 + 0.8);

int r = (int) (255 * (0.6 + pulse * 0.4));   // 金色主调
int g = (int) (255 * (0.3 + pulse * 0.55));
int b = (int) (255 * pulse * 0.15);
int a = (int) (160 * breathe);

int color = (a << 24) | (r << 16) | (g << 8) | b;
```

### 7.3 顶点位移（Alex's Caves / Ars Nouveau）

```glsl
// 气泡 wobble 效果（Alex's Caves rendertype_bubbled.vsh）
float animation = GameTime * 3000;
float xs = cos(Position.x * 3 + animation + 0.5) * 0.05;
float ys = sin(Position.y * 3 + animation) * 0.05;
float zs = cos(Position.z * 3 + animation - 0.5) * 0.05;
vec3 displaced = Position + vec3(xs, ys, zs);
gl_Position = ProjMat * ModelViewMat * vec4(displaced, 1.0);

// 噪声搔动效果（Ars Nouveau blamed_entity.vsh）
float n = snoise(Position + GameTime * 800);
vec3 displaced = Position + n * 0.1;
gl_Position = ProjMat * ModelViewMat * vec4(displaced, 1.0);
```

### 7.4 UV 滚动 + 纹理动画

```java
// Java 侧（CultistEmpowermentLayer）
float u = (delta * 0.02f) % 1.0F;
float v = (delta * 0.01F) % 1.0F;
VertexConsumer vc = buffer.getBuffer(RenderType.energySwirl(texture, u, v));
```

> **深度参考**：
> - Malum `CultistEmpowermentLayer.java` — UV 滚动纹理叠加
> - Alex's Caves `rendertype_irradiated.fsh` — GameTime 驱动颜色脉冲（绿色辐射）
> - Ars Nouveau `rainbow_entity.fsh` — 彩虹 sin 波动画
> - ShaderTest `explosion_magic_circle.fsh` — 程序化图案 + 展开动画

---

## 8. 常用图案代码

### 8.1 Sobel 边缘检测

```glsl
uniform sampler2D DiffuseSampler;
uniform vec2 ScreenSize;

vec4 sobel() {
    vec2 t = 1.0 / ScreenSize;
    float tl = texture(DiffuseSampler, texCoord + vec2(-t.x,  t.y)).a;
    float t_ = texture(DiffuseSampler, texCoord + vec2( 0.0,  t.y)).a;
    float tr = texture(DiffuseSampler, texCoord + vec2( t.x,  t.y)).a;
    float l  = texture(DiffuseSampler, texCoord + vec2(-t.x,  0.0)).a;
    float r  = texture(DiffuseSampler, texCoord + vec2( t.x,  0.0)).a;
    float bl = texture(DiffuseSampler, texCoord + vec2(-t.x, -t.y)).a;
    float b_ = texture(DiffuseSampler, texCoord + vec2( 0.0, -t.y)).a;
    float br = texture(DiffuseSampler, texCoord + vec2( t.x, -t.y)).a;
    
    float gx = -tl - 2.0*t_ - tr + bl + 2.0*b_ + br;
    float gy = -tl - 2.0*l - bl + tr + 2.0*r + br;
    return vec4(sqrt(gx*gx + gy*gy));
}
```

### 8.2 色差（Chromatic Aberration）

```glsl
vec4 chromaticAberration(sampler2D tex, vec2 uv, float strength) {
    float r = texture(tex, uv + vec2( strength, 0)).r;
    float g = texture(tex, uv).g;
    float b = texture(tex, uv - vec2( strength, 0)).b;
    return vec4(r, g, b, 1.0);
}
```

### 8.3 高斯模糊（分离式）

```glsl
// 水平 pass (BlurDirection = vec2(1,0))
// 垂直 pass (BlurDirection = vec2(0,1))
uniform sampler2D DiffuseSampler;
uniform vec2 BlurDirection;
uniform float BlurRadius;

const float[] weights = float[](
    0.227027, 0.1945946, 0.1216216, 0.0540540, 0.0162162
);
const float[] offsets = float[](0.0, 1.3846, 3.2308, 5.0769, 6.9231);

void main() {
    vec4 color = texture(DiffuseSampler, texCoord) * weights[0];
    for (int i = 1; i < 5; i++) {
        color += texture(DiffuseSampler,
            texCoord + offsets[i] * BlurDirection * BlurRadius / ScreenSize
        ) * weights[i];
        color += texture(DiffuseSampler,
            texCoord - offsets[i] * BlurDirection * BlurRadius / ScreenSize
        ) * weights[i];
    }
    fragColor = vec4(0, 0, 0, color.a);  // 仅模糊 alpha
}
```

### 8.4 辉光（添加散射）

```glsl
uniform sampler2D DiffuseSampler;     // 原始遮罩
uniform sampler2D NearBlurSampler;    // 近模糊 alpha
uniform sampler2D FarBlurSampler;     // 远模糊 alpha

void main() {
    float mask = texture(DiffuseSampler, texCoord).a;
    float nearBlur = texture(NearBlurSampler, texCoord).a;
    float farBlur = texture(FarBlurSampler, texCoord).a;
    
    float outsideMask = 1.0 - smoothstep(0.0, 0.5, mask);
    float nearHalo = max(0.0, nearBlur - mask * 0.985);
    float farHalo = max(0.0, farBlur - mask * 0.985);
    float glow = (nearHalo + farHalo * 0.5) * outsideMask;
    
    fragColor = vec4(glow * glowColor, glow * glowStrength);
}
```

### 8.5 fBM 噪声

```glsl
float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1,0)), f.x),
               mix(hash(i + vec2(0,1)), hash(i + vec2(1,1)), f.x), f.y);
}

float fbm(vec2 p) {
    float value = 0.0;
    float amplitude = 0.5;
    for (int i = 0; i < 5; i++) {
        value += amplitude * noise(p);
        p *= 2.0;
        amplitude *= 0.5;
    }
    return value;
}
```

> **深度参考**：
> - ShaderTest `black_hole.fsh` — 完整 fBM + 吸积盘体渲染
> - Malum `soulless_creature_outline.fsh` — fancySample 噪声 + 天空盒混合
> - Alex's Caves `watcher_blur.fsh` / `hologram.fsh` — 球形模糊 + 全息扭曲
> - AdorableArmory `dimensional_slash_screen_fx.fsh` — 径向模糊 + 色差 + 闪光复合效果

---

## 9. 项目技术特色索引

| 项目 | MC 版本 | 关键技术点 | 对描边的参考价值 |
|---|---|---|---|
| **ItemGlint** | 1.21.1 Fabric | FBO 遮罩 + 深度感知边缘检测 + 辉光模糊 + Iris 三管线兼容 | ★★★★★ FBO 描边全流程参考 |
| **AdorableArmory** | 1.20.1 Forge | 物品描边（搜索式边缘检测 + 深度遮挡）+ 宇宙着色器 + 次元斩玻璃破碎 + 黑洞透镜 | ★★★★★ 最完整的描边引擎实现 |
| **Alex's Caves** | 1.21.1 NeoForge | OutputStateShard + PostEffectRegistry 分层渲染 + 全局实体效果层 | ★★★★★ 实体效果层架构最佳参考 |
| **Malum** | 1.21.1 NeoForge | Lodestone 系统 + CultistEmpowermentLayer + ParallelWorldRenderer FBO | ★★★★ 最简单可用的叠加层模式 |
| **ShaderTest** | 1.20.1 Forge | 手动物体渲染 + FBO blit 后处理 + 程序化着色器（黑洞/爆炸/天降激光） | ★★★★ FBO blit + 自定义着色器最佳范例 |
| **Photon** | 1.21.1 NeoForge | HDR 粒子管线 + 泛光后处理 + GPU 实例化 | ★★★ 后处理管线参考（泛光链） |
| **Ars Nouveau** | 1.21.1 NeoForge | CoreShader + RenderType + 顶点位移 + 彩虹着色器 | ★★ 基础着色器注册与使用参考 |

---

## 附录 A：故障排查清单

当渲染不可见时，按以下顺序排查：

### A.1 编译层
- [ ] 着色器 JSON 中的 `attributes` 是否与 `VertexFormat` 匹配？
- [ ] 着色器 JSON 中的 `uniforms` 是否包含了所有必需的 uniform（ModelViewMat、ProjMat、ColorModulator 等）？
- [ ] `.json` 文件名与注册路径是否一致？
- [ ] GLSL 版本是否为 `#version 150`（Minecraft CoreShader 要求）？

### A.2 注册层
- [ ] `RegisterShadersEvent` 是否触发？（日志确认）
- [ ] ShaderInstance 是否非 null？（空检查 + 日志）
- [ ] RenderType 是否已缓存且复用了正确的 ShaderInstance？

### A.3 渲染层
- [ ] **深度测试配置是否正确？**（NO_DEPTH_TEST / LEQUAL / EQUAL 各不同）
- [ ] 如果使用了 GL_FRONT 剔除 + scale → 检查深度预写是否被正确完成
- [ ] 如果使用了 Translate → 确保变换矩阵是正确的（Minecraft Y 轴已反转）
- [ ] 如果使用了 FBO → 确保 FrameBuffer 绑定/解绑正确
- [ ] 如果使用了 BufferSource → endBatch() 是否在 popPose() 之前调用？
- [ ] Alpha 值是否 > 0？颜色是否 > 0？

### A.4 混合模式
- [ ] 半透明效果需要 `TRANSLUCENT_TRANSPARENCY`（SRC_ALPHA, ONE_MINUS_SRC_ALPHA）
- [ ] 发光效果可以使用 `ADDITIVE_TRANSPARENCY`（SRC_ALPHA, ONE）

### A.5 写入掩码
- [ ] 仅颜色输出 → `COLOR_WRITE`
- [ ] 颜色+深度输出 → `COLOR_DEPTH_WRITE`
- [ ] 仅深度预写 → `DEPTH_WRITE`

### A.6 调试技巧
- 先用纯色（红/绿/蓝）替换最终输出，确认渲染管线是否连通
- 使用 `NO_DEPTH_TEST` 排除深度问题
- 使用 `System.out.println` 记录关键状态（渲染器是否被调用、着色器是否非 null）
- 在 Fragment Shader 底部使用 `fragColor = vec4(1.0, 0.0, 0.0, 1.0);` 测试管线连通性

---

> 本文档基于以下项目源码分析：
> - [`ItemGlint-1.21.1-fabric`](../渲染/ItemGlint-1.21.1-fabric)
> - [`Malum-Mod-1.21.1`](../渲染/Malum-Mod-1.21.1)
> - [`Photon-1.21`](../渲染/Photon-1.21)
> - [`ShaderTest-main`](../渲染/ShaderTest-main)
> - [`Ars-Nouveau-main`](../渲染/Ars-Nouveau-main)
> - [`AdorableArmory-1.20.1`](../渲染/AdorableArmory-1.20.1)
> - [`AlexsCaves-main`](../渲染/AlexsCaves-main)
