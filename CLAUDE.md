# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

Len Souls（镜魂）—— 基于 NeoForge 1.21.1 的 Minecraft 扩展模组，是 [Exposure](https://github.com/mortuusars/Exposure) 相机模组的灵魂扩展衍生。核心机制：元素伤害系统 + 摄魂术附魔（用照片对特定实体增伤）。

## 工作区结构

本仓库位于一个多项目工作区中，同级目录包含三个参考项目（只读查阅）：

| 目录 | 说明 |
|------|------|
| `lensouls-template-1.21.1/` | **主力项目** — NeoForge 1.21.1 模组 |
| `Exposure/` | 基础相机模组，`io.github.mortuusars.exposure` |
| `ExposurePolaroid/` | 拍立得扩展，Fabric + NeoForge 双加载器 |
| `exposure-expanded/` | 功能扩展，Fabric + NeoForge 双加载器 |
| `Legendary-Monsters-1.21.1-NeoForge/` | 传奇怪物模组 1.21.1（compileOnly 引用） |
| `lionfish_1.21/` | LionfishAPI 1.21（Cataclysm 前置，compileOnly 引用） |
| `new1.20.1/` | 灾变 1.20.1 源码（只读参考） |
| `[灾变] L_Ender's Cataclysm 1.21.1-3.32/` | 灾变 1.21.1 解压 class（compileOnly 引用） |

## 构建与运行

```bash
./gradlew build            # 构建模组 JAR（产出 build/libs/lensouls-1.0.0.jar）
./gradlew runClient        # 启动 Minecraft 客户端
./gradlew runServer        # 启动专用服务器
./gradlew runData          # 运行数据生成器
./gradlew --refresh-dependencies  # 刷新依赖
```

## 纹理路径规范

物品纹理按来源组织：

| 分类 | 纹理路径 |
|------|---------|
| 基础镜魂 | `textures/item/len_souls/basic/{element}_soul.png` |
| 灾变 BOSS 镜魂 | `textures/item/len_souls/cataclysim/{boss}_soul.png` |
| 传说 BOSS 镜魂 | `textures/item/len_souls/legendary_monsters/{boss}_soul.png` |
| 子弹 | `textures/item/bullet/{type}_bullet.png` |
| 能力球 | `textures/item/skill_ball/{ability}.png` |

## 关键技术参数

| 参数 | 值 |
|------|-----|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.235 |
| Java | 21 |
| 映射 | Parchment 2024.11.17 |
| Mod ID | `lensouls` |
| 包名 | `com.plumejade.lensouls` |

## 模块架构

### 注册体系

使用 NeoForge `DeferredRegister` 体系，所有 register() 调用在 `LenSouls` 构造器中绑定到 `modEventBus`：

```
DeferredRegister.create(Registries.ITEM, MODID)           → ModItems
DeferredRegister.create(Registries.MOB_EFFECT, MODID)     → ModEffects
DeferredRegister.create(Registries.ENCHANTMENT, MODID)    → ModEnchantments
DeferredRegister.create(Registries.MENU, MODID)           → ModMenus
DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID) → ModCreativeTabs
```

项目中**没有**自定义方块或方块物品。

### 事件注册（无弃用模式）

已统一迁移到直接注册，不再使用弃用的 `@EventBusSubscriber(bus = Bus.GAME/MOD)`：

```java
// LenSouls.java 构造器
NeoForge.EVENT_BUS.register(this);
NeoForge.EVENT_BUS.register(DamageHandler.class);
NeoForge.EVENT_BUS.register(PhotoDamageHandler.class);
NeoForge.EVENT_BUS.register(EnchantmentRemovalListener.class);

// Mod 总线事件 → modEventBus.addListener()
PacketHandler.register(modEventBus);  // RegisterPayloadHandlersEvent
```

客户端侧：`LenSoulsClient` 构造器接收 `IEventBus modEventBus` 后直接 `modEventBus.addListener()`。

### 网络包（CustomPacketPayload）

所有包为 C2S，使用 `StreamCodec.of(encode, decode)`（**不要用** `StreamCodec.unit()`，它会导致编码/解码实例不一致）：

```java
public class XxxPacket implements CustomPacketPayload {
    public static final Type<XxxPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MODID, "xxx"));
    public static final StreamCodec<RegistryFriendlyByteBuf, XxxPacket> STREAM_CODEC =
        StreamCodec.of((buf, pkt) -> {}, buf -> new XxxPacket());
    public void handle(XxxPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> { /* server-side logic */ });
    }
}
```

注册：`PacketHandler` → `registrar.playToServer(TYPE, STREAM_CODEC, XxxPacket::handle)`

### 物品数据持久化

**必须使用 `DataComponents.CUSTOM_DATA` + `CustomData`**（1.21.1 删除了 `ItemStack.getTag()`）：

```java
// 写入
CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
tag.putString("key", value);
stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

// 读取
CustomData data = stack.get(DataComponents.CUSTOM_DATA);
if (data != null) {
    CompoundTag tag = data.copyTag();
    tag.getString("key");
}
```

### 动态快捷键显示（ConverterItem）

Tooltip 中需要动态读取玩家实际设置的键位时，使用反射访问 `KeyBindings`：

```java
private static String getConverterKeyName() {
    try {
        Class<?> kbClass = Class.forName("com.plumejade.lensouls.key.KeyBindings");
        var field = kbClass.getDeclaredField("CONVERTER_KEY");
        field.setAccessible(true);
        Object lazy = field.get(null);
        var getMethod = lazy.getClass().getMethod("get");
        Object mapping = getMethod.invoke(lazy);
        var gtmMethod = mapping.getClass().getMethod("getTranslatedKeyMessage");
        var component = (net.minecraft.network.chat.Component) gtmMethod.invoke(mapping);
        return component.getString();
    } catch (Exception | NoClassDefFoundError ignored) {}
    return "G"; // 服务端回退
}
```

服务端环境无法加载 `KeyMapping` 等 client-only 类，必须用反射 + `NoClassDefFoundError` 兜底。

### GUI / 菜单系统

使用 `IMenuTypeExtension.create(IContainerFactory)` 而非 `new MenuType<>()`：

```java
MENUS.register("converter", () -> IMenuTypeExtension.create(
    (IContainerFactory<ConverterMenu>) (id, inv, buf) -> new ConverterMenu(id, inv, stack)));
```

屏幕注册：`RegisterMenuScreensEvent` → `event.register(ModMenus.XXX.get(), XxxScreen::new)`

### 附魔注册

1.21.1 的 `Enchantment` 和 `EnchantmentDefinition` 均为 record 类型，**必须使用构造器**（静态工厂方法不存在）：

```java
var def = new Enchantment.EnchantmentDefinition(
    supportedItems,          // HolderSet<Item>
    Optional.empty(),        // primaryItems
    weight, maxLevel,        // int, int
    new Enchantment.Cost(minBase, minPerLevel),   // minCost
    new Enchantment.Cost(maxBase, maxPerLevel),   // maxCost
    anvilCost,               // int
    List.of(EquipmentSlotGroup.MAINHAND)          // slots
);
new Enchantment(description, def, HolderSet.empty(), DataComponentMap.EMPTY);
```

附魔 JSON 中字段名必须用 `per_level_above_first`（不是 `per_level_after_first`），无 `effects` 字段。

在创造标签中展示附魔书需用 `params.holders()` 而非 `DeferredHolder.get()`：

```java
var holder = params.holders().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(SOUL_PHOTOGRAPHY_KEY);
ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
book.enchant(holder, 1);
```

### 伤害系统

- **元素伤害**（`DamageHandler`）：`LivingDamageEvent.Pre` → `getOriginalDamage()/setNewDamage()`。攻击者玩家持有隐藏效果时，按实体弱点数据包追加倍率伤害
- **照片增伤**（`PhotoDamageHandler`）：武器有摄魂附魔 + 存储了目标实体照片时，追加 `photoBonus` 倍率
- 弱点数据包：`data/lensouls/entity_weakness/*.json`，通过 `/reload` 热重载

### 元素扩展指南

添加一个新元素只需修改以下位置（以闪电 Lightning 为例）：

| 步骤 | 文件 | 改动 |
|---|---|---|
| 1 | `ElementDamage.java` | 添加 `LIGHTNING("lightning")` 枚举常量 |
| 2 | `ModEffects.java` | 注册 `LIGHTNING_INFUSION` 效果（调用 `ElementInfusionEffect` 一行） |
| 3 | `ModItems.java` | 注册 `LIGHTNING_SOUL` 物品（调用 `LensoulItem` 一行） |
| 4 | `ModCreativeTabs.java` | 将新物品加入 `displayItems` |
| 5 | `entity_weakness/example.json` | 添加实体对闪电的弱点倍率 |
| 6 | `zh_cn.json` / `en_us.json` | 添加物品、效果、元素名的翻译 |

**无需修改**的类：`DamageHandler`（通过 `instanceof ElementInfusionEffect` 多态自动适配）、`ElementInfusionEffect`（通用效果模板）、`TimerService`、`ConverterTriggerPacket`、`ConverterMenu`。

### 镜魂冷却系统

- `TimerService`：服务端单例，每 tick 驱动过期清理
- 每个物理物品实例有独立 UUID（存于 `CustomData.SoulItemId`），冷却键为 `"soul_item_" + itemUuid`
- **注意：冷却键不是 `"soul_cooldown_" + elementId`**（旧格式，`SoulCache` 已删除）
- BOSS 镜魂走 `Config.BOSS_COOLDOWN`（默认 120s），基础镜魂走 `Config.DEFAULT_COOLDOWN`（默认 60s）
- 冷却时间双源持久化：`TimerService`（内存）+ `CustomData`（跨重启持久化，写 `SoulCooldownEnd` + `SoulCooldownDur`）
- G 键（`ConverterTriggerPacket`）从转换器 NBT 的 `SoulItemIds` 映射读取 UUID
- **`ConverterMenu.saveToStack()` 不再覆盖 `SoulItemIds`**（保留 G 键写入的映射）

### BOSS 韧性系统

BOSS 通过拍照攻击削减韧性条，破防后进入 10 秒定身状态。

**架构：**
- `BossToughnessManager`（服务端单例）：`ServerTickEvent.Post` 驱动 tick，管理所有 BOSS 的韧性状态
- `BossToughnessData`：单实体韧性数据（破防阈值、当前进度、恢复计时器、定身倒计时）
- `ToughnessDamageHandler`：`LivingDamageEvent.Pre` 中减伤 + 自动注册 BOSS
- `ToughnessPhotoHandler`：反射监听 `FrameAddedEvent`，每次拍照削 1 次韧性
- `ToughnessBarRenderer`：客户端 `RenderLevelStageEvent` 渲染韧性条（双纹理分界，延迟平滑跟位）

**BOSS 判定（`ToughnessDamageHandler.isBoss()`）：**
1. 白名单排除：铁傀儡/雪傀儡/乌龟等非 BOSS 高血实体直接返回 false
2. `BossDetectionMixin` 扫描 {@code Mob} 构造体是否有 `ServerBossEvent` 字段
3. 补偿血量阈值：`maxHealth >= 100.0`（排除上述白名单后）

**削韧流程：**
```
拍照 → ToughnessPhotoHandler → BossToughnessManager.hit()
  → BossToughnessData.hit()  削 1 次
  → 发送 ToughnessHitSoundPacket（随机选 1/4 音效，音量 0.7~1.2，音调 0.8~1.2）
  → 广播 ToughnessSyncPacket 到客户端
  → ToughnessBarRenderer 更新进度条
```
破防时：10 秒定身（setNoAi + setNoGravity）+ 广播到客户端

**所需削韧次数：** `computeRequiredHits()` 固定 5 次（或按血量比例计算 `toughPhotosPer20000HP`）

六个 BOSS 镜魂激活时触发的 3 秒幻灵表演序列。

**架构：**
- `BossPhantomManager`（服务端单例）：`ServerTickEvent.Post` 中驱动幻灵时间线
- `BossPhantomData`：记录每条幻灵的状态（类型、剩余 tick、幻灵实体 ID）
- `BossPhantomEntity`：自定义实体，服务端生成 → 客户端渲染，无碰撞无 AI

**激活流程：**
1. 右击或 G 键 → 启动冷却 → 走虚影路径（BossPhantomManager.startPhantom）
2. 服务端锁定玩家（Slowness 255 + Resistance 255），切换旁观者模式
3. 借真实 BOSS 实体（模组加载时）或生成 BossPhantomEntity（降级）
4. 200 ticks（10 秒）幻灵表演：
   - Charge 阶段（~第 30 tick）：发送 PhantomTickPacket（phase=0）→ 客户端蓄力粒子
   - Execute 阶段（~第 30~35 tick）：发送 PhantomSkillPacket → 客户端技能特效
   - Decay 阶段（~第 60 tick）：发送 PhantomTickPacket（phase=2）→ 客户端消散粒子
5. 表演结束：恢复游戏模式 → 清除锁定 → 施加 30s ElementInfusionEffect → 清理幻灵实体

**模型集成（compileOnly 依赖）：**

| BOSS | 模组 | 模型类 | 体系 |
|------|------|--------|------|
| Ignis | cataclysm | `Ignis_Model`（no-arg, 包装为静态姿势）| LionfishAPI AdvancedEntityModel |
| Ender Guardian | cataclysm | `Ender_Guardian_Model`（同上）| LionfishAPI AdvancedEntityModel |
| Netherite Monstrosity | cataclysm | `Netherite_Monstrosity_Model(ModelPart)` | 原生 HierarchicalModel |
| Cloud Golem | legendary_monsters | `Cloud_GolemModel<>(ModelPart)` | 原生 HierarchicalModel |
| Possessed Paladin | legendary_monsters | `NewPossessedPaladinModel<>(ModelPart)` | 原生 HierarchicalModel |
| The Obliterator | legendary_monsters | `TheObliteratorModel<>(ModelPart)` | 原生 HierarchicalModel |

**模组未加载时：** 使用 `BossPhantomModel`（内置简洁人形）+ BOSS 主色调叠加 + 默认贴图。

**关键文件：**
| 文件 | 作用 |
|------|------|
| `entity/BossPhantomType.java` | BOSS→幻灵映射枚举 |
| `entity/BossPhantomData.java` | 幻灵状态记录 |
| `entity/BossPhantomManager.java` | 服务端序列管理器 |
| `entity/BossPhantomEntity.java` | 幻灵实体 |
| `entity/ModEntities.java` | 实体类型注册 |
| `network/PhantomStartPacket.java` | S2C 启动 |
| `network/PhantomSkillPacket.java` | S2C 技能特效 |
| `network/PhantomStopPacket.java` | S2C 停止 |
| `client/phantom/ClientPhantomHandler.java` | 客户端管理（第三人称+粒子+音效）|
| `client/model/BossPhantomModel.java` | 后备简洁人形模型 |
| `client/render/BossPhantomRenderer.java` | 渲染器（原生模型优先）|
| `client/render/BossPhantomModelIntegration.java` | 跨模组模型加载 |

### 武器系统

两把独立武器：**次元枪**（Dimensional Gun）和**引力枪**（Gravity Gun），不依赖镜魂系统。

#### 次元枪（DimensionalGunItem）

- **三种弹药类型**：主世界（治疗玩家）/ 地狱（点燃）/ 末地（拉向玩家）
- **击杀成长**：`getKillProgress()` → 劫掠进度，影响伤害、穿透、蓄力时间、弹药上限、恢复速度
- **弹药恢复**：`inventoryTick` 中按冷却间隔恢复一发，持久化到 `CustomData`
- **射击模式**：半自动（蓄力→松开）或全自动（`onUseTick` 按射速发射）
- **蓄力**：`getUseDuration()` 恒为 `72000`（可无限保持满蓄瞄准），`releaseUsing()` 中 `elapsed / getChargeTicks` 计算蓄力比
- **公共 API**：`cycleAmmoType()`、`toggleFireMode()`、`addKill()`、`checkDimensionUnlocks()` 由 C2S 包触发

#### 引力枪（GravityGunItem）

- **立即发射，无蓄力无动画**：右键即射，`UseAnim.NONE`，item 属性 `useDuration=0`
- **0.2s cooldown（4 ticks）**：`player.getCooldowns().addCooldown(this, Config.GG_COOLDOWN.get())`
- **每实例 UUID**：`getOrCreateItemId()` 用于在牵引取消时精确定位子弹
- **牵引逻辑**：命中后 Phase1 拉到玩家前方 2 格，多段碰撞检测防止实体穿越玩家；Phase2 持续维持距离；右键再次使用取消牵引
- **客户端隐藏**：命中后通过 `SynchedEntityData` 同步 `DATA_HAS_HIT`，`shouldRender()` 返回 false
- **磁力闪电弧**：`GravityTetherRenderer` 在 `RenderLevelStageEvent.AFTER_ENTITIES` 渲染天蓝色闪电弧（`rendertype_lightning` 着色器，Iris 兼容），12 段正弦组合噪声扰动，位置延迟平滑跟位

#### 公共武器模式

| 模式 | 文件 |
|------|------|
| 物品模型 JSON（`items/` 目录，1.21 新格式） | `assets/lensouls/items/*.json` |
| `hand_animation_on_swap: false` | 阻止组件更新触发物品切换动画 |
| `shouldCauseReequipAnimation()` 返回 `slotChanged \|\| !isSameItem` | Java 层保底，同物品同槽不重装备 |
| 拉弓动画速度对齐 | `GunBowAnimationMixin`（`@ModifyConstant 20.0F → chargeTicks`） |
| 弹药 DataComponent | `GunAmmoData` / `GunKillData`（`ModDataComponents`） |
| `getChargeTicks()` 为 **public** | mixin 需要跨包访问 |
| 子弹实体 | `GunBulletEntity` / `GravityBulletEntity`（均为 `ThrowableItemProjectile`）|
| 子弹渲染 | `ThrownItemRenderer`（在 `LenSoulsClient` 注册）|
| 快捷键 | `GunInputHandler`（左键空气检测）+ C2S 包 |
| 击杀追踪 | `GunKillHandler`（`LivingDeathEvent`）+ 维度解锁（`PlayerChangedDimensionEvent`）|

### Mixin 配置

NeoForge moddev 原生集成 Mixin，通过 `neoforge.mods.toml` 的 `[[mixins]]` 块声明配置：

```toml
[[mixins]]
config="${mod_id}.mixins.json"
```

关键要点：
- `lensouls.mixins.json` 中**必须指定 `refmap` 字段**指向 `lensouls.mixins.refmap.json`
- `src/main/resources/` 下**必须有空占位 refmap 文件**（内容为 `{"minVersion":"0.8","plugin":"","refmap":{}}`），否则在复杂整合包环境下会出现 refmap 缺失错误（模组开发环境 runClient 不会有此问题，但 300+ 模组生产环境必现）
- NeoForge moddev **不会自动生成** refmap 文件，需要手动创建空占位
- `mixins` 数组**必须至少有一个条目**以触发 NeoForge moddev 的注解处理器
- 纯客户端 mixin 放在 `client` 数组，服务端兼容的放在 `mixins` 数组
- 可使用一个空 `@Mixin(Entity.class)` 触发器来保持 `mixins` 非空

**`lensouls.mixins.json` — 当前状态：**
```json
{
  "required": true,
  "package": "com.plumejade.lensouls.mixin",
  "compatibilityLevel": "JAVA_21",
  "refmap": "lensouls.mixins.refmap.json",
  "mixins": [
    "SoulOutlineMixinTrigger",
    "ability.PlayerInteractionMixin",
    "BossDetectionMixin"
  ],
  "client": [
    "client.FrozenEntityRenderMixin",
    "client.BufferSourceGetBufferMixin",
    "client.GameRendererMixin",
    "client.GunBowAnimationMixin",
    "client.ItemInHandRendererMixin",
    "client.ItemRendererMixin",
    "client.ItemRendererAccessor",
    "client.ItemInHandLayerMixin",
    "client.PlayerClientMixin",
    "client.MultiPlayerGameModeMixin",
    "client.GameRendererFrameEndMixin",
    "client.LivingEntityPhantomMixin"
  ],
  "injectors": { "defaultRequire": 1 }
}
```

**`lensouls.compat.mixins.json`（required: false）— 兼容 Mixin：**
```json
{
  "required": false,
  "package": "com.plumejade.lensouls.mixin.compat",
  "compatibilityLevel": "JAVA_21",
  "refmap": "lensouls.mixins.refmap.json",
  "client": [
    "PolaroidFixMixin",
    "BetterCombatRangeMixin",
    "IrisBufferSourceGetBufferMixin"
  ],
  "injectors": { "defaultRequire": 0 }
}
```

### Iris/Oculus 兼容

镜魂描边系统与 Iris/Oculus 光影模组的兼容策略：

- **不使用 @Redirect**：`renderHandsWithItems` 的注入改为 `@Inject RETURN`（`ItemInHandRendererMixin`），避免与 Iris/Oculus 的 `@Redirect` 冲突
- **检测工具**：`SoulOutlineCompat` 运行时检测 Iris/Oculus 加载状态和 shaderpack 激活状态
- **兼容路径**：`@Inject` 作用于方法体，无论在哪个调用路径（原版、Iris 代理、Oculus HandRenderer）都会执行；帧末 `@Inject RETURN of renderItemInHand` 始终执行合成
- 参考 `../ItemGlint-1.21.1-fabric/`（Fabric 版 item outline 模组，有三条渲染路径：Vanilla / Iris Fallback / Iris Active）

无需在 `build.gradle` 中添加注解处理器依赖，空占位 refmap 配合 `refmap` 字段声明即可。

### BOSS 镜魂描边系统（mask FBO + cos² 着色器）

当玩家激活 BOSS 镜魂获得 `ElementInfusionEffect` 时，手持物品/实体获得彩色发光描边。

**架构：平行 FBO + RenderType composite**
- `BossOutlineManager` — mask FBO 管理 + 帧末 composite 驱动
- `BossEntityRenderMixin` — 第三人称玩家实体渲染时重定向到 mask FBO
- `ItemInHandRendererMixin` — 第一人称手部渲染重定向到 mask FBO（空手跳过）
- `GameRendererFrameEndMixin` — 帧末触发 composite
- 共享 `FrozenOutlineManager.goldOutlineShader`

**每帧流程：**
```
GameRenderer.render() HEAD → beginFrame() 清空 currentColors
  → AFTER_SKY → 清空 mask FBO + 帧级去重集合
  → 实体/手部渲染 → 写入 mask FBO（白色实体区域，alpha=1.0）
  → GameRenderer.render() RETURN → composite()
    → goldOutlineShader（rendertype_gold_outline.fsh）
    → Sobel 边缘检测 + cos² 四色渐变混合
    → 全屏四边形渲染到主帧缓冲
```

**着色器核心（`rendertype_gold_outline.fsh`）：**
- Sobel 3×3 边缘检测 → `discard edge < 0.04`
- BOSS 模式（`BossGlowStrength > 0`）：cos² 四色平滑权重混合，时间驱动渐变流动
- 冰蓝 fallback（`BossGlowStrength = 0`）：sin 相位双色混合定身描边
- `finalColor = color`（配色值直接输出，无额外亮度乘数）
- 流向：`x×3.2 + y×1.1`（左上→右下对角线），速度 `Time×1.2`（1.2 轮/秒）
- `Time` uniform：`(floorMod(gameTime, 240000) + partialTick) × 0.05`（帧间插值）

**六 BOSS 配色方案（`BossOutlineColors.java`）：**
| BOSS | 四色序列 |
|------|---------|
| 焰魔 | rgb(1.8,0.4,0) / rgb(1.5,1.2,0) / rgb(1.4,0.2,0) / rgb(0.8,0,0) |
| 云筑魔像 | #84eeff / #1dabff / #1b4e98 / #d2d8e1 |
| 堕落圣骑 | #626b6e / #626b6e / #dbe5e9 / #3fe3ed |
| 湮灭构造体 | #4cff1a / #a9ff00 / #264840 / #e7edaf |
| 末影守卫 | #3c3a3c / #ffe500 / #5a1a68 / #271a38 |
| 下界合金巨兽 | #faf695 / #fbdc61 / #cf200b / #3c3a3c |

支持 `hex(0xRRGGBB)` 辅助方法直接输入十六进制色值。

**关键文件：**
| 文件 | 职责 |
|------|------|
| `ability/client/BossOutlineManager.java` | mask FBO + composite 驱动 |
| `client/outline/BossOutlineColors.java` | 配色 + 效果检测 |
| `mixin/client/BossEntityRenderMixin.java` | 第三人称 mask 捕获 |
| `mixin/client/ItemInHandRendererMixin.java` | 第一人称 mask 捕获（空手跳过） |
| `mixin/client/GameRendererFrameEndMixin.java` | 帧末 composite 触发 |
| `ability/client/FrozenOutlineManager.java` | 冰蓝定身描边（共享 shader） |
| `shaders/core/rendertype_gold_outline.fsh` | Sobel + cos² 着色器 |

### 可选前置依赖

Exposure、ExposurePolaroid、exposure-expanded、L_Ender's Cataclysm（灾变）、Legendary Monsters（传奇怪物）均为 optional。
- Exposure 系列：通过 NBT 序列化读取照片数据，无编译期依赖
- Cataclysm + Legendary Monsters：compileOnly 依赖，通过 ModList 运行时检测
- JEI（Just Enough Items）：compileOnly 依赖，JEI jar 位于 `run/mods/[JEI物品管理器] jei-1.21.1-neoforge-19.27.0.343.jar`，通过 `@JeiPlugin` 实现信息提示

### 配置键名（Config.java → TOML）

| 领域 | Config.java 字段 | TOML 键 | 默认值 |
|------|---|---|---|
| 镜魂 | `DEFAULT_DURATION` | `defaultDuration` | 30 |
| 镜魂 | `DEFAULT_COOLDOWN` | `defaultCooldown` | 60 |
| 镜魂 | `BOSS_COOLDOWN` | `bossCooldown` | 120 |
| 照片 | `PHOTO_BONUS` | `photoBonus` | 1.2 |
| 次元枪 | `DG_BASE_MAX_AMMO` | `dgBaseMaxAmmo` | 10 |
| 次元枪 | `DG_BASE_REGEN_TIME` | `dgBaseRegenTime` | 60 |
| 次元枪 | `DG_BASE_DAMAGE` | `dgBaseDamage` | 5.0 |
| 次元枪 | `DG_BASE_ARMOR_PEN` | `dgBaseArmorPen` | 0.0 |
| 次元枪 | `DG_KILL_TARGET` | `dgKillTarget` | 200 |
| 次元枪 | `DG_MAX_DAMAGE` | `dgMaxDamage` | 40.0 |
| 次元枪 | `DG_MAX_ARMOR_PEN` | `dgMaxArmorPen` | 80.0 |
| 次元枪 | `DG_MAX_AMMO` | `dgMaxAmmo` | 25 |
| 次元枪 | `DG_MIN_REGEN_TIME` | `dgMinRegenTime` | 10 |
| 次元枪 | `DG_BASE_CHARGE_TIME` | `dgBaseChargeTime` | 10 |
| 次元枪 | `DG_MIN_CHARGE_TIME` | `dgMinChargeTime` | 4 |
| 次元枪 | `DG_ACCURACY_OFFSET` | `dgAccuracyOffset` | 0.8 |
| 次元枪 | `DG_FIRE_RATE` | `dgFireRate` | 5 |
| 次元枪 | `DG_HELL_FIRE_DURATION` | `dgHellFireDuration` | 5 |
| 次元枪 | `DG_ENDER_PULL_FORCE` | `dgEnderPullForce` | 1.0 |
| 次元枪 | `DG_ENDER_PULL_DURATION` | `dgEnderPullDuration` | 10 |
| 次元枪 | `DG_OVERWORLD_HEAL` | `dgOverworldHeal` | 2.0 |
| 次元枪 | `DG_ELEMENT_MULTIPLIER` | `dgElementMultiplier` | 1.0 |
| 引力枪 | `GG_COOLDOWN` | `ggCooldown` | 4 |
| 引力枪 | `GG_PULL_FORCE` | `ggPullForce` | 1.0 |
| 韧性 | `TOUGH_BAR_WIDTH` | `toughBarWidth` | 32 |
| 韧性 | `TOUGH_BAR_HEIGHT` | `toughBarHeight` | 32 |
| 韧性 | `TOUGH_BAR_VERTICAL_OFFSET` | `toughBarVerticalOffset` | 0.5 |
| 韧性 | `TOUGH_DAMAGE_REDUCTION` | `toughDamageReduction` | 0.8 |
| 韧性 | `TOUGH_RECOVERY_SECONDS` | `toughRecoverySeconds` | 100 |
| 韧性 | `TOUGH_STUN_DURATION_TICKS` | `toughStunDurationTicks` | 200 |
| 韧性 | `TOUGH_PHOTOS_PER_20000HP` | `toughPhotosPer20000HP` | 10.0 |

### 获取方式系统

### 掉落获取（AcquisitionHandler）

通过 `LivingDropsEvent` 实现，由 `Config` 控制开关（默认全部开启）：

| 配置键 | 效果 |
|--------|------|
| `enableBasicSoulDrop` | 击杀任意怪物 10% 概率掉落随机基础镜魂 |
| `enableBossSoulDrop` | 击杀对应 BOSS 必定掉落其专属镜魂 |
| `enableEnchantmentLoot` | 摄魂术附魔书出现在地牢箱子与大师级图书管理员交易 |
| `enableDimensionalGunRecipe` | 次元枪合成配方 |
| `enableGravityGunRecipe` | 引力枪合成配方 |
| `enableConverterRecipe` | 转换器合成配方 |
| `enableSkillBallBossLoot` | 击杀 BOSS 50% 概率掉落随机能力球 |

BOSS 检测通过 `BossPhantomType` 的实体 ID 匹配（`modId:entityRegistryName`）+ 模组加载判断。

### 能力球系统（SkillBallItem）

- **随机能力球**（`skill_ball`）：右键从全部 5 个能力中随机抽取并解锁
- **指定能力球**（`xxx_ball`）：右键解锁对应能力，仅创造模式获取
- 能力不再默认解锁（`PlayerAbilityData` 构造器不再设置 `WEAKNESS_LENS` 为已解锁）
- 首次解锁能力时 `AbilityManager.setUnlocked()` 自动发送能力描述

### 铁砧合成（AnvilUpgradeHandler）

两个同元素同等级镜魂 → 下一等级，消耗经验：II=5级、III=10、IV=15、V=20。最高 V 级。
通过 `AnvilUpdateEvent` 驱动，等级存于 `CustomData.SoulLevel`。

### JEI 兼容

**依赖配置**（`build.gradle`）：
```groovy
compileOnly files("run/mods/[JEI物品管理器] jei-1.21.1-neoforge-19.27.0.343.jar")
```

**插件类**：`integration/jei/LensoulsJeiPlugin.java`
- 为物品添加获取方式说明（`addIngredientInfo` + `VanillaTypes.ITEM_STACK`）
- 配方由 JEI 自动从 `data/recipe/` 发现
- 镜魂物品同时显示获取方式 + 铁砧升级提示（绿色）

## 物品 Tooltip 规范

所有物品 tooltip 使用统一格式：

| 风格 | 用途 | 色值 |
|------|------|------|
| `§a操作：(描述)` | 使用提示 | 绿色 |
| `§d操作：(描述)` | 特殊效果提示 | 浅紫 |
| `§e键名§7` | 快捷键高亮 | 黄色 |
| 灰色 | 描述/状态信息 | `ChatFormatting.GRAY` |
| 暗灰色 | 次要信息 | `ChatFormatting.DARK_GRAY` |
| 黄色 | 操作提示 / 冷却 | `ChatFormatting.YELLOW` |

格式约定：`操作：(描述)`、`按X：(描述)`。

- `ModSounds.java`：`DeferredRegister<SoundEvent>` 注册所有音效
- 音效文件在 `assets/lensouls/sounds/` 下，格式为 OGG Vorbis
- `sounds.json` 定义音效条目，支持多文件随机变体（如 `boss.toughness_change` 有 4 个变体）
- 客户端播放：通过 S2C 包携带实体 ID，用 `level.playLocalSound()` 在实体位置播放（如 `ToughnessHitSoundPacket`）
- 播放参数可随机浮动：音量 `0.7~1.2`，音调 `0.8~1.2`

### ElementInfusionEffect 效果通知

`ElementInfusionEffect` 通过 `RegisterClientExtensionsEvent` 设置无图标、无粒子（`LenSoulsClient.registerClientExtensions()`）：

`ElementInfusionEffect` 通过 `RegisterClientExtensionsEvent` 设置无图标、无粒子（`LenSoulsClient.registerClientExtensions()`）：

```java
IClientMobEffectExtensions hidden = new IClientMobEffectExtensions() {
    @Override public boolean isVisibleInInventory(MobEffectInstance inst) { return false; }
    @Override public boolean isVisibleInGui(MobEffectInstance inst) { return false; }
};
event.registerMobEffect(hidden, ModEffects.FIRE_INFUSION, ...);
```

粒子也可在 `MobEffectInstance` 构造器中关闭（`showParticles=false`）。<p>到期前 3 秒系统消息提醒（`shouldApplyEffectTickThisTick` → `applyEffectTick`，60/40/20 ticks 时触发）。</p>

## 跨模组交互陷阱记录

### 拍立得创造模式复制问题

**现象**：主手持拍立得相机，在创造模式背包中右键安装相纸时，一个重复的拍立得出现在副手槽，且两台相机都装满了相纸。

**根因**：创造模式下 `overrideOtherStackedOnMe` 的 `slot.index` 是创造屏幕的**虚拟槽位索引**（受所有已注册创造标签页物品数量的影响），不匹配服务端 `InventoryMenu` 的**真实槽位索引**。拍立得内部调用 `handleCreativeModeItemAdd(polaroid, slot.index)` 将偏移后的索引发给服务端，服务端按 `InventoryMenu` 解析——`slot.index=45` 对应的是 **副手槽**（`InventoryMenu` 布局中槽位 45 = offhand）。

**为什么只发生在 lensouls 加载后**：`ModCreativeTabs` 注册了 16 个物品和一个附魔书，这些额外物品增加了创造模式物品列表总长度，改变了创造屏幕的虚拟槽位索引分布。主手槽位的 `slot.index` 被推到 45+，恰好映射到服务端的副手槽。

**修复方案**（`PolaroidFixMixin.java`，位于 `lensouls.compat.mixins.json`）：
```java
@Redirect(
    method = "overrideOtherStackedOnMe",
    at = @At(value = "FIELD", target = "Lnet/minecraft/world/inventory/Slot;index:I", remap = true),
    require = 0
)
private int lensouls$fixSlotIndex(Slot slot) {
    return slot.getContainerSlot();  // 使用真实背包槽位索引
}
```
`@Redirect` 拦截 `Slot.index` 字段读取，返回 `slot.getContainerSlot()`（真实背包容器内的索引）而非 `slot.index`（创造屏幕虚拟索引）。该方法中唯一读取 `Slot.index` 的地方就是 `handleCreativeModeItemAdd` 的参数。

**经验教训**：
- `slot.index` ≠ `slot.getContainerSlot()`，尤其在创造模式下差异显著
- `handleCreativeModeItemAdd(stack, slotIndex)` 发送给服务端的 `slotIndex` 是基于 `InventoryMenu` 的布局，不是创造屏幕的布局
- 创造模式中一个模组注册的物品越少，对槽位索引的影响越小
- JEI 的重复物品警告（`1 duplicate items were found in creative tab`）不会直接导致复制，但说明创造标签条目的配置可能有问题

## 摄魂术四能力系统

相机（`exposure:camera` / `exposure_polaroid:instant_camera`）四能力通过左键空气循环切换，不同能力拍出的照片具有不同灵蕴性质。

| 能力 | 照片数据 | 左键效果 | 右键效果 |
|------|---------|---------|---------|
| **弱点透镜** | `lensouls:injected` | 切换能力 | 拍普通摄魂照片，可装剑槽增伤 |
| **空间扭曲** | `lensouls:spatial_warp_pos {x,y,z}` | 开启/关闭扭曲圈（不消耗照片） | 拍扭曲照片 |
| **时空回溯** | `lensouls:snapshot` | 主动触发回溯 | 拍回溯照片 |
| **时间定格** | 无（不需要照片） | 切换能力 | 冻结视锥体内实体 5s |

**相机必须附有摄魂术**才能拍出有效果的照片。无附魔时静默跳过注入，照片不带 `lensouls:injected` 标记 → 无法装剑槽。

### 数据流分离

每种能力只走自己的数据流，互不混淆：

| 照片类型 | `lensouls:injected` | `lensouls:ability_type` | 可否装剑槽 | 用途 |
|---------|-------------------|------------------------|-----------|------|
| 无附魔相机拍摄 | ❌ 无 | ❌ 无 | ❌ 拒绝 | 普通照片 |
| 弱点透镜 | ✅ `true` | `weakness_lens` | ✅ 允许 | 剑槽增伤 |
| 空间扭曲 | ✅ `true` | `spatial_warp` | ❌ 拒绝 | 左键激活扭曲圈 |
| 时空回溯 | ✅ `true` | `temporal_recall` | ❌ 拒绝 | 回溯保命 |

剑槽接受判断（`ExposureHelper.isSwordSlotSuitable`）：必须有 `lensouls:injected` 且 `ability_type` 为空或 `weakness_lens`。

### 关键文件

| 文件 | 职责 |
|------|------|
| `ability/AbilityType.java` | 四能力枚举 |
| `ability/PlayerAbilityData.java` | 玩家能力持有者（NBT 序列化） |
| `ability/AbilityManager.java` | 服务端单例管理 + S2C 同步 |
| `ability/handler/PhotoInjectionHandler.java` | 拍照时多 tick 扫描注入能力数据 |
| `ability/handler/CameraInputHandler.java` | 客户端检测相机左键空气 |
| `ability/handler/SpatialWarpHandler.java` | 维度切换时清理 |
| `ability/handler/TemporalRecallHandler.java` | 致命伤被动回溯 |
| `ability/handler/TimeFreezeHandler.java` | 时间定格触发 |
| `ability/handler/FreezeCleanupHandler.java` | 断线清理冻结 |
| `ability/network/AbilityCyclePacket.java` | C2S 切换能力 |
| `ability/network/AbilitySyncPacket.java` | S2C 同步能力状态+球心坐标 |
| `ability/network/SpatialWarpActivatePacket.java` | C2S 开关扭曲（不消耗照片） |
| `ability/network/TemporalRecallTriggerPacket.java` | C2S 主动回溯 |
| `ability/client/ClientAbilityCache.java` | 客户端缓存+S2C 状态+球心坐标 |
| `ability/client/SpatialWarpOutlineRenderer.java` | 空间扭曲球体描边渲染（`RenderLevelStageEvent`） |
| `ability/client/WireframeRenderTypes.java` | 自定义 RenderType 工厂（反射补全保护字段） |

## 空间扭曲设计细节

### 四球模型

空间扭曲激活后以照片拍照位置为球心，玩家自身位置也有自己的球，共四个球：

| 球 | 球心 | 半径 |
|---|------|------|
| 玩家自身方块触及球 | 玩家位置 | `Attributes.BLOCK_INTERACTION_RANGE`（生存 4.5 / 创造 6.0）|
| 玩家自身实体触及球 | 玩家位置 | `Attributes.ENTITY_INTERACTION_RANGE`（生存 3.0）|
| 扭曲方块交互球 | 照片拍照位置 | `Attributes.BLOCK_INTERACTION_RANGE` |
| 扭曲实体攻击球 | 照片拍照位置 | `Attributes.ENTITY_INTERACTION_RANGE` |

只要目标在任一相同类型的球内就能交互。

### 双端 Mixin 架构

#### 客户端（`client` 数组，仅影响 `LocalPlayer`）

| Mixin | 文件 | 做法 |
|---|---|---|
| `PlayerClientMixin` | `mixin/client/PlayerClientMixin.java` | `@Inject RETURN blockInteractionRange/entityInteractionRange`，空间扭曲激活时膨胀到可到达扭曲球最远端的距离 |
| `MultiPlayerGameModeMixin` | `mixin/client/MultiPlayerGameModeMixin.java` | `@Inject HEAD startDestroyBlock` 预检：方块在正常触及范围 OR 扭曲球内才放行（消除非球内挖掘音效闪烁）|

注意：`instanceof LocalPlayer` 确保不污染集成服务端的 `ServerPlayer`。

#### 服务端（`mixins` 数组，最终防线）

| Mixin | 文件 | 做法 |
|---|---|---|
| `PlayerInteractionMixin` | `mixin/ability/PlayerInteractionMixin.java` | `@Inject RETURN canInteractWithBlock/Entity`：原返回 false 时检查扭曲球，在球内则放行 |

#### 兼容 Mixin（`lensouls.compat.mixins.json`，`required: false`）

| Mixin | 目标 | 作用 |
|---|---|---|
| `PolaroidFixMixin` | Exposure Polaroid | `@Redirect Slot.index` → `getContainerSlot()` 修复创造模式复制 |
| `BetterCombatRangeMixin` | Better Combat | `@Inject RETURN getRangeForItem` 膨胀攻击范围（BC 直接读属性不走 `entityInteractionRange()` 方法）|

### 状态同步

- `AbilitySyncPacket`（S2C）：携带 `enabledOrdinal + spatialWarpActive + warpX/Y/Z + warpDimension`
- 客户端 `ClientAbilityCache` 缓存球心坐标，用 `getAttributeValue(BLOCK/ENTITY_INTERACTION_RANGE)` 检查目标是否在球内（不受客户端范围膨胀影响）
- 维度切换 / 切走能力 / 断线时自动关闭扭曲

### 照片注入流程

```
右键相机 → PhotoInjectionHandler.onRightClickCamera()
  → 快照已有照片槽位（避免旧照被注入）
  → 附魔检查：无附魔 → 静默跳过
  → pendingInjections.put(UUID, PendingInjection)
  → onServerTick.Post 每 tick 扫描
    → 跳过 knownPhotoSlots 中的旧照
    → 新照片写入 lensouls:injected + lensouls:ability_type + 能力数据
    → 修改照片显示名（如 "照片 (空间扭曲)"）
    → 成功后移除等待队列
```

## 渲染技术路线（参考研究）

> **渲染技术体系完整文档**：[docs/shader.md](docs/shader.md) — 涵盖 CoreShader 注册、RenderType 搭建、RenderLayer 模式、FBO 后处理、OutputStateShard 分层渲染、全局实体效果层、着色器动画模式、常用图案代码以及故障排查清单。基于 7 个模组源码（ItemGlint/Malum/Photon/ShaderTest/ArsNouveau/AdorableArmory/AlexsCaves）深度分析。

基于对以下模组源码的研究（均在 `../渲染/` 目录中）：
- **ItemGlint**（Forge + Fabric 双版）— 3-pass 后处理描边
- **Epic Fight** — 自定义 RenderType + 残影 stencil + 粒子渲染管线
- **Alex's Caves** — CoreShader + OutputStateShard + PostEffectRegistry
- **Fancy VFX** — Lodestone 粒子 VFX
- **Iris** — 全管线 shaderpack 拦截系统

### Iris 兼容核心原则

> **通过 `RenderType` 管线渲染，不要直接调用 `RenderSystem` 裸 GL。**

Iris 在 `MixinLevelRenderer` / `MixinGameRenderer` 中拦截 `RenderType` 创建和应用，为每个阶段注入正确着色器：
- `Tesselator` + `RenderSystem.setShader()` 裸画 → ✗ Iris 不知情，着色器被覆盖
- `BufferSource.getBuffer(RenderType)` + `VertexConsumer` → ✓ Iris 会拦截并设置阶段
- `OutputStateShard` 切换到自定义 FBO → ✓ Iris 不干涉 FBO 绑定

### 世界空间球体渲染方案对比

| 方案 | 原理 | 可见度 | Iris 兼容 |
|---|---|---|---|
| **A: Tesselator 裸 GL** | `RenderSystem.disableDepthTest()` + `Tesselator.begin(LINES)` | ⭐ | ✗ |
| **B: 自定义 RenderType + MultiBufferSource** | 创建 `RenderType`（`rendertype_lines` shader + `POSITION_COLOR_NORMAL` + `MAIN_TARGET`），通过 `BufferSource` 提交顶点 | ⭐⭐⭐ | ✓ |
| **C: 粒子球壳** | 球面分布粒子，自定 `ParticleRenderType` | ⭐⭐ | ✓ |

**已验证通过方案 B** — 使用 `RenderLevelStageEvent.Stage.AFTER_ENTITIES` + `MultiBufferSource` + 自定义 RenderType。

### 实体描边方案对比

| 方案 | 原理 | Iris 兼容 |
|---|---|---|
| **X: `shouldEntityAppearGlowing()` + OutlineBuffer 改色** | 冻结实体返回 `isGlowing=true` → 改 outline 颜色 | ✓ |
| **Y: 全局渲染层（Alex's Caves `ACPotionEffectLayer` 模式）** | 所有 `LivingEntityRenderer` 添加层 → 后处理 FBO 辉光 | ✓ |
| **Z: stencil 二遍 + 残影（Epic Fight 模式）** | 一遍 depth-only 写入模板 → 二遍 EQUAL 深度测试 | ⚠ 需处理 Iris 模板冲突 |

**推荐路径**: 先 X 快速实现 → 再 Y 升级华丽度。

### 实现路径总览

| 阶段 | 内容 | 技术 |
|---|---|---|
| **1 ✅ 已完成** | 空间扭曲球体可视化 | 自定义 RenderType + `rendertype_lines` shader + `POSITION_COLOR_NORMAL` + `MAIN_TARGET` |
| **2** | 时间定格实体描边 | `shouldEntityAppearGlowing()` + OutlineBuffer 改色 |
| **3** | 华丽升级 + Iris 完整兼容 | `OutputStateShard` + 后处理 FBO bloom；`DuplicatingBufferSource` |

### 已验证的自定义 RenderType 世界空间渲染模式

**核心模式（空间扭曲球体描边）：**

1. 事件阶段：`RenderLevelStageEvent.Stage.AFTER_ENTITIES`
2. 获取缓冲区：`Minecraft.getInstance().renderBuffers().bufferSource()`
3. 获取 RenderType：`bufferSource.getBuffer(WireframeRenderTypes.sphereOutline())`
4. 提交顶点：`consumer.addVertex(poseMatrix, x, y, z).setColor(r, g, b, a).setNormal(nx, ny, nz)`
5. 结束批次：`bufferSource.endBatch(WireframeRenderTypes.sphereOutline())`
6. 平移到世界坐标：`poseStack.translate(worldX - camX, worldY - camY, worldZ - camZ)`

**RenderType 搭建关键（`WireframeRenderTypes.java`）：**

```java
// 着色器
private static final RenderStateShard.ShaderStateShard RENDERTYPE_LINES_SHADER =
    new RenderStateShard.ShaderStateShard(GameRenderer::getRendertypeLinesShader);

// 反射补全 LineStateShard（glLineWidth 控制，原为 protected）
static {
    Constructor<RenderStateShard.LineStateShard> lineCtor =
        RenderStateShard.LineStateShard.class.getDeclaredConstructor(OptionalDouble.class);
    lineCtor.setAccessible(true);
    LINE_STATE = lineCtor.newInstance(OptionalDouble.of(2.0));

    var layeringField = RenderStateShard.class.getDeclaredField("VIEW_OFFSET_Z_LAYERING");
    layeringField.setAccessible(true);
    VIEW_OFFSET_Z_LAYERING = (RenderStateShard.LayeringStateShard) layeringField.get(null);
}

private static RenderType create() {
    RenderType.CompositeState state = RenderType.CompositeState.builder()
        .setShaderState(RENDERTYPE_LINES_SHADER)
        .setLineState(LINE_STATE)                       // ← 反射补全
        .setLayeringState(VIEW_OFFSET_Z_LAYERING)       // ← 反射补全（防深度冲突）
        .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
        .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
        .setCullState(RenderStateShard.NO_CULL)
        .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
        .setOutputState(RenderStateShard.MAIN_TARGET)   // ← 主帧缓冲（非 ITEM_ENTITY_TARGET）
        .createCompositeState(false);

    return RenderType.create("lensouls_lines_main",
        DefaultVertexFormat.POSITION_COLOR_NORMAL,
        VertexFormat.Mode.LINES, 1024, false, false, state);
}
```

**关键陷阱与经验教训：**
- `RenderType.LINES` 输出到 `ITEM_ENTITY_TARGET`，会经过后处理管线 → 隐约可见但昏暗；切换 `MAIN_TARGET` 后需要补全 `LineStateShard` 和 `VIEW_OFFSET_Z_LAYERING`（均为 `protected`，通过反射访问）
- `POSITION_COLOR_NORMAL` 格式必须用 `setNormal()`，否则着色器结果异常
- 顶点提交必须使用 `addVertex(Matrix4f, x, y, z)` + `.setColor().setNormal()` 链式调用
- 不使用 lightmap/UV（`POSITION_COLOR_NORMAL` 格式不含 UV）
- 线宽通过 `glLineWidth`（`LineStateShard`）控制，默认 1px 太细不可见，至少 2.0
- `MAIN_TARGET` 可直接渲染到主帧缓冲，不受后处理 FBO 影响
- 使用 `TRANSLUCENT_TRANSPARENCY`（`SRC_ALPHA, ONE_MINUS_SRC_ALPHA`）而非 `NO_TRANSPARENCY`，否则亮色线条可能覆盖不正确
- `bufferSource.endBatch()` 必须在 `poseStack.popPose()` 之前调用，因为 `endBatch` 立即刷新缓冲

### 关键文件模式

**`rendertype_lines` 自定义 RenderType（空间扭曲球体描边，已验证）：**
```java
private static final RenderStateShard.ShaderStateShard RENDERTYPE_LINES_SHADER =
    new RenderStateShard.ShaderStateShard(GameRenderer::getRendertypeLinesShader);

// 反射补全 LineStateShard（protected）
private static final RenderStateShard.LineStateShard LINE_STATE;
static {
    Constructor<RenderStateShard.LineStateShard> ctor =
        RenderStateShard.LineStateShard.class.getDeclaredConstructor(OptionalDouble.class);
    ctor.setAccessible(true);
    LINE_STATE = ctor.newInstance(OptionalDouble.of(2.0));
}

public static RenderType sphereOutline() {
    RenderType.CompositeState state = RenderType.CompositeState.builder()
        .setShaderState(RENDERTYPE_LINES_SHADER)
        .setLineState(LINE_STATE)
        .setLayeringState(VIEW_OFFSET_Z_LAYERING)  // 反射补全
        .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
        .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
        .setCullState(RenderStateShard.NO_CULL)
        .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
        .setOutputState(RenderStateShard.MAIN_TARGET)
        .createCompositeState(false);
    return RenderType.create("lensouls_lines_main", DefaultVertexFormat.POSITION_COLOR_NORMAL,
        VertexFormat.Mode.LINES, 1024, false, false, state);
}
```

**OutputStateShard 分层渲染（参考 Alex's Caves）：**
```java
protected static final OutputStateShard IRRADIATED_OUTPUT = new OutputStateShard("irradiated_target",
    () -> {
        RenderTarget target = PostEffectRegistry.getRenderTargetFor(...);
        if (target != null) { target.copyDepthFrom(mainTarget); target.bindWrite(false); }
    },
    () -> { Minecraft.getInstance().getMainRenderTarget().bindWrite(false); }
);
```

**线框球体顶点生成（球面参数化）：**
```java
// 经线（北极→南极弧线）：每个经度对应一条从 phi=0 到 phi=PI 的弧
for (int i = 0; i < lons; i++) {
    float theta = (float)(2 * PI * i / lons);
    for (int j = 0; j < lats; j++) {
        float phi1 = (float)(PI * j / lats);
        float phi2 = (float)(PI * (j + 1) / lats);
        float x1 = r * sin(phi1) * cos(theta), y1 = r * cos(phi1), z1 = r * sin(phi1) * sin(theta);
        float x2 = r * sin(phi2) * cos(theta), y2 = r * cos(phi2), z2 = r * sin(phi2) * sin(theta);
        consumer.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a).setNormal(x1/r, y1/r, z1/r);
        consumer.addVertex(matrix, x2, y2, z2).setColor(r, g, b, a).setNormal(x2/r, y2/r, z2/r);
    }
}
// 纬线环（纬度水平圆环）
for (int j = 1; j < lats; j++) {
    float phi = (float)(PI * j / lats);
    float ringY = r * cos(phi), ringR = r * sin(phi);
    for (int i = 0; i < lons; i++) {
        float t1 = (float)(2 * PI * i / lons), t2 = (float)(2 * PI * (i + 1) / lons);
        float x1 = ringR * cos(t1), z1 = ringR * sin(t1);
        float x2 = ringR * cos(t2), z2 = ringR * sin(t2);
        normal = normalize(x1, ringY, z1);
        consumer.addVertex(matrix, x1, ringY, z1).setColor(...).setNormal(normal);
        consumer.addVertex(matrix, x2, ringY, z2).setColor(...).setNormal(normal);
    }
}
```

### 现有渲染参考文件索引

| 项目 | 目录 | 关键文件 |
|---|---|---|
| **本模组：空间扭曲球体描边** | `ability/client/` | `SpatialWarpOutlineRenderer.java`, `WireframeRenderTypes.java` |
| ItemGlint Fabric 1.21.1 | `../渲染/ItemGlint-1.21.1-fabric/` | `HeldItemOutlineRenderer.java` 三管线设计 |
| ItemGlint Forge | `../渲染/ItemGlint/` | 同上 + Embeddium 兼容 |
| Alex's Caves | `../渲染/AlexsCaves-main/` | `ACRenderTypes.java`, `ACInternalShaders.java`, `ACPotionEffectLayer.java` |
| Epic Fight | `../渲染/epicfight/` | `EpicFightRenderTypes.java`, `EpicFightParticleRenderTypes.java` |
| Fancy VFX | `../渲染/fancy-vfx-master/` | `ParticlesImpl.java` 粒子链式 API |
| Iris | `../Iris-1.21.1/` | `WorldRenderingPhase.java`, `OuterWrappedRenderType.java` |
