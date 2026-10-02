# AGENTS.md

NeoForge 1.21.1 模组（镜魂），基于 Exposure 相机模组的扩展。详细架构见 `CLAUDE.md`（部分内容已过时，以代码为准）。

## 构建与运行

```bash
./gradlew build          # 产出 build/libs/lensouls-<版本>.jar
./gradlew runClient      # 启动客户端
```

- git bash 下用 `./gradlew`（不要用 `gradlew.bat`）
- 版本号在 `gradle.properties` 的 `mod_version`，构建前先递增
- 常用交付：构建后复制 JAR 到桌面（`C:/Users/volans/Desktop/`）；提交/推送需用户明确要求
- 语言文件（`assets/lensouls/lang/*.json`）只**追加**键，追加后跑 `python -c "import json; json.load(...)"` 验证；曾发现 en_us 遗留重复键（`command.lensouls.toughness.*`）已清理

## 工作区路径地图（全面整理后，2026-08 扫描确认）

> 仓库根 = `E:\volans\Documents\GitHub\lensouls\lensouls-template-1.21.1\`（git）。上级 `E:\volans\Documents\GitHub\lensouls\` 是多项目工作区。
> 注意：PowerShell 访问含 `[` 的目录名必须用 `-LiteralPath`（如 `[灾变]`、`[暮色森林]`），否则被当作通配符导致扫描为空。

### 主力项目内部

| 路径（相对仓库根） | 用途 |
|------|------|
| `src/main/java/com/plumejade/lensouls/` | 模组全部源码：`ability/`（四能力系统）、`boss/`（韧性）、`client/`（渲染/模型）、`component/`、`config/`（Config.java）、`damage/`（元素伤害核心）、`effect/`、`enchantment/`、`entity/`、`event/`、`gui/`、`handler/`、`integration/`（JEI/Jade）、`item/`（次元枪/瓶/镜魂）、`key/`、`mixin/`（ability/client/compat 子包）、`network/`（CustomPacketPayload）、`particle/`、`recipe/`、`sound/`、`timer/`、`util/` |
| `src/main/resources/assets/lensouls/` | 语言 `lang/zh_cn.json`/`en_us.json`、物品模型 `items/`、纹理 `textures/`、音效 `sounds/`、着色器 `shaders/core/` |
| `src/main/resources/data/lensouls/` | 数据包：`entity_weakness/`（实体弱点）、`attacker_element/`（实体活性等级）、`item_element_activity/`（武器活性）、`damage_type_element/`、`recipe/` |
| `src/main/resources/lensouls.mixins.json` | 必装 mixin 配置（refmap 占位必备） |
| `src/main/resources/lensouls.compat.mixins.json` | 可选兼容 mixin（required:false，灾变/传奇怪物/BetterCombat/拍立得） |
| `src/main/templates/META-INF/neoforge.mods.toml` | 模组元数据模板 |
| `docs/` | 设计文档：`元素伤害系统.md`、`渲染体系.md`、`shader.md`、`soul-outline-system.md`、`phantom-system.md`、`glow-fbo-pipeline.md`、`frozen-outline.md`、`build-guide.md`、`curseforge_modrinth.md`、`重构方案-双FBO解耦.md` |
| `outline-system/` | 描边系统独立备份：`java/`（源码）、`resources/shaders/core/`（全部 outline 着色器）、`docs/README.md` |
| `tools/PaletteExtractor.java` | 调色板提取工具 |
| `libs/` | 本地 flatDir：`cataclysm.jar`、`legendary_monsters.jar`、`lionfishapi.jar`（build.gradle `compileOnly "lensouls:..."` 引用） |
| `run/mods/` | 测试环境全部模组 jar（21+ 个）：Exposure 1.9.18、ExposurePolaroid 1.1.5、Curios 9.5.1 为 `implementation`；JEI 19.27.0、Jade 15.10.5 为 `compileOnly`；灾变 3.32、传奇怪物 2.1.20、LionfishAPI 3.1、暮色 4.8.3345、BetterCombat 2.3.2、GeckoLib 4.9.2、Iris 1.8.14、Sodium 0.8.12 等 |
| `run/config/lensouls-common.toml` | 运行时配置（改 Config.java 默认值需删此文件重生成） |
| `com/` `net/`（项目内） | 辅助源码库（不全）：仅渲染相关（mojang blaze3d vertex、原版渲染类），服务端一律用 neoformruntime 反编译 |
| `.claude/memories/` | Claude Code 长期记忆（`soul-item-outline.md` 等） |

### 工作区参考项目（只读查阅）

| 路径（相对工作区根 `E:\volans\Documents\GitHub\lensouls\`） | 用途 |
|------|------|
| `参考项目的源码\Exposure\`（common\src\main\java\io\github\mortuusars） | 相机模组源码，出片/帧流程/StackedPhotographs 查阅 |
| `参考项目的源码\ExposurePolaroid\`（common → io.github.mortuusars） | 拍立得扩展源码 |
| `参考项目的源码\exposure-expanded\`（common → dev.titanite.sparkwave） | Exposure 功能扩展源码 |
| `参考项目的源码\curios\Curios-1.21.1\`（common → top.theillusivec4） | Curios 饰品槽源码 |
| `参考项目的源码\BetterCombat\`（common → net.bettercombat） | Better Combat 攻击模组源码（PlayerAttackHelper、NeoForgeEvents、getRangeForItem） |
| `参考项目的源码\Legendary-Monsters-1.21.1-NeoForge\`（src\main\java\net\miauczel） | 传奇怪物源码（compileOnly 引用） |
| `参考项目的源码\[暮色森林]twilightforest-1.21.1\` | 暮色森林完整源码（10136 文件，gradle 项目 + tf-asm） |
| `参考项目的源码\Malum-Mod-1.21.1\`（com.sammy.malum） | Malum 魔法模组源码 |
| `参考项目的源码\Iris-1.21.1\` | Iris 光影源码（渲染管线兼容研究） |
| `参考项目的源码\Photon-1.21\`（com.lowdragmc） | Photon 渲染 VFX 模组源码 |
| `参考项目的源码\LDLib2-1.21\`（com.lowdragmc） | LowDragonLib2 基础库源码 |
| `参考项目的源码\SubtleEffects-main\`（einstein.subtle_effects） | 粒子特效模组源码 |
| `参考项目的源码\minecraftPlayerAnimator\`（minecraft\common） | Player Animator 库源码（coreLib） |
| `boss源项目\Legendary-Monsters-1.21.1-NeoForge\`（src\main\java\net\miauczel） | 传奇怪物源码（另一份） |
| `boss源项目\lionfish_1.21\`（src\main\java\com\github） | LionfishAPI 源码（灾变前置，compileOnly 引用） |
| `bossJAR解包\[传奇怪物] legendary_monsters-2.1.20 MC 1.21.1\` | 传奇怪物 jar 解包（net/、minecraft/、assets/、data/，`javap -p -c` 反编译用） |
| `bossJAR解包\[灾变] L_Ender's Cataclysm 1.21.1-3.32\`（及 boss源项目、参考项目的源码 下同名目录） | 灾变 jar 解包（com/、assets/、data/，三处内容相同，`javap -p -c` 查桶/上限逻辑） |
| `better-climbing-1.21-\`（Xplat → artemis.better_climbing） | 攀爬模组源码（NeoForge+Fabric+Xplat） |
| `block-place-particles-1.21-v0.4\`（common → games.enchanted） | 方块放置粒子模组源码 |
| `damage_number-master\`（src\main\java\cc\xypp） | 伤害数字模组源码（1.21，`[伤害数字显示]` jar 对应） |
| `com\`（工作区根） | **旧版 lensouls 1.2.0 jar 解包**（.class：ability/boss/damage/entity 等，与 `lensouls-1.2.0.jar` 对应） |
| `data\`（工作区根） | 测试数据包：`cataclysm\tags\damage_type\bypasses_hurt_time.json` |
| `docs\`（工作区根） | 空（预留） |
| `lensouls-1.2.0.jar` | 旧版成品 jar（808KB） |
| `~/.gradle/caches/neoformruntime/` | ModDevGradle 产物：`artifacts/minecraft_1.21.1_client.jar`、`intermediate_results/recompile_*.jar`——反编译原版/NeoForge 首选，`javap -p -c` 即可 |

## 照片注入管线（拍照能力系统）

```
FrameAddedEvent → PhotoInjectionHandler.onFrameAdded
  → pendingAbilities.put(exposureId, ability)   ← 按帧 ID 存能力，与玩家当前能力解耦

出片时：
  PolaroidPrintMixin（拍立得）/ LightroomInjectMixin（Exposure 暗房）
  → pollAbility(exposureId) → 注入照片 CustomData
```

- 能力按 **exposureId**（Frame identifier 字符串）索引，不用玩家 UUID 队列——之前用队列导致切能力后出片错乱
- `PolaroidPrintMixin` 用 `@ModifyArg` 拦截 `photograph` 传参路径（`Inventory.setItem` / `StackedPhotographsItem.addPhotographOnTop` / `Player.drop`），**不要用 `LocalCapture`**（ExposurePolaroid JAR 缺调试信息会静默失败）
- 能力窃取/弱点透镜：实体为空或无注册效果 → 不出能力照片（普通照片）
- 自定义调试日志前缀：`[PhotoInject]`、`[Polaroid]`

## 渲染系统（易踩坑）

- **BOSS 描边**：`BossMaskRenderTypes`（输出到 `BossOutlineManager` 自己的 mask FBO）与 `MaskRenderTypes`（冻结描边，输出到 `FrozenOutlineManager` FBO）**必须分离**——混用会导致第一人称物品描边丢失
- 第一人称捕获在 `ItemInHandRendererMixin.beforeRenderHands`：挥砍中（`getAttackAnim > 0.001`）整体跳过；用 `startCapture` 而非 `tryStartCapture`（去重会拦截手部）
- 韧性条位置平滑参数与 `GravityTetherRenderer.POS_DELAY`（0.06）对齐，受伤时减半

## 伤害限制绕过（灾变/传奇怪物）

- **NeoForge 1.21.1 坑：`LivingDamageEvent.Pre` 的护甲在事件前已结算**（反编译 `actuallyHurt`：先 `setReduction(ARMOR)` 再抛 Pre）。事件里加伤/减伤的基数**必须用 `event.getNewDamage()`**（护甲后），用 `getOriginalDamage()` 做基数会撤销护甲（骷髅箭射玩家从 1 血变 4 血事故）。已修：`DamageHandler` 元素弱点、`PhotoDamageHandler` 摄魂增伤、`PhotoSpecialEffects` 飞行惩罚
- 灾变桶机制（反编译确认）：`damageBucket += amount`，桶超 `DamageCap()` 后伤害变 0.1——`CataclysmDamageBucketMixin` HEAD 无条件清桶
- 单次上限：`Math.min(DamageCap(), amount)`，`@ModifyArg` 在 `ElementBypassHelper.shouldBypassCap()` 时返回 `Float.MAX_VALUE`；破定期间 + 元素弱点武器 → 绕上限（不限活性等级）
- 幻灵（借真身实体，persistentData 标记 `lensouls:phantom`）攻击由 `PhantomDamageHandler` 在 `LivingDamageEvent.Pre` 覆盖为固定穿透伤害（按 `lensouls:phantom_level` 1-5：10/18/21/35/37）

## 配置

- 配置文件 `run/config/lensouls-common.toml`；**NeoForge 不更新已存在配置的默认值**——改了 `Config.java` 默认值需删配置文件重新生成
- 客户端语言文件：`src/main/resources/assets/lensouls/lang/zh_cn.json` / `en_us.json`（json 语法易碎，改完确认无尾逗号/重复键）

## 次元枪升级

- 满级（+20 伤害）不再由击杀自动发放：满 400 击杀（`dgKillTarget`）后需配方合成
- 升级：满击杀枪 + `eternal_starlight:tenacious_vine` + `eternal_starlight:oxidized_golem_steel_ingot`（自定义配方 `lensouls:gun_upgrade`，JSON 带 mod_loaded 条件）
- 降级：满级枪 + 龙首 → `lensouls:gun_downgrade`（移除 Maxed 标志）
- 伤害成长曲线：`getKillProgress`（Hermite S 曲线，慢快慢）；弹药/蓄力用 `getFastSlowProgress`（√t）
- **穿甲**：`dgBaseArmorPen`/`dgMaxArmorPen`（0~80 百分点，按击杀进度插值）算好后随子弹 NBT 传递；`ArmorPenHandler`（`LivingDamageEvent.Pre`）用 `DamageContainer.getReduction(ARMOR)` 加回被护甲削减量的 pen 比例——NeoForge 1.21.1 护甲在 Pre 前已结算，这是唯一可行点

## 1.21.1 合成/容器机制（复制之魂实现验证，反编译确认）

- **`Recipe.getResultItem(CraftingInput)` 在 1.21.1 不存在**——覆写会编译报错"不会覆盖超类型方法"。动态输出只能覆写 `assemble(CraftingInput, HolderLookup)`（`CraftingMenu.slotChangedCraftingGrid` 每次格子变化都调它，工作台预览即动态）
- **"原物品不消耗"实现**：覆写 `Recipe.getRemainingItems(CraftingInput)`（NeoForge 补丁的泛型版，返回按槽 `NonNullList<ItemStack>`，可带 NBT）。`ResultSlot.onTake` 流程：每槽 `removeItem(1)` → `RecipeManager.getRemainingItemsFor` → 非空 remaining 放回原槽（同组件 grow 合并）。这就是"消耗水保留桶"的服务端版本，`CopySoulRecipe` 即范例
- 工作台硬限制：结果槽仅 1 堆、输入全消耗（无 NeoForge hook 可改）——"输出 2 个"或"保留原物"在纯配方层不可能
- **铁砧两个坑**（AnvilMenu 反编译）：`mayPickup` 要求 **cost > 0 且玩家等级 ≥ cost** 才可取出（cost=0 拿不出）；`onTake` 取出时左槽 `setItem(0, EMPTY)` 直接清空——铁砧无法实现"保留左槽物品"

## 新道具机制（次元瓶/复制之魂）

- **次元瓶**：使用次数 = 耐久条（总耐久 = 1 + 已到访维度数）。维度列表**存物品自身**（stack CUSTOM_DATA `lensouls:visited_list`，`recordVisited` 每 20 tick 由 `inventoryTick` 记录，背包任意槽位生效，死亡/换人后不丢失）；旧玩家 persistentData（`lensouls/visited_dimensions`）首次记录时迁移。30s 恢复 1 点：`inventoryTick` 每 20 tick + `lensouls:last_regen` 时间戳（使用/恢复时重置）
- **复制之魂**：BOSS（有 boss bar）死亡掉落 5-20 个；工作台复制配方（见上）
- **BOSS 判定（无注册表可查）**：1.21.1 `MinecraftServer` 无 `getBossOverlay`（仅 `getCustomBossEvents`，那是 /bossbar 命令用的），原版 EnderDragon/Wither 的 bossEvent 是私有字段不暴露。通用检测 = 反射沿类层次找 `BossEvent` 类型字段（`CopySoulDropHandler.hasBossBar`，ServerBossEvent 再查 `isVisible()`），覆盖原版+暮色 BossEventServer+各 mod BOSS

## 音效（易踩坑）

- **自定义音效在服务端 `player.playSound()` 播放不可靠**（曾实测无声）——项目惯例：在**客户端**分支本地播放（`use()` 里 `level.isClientSide` 时判定成功条件后 `player.playSound`），参照 `ToughnessHitSoundPacket`/`ClientPhantomHandler`
- 新增音效：ffmpeg 转 `-c:a libvorbis` 的 ogg 放 `assets/lensouls/sounds/`，`sounds.json` 注册（key 带点如 `heal.use` 可行），`ModSounds` 注册 DeferredHolder

## 跨模组依赖

- Exposure/ExposurePolaroid = implementation（必装）；JEI/Jade = compileOnly（jar 在 `run/mods/`）
- 灾变/传奇怪物相关代码用反射/类名判断（`BossPhantomType.isModLoaded()`），无编译依赖
- 新增元素：改 `ElementDamage` 枚举 + `ModEffects` + `ModItems` + `ModCreativeTabs` + 弱点数据包 + 语言文件（见 CLAUDE.md）


## 2026-09 需求批次（提示词222）落地记录

### 拍摄可见性（Exposure 视锥问题）
- 唯一入口 `util/CameraVisibility`：视锥 + 硬距离上限（`Config.CAMERA_MAX_CAPTURE_DISTANCE`，默认 32）+ **必须命中包围盒中心**的方块遮挡判定（`ClipContext.Block.COLLIDER`）。
- `mixin/EntitiesInFrameMixin` 已重写为 TAIL `@Inject`：只**收窄** Exposure 给出的实体列表。不要退回 `@Redirect`——处理器多写的 `Entity` 参数会被当成宿主方法隐式捕获，曾导致距离/穿墙过滤恒失效。
- `util/AimTargetUtil.isAimedAt` 同样叠加可见性判定（要害打击/断魂不再隔墙锁定）。
- `mixin/compat/FieldGuideExposureMixin` 取消 Field Guide 的 `ExposureCompat.unlockContentInFrame`（1+49 条射线 × 256 格 + 解锁方块 = 卡顿根因）。
- `integration/FieldGuideBridge`：纯反射，只解锁 **mob**（过滤 `MobCategory.MISC`），先 `tryUnlock(...SCAN)`，失败再强制 `unlock(player,id,null,true)`——不依赖对方模组的配置/触发/前置。

### 新相机能力：见微知著 wild_glimpse
- `AbilityType.WILD_GLIMPSE` **必须追加在枚举末尾**（ordinal 参与网络同步）。
- `ability/AbilityBehavior`：能力 → 是否产出能力照片 / 是否需要框内有实体 / 写什么照片数据；新增能力只改这一处。
- `ability/PhotoInjector`：拍立得与暗房两条出片路径共用的一份注入实现。
- 只有 WILD_GLIMPSE 调 `FieldGuideBridge.unlockEntities` 解锁图鉴（需求明确：不依赖原模组配置，拍到即解锁；且只有该能力解锁）。

### 照片饰品
- 新属性 `lensouls:hit_chance`（默认 100%、上限 100%）：`damage/HitChanceHandler` 在 `LivingIncomingDamageEvent`（LOWEST）掷骰，未命中直接 cancel（完全无法造成伤害）。弹幕类照片 -24%、`minecraft:skeleton`/`evoker` -6%、其余 +7%~14%。
- 套装定义按 7 类拆分为 `data/lensouls/photo_set_defs/{attack,defense,survival,mobility,conversion,daynight,balanced}.json`（`photo_set/membership.json` 同步拆分）；`cataclysm:lava_bat` 已从 JEI/能力窃取/套装配置中全部移除。
- 新描述符：`dmg_element:<元素>:<x>`、`dot_mult:<元素>:<x>`、`speed_mult:<x>`（乘区）、`convert_heal:<x>`、`convert_buff:<x>`；转换期临时增益存**根键** `lensouls:convert_dmg_mult` / `lensouls:convert_dmg_until`（不能放 FLAGS，`applyPlan` 会重写它）。
- **Boss 照片弹幕触发面（两条，共用 3 tick 去重）**：① 挥击信号（`PlayerSwingMixin` / `BetterCombatAttackMixin` / `MinecraftSwingMixin` 的 C2S 包）；② **远程伤害命中**（`BossPhotoProjHelper.onRangedHit`，`LivingDamageEvent.Pre` + `RangedAttackHelper.isRanged`，弓箭/弩/三叉戟/次元枪子弹等）。远程路必须跳过 `lensouls:photo_proj` 标记的自家弹幕（否则弹幕命中会自我增殖），并排除自伤；未通过 `HitChanceHandler` 掷骰的"打空"因事件不可达而天然不触发。

### 虚影核心 / 虚影残像（复刻 Enigmatic Legacy 超维容器）
- 死亡结算必须放 `LivingDeathEvent`（此时背包完好）；`LivingDropsEvent` 只能当兜底——`dropEquipment()` 在 drops 之前就已清空背包与 Curios。
- **实体类型必须显式用 `ModEntities.PHANTOM_REMNANT.get()`**：原版 `ItemEntity(Level,x,y,z,ItemStack)` 第一行写死 `this(EntityType.ITEM, level)`，服务端类是 `PhantomRemnantEntity` 而客户端按 `minecraft:item` 造出普通 `ItemEntity` → 走原版渲染器（1×、无自定义渲染、无实体 tick/粒子），实机症状就是"一个发光的普通掉落物"。
- `ItemEntity.setUnlimitedLifetime()` 的字节码就是 `age = -32768`，而 `tick()` 内是 `if (age != -32768) ++age` → **age 永久冻结**；`getSpin()` 与浮动都以 age 为相位源，冻结后表现为"定住一个方向 + 抽搐"。自绘渲染器的动画相位一律用 `tickCount`（`BossPhantomRenderer` 同款做法）。
- 发光走原版描边（`setGlowingTag(true)`）：`LevelRenderer.renderEntity` 在 `shouldEntityAppearGlowing` 成立时把 `MultiBufferSource` 换成 `OutlineBufferSource`，后者对非 outline 的 RenderType 取 `RenderType.outline()` —— 因此**自绘渲染器同样会被描边**。
- 经验**全额**缓存（等级+进度），拾取时先发经验再逐件按原槽位归位；`keepInventory` 开启时整套不生效；容器仅所有者可拾取；虚影核心自身 `ALWAYS_KEEP` 且不被容器收纳。

### 跨模组战斗改动
- 弹射物免疫全局解除：`mixin/compat/ProjectileImmunityMixins`（嵌套静态类 ×7：传奇怪物 Shulker_Mimic / FlamebornGuard / FlamebornWarrior / AnnihilationPursuer + 灾变 Kobolediator / Wadjet / Scylla）。**刻意排除** Ignis / Ignited_Revenant / Royal_Draugr——它们的 `IS_PROJECTILE` 只用于"吸收自身火球回盾"和盾反前置，改掉会让投射物反而触发盾反。
- 定身优化：`BossToughnessManager.tick` 中「生命 < 1」同样解除定身（适配 block_factorys_bosses 的 `maybeCancelDeath` 把血量压到 0.1 并取消死亡，否则阶段/死亡流程一直卡住）。
- 克拉肯船炮：`DamageHandler.isKrakenCannonball` 豁免"非元素弱点攻击 ×0.1"的武器匹配惩罚（伤害类型 `block_factorys_bosses:cannonball_hit` / 实体 `block_factorys_bosses:cannonball` / 物品 `kraken_cannon_item`）。
- N公司2级员工：`Level2StaffBossAnimations.randomMelee/randomSpike(random, exclude)` 用蓄水池抽样排除上一次动作；挥击音效移到攻击判定帧（`meleeSounded`），不再等命中成立。
- 药水玻璃板：`PotionGlassPaneRecipe.effectFrom` 改 public，tooltip 对药水/试剂额外列出"可注入效果名 + 等级 + 时长"。

### 构建/环境
- pwsh 下用 `.\gradlew.bat build`；**客户端开着时 `createMinecraftArtifacts` 会因 merged jar 被锁而失败**（`AccessDeniedException ... is locked by`）。dev 客户端本身就编译源码，无需先构建 jar。
- `build.gradle` 的 `-Dlensouls.leakdiag=true` 与 `-XX:NativeMemoryTracking=summary` 已注释（遗留诊断，会持续刷 `[LeakDiag]` 日志）。


### 补记：弹射物免疫有「两套写法」（实测 legendary_monsters:overgrown_colossus 仍反弹弓箭后发现）

第一版只处理了 `DamageSource.is(DamageTypeTags.IS_PROJECTILE)`，实测遗漏。反编译实际 jar 后确认这两批 boss 还有**第二套**写法：
`source.getDirectEntity() instanceof AbstractArrow / ThrownPotion` 直接 `return false`（传奇怪物的判定还被
`ModConfig.MobConfig.<Boss>projectile` 这一系列配置项包着，`Overgrownprojectile` 就是其中之一——所以**改玩家配置没用，必须 mixin**）。

| 写法 | 判定点 | 解除方式 |
|------|--------|---------|
| 标签判定 | 传奇怪物 4（Shulker_Mimic / Flameborn×2 / AnnihilationPursuer）＋灾变 3（Kobolediator / Wadjet / Scylla） | `ProjectileImmunityHelper.allowProjectile`：`is(IS_PROJECTILE)` 恒 false |
| 类型判定 | 传奇怪物 **19**（Cloud_Golem / Frostbitten_Golem / Lava_eater / Overgrown_colossus / Skeletosaurus / Warped_Fungussus / Withered_Abomination / Ambusher / Ancient_Guardian / Chorusling / Endersent / HauntedGuard / PosessedPaladin / HauntedKnightOld / OldHauntedGuard / MossyGolem / Skeloraptor / FHauntedGuard / DuneSentinel）＋灾变 **5**（Ender_Guardian / The_Harbinger / Ancient_Remnant / Kobolediator / Wadjet） | `ProjectileImmunityHelper.hideProjectileDirectEntity`：把外来投射物的直接实体置空，使 `instanceof` 分支失效 |

**必须保留「自己的弹射物不打自己」的自伤保护**：灾变 `Kobolediator`/`Wadjet` 的毒镖免疫与弓箭判定**共用同一个局部变量**，
`Ender_Guardian` 的自身子弹免疫同理。因此置空逻辑是「`projectile.getOwner() == self` 时原样放行，其余置空」，
无差别置空会让这些 boss 被自己的弹射物打伤。

刻意不动：`Ignis`（吸收自身火球回盾＋盾反前置）、`Ignited_Revenant`/`Royal_Draugr`（盾反前置）、
`AreaEffectCloud` 滞留药水云（不属于「弹射物」）。

`ProjectileImmunityMixins` 现为 31 个嵌套静态类，全部由 `javap -c` 逐点核实；`lensouls.compat.mixins.json` 同步登记。
远端验证方法：进游戏用弓射这些 boss，伤害应正常结算（不再反弹/免伤）。

## 2026-09 需求批次（提示词222b）：次元强化系统

### 一句话结构

次元锤（`lensouls:dimensional_hammer`，16×64 四帧动图）右键 → `ReinforceMenu` + `ReinforceScreen`（全部自绘）
→ 大方框选物（玩家物品栏页）→ 5×10 材料网格点击 → 服务端消耗 1 个材料 + 写入主手 ADD_VALUE 攻击伤害。

### 关键文件

| 文件 | 职责 |
|------|------|
| `reinforce/ReinforceDataLoader.java` | 数据包加载器：`data/lensouls/reinforcement/*.json`（黑名单 + 材料定义），顺序即 GUI 默认排列；`version()` 供客户端搜索索引重建 |
| `reinforce/ReinforceMaterial.java` / `ReinforceModifier.java` | 材料 / 单条属性修饰符（attribute+amount+operation+slot，网络编解码 + tooltip 文本） |
| `reinforce/ReinforceHelper.java` | 已强化列表组件读写、`ATTRIBUTE_MODIFIERS` 写入、可强化判定（两条路径共用） |
| `reinforce/ReinforceInventoryScanner.java` | 三容器统计与消耗：玩家物品栏 → 饰品栏容器（`Capabilities.ItemHandler.ITEM`）→ 超越维度 |
| `reinforce/BeyondDimensionsCompat.java` | 超越维度（`beyonddimensions`）纯反射兼容：网络存储 + 物质压缩球组件 |
| `reinforce/ReinforceSearchContext.java` | 拼音搜索（内嵌 PinIn）+ 子串兜底，后台线程建索引 |
| `reinforce/ReinforceClientPrefs.java` | 收藏 / 两个开关 / 上次搜索，单人存存档目录、多人存游戏目录 |
| `mixin/ReinforceCraftConsumeMixin.java` | 工作台「整堆吞掉原物品」：注入 `ResultSlot.onTake` HEAD 清空被强化物品槽位 |
| `reinforce/pinyin/**` | 内嵌 PinIn 拼音库（MIT，upstream Towdium；由 JustEnoughCharacters/Remorphed 转手），包名改为 `com.plumejade.lensouls.reinforce.pinyin`，字典 `resources/com/plumejade/lensouls/reinforce/pinyin/data.txt`（302KB） |
| `recipe/ReinforceRecipe.java` / `ReinforceRecipes.java` | 工作台配方（`lensouls:reinforce`）：任意物品 + 1 材料 → 完整副本，数量 = 原堆数量 |
| `gui/ReinforceMenu.java` | 菜单：41 个**隐藏**玩家物品栏槽位（只为同步）+ 选中槽位 `ContainerData` + 服务端权威结算 + 数量下发 |
| `gui/ReinforceScreen.java` | 主界面全自绘：大方框 / 搜索框 / 5×10 网格（收藏星、数量、已强化）/ 右上开关 / 滚动条。**版式按参考图逐像素实测还原**（见 `docs/强化界面-参考图规格.md`） |
| `gui/ReinforceSelectScreen.java` | 选物界面：**独立屏幕**（不是主界面里叠一层），展示玩家物品栏 41 格，点选后返回主界面 |
| `network/Reinforce{Select,Apply,Counts}Packet.java` | 选物 C2S / 强化 C2S / 数量 S2C，三个处理器全部 try/catch 兜底 |
| `item/DimensionalHammerItem.java` | 右键 `openMenu`，贴图 `textures/item/dimensional_hammer.png(+.mcmeta)` |
| `docs/强化界面-参考图规格.md` | 参考图规格：模块矩形 / 配色 / 6 条对齐约束 / 以格子为单位的换算公式 + 4 组窗口算例 |

### 版式（参考图 1063×807 实测，单位 = 格子边长 k）

- 唯一缩放单位 `k`；内容块固定 `11.892k × 9.162k`，整体居中：`k = clamp(floor(min((宽-12)/11.892, (高-12)/9.162)), 14, 40)`。
- 大方框 `2.135k × 2.108k` 贴内容块左上；搜索条高 `0.662k`，**底边与方框底边齐平、右缘与网格右缘齐平**；
  网格 `11.216k × 5.919k`，列距 `1.135k`、行距 `1.230k`（竖直比水平更透气）；滚动条宽 `0.324k`，在网格右侧 `0.351k`；
  两个开关行高 `0.378k`、行距 `0.676k`，方块 `0.23k` 在标签右侧、右缘比网格右缘内缩 `0.27k`。
- 配色只有五种：页面 `#1E1E1E`、模块 `#0F0F0F`、滑块 `#A7A7A7`、勾选绿 `#22FF46`、文字 `#FDFEFD`。
  **无边框/无圆角/无渐变**——模块靠"深色块贴在浅底上"区分。

### 必须知道的坑

- **界面用 `AbstractContainerScreen` 但完全自绘**：`RegisterMenuScreensEvent.register` 要求屏幕实现 `MenuAccess<M>`，
  纯 `Screen` 注册不过编译；因此继承 `AbstractContainerScreen` 覆写 `render`（不调 `super.render`，不渲染原版槽位），
  `renderBg` 留空。41 个槽位坐标放在屏幕外（-10000），只承担「玩家背包同步」职责。
- **没有这些槽位，服务端改背包物品不会同步到客户端**——原版只在当前打开的菜单槽位里同步玩家物品栏。
- **`ContainerData` 自动双端同步**（`addDataSlots` + `SimpleContainerData`，`DataSlot.forContainer` 内部记录 prevValue），
  选中槽位用它而不是自定义包；构造器里先 `set(0, -1)` 再 `addDataSlots`，保证两端初值一致。
- **材料数量必须服务端算**：精妙背包内容物客户端只有「已同步过」的才有，超越维度客户端根本没有访问入口。
- **工作台「整堆」必须在 `ResultSlot.onTake` 上做（不能用 `ItemCraftedEvent`）**：`ResultSlot.onTake` 固定每槽 `removeItem(1)`，
  `getRemainingItems` 无法多消耗，所以「整堆吞掉原物品」只能在取走结果那一刻把原槽清空。
  注入点选 `onTake` 的 HEAD（`ReinforceCraftConsumeMixin`）：此处输入容器完整、尚未发生任何消耗。
  **不要退回 `ItemCraftedEvent`**：它由 `checkTakeAchievements` 派发，而外面套着 `if (removeCount > 0)`，
  `removeCount` 只在 `ResultSlot.remove(int)` 里累加 —— 即普通左键取出那条路径；shift 快速移动走
  `CraftingMenu.quickMoveStack → moveItemStackTo`（直接搬运 ItemStack，不经过 `ResultSlot.remove`），
  事件根本不派发，槽位清不掉 → 「8 铁锭强化后得到 7 未强化 + 8 强化过的」。
  `onTake` 在两条路径上都会被调用（快速移动那条传进去的结果堆是空的，但方法照常执行）。
  特殊配方 `getIngredients()` 为空，模组化自动合成器拿不到原料，不会绕过这条逻辑。
- **`Registry.getOptional` 返回的是值不是 Holder**：属性要 `BuiltInRegistries.ATTRIBUTE.getHolder(id)`。
- **物品默认属性修饰符的取法**：`stack.get(DataComponents.ATTRIBUTE_MODIFIERS)` 为空时用
  `stack.getItem().getDefaultAttributeModifiers()`；**不要**用 `stack.forEachModifier`（它会把附魔加成也烤进组件）。
- 拼音字典构建耗时数百毫秒 → 必须后台线程（`ensureLoadedAsync`），主线程先用子串兜底。

## 2026-09 需求批次（提示词：`新建 Microsoft Word 文档.docx`）

### 1. 工作台强化配方「整堆消耗」失效（已修）
- `ItemCraftedEvent` 由 `ResultSlot.checkTakeAchievements` 派发，外面套着 `if (removeCount > 0)`；
  `removeCount` 只在 `ResultSlot.remove(int)` 里累加 = **只有普通左键取出**那条路径会派发事件，
  shift 快速移动走 `moveItemStackTo`（直接搬运 ItemStack）→ 事件不派发 → 原槽只被 `removeItem(1)`
  扣 1 个 → 「8 铁锭强化后得到 7 未强化 + 8 强化过的」。
- 现由 `mixin/ReinforceCraftConsumeMixin` 注入 `ResultSlot.onTake` 处理取走路径
  （HEAD 处输入完整、两条路径都会经过；材料仍由原版循环恰好 -1，因为 `getRemainingItems` 返回全空）。
  已删除原 `ReinforceCraftHandler` 与注册，**不要退回事件方案**。
- **兼容性红线：`assemble` 的输出数量必须是 1**。配方契约是「这一份配方产出什么」，输入由合成台
  按**每槽 1 个**消耗；原版工作台、便携工作台（复用原版 `CraftingMenu`）、自动合成器以及各模组自研
  合成逻辑都遵循该契约。早期版本返回「原堆数量」，那些站台只扣 1 个原料却整份发货 →
  **恶性的大量物品复制**（实测）。
- **定案定价：所有合成台一律「1 个材料 = 1 次强化」**（玩家选定，1.4.90）。一度做过「原版 `ResultSlot`
  上整堆、其它站台按件」，但那样站台之间价格不一致（玩家会问为什么模组工作台更贵），而且整堆要靠我们
  替原版循环清空原槽、容易与别的配方串味 —— 该逻辑已全部移除：`ReinforceCraftConsumeMixin` 现在只做
  「兜底校验 + 取证」两件事，不再清空任何槽位。
- **「1 材料 = 整堆」只由次元锤界面（`ReinforceMenu`）提供**：它直接改槽位、模组自己扣料，不走合成配方。
  这也是同类模组的惯例 —— 附魔灌注台（EnchantingInfuser）解包后只有 `InfuserMenu/InfuserScreen/InfuserBlock`、
  **没有任何配方类**；KubeJS 的输入处理 `ModifyCraftingItemKubeEvent(grid, width, height, item, index)` 同样是
  「按槽 1 个」的语义；vanilla 自己的升级先例也是 1:1（下界合金升级、盔甲纹饰）。⇒ 「消耗整堆」在原版/通用
  配方契约里根本表达不出来，只能放在自研站台/自研界面上。

### 2. 照片套装面板分页（已修）+ 排版调试指令
- l2tabs 的 `BaseTextScreen` 面板固定 `imageWidth × imageHeight = 176 × 166`（构造器写死，
  `leftPos/topPos` 在 `init()` 里居中），正文从 `topPos + 6` 起、行高 10 → 实际可用约 15 行。
  原实现写死 `LINES_PER_PAGE = 14`，而且**首页「通用效果」的行数没算进预算** → 首页必然溢出；
  又没有裁切，文字直接画到面板外。
- 现在：行数由面板几何推导；首页扣除通用效果行数；套装块优先不跨页、**单块超一页时拆页续排**；
  正文区 `enableScissor` 兜底裁切。
- 调试指令 `/lensouls gui photo_set testopen|testfalse`：服务端指令 + S2C `PhotoSetDebugPacket`
  单发执行者 → 面板塞入 5 / 18 / 26 行人造文本（覆盖「跨页」与「超长拆页」），关闭即完全恢复。

### 3. L2 数值面板显示自定义属性（已修）—— 关键是 NeoForge 数据映射
- l2tabs 属性页取 `AttrDispEntry.get(entity)`；默认配置 `attributeSettings = COMMON` 时
  **只显示登记在数据映射 `l2tabs:attribute_entry` 里的属性**（其它两种模式才会追加「有修饰符的属性」）。
- NeoForge 数据映射的文件布局：`data/<数据映射命名空间>/data_maps/<注册表路径>/<数据映射路径>.json`，
  类型 id 由**文件所在命名空间**决定 → 要往 l2tabs 的表里加条目，必须写进
  `data/l2tabs/data_maps/attribute/attribute_entry.json`（本模组 jar 内即此路径）。
- `DataMapLoader` 用 `FileToIdConverter.listMatchingResourceStacks` → **同 id 的多个包文件会被全部读取并合并**，
  所以我们的文件与 l2tabs 自带的共存，不需要覆盖对方（跨模组扩展数据映射的正规做法）。
- 条目字段：`{"intrinsic": 0.0, "order": 11000, "usePercent": true}`；`usePercent` 让面板按
  `val*100` + `attribute.modifier.equals.1` 渲染成百分比；`order` 11000+ 排在原版（1000~10000）之后。
- 属性本身早已通过 `EntityAttributeModificationEvent.add(EntityType.PLAYER, …)` 挂到玩家身上，
  缺的只是这张显示表；属性名语言键 `attribute.name.lensouls.*` 已存在。
- **自定义属性必须 `.setSyncable(true)`**（`Attribute` 的同步开关默认关闭，原版 `Attributes` 里 26 个属性逐个显式开启）。
  不开的结果非常隐蔽：服务端修饰符照常生效（战斗结算在服务端），但 `ClientboundUpdateAttributesPacket` 永远不发这个属性，
  客户端实例停在基值 → L2 数值面板/任何读 `player.getAttributeValue(...)` 的客户端界面都显示 100%（实测踩过）。

### 4. 破韧描边在多子部件 boss 上「半红半白」（已修）
- 暮色九头蛇头 `HydraHead`、娜迦体节 `NagaSegment` 都继承 `TFPart<T> extends PartEntity<T>`
  （NeoForge 标准部件），是**独立渲染的实体**，会各自走一遍 `Entity.isCurrentlyGlowing()/getTeamColor()`；
  而它们不是 `LivingEntity` → 原 mixin 只按本体解析状态 → 部件拿不到破定/霸体状态，颜色落回原版白
  → 「上边三个头白边、下边身子红边」「娜迦头红、体节白」。
- 现 `EntityBossOutlineMixin` 统一改为：`instanceof PartEntity<?>` 时用 `getParent()` 的父实体解析状态与配色
  → 同一只 boss 的所有部件与本体同色同灭（末影龙等所有 `PartEntity` 一并覆盖）。
- 注意：`参考项目的源码/[暮色森林]…` 这类含 `[` 的路径在 PowerShell 下必须用 `-LiteralPath`，
  否则 `Get-ChildItem -Recurse` 被当通配符、扫描结果为空（本次踩过）。

## 2026-09 需求批次（弹幕自增殖 / 韧性与兼容 / 复制之魂封印 / 减速铁板）

### 1. 弹幕伤害不再回头掷弹幕触发（防「左脚踩右脚升天」）

- 新 `util/PhotoProjMarker`（标记的单一事实来源）：`lensouls:photo_proj`（所有照片弹幕）
  + `lensouls:photo_percent`（%maxHP 弹幕）。`mark/markAndSpawn/markPercentAndSpawn` 全部走它。
- `BossPhotoProjHelper.onRangedHit` 改成**全链路**判定：`isBarrageDamage(DamageSource)` 同时看
  直接实体与间接实体（间接实体是 `Player` 时排除，否则玩家自己的弓箭会被误判）。
  **只查直接实体会漏**「爆炸/射线/召唤物代打」那类写法。
- `trigger()` 三层闸：① `ThreadLocal` 重入保护（同刻命中/引爆不再递归触发）；
  ② 原有 3 tick 去重；③ **高频触发熔断**：每玩家 10 tick 内最多 24 次触发额度，
  超限直接忽略并最多每 30 秒 WARN 一次（`consumeRollBudget`）。这是给「某个漏标记的第三方弹幕
  混进来形成回环」留的保险，不是正常玩法的手感限制（10 张 boss 照片狂点约 66 次/秒的**掷骰**里
  实际触发远低于额度）。
- **非 `markAndSpawn` 生成的第三方弹幕必须补标记**：`spawnGeburahRay`（fdbosses 法阵射线由对方
  `summon` 自行入世）现在用新的 `markOnly(Object)` 补 `photo_proj`——它伤害归属玩家且原来不带标记，
  是最可能形成回环的一处。

### 2. 照片弹幕自伤保护（骷髅箭被反弹打回玩家）

- 新 `handler/PhotoProjSafetyHandler`：`LivingIncomingDamageEvent`（HIGHEST，**无敌帧/护甲结算之前、可取消**）
  里判定 `PhotoProjMarker.isSelfHit(...)` 或「标记弹幕命中任意玩家」→ `setCanceled(true)`。
  用 `LivingIncomingDamageEvent` 而不是 `LivingDamageEvent.Pre`：后者只能把伤害置 0，击退与受击动画照旧。
- 为什么不用 `LivingDamageEvent`：原版「自己的箭打自己」是合法行为（盾反），我们只掐**自己标记过的弹幕**，
  普通弓箭/别的玩家射来的箭/灾变 boss 的弹幕一律不受影响。

### 3. gytrinket（`com.gytrinket.gytrinket`）点射/连击兼容

- 现象：该模组的 `ProjectileBurstManager.onEntityJoinLevel`（`EntityJoinLevelEvent`, HIGH）会把
  **任何 owner 是 ServerPlayer 的 `Projectile`** 快照，并按玩家 `combo` 属性（= 已装备点射模块数 × 2）
  每刻复制一份 → 我们的照片弹幕（三连箭/水波/火球…）被成倍复制。
- 修法：`mixin/compat/GytrinketProjectileBurstMixin`，`@Inject(method = "onEntityJoinLevel", at = HEAD,
  cancellable, require = 0)`，命中 `PhotoProjMarker.isBarrage` 即 `ci.cancel()`。用
  `@Mixin(targets = "…")` 字符串目标（该模组不在编译依赖里，本机 `run/mods` 也没有它）+ `remap = false`；
  配置 `required=false`，模组缺席时整条被跳过。**实测可编译**（`targets=` 对缺失类不会让 AP 失败）。
- 刻意**不**用「给它打 `ProjectileBurstCopy` 标记」的取巧办法：那个标记会让对方在命中时清目标无敌帧、
  标记无击退，并在首次碰撞后 1 刻把弹幕 `discard()` —— 等于换一种方式改我们的弹幕行为。
- 对方还有一个 `ProjectileDamageHandler.onEntityJoinLevel` 会给玩家 owner 的 `AbstractArrow` 加成伤害
  （无复制体标记判断）。本次**只掐复制（连击），不动伤害增幅**（用户诉求是「不要触发连击」）。

### 4. 韧性系统：高频触发保护 + 空指针加固

- `BossToughnessManager.hit()` 增加**同实体同 tick 去重**（`lastHitTick`，>64 条时按 200 tick 清理）：
  拍照/要害打击/内部调用三路同刻并发时会重复发粒子音效包并重复累加。
- 空指针的**结构性来源**是 `BuiltInRegistries.ENTITY_TYPE.getKey(type).toString()`：未注册类型
  （第三方临时/伪造实体）会返回 `null`。已加固：`BossToughnessAttributes.entityId()`、
  `ToughnessDamageHandler.isBoss`、`computeRequiredHits` 全部判空；`StunPauseHelper` 判 `entity/level == null`。
- `ToughnessDamageHandler` 的 Pre/Post 整体 `try/catch`（100 tick 节流报错）：韧性检查绝不能把异常抛回
  伤害结算链——高频触发下一次抛错会把整段实体刻带崩。
- 顺带的性能修复：`broadcastAll()` 现在「内容签名未变且距上次广播 < 20 tick」就跳过，
  且**空表也会广播一次**（客户端据此清掉已消失 boss 的残留韧性条；原来是直接 return）。

### 5. 巨兽 / 遗魂弹幕重做（**关键教训：源码树 ≠ 运行时 jar**）

> `参考项目的源码\灾变\gradle.properties` 是 **3.33**，而 `libs/cataclysm.jar` 与 `run/mods/[灾变]…`
> 都是 **3.32**（两者 SHA256 相同）。`Ancient_Desert_Stele_Entity` 在两个版本里是**两套实现**：
> 3.33 `extends Entity`（有 `setImpactDamage/setImpactRadius` 落地 AoE），3.32 `extends Projectile`
> （**没有** AoE，`onHitBlock` 空实现，伤害只来自每 tick 的移动射线命中实体）。
> **按 3.33 源码改会写出一堆在实机上不存在的方法**，所以改灾变交互前必须
> `javap -p -cp libs/cataclysm.jar <类>` 核对真实签名。

- `Cm_Falling_Block_Entity`（`entity/effect/`）是**零伤害的纯视觉实体**（只有 `tick/move`，
  没有 `hurt/explode/setOwner/setDamage`；`extends Entity` 连 `setOwner` 都没有）。
  灾变本体的伤害在**调用方**：`Netherite_Monstrosity_Entity.spawnBlocks` 里另有一段
  1×1 竖直 AABB + `mobAttack`。旧照片版只搬了装饰物 → 「看着砸下来却一点伤害没有」，这也是
  「召唤掉落方块很鸡肋」的字面根因。
- 现 `spawnFallingBlocks`（1.4.93 重做）：从「3 块装饰落石」升级为**本体招牌技能「地震践踏」**——
  照抄 `EarthQuake` 口径，以最近敌人为中心 4 格半径内全部结算
  `面板 + min(面板, 目标最大生命 × 0.08)`（0.08 = `CMCommonConfig.NetheriteMonstrosity.SmashHpdamage`）、
  朝外上抛（本体 `launch(entity, 2.0D, 0.6D)`），伤害走 `playerAttack`；
  掀地落石改成**纯视觉**（5 块撒在圈内，不再单独结算，避免同一目标被算两次）；
  落点也从「玩家视线前 3 格」改成敌人脚下。本体还有破盾 120t 与狂暴点燃 6s，照片版刻意不做
  （我们的弹幕不伤玩家，破盾无意义）。
- 现 `spawnDesertStele`（1.4.93 重做）：从「视线前 3 面」升级为**岩碑风暴**——以敌人为中心画
  半径 1.2~2.0 的环、5 面碑 warmup 1/3/5/7/9 依次砸下（本体是 144 个风车阵的缩水版）。
  **必须高空生成**：3.32 里石碑贴地生成时，warmup 结束后第一个移动 tick 的射线（起点=脚底）
  就打中脚下方块 → `onHitBlock`（空）→ `onHit()` 放音效粒子后 `discard()` ⇒ 伤害恒为 0
  ——这是与韧性减伤、命中率完全无关的独立根因（实测「打无韧性目标也不掉血」即此）。
- 注意：两只 boss 在 `run/config` 里 `toughDamageReduction=0.8` 且各需 9 次拍照破韧，
  **破韧前一切弹幕伤害只剩 20%**——排查「弹幕没伤害」时要先把这条独立原因排除。

### 6. 斯库拉照片击退减弱

- `Wave_Entity.attackEntities(strength, x, z)` 的 `strength` 只用于击退（`adjustedStrength`），
  本体固定传 `1.5`，水波存活 60 tick、每 tick 结算一次 → 3 道水波是持续推挤。
- `mixin/compat/ScyllaWaveKnockbackMixin`：`@ModifyVariable(argsOnly, index = 1)` 把标记过的
  照片水波 strength × 0.35（约 −65% 击退），伤害/湿润完全不动。

### 7. 复制之魂封印扩展到终端与精妙背包

- 新 `handler/CopySoulSealHandler`：`CopySoulItem.inventoryTick` 改为读**缓存的**佩戴标志
  （`shouldSeal`，10 tick 刷新）——原实现每个复制之魂**每 tick** 查 3 次 Curios（`findFirstCurio` 会遍历全部饰品槽）。
  周期（20 tick，`tickCount % 20`）扫描：物品栏 41 格 → 物品栏/饰品栏里容器类物品的内容物 → 超越维度。
  只在「戴着禁复制羽毛」或「上次确实在容器里封过东西」时才扫，平时一个判断就返回。
- `CurioChangeEvent` 触发一次即时对齐（摘下/戴上羽毛最多晚 1 tick），不用等周期档。
- **精妙背包**（`Capabilities.ItemHandler.ITEM` 注册在 Item 上，与所在槽位无关）：
  内容物在 `BackpackStorage extends SavedData`（按 `sophisticatedcore:storage_uuid` 键），
  `serializeNBT` 写的是缓存的槽位 NBT ⇒ **必须 `setStackInSlot` 回写**，就地改实例重进世界就丢；
  回写会自动 `saveInventory → setDirty`，不需要自己 markDirty。
  边界：能力在 `getContentsUuid()` 为空时返回 0 槽 `EmptyItemHandler`（只读不建），
  兜底走反射 `BackpackWrapper.fromStack(stack).getInventoryHandler()`（会惰性补 UUID）。
  取能力必须用槽位里的**活** ItemStack：传副本会让对方新建一份 wrapper（其缓存是 ItemStack 身份语义），
  一个背包出现两份内存态会互相脏写。
- **超越维度**：`getStorage()` 是 `unmodifiableList(AbstractList)` 的**只读活视图**
  （每次 `get(i)` 现造 `KeyAmount` record），底层 `slotIndex` 删除时**换尾** ⇒
  **先快照、再按键写回**（`AbstractUnorderedStackHandler.setAmountByKey`）。
  **绝不能缓存升序下标再逐个 `setStackDirectly`**：同网络 ≥2 条待改时第二笔会写到别的条目上。
  `ItemStackKey.getReadOnlyStack()` 是 key 的共享缓存（数量强制为 1）→ 必须 `copy()` → 改组件 →
  `new ItemStackKey(copy)`（改组件 = 换 key，数量在 `KeyAmount.amount()` 上，原样保留）。
  封印方向可以对所有网络做；**解封方向必须先查该网络全部在线成员**——只要还有成员戴着禁复制羽毛
  就不能解封（终端是共享的）。句柄缺失时封印降级为「只处理随身压缩球」，不影响统计/消耗。

### 8. 减速铁板（`lensouls:slow_iron_plate`）

- 4 铁锭无序合成 1 块；`handler/SlowIronPlateHandler` 每 10 tick 扫**玩家物品栏 41 格**计数，
  每块 `ADD_MULTIPLIED_TOTAL -10%` 移速，上限 −90%（10 块封顶，避免 −100% 把自己锁死到走不动）。
- 只扫物品栏：饰品栏、精妙背包内容物、超越维度终端**一律不计**（收进容器即无副作用）。
- 性能：目标是「修饰符现值与目标值不一致才写」——属性修饰符写入会走
  `ClientboundUpdateAttributesPacket`，每 tick 重写就是网络风暴；且**不用「上次数量」缓存**，
  而是直接读回属性实例上的修饰符（死亡重生会重建属性实例，缓存会残留脏值导致减速丢失）。

### 9. 贴图

- `soul_whistle.png` / `mage_brooch.png` 用桌面 `杂项\贴图\灵魂骨哨.png` / `法师胸针.png` 覆盖；
  新物品 `slow_iron_plate.png` 来自同目录 `减速铁板.png`（均 16×16）。

### 10. 夺魂索命（`soul_sever`）削减下限 50%

- `ability/handler/SoulSeverHandler`：原来是无下限的「当前生命 ×10%~20%」削减，能一路磨到死。
  现加 `SEVER_FLOOR_RATIO = 0.5f`：
  ① **目标生命 ≤ 最大生命 ×50% 时判定直接失败**（不掷骰、不放雷霆音效/冲击波，只走失败音效 + 0.5s 冷却）；
  ② 成功那一刀夹在 `max(下限, 当前 - 削减量)`，**最多削到半血，绝不穿透**。
  语义变成「控场：把目标打到半血」，不会自己打死人；能力详情文本（zh_cn/en_us）同步补了两行说明。

### 12. 湮灭构造体 / 先驱者弹幕重做（`1.4.94`）——两条都是「用错了实体 / 传错了参数」

- **湮灭构造体：射线很短**。`AnnihilationBeamEntity` 的**长度就是构造参数最后一个 `r`**（同步字段
  `B_RADIUS`），而 `calculateEndPos()` 里写死
  `float r = caster instanceof Player ? B_RADIUS / 2 : B_RADIUS;`
  （`javap -c` 在实机 jar 2.1.20 上确认：`instanceof Player` → `B_RADIUS` → `fdiv`）。
  旧版传 `r = 3.0f` ⇒ 玩家 caster 实际只有 **1.5 格**射线，所以「像没打出去」。
  官方玩家武器 `AtomSplitterItem` 是构造后 `setRadius(30)`（玩家 → 15 格），boss 本体传 5~30。
  现传 `ANNIHILATION_BEAM_REACH = 60` → **30 格**，并在构造后补一次 `setRadius`（构造器里
  `calculateEndPos()` 跑在 `setRadius` 之前）。伤害口径照抄本体
  `getDamage() + maxHealth × (getHpDamage() × 0.01)`（`Hpdamage` 是百分数）。
- **先驱者：用错了实体**。旧版是 `Laser_Beam_Entity`——那是先驱者**普通远程攻击**的小型激光弹
  （`extends Projectile` + `accelerationPower`/`getInertia()`，本质是快弹丸），怎么摆都不显眼。
  本体的招牌是 `Death_Laser_Beam_Entity`（`The_Harbinger_Entity` 死亡激光状态里
  `new Death_Laser_Beam_Entity(..., 60, DeathLaserdamage, DeathLaserHpdamage)`）：
  `RADIUS = 30` **常量**（固定 30 格贯穿）、`tickCount > 20` 才开始结算（1 秒蓄力预警）、
  命中盒 `inflate(1,1,1)`、伤害 `damage + min(damage, maxHealth × Hpdamage × 0.01)`、每 tick 一次
  （由 `photo_percent` 的 10tick 节流兜住）。
  **安全点**：本体 powered 时会 `setFire(true)`，开了会在命中地面处**生火**，照片版一律不发火。
  3.32 jar 签名已 `javap` 核实：`(EntityType, Level, LivingEntity, double, double, double, float, float, int, float, float)`。
- **改完形态必须同步改玩家可见描述**：照片效果的文案在 `integration/PhotographEffectRegistry.add(bossId, …)`
  里**硬编码中文**（不在 lang 文件里，也不是数据包）。本次跟着改版的 5 条：
  巨兽（落石 → 地震践踏）、先驱者（凋零激光束 → 死亡激光，并删掉「点燃 5 秒」）、
  遗魂（脚下岩碑阵 → 头顶岩碑风暴）、湮灭构造体（补「30 格贯穿 / 1.5 秒 / 每段判定」）、
  斯库拉（补「击退已大幅减弱」）。**以后再调弹幕形态，记得一起改这里**。

### 13. 交付

- 版本 `1.4.91`（本批次主体）→ `1.4.92`（夺魂索命下限）→ `1.4.93`（巨兽「地震践踏」/ 遗魂「岩碑风暴」）
  → `1.4.94`（湮灭构造体长射线 / 先驱者死亡激光）→ `1.4.95`（描述跟进）→ `1.4.96`（三条实机复核修正）：
  `.\gradlew.bat build` 通过（只剩既有的 JEI 弃用 API 警告）；
  jar 复制到 `C:/Users/volans/Desktop/lensouls-1.4.96.jar`（5,877,677 字节），
  MD5 `6C7112CC0B059057046943A6411287F8`。**1.4.91 ~ 1.4.95 作废，别分发**。
- 排查口径教训：**别拿「韧性减伤 80%」去解释弹幕没伤害**——用户实测的是**无韧性目标**，
  弹幕照样不掉血；灾变这两个弹幕的零伤害在源码层面就是结构性事实（见第 5 节）。
- 同类教训（第 12 节）：**弹幕「不明显/很短」先怀疑参数与实体选型**，而不是数值。
  两个 boss 的「射线长度」分别在 `B_RADIUS`（且玩家 caster 会被 /2）与「选错实体」上。

### 14. 本批次（`1.4.97`）：荒厄遗咒真伤 / gytrinket 不干扰拍照 / 拍脚底也能捕捉

- **羽·荒厄遗咒：每次伤害的 30% 视为真伤**（`1.4.98` 按需求订正口径）
  - **不是概率触发**：每一次伤害都按比例拆分 —— `最终 = D×0.30 + (D−D×0.30)×(1 − 韧性减伤)`，
    即「三成真伤不吃韧性减伤、七成照常结算」。1.4.97 我先做成了「30% 概率整段免减伤」，
    按用户澄清已改（顺带删掉了掷骰与那份 (攻击者,目标,tick) 缓存，逻辑更简单）。
  - 实现：`FeatherHardmanHandler.TRUE_DAMAGE_SHARE = 0.30f` +
    `trueDamageShare(target, source)`（佩戴者造成、非自伤时返回该比例，否则 0）；
    `ToughnessDamageHandler.onLivingDamagePre`（`EventPriority.HIGHEST`，最先跑）里
    `truePart = D×share` 原样保留、其余交给 `applyDamageReduction`。
    护甲与其它模组的减伤不受影响（它们在本事件之前已结算）。
  - 文案 `item.lensouls.feather_hardman.desc5`（zh/en）与类 javadoc 同步更新。
- **拍照/瞄准无视第三方辅助作战单位（gytrinket 无人机/蜂群/僚机）**（`1.4.98`）
  - 需求：这些单位跟着玩家满天飞，会抢走「照片主体」与弹幕的「最近敌人」，
    要忽略它们**并继续选取它后面的生物**。
  - 关键事实：它们在对方模组里的公共父类是
    `com.gytrinket.gytrinket.core.entity.construct.AbstractConstructEntity extends PathfinderMob`
    ——**是 LivingEntity**，所以不能靠「非生物」筛掉，必须显式按类忽略。
  - 实现：`util/PhotoTargetFilter.isIgnored(entity)`——按**包名前缀**
    `com.gytrinket.gytrinket.core.entity.construct.` 判定（drone / swarm / wingman 三个子包都在其下，
    以后新增第四种 construct 自动覆盖），比较结果按 `Class` 缓存、`ModList` 查询也缓存，模组没装直接短路。
    接入三处目标选取：`CameraVisibility.isVisible`（拍照画面列表 = `EntitiesInFrameMixin` 的收口）、
    `AimTargetUtil.isAimedAt`（准星/要害打击）、`BossPhotoProjHelper.findNearestNonPlayer`（弹幕选敌）。
    本类只回答"是不是忽略"、**不做选取**，所以"继续选下一个"由调用方的候选遍历自然完成。
- **gytrinket 不再干扰拍照**（对着对方源码逐点核实；**两处机制我先前写错过，已按实情订正**）
  - 门槛谓词：`AttackModeClientUtil.hasActiveItem(...)` 查的是**光点核心存储（`PlayerStore`，27 槽）
    + Curios 饰品栏**——**不含原版物品栏，也与主手拿什么无关**。所以「手持相机时前置条件天然不成立」
    **不成立**：装了充能模块的玩家手持相机照样被卷进去。
  - 具体副作用（仅装了模块时）：`MouseHandlerMixin` 在**任何一次右键按下**都调
    `startChargingFromRightButton()` → 服务端 `ChargedAttackManager.startItemUseCharge` 因相机不在它的
    武器白名单里而施加**临时 -3.0 攻速**修饰符并每 tick 抛 `ChargedAttackEvent`（Exposure 相机正是
    「长按右键开取景器」）；左键侧 `startCharging()` + 松开的 `releaseAttack()` 会做矩形光束索敌后
    `gameMode.attack(...)`——**「对着空气点一下切能力」会额外打出一记真实近战**。
  - 修法：`GytrinketCameraChargeMixin`（compat 配置的 **client** 数组）对两个入口
    `startCharging` / `startChargingFromRightButton` 做 HEAD cancel——**主手或副手拿着相机时不进入充能**。
    它同时消掉上面三件事（幽灵近战 / -3.0 攻速与事件 / `continueAttack` 被 cancel）。
    `targets` 字符串 + `remap=false` + `require=0`；服务端无需改动（充能启动全依赖客户端包）。
  - **已知残留（刻意保留）**：对方的 `startAttack` 里 `cir.setReturnValue(false)` 在 `startCharging()`
    **之外**，所以相机在手点左键**没有原版挥击动作/动画**。我们的照片弹幕信号不受影响（见下条）。
    要去掉残留，正确做法是覆写它的门槛谓词 `hasChargedAttackItem()`/`hasAssaultItem()`（相机在手返回 false），
    **不要** mixin 它的 mixin 类、不要 `@Redirect setReturnValue`、不要覆写 `isCharging()`（会状态失配）。
  - **Mixin 顺序备忘（重要，别再想当然）**：`CallbackInfoReturnable.setReturnValue` 内部会 `cancel()`，
    同注入点上**更靠后的回调会被跳过**——「同点多个 `@Inject` 一定都会跑」是**错的**。
    我们的 `MinecraftSwingMixin`（lensouls，无 priority = 1000）之所以没被 gytrinket（999）掐掉，
    是因为**优先级数字小者先应用、先插入者埋在下面**，而 HEAD 取的是「活指令表首指令」，
    ⇒ 在 HEAD 处**后应用（数字大）者先执行**：`[lensouls][gytrinket+cancel][原版体]`。
    顺带：对方源码里「priority 999 早于 Better Combat 默认 1000」这句注释在 HEAD 处也是反的
    （Better Combat 源码里根本没有 `startAttack`/`continueAttack` 注入，无实际后果）。
  - 官方开关：**没有**能让它忽略相机的配置/数据包/标签。`charged_attack.itemUseChargeWhitelist` 只能去掉
    -3.0 攻速副作用（且因 `Config.onLoad` 的 `initialized` 守卫，`/reload` 不重载、要重启）；
    `gytrinket_ui_overrides.json` 是全局废掉模块（可 `/reload`，但是"一刀切"）。
- **拍生物脚底也能捕捉**（`util/CameraVisibility`）
  - 原实现：视锥按**眼睛**采样，遮挡按**包围盒中心**单射线——上半身被方块挡住时中心不通 ⇒ 拍不到。
  - 现改为**多身体采样点**：`BODY_SAMPLE_FRACTIONS = {0.08, 0.28, 0.5, 0.72}`（相对包围盒高度，
    **脚底优先**）+ 眼睛兜底；**任一采样点「在视锥内且视线通畅」即算拍到**。
    整只被墙挡住时所有采样点都不通，依然拍不到（不会退化成透墙）；命中即返回，平均射线数仍接近 1。
  - `hasClearSight`（只认中心）**保留给 `AimTargetUtil`**（要害打击/断魂的瞄准）——那里刻意要求中心可见，
    避免隔墙锁头；拍照捕捉走新的 `isVisible` / `hasClearSightTo`。改这两个口径时别互相覆盖。

### 15. 交付

- 版本 `1.4.91` → `1.4.96`（见第 13 节）→ `1.4.97` → `1.4.98`
  （荒厄遗咒真伤改成**每次伤害的 30%**、拍照无视 gytrinket 无人机/蜂群/僚机、
  gytrinket 充能不干扰相机、拍脚底也能捕捉）：
  `.\gradlew.bat build` 通过（只剩既有的 JEI 弃用 API 警告）；
  jar 复制到 `C:/Users/volans/Desktop/lensouls-1.4.98.jar`（5,881,492 字节），
  MD5 `7A4A8B09370D8EFBED2FBE571844B6FC`。**1.4.91 ~ 1.4.97 作废，别分发**。
- 已推送：`bc6c3d9`（虚影核心饰品槽解析）、`b2627a4`（弹幕/韧性/封印/减速铁板批次）→ `origin/main`。
  本节（1.4.97 / 1.4.98）**尚未提交**，等指示。

## 2026-09 需求批次（`1.4.99`）：弱点透镜革新（右键装机 / 耐久 100 / 记录弱点）

### 16. 需求三条

1. **双持右键放入/更换**：弱点透镜照片与**任意附魔摄魂术的物品**（**排除两个相机**）双持时，
   照片在副手或主手都行，右键即把照片装进另一手物品的「剑槽」（此前只能按键开 GUI）。
   已有照片则**更换**，旧照片退回背包。
2. **耐久 100**：照片可生效 100 次，归零即销毁。
3. **记录弱点**：拍照时识别主体的弱点元素写进照片；装到武器上时该武器**视为该元素的 2 级武器活性**
   （武器自身 `item_element_activity` 等级更高则按自身的）。

### 17. 实现落点

| 位置 | 职责 |
|------|------|
| `util/WeaknessLensPhoto` | 全部口径与常量：照片/武器 NBT 键、耐久、弱点识别、装机读写、`inspect/inspectActive`、活性取 max |
| `handler/WeaknessLensHandler` | 三个事件：`RightClickItem`（双持放入/更换）、`LivingDamageEvent.Pre`(LOWEST，耐久扣减与销毁)、`ItemTooltipEvent` |
| `mixin/compat/PhotographInstallMixin` | 客户端：装机右键时**不要打开 Exposure 照片查看界面** |
| `ability/AbilityBehavior.writePhotoData` | `WEAKNESS_LENS` 分支：写 `lensouls:weakness_element` + `lensouls:durability=100` |
| `damage/DamageHandler` / `damage/ElementBypassHelper` | 武器活性取 max(自身, 照片 2 级)；**武器匹配 ×0.1 惩罚**也要认这条活性 |
| `handler/ElementActivityTooltipHandler` | 武器活性 tooltip 走 `WeaknessLensPhoto.getActivityLevel`（随照片实时变化） |
| `gui/PhotoGuiMenu` / `event/EnchantmentRemovalListener` | 同步新键 `SoulPhotoWeakness`；GUI 装机时补齐并写回照片的元素 tag |
| `client/WeaknessLensDurabilityDecorator`（+ `LenSoulsClient.registerItemDecorations`） | 物品栏里的耐久条（NeoForge `RegisterItemDecorationsEvent`），满耐久不画 |

- NBT：照片 `lensouls:weakness_element` / `lensouls:durability`；武器 `SoulPhotoStack`（**数量固定 1**）
  / `SoulPhotoEntityId`（旧键沿用）/ `SoulPhotoWeakness`（新）。
- 旧照片/旧存档缺键时的兜底：耐久缺省 = 满；记录元素缺省 = 按 `SoulPhotoEntityId` 到
  `entity_weakness` 反查，并在下次装机时补写回照片。
- 「生效」口径（扣 1 点耐久）= 该次伤害里照片确实参与了：**对照片主体增伤**，或**它赋予的元素活性命中了目标弱点**。
  打空（命中率没过 / 伤害 ≤ 0）与没参与的一刀都不扣；归零 → 清空武器上的照片三项键 + 动作栏提示 + `ITEM_BREAK`。

### 18. 必须记住的坑（本次逐字节确认）

- **`PhotographItem.use` 在客户端直接开照片查看界面**（`ClientGUI.openPhotographsScreenFromItem`）并返回
  `success`。装机右键必然踩到 ⇒ 必须有 `PhotographInstallMixin` 掐掉，否则每次装机都先弹一张照片界面
  （服务端那侧已被事件取消，客户端只在 `use` 里）。
- 该 mixin 必须返回 **`success` 而不是 `pass`**：原版 `Minecraft.startUseItem` 是
  `for (InteractionHand hand : InteractionHand.values())` 的**双手循环**，
  结果 `consumesAction()` 才 `return`，否则继续把这次右键交给**另一只手**——`pass` 会顺带触发目标物品
  自己的右键效果（`compiledWithNeoForge_*.jar` 的 `Minecraft.startUseItem` offset 442~502 已核对）。
- `MultiPlayerGameMode.useItem` 的预测 lambda（`lambda$useItem$5`）顺序是
  `new ServerboundUseItemPacket` → `CommonHooks.onItemRightClick`（**客户端也会发 `RightClickItem`！**）
  → `stack.use(...)` → 返回 packet 交给 `startPrediction` 发送。所以服务端处理器必须 `isClientSide` 守卫，
  客户端那条路只能靠 mixin。
- 同一次右键会在主手、副手各派发一次 `RightClickItem` ⇒ 处理器**只认「照片那一手」**
  （`player.getItemInHand(event.getHand())` 是弱点透镜照片），另一手是目标；再加同 tick 去重兜底。
- 装机时照片**必须 `copyWithCount(1)` 存进武器**：照片可堆叠，若连数量一起存，武器上那份会变成一整堆。
- 「识别弱点」只认 `entity_weakness` 里**显式配置**的元素：`DataPackLoader.getWeakness` 对未配置元素给
  0.1 兜底，用它识别等于「任何生物都有弱点」，那不是识别。排除 `PROJECTILE`（其活性来自投射物，不是武器）。
  倍率最高者胜；**平手按 `ElementDamage.values()` 声明顺序取前者**——`getAllWeaknesses` 返回的 Map
  迭代顺序不确定，不能靠 `Map.Entry` 遍历决定。
- 照片提供的活性**必须与增伤同口径要求摄魂术**（`inspectActive`）：装机时需要附魔，但砂轮祛魔后武器 NBT
  还在；不查附魔会留下「祛了魔照样吃 2 级活性」的口子（增伤侧 `PhotoDamageHandler` 本来就查附魔）。
- 热路径零分配：`inspect` 走 `CustomData.contains("SoulPhotoStack")` 先判存在，再 `copyTag()`；
  否则「拿普通武器（甚至带弹药/强化 NBT 的武器）打人」会在每个伤害事件复制整份 NBT。
- `DamageHandler` 的**武器匹配 ×0.1 惩罚**（目标有显式弱点但武器活性不匹配 → 最终伤害砍到 10%）
  必须把照片活性算进去，否则装照片反而被这条惩罚吃掉。
- 键名一律走 `WeaknessLensPhoto` 常量（`SoulPhotoStack` / `SoulPhotoEntityId` / `SoulPhotoWeakness`），
  避免再次出现「改了一处、另一处还在读旧字面量」。
- **耐久条不要用原版 `DataComponents.DAMAGE`**：原版对带耐久组件的物品强制 `stackSize = 1`，
  照片会立刻变成不可堆叠，而照片堆叠是既有玩法（`StackedPhotographsItem`、时空回溯按堆查找都依赖它）。
  正确做法是自绘：耐久存 `lensouls:durability`，条由 `client/WeaknessLensDurabilityDecorator`
  （NeoForge `RegisterItemDecorationsEvent`，只在 `LenSoulsClient` 的 mod 总线注册，客户端专属）绘制，
  口径照抄 `ItemRenderer.renderBar`（13×2 像素、+2/+13、底色纯黑、前景 HSV 绿→红、**满耐久不画**）。
  该事件在 `RegisterClientReloadListenersEvent`/`RegisterRenderers` 之后由 `ItemDecoratorHandler.init()` 触发，
  所以 `Exposure.Items.PHOTOGRAPH.get()` 此时已可用（1.9.18 实机 jar 已 `javap` 核对字段存在）。

### 19. 交付

- 版本 `1.4.99`：`.\gradlew.bat build` 通过（只剩既有的 JEI 弃用 API 警告）；
  jar 复制到 `C:/Users/volans/Desktop/lensouls-1.4.99.jar`（5,897,454 字节），
  MD5 `29582365B518E7A27E77F5FB6E3A7E55`。**1.4.91 ~ 1.4.98 作废，别分发**。
- 描述跟进：`ability.lensouls.weakness_lens.detail`（zh/en）重写；新增 9 个 lang 键
  （照片耐久 / 记录弱点 / 装机提示 / 武器已装照片 / 照片赋予活性 / 三条动作栏消息）。
- 未提交（与 1.4.97 / 1.4.98 一并等待指示）。
- **待实机验证**：双持右键装机（含照片在主手/副手两种顺序、相机不被拦截、照片查看界面不弹出）、
  100 次耐久递减与销毁提示、武器 tooltip 上的「火 II」与照片记录元素一致性。

## 需求批次（`1.5.0` → `1.5.1`）：把四个第三方物品的堆叠上限改成 64

### 20. 需求与实现（`1.5.1` 为最终口径）

- 需求：`legendary_monsters:anchor_handle`、`fdbosses:chesed_trophy/malkuth_trophy/geburah_trophy`
  最大堆叠 1 → **64**。全部四个物品最终都走**声明式** `ModifyDefaultComponentsEvent`
  （`handler/ItemStackSizeHandler`，mod 总线），`MAX_STACK_SIZE=64`。
  **没有 mixin**：中间版搞的 `mixin/StackSizeItemStackMixin`（`ItemStack#getMaxStackSize`
  覆盖 + `hurtAndBreak` 耐久重置）在 `1.5.1` 已**删除**（类与 `lensouls.mixins.json` 登记项都撤）。
  以下反编译结论保留，因为它们是这条路的判据。
- **为什么 `anchor_handle` 直改会炸（反编译 `Item$Properties.validateComponents`）**：
  它是 `AnchorHandleItem extends SwordItem`（2.1.20 实机 jar 确认），原版 `TieredItem`
  构造器调 `properties.durability(250)` → **同时写入 MAX_DAMAGE=250 / DAMAGE=0 / MAX_STACK_SIZE=1**。
  NeoForge 事件落地时走 `Item.modifyDefaultComponentsFrom` → `validateComponents`：
  **`map.has(DAMAGE) && getOrDefault(MAX_STACK_SIZE,1) > 1` → 直接
  `IllegalStateException("Item cannot have both durability and be stackable")`**⇒
  原版禁止「有耐久 + 可堆叠」。`1.5.0` 曾用 mixin 绕开并配耐久重置；`1.5.1` 按用户口径改走
  **删除 DAMAGE 组件**（`builder.remove(DataComponents.DAMAGE)` + `MAX_STACK_SIZE=64`）：
  - 校验不再成立（`has(DAMAGE)=false`）→ 启动即可生效；只摘 DAMAGE、**保留 MAX_DAMAGE=250**，
    其它模组按 MAX_DAMAGE 判「是武器」的逻辑不受影响；
  - `isDamageableItem() = has(MAX_DAMAGE) && !has(UNBREAKABLE) && has(DAMAGE)` ⇒ false
    → `hurtAndBreak` 进门即 return ⇒ **无耐久、永不磨损**，也没了耐久条。
- **无耐久后「拆堆/并堆」才真正自洽（用户问出来的关键）**：耐久值是**每一堆一个整数**，
  拆堆会把 damage 分量整份复制到每一小堆（大堆现存的充裕耐久被成倍复制 = 变相白嫖），
  并堆则反过来会**吞掉**多余耐久；原版正是明白这一点才强制 `durability ⇒ stackSize=1`。
  声明式「保留耐久 + 可堆叠」无论怎么补丁都绕不开这层语义——**把耐久摘掉是唯一干净解**。
  （中间版的耐久重置补丁只能保「整堆不连爆」，拆/并的账没法算平，所以弃用。）
- 三个 fdbosses 奖杯 = 无耐久 `BlockItem`（`new Item.Properties().stacksTo(1)`，
  `javap -c` 已核对），事件补丁直接过校验，无需额外处理。
- 顺带确认（防踩）：
  - `BuiltInRegistries.ITEM` 是 **DefaultedRegistry**，缺条目时 `get()` 返回 `minecraft:air`
    而不是 null——判存在必须用 `containsKey`。
  - `fdbosses`（逆卡巴拉 3.2）不在本机 `run/mods`，参考包在
    `参考项目的源码\[逆卡巴拉：觉醒] fdbosses-3.2-1.21.1.jar`，反编译在
    `参考项目的源码\decompiled\fdbosses`；`javap -p -c` 已核对三件奖杯的注册名与构造。
  - 该事件是 `IModBusEvent`（mod 总线），在 `LenSouls` 构造器里 `modEventBus.addListener` 注册；
    触发时机在 `GameData.postRegisterEvents` 内、注册表冻结前，物品皆已注册。
    补丁最终走 `Item.modifyDefaultComponentsFrom`，会**再过一次** `validateComponents`
    所以「先摘 DAMAGE 再改堆叠」要在同一个 patch 里做（顺序无关，补丁容器是同一个）。

### 21. 交付

- `1.4.99`（弱点透镜批次，5,897,454 字节 / MD5 `29582365B518E7A27E77F5FB6E3A7E55`）
  → `1.5.0`（堆叠 64 的第一版 + ItemStack mixin，5,901,796 字节 /
  MD5 `277CBD68B0C83CAE6F4E8C7C6E871FE7`）→ **`1.5.1`（最终）**：
  anchor_handle 改为**移除耐久**（永不磨损）、四个物品全部声明式堆叠 64、
  删除 `StackSizeItemStackMixin`（文件与 `lensouls.mixins.json` 登记项都撤）；
  jar 复制到 `C:/Users/volans/Desktop/lensouls-1.5.1.jar`（5,900,383 字节），
  MD5 `5F0A43B32287BC953913302182FDC6AC`。**1.4.91 ~ 1.5.0 作废，别分发**。
- 待实机验证：四件物品创造栏拖出 64 堆叠；anchor_handle 挥砍不再掉耐久（tooltip 无耐久条）。

## 需求批次（`1.5.2`）：单能力的潜行+滚轮 = 选中↔未选切换

- 修改点只有一处：`ability/gui/AbilityWheelHud.onMouseScrolled` 的 `list.size() <= 1` 分支。
  原行为：直接 return（滚轮放回原版热栏）。现为：
  - **不滚列表**，只在「选中 ↔ 未选中」翻转：相机 NBT 里记录了该唯一能力 → 发
    `AbilitySelectPacket(-1)`（协议本就支持，`CameraAbilityStore.clearSelected`）；
    未选中 → 发该能力 ordinal。当前态读 `CameraAbilityStore.getSelectedType(cam)`（本地 NBT，零延迟）。
  - **0.5s 冷却**（`TOGGLE_COOLDOWN_MS=500`，`Util.getMillis()`，字段 `lastToggleAtMs`）：
    只有**成功切换**才计入冷却（被冷却拦下的滚轮不刷新起点）。
  - 发包同时**本地镜像即时翻转** `ClientAbilityCache.setHeldCameraSelected(target)`（换机播种同款
    原则）：冷却窗口结束后下一次滚轮必须读到最新态，依赖 ~100ms 的 NBT 同步回声不够快也不可靠。
    S2C 回声再 set 同值，幂等。
  - **该分支取消原版事件**（不放热栏）：潜行+手持相机的滚轮已被模组接管，同一滚动既翻转能力态
    又切热栏会造成误操作；多能力分支原本就 `setCanceled(true)`，语义就此统一。
    **刻意保留**：`list.isEmpty()`（一个能力都没解锁）时早退、**不放回**取消逻辑→ 滚轮照滚热栏
    （同多能力下未拿相机的口径）；`getUnlockedList()` 同空返回，原来就走 `size()<=1` 分支，
    新代码必须在这个分支里防 `list.get(0)` 越界。
- 不影响面：多能力滚动路径一字未动；GUI 内滚动（`mc.screen != null` 早退）不受影响；
  没拿相机时本 handler 在 `getWieldedCamera` 判空处就返回，热栏照滚；
  GUI 点击卡片切换/取消选中、左键打开界面都不受影响。
- 交付：`C:/Users/volans/Desktop/lensouls-1.5.2.jar`（5,900,657 字节），
  MD5 `391EF9E0D6ACB82F383349E5DF7B5EB0`。**1.4.91 ~ 1.5.1 作废，别分发**。
- 待实机验证：单能力下潜行滚轮的翻转 + 0.5s 内连滚只动作一次；多能力下滚动不受影响。

## 需求批次（`1.5.3`）：先驱者弹幕实机崩溃（CopyOnWriteArrayList 迭代器 remove）

### 22. 崩溃定位（crash-2026-09-29_15.00.59-server.txt）

- 现象：玩家实机（整合包装的是 1.4.96）触发**先驱者照片弹幕 → 服务端 tick 崩溃**：
  `java.lang.UnsupportedOperationException: null` at
  `CopyOnWriteArrayList$COWIterator.remove`
  ← `BossPhotoProjHelper.syncDeathLaserAim(:453)`
  ← `BossPhotoProjHelper.onServerTick(:903)`（`ServerTickEvent.Post` 直抛 `fireServerTickPost` → 整服崩）。
- **触发时机不是发射，而是结算完**：`syncDeathLaserAim` 清理分支 `beam.isRemoved()`
  （死亡激光 30 tick 到期消失，或 WeakReference 被 GC）→ `it.remove()`。
  即**弹幕正常播完的那一 tick 必炸**——表现为「弹幕崩溃」，实际是列表清理炸。
- **根因**：`PLAYER_DEATH_LASERS` 是 `CopyOnWriteArrayList`，其迭代器
  `COWIterator.remove()` **恒抛 UnsupportedOperationException**（COW 的写走
  `list.add/remove` 内部锁 + 整表复制，迭代器只读）。1.4.95 引入死亡激光同步时写成
  `for (var it = ...; it.hasNext(); ) { ... it.remove(); }` → 每发激光到期必炸一次。
  **1.5.2 及之前的现行源码同样带雷**（不是旧版本残留，是同一段代码活着）。
- **修法（1.5.3）**：for-each 走 COW 快照遍历，移除改 `PLAYER_DEATH_LASERS.remove(link)`
  （COW 支持；`removeIf` 也行）。**别退回 `iterator.remove()`**。
- **排查面**：全库 `CopyOnWriteArrayList/Set` 仅此一处；其余 8 处 `.iterator()` 全是
  普通 `ArrayList`/`HashMap`（支持 remove），逐一确认无同类隐患。
- 教训：`ServerTickEvent.Post` 一炸就是「Exception in server tick loop」整服崩；
  弹幕这类**每 tick 结算 + 定期清表**的逻辑，集合类型必须与迭代方式匹配——
  `ConcurrentLinkedQueue`（`poll`）或普通 `ArrayList`（单线程）都行，唯独 COW 不能配迭代器 remove。

### 23. 交付

- 版本 `1.5.3`：`.\gradlew.bat build` 通过；
  jar 复制到 `C:/Users/volans/Desktop/lensouls-1.5.3.jar`（5,900,620 字节），
  MD5 `54B3A092DFDC77CD0CD12015FC67968E`。**1.4.91 ~ 1.5.2 作废，别分发**。
- **待实机验证**：先驱者照片弹幕完整放完一轮（死亡激光 30 tick 到期消失）不再崩溃、
  服务端日志无 `UnsupportedOperationException`。
  **注意**：实机装的是 1.4.96，必须换成 1.5.3 才含本次修复（1.5.1 的 anchor_handle 摘耐久、
  1.5.2 的单能力滚轮翻转也一并在这份里）。

## 需求批次（`1.5.4`）：磁铁吸复制之魂进终端 → 之前的复制之魂消失

### 24. 根因（对着 0.7.24 完整源码核实，非 jar 猜测）

- 用户实测：超越维度的**网络磁铁**把地上的复制之魂吸进终端网络存储后，**终端里已存的复制之魂少了**。
- **直接根因**：`BeyondDimensionsCompat.sealCopySouls` 的网络迁移是
  「`setAmountByKey(oldKey, 0)` 清零旧键 + `setAmountByKey(newKey, 快照量)` 写新键」，
  而 `AbstractUnorderedStackHandler.setAmountByKey` 是**绝对量覆盖写**（storage.put(key, target)），
  **不是合并**。终端网络是共享的，`UnifiedStorage` 默认零策略 `RemoveZero`（清零=删条目）——
  新键下已有的封印存量被覆盖成本次迁移量 ⇒ 之前的封印复制之魂凭空减少。
  复现面：维度磁铁（`NetMagnetItem.workContent` → `storage.insert(itemKey, count, false)` 按键合并）、
  网络馈送器、restocker、其他玩家——任何往网络里放复制之魂的行为都会踩。
- **为什么说「补羽毛标签代码不够牢固」是对的**：封印 sweep 原本只考虑了自己这一个写者，
  没有考虑共享网络上的并发写入者；快照-写回之间网络内容随时可变。
- 修法（`sealCopySouls` 网络路径重写）：
  1. 快照仍用于**枚举候选键**，数量只当"计划迁移量"；
  2. 每条写前用**新反射句柄 `getStackByKey`**（`IStackHandler` 接口，0.7.24 有）重读
     两键的**活值**：`oldLive <= 0` → 旧键已被别人动过，本轮跳过；
  3. **先加后减**：`setAmountByKey(newKey, newLive + moved)` → 返回值 = 实际写入量
     （新键槽容量不足时会被钳制到 capacity）→ 按 `actual = applied - newLive` 扣旧键
     `setAmountByKey(oldKey, oldLive - actual)`；
  4. 反射失败/异常 → 返回 0 → actual 为负 → 跳过，**绝不盲目覆盖写**；容量满 → 本轮不动，
     下一轮 sweep 以活值幂等收敛。顺序**必须先加后减**：先减后加在容量不足时会把魂"减没了"。
  5. 缺 `getStackByKey` 句柄（更旧版本）→ 网络封印整体降级为**不动**（日志 debug 提示），
     绝不退回覆盖写。
- 解封方向同一路径自动受益（newKey=未封印键同样合并，不覆盖别的玩家放的未封印存量）；
  `netHasFeatherMember` 的解封守卫不变。
- **顺带核实的结构事实（防再猜）**：
  - 调用链 `UnifiedStorage extends UnorderedStackHandlerRemoveZero extends AbstractUnorderedStackHandler`
    ⇒ `setAmountByKey`（Abstract 声明）与 `getStackByKey`（`IStackHandler` 接口）**在 unified 实例上
    反射调用都合法**；0.7.24 默认零策略 = `REMOVE_ON_ZERO`（清零即删条目）。
  - `ItemStackKey.equals/hashCode` 走 `isSameTypeSameComponents`（完整组件 patch 参与，
    内部有 equalsByte 缓存）⇒ 封印/未封印复制之魂是**两个独立网络条目**，磁铁按键合并不串键。
  - 磁铁默认 `HopperNBTMode.DENY`（`hasExtraComponents` 过滤）：带灵魂 UUID 组件的复制之魂
    默认**不会被吸**，用户开了 NBT 允许/过滤后才吸——所以这个 bug 只在特定配置下出现。
- `CopySoulSealHandler` 本体（触发链/缓存/sweep 频率）复查无缺陷，本次不改。

### 25. 交付

- 版本 `1.5.4`：`.\gradlew.bat build` 通过；
  jar 复制到 `C:/Users/volans/Desktop/lensouls-1.5.4.jar`（5,901,016 字节），
  MD5 `920F887977BA137CD938885929526DA3`。**1.4.91 ~ 1.5.3 作废，别分发**。
- **待实机验证**：终端里放一摞封印复制之魂 + 戴羽毛 → 开磁铁吸若干地上的复制之魂进网络
  → 20 tick 内 sweep 后：**原有封印魂数量不减**，新吸进来的被补上封印；
  反向（摘羽毛解封）同理不覆盖别人的未封印魂。

## 需求批次（`1.5.5`）：`curios:photograph` 缺引用报错（整条 tag 加载失败）

### 26. 根因（对着 exposure_polaroid 1.1.5 的源码 + jar 逐条核实）

- 现象（实机日志 `【摄影奇境】Photo Wanderer\logs\latest.log`）：
  `Couldn't load tag curios:photograph as it is missing following references:` /
  `exposure_polaroid:instant_photograph (from mod/lensouls)`。这是**整条 tag 加载失败**，
  不是"少一个成员"——tag 压根不进注册表（每次资源重载 + KubeJS 各报一次）。
- 直接原因：`data/curios/tags/item/photograph.json` 列了一个**不存在的物品 id**。
  报错里的来源写 `mod/lensouls` 是因为 TagLoader 的 `source` 是**列出条目的数据包**
  （`EntryWithSource.toString()`），不是物品所属模组，别读成"拍立得模组提供的"。
- 逐条证据（1.1.5，与 `run/mods`、整合包 mods 里的 jar 同一个）：
  - 物品注册只有 `instant_camera` / `instant_color_slide` / `instant_black_and_white_slide` /
    `high_sensitivity_instant_{color,black_and_white}_slide`（`ExposurePolaroid` 源码 +
    对 jar 内 class 做字符串扫描，`instant_photograph` 一次都没出现）；
  - `assets/exposure_polaroid/lang/en_us.json` **没有** `item.exposure_polaroid.instant_photograph` 键；
  - 拍立得出片就是 `Exposure.Items.PHOTOGRAPH.get()`（`InstantCameraItem` 第 290 行
    `new ItemStack(Exposure.Items.PHOTOGRAPH.get())` + `PHOTOGRAPH_FRAME`/`PHOTOGRAPH_TYPE` 组件）
    ⇒ 就是 `exposure:photograph`，**本来就写在 tag 第一行**，那个 id 纯属多余且有害。
- 连带功能影响（真正要修的东西）：`CuriosIntegration.onCurioCanEquip` 靠 `stack.is(PHOTOGRAPH_TAG)`
  放行。tag 缺失 ⇒ `lensouls:entity_photograph` / `photo_album`（没有旧
  `lensouls:photograph_curio` 标记的那些）一路落到 `TriState.FALSE`，**照片装不进照片栏**
  （shift 快速转移 / 装备事件路径；槽位自身的 `lensouls:photo_curio` 谓词是另一条路，不受影响）。
- 修法：改成**可选条目** `{"id": "exposure_polaroid:instant_photograph", "required": false}`
  （与 `enchantable/cameras.json` 同款）。原版语义已反编译确认（`net.minecraft.tags.TagEntry.build`）：
  `T t = lookup.element(id); if (t == null) return !this.required;`
  ⇒ 可选条目缺失时**算构建成功**、不进 missing 列表，tag 照常加载；
  将来拍立得真新增该物品也会自动纳入。**别用 `"replace": false` 顶替**（`replace` 管的是
  是否清空已有条目，与缺引用无关），**也别直接删**（删掉就丢前向兼容）。
  条目格式的合法性同样反编译确认：`FULL_CODEC` = `id` 字段 + `required` 可选布尔（默认 true）。
- `CuriosIntegration` 里那条 key 判断保留（前向兼容），只加了一句注释说明
  「1.1.5 的拍立得照片就是 `exposure:photograph`」。

### 27. 交付

- 版本 `1.5.5`：`.\gradlew.bat build` 通过；
  jar 复制到 `C:/Users/volans/Desktop/lensouls-1.5.5.jar`（5,901,040 字节），
  MD5 `D52D07FD24BFCC8FF8C3AD448719871C`。**1.4.91 ~ 1.5.4 作废，别分发**。
  已开箱核验：jar 内 `data/curios/tags/item/photograph.json` 为可选条目版本，
  `META-INF/neoforge.mods.toml` 内 `version="1.5.5"`。
- **待实机验证**：启动日志里 `curios:photograph` 的 `Couldn't load tag` 消失；
  带镜魂数据的照片能 shift 进照片栏。
- **不是我们的报错**（同一条日志里的邻居）：`youkaisfeasts:wine` 缺 `kaleidoscope_tavern:*`
  （`source=mod/kaleidoscope_compat`）——整合包装了 kaleidoscope_compat 却没装 kaleidoscope_tavern，
  属对方模组问题。
- 同日志另有一条非致命告警：`@ModifyConstant conflict. Skipping
  lensouls.mixins.json:client.GunBowAnimationMixin ... already redirected by morearrows`——
  morearrows 抢了同一个 `@ModifyConstant`（弓动画除数），我们的拉弓动画速度对齐在该包里失效，
  与本次报错无关，暂不处理。

## 需求批次（`1.5.6`）：湮灭激光 / 死亡激光照片弹幕「纸片」→ 立体光束

### 28. 根因（两个模组的渲染器是**同一套写法**，对实机 jar 逐字节核实）

- 现象（用户）：湮灭构造体与先驱者的照片弹幕，本体（BOSS 发的那道）是立体光束，我们（玩家发的）是**纸片**。
- 两个渲染器开头都是同一行：
  `clearerView = caster instanceof Player && Minecraft.getInstance().player == caster
  && Minecraft.getInstance().options.getCameraType() == CameraType.FIRST_PERSON;`
  （传奇怪物 `AnnihilationBeamRenderer`、灾变 `Death_Laser_beam_Renderer`；字段名 `clearerView` 两边
  都经 `javap -p -c` 确认，setter 处都是 `getCameraType()` + `CameraType.FIRST_PERSON` 比较）
- 这一个标志同时管三件事，**它才是「立体 vs 纸片」的唯一开关**：
  1. `renderBeam`：为真 → **只画一面**四边形，且不做「±(相机俯仰+90°) 绕光束轴滚转」对齐；
     为假 → **两面对插** + 滚转对齐，这才是本体那种体积感。
     发射瞬间视线沿光束轴（那张面与视线共面、看不见），**之后随手转视角就会看到它是一张大平板**——
     正是用户描述的「纸片」。
  2. `renderStart`：为真 → 跳过「起点贴脸光斑」。那个四边形是 camera-facing 的、起手位置就在相机上，
     画出来会糊住整屏；这是原版给「第一人称自己开的光束」留的保护。
  3. `drawBeam` 起点偏移（真 −1 / 假 0）。
- 本体发激光时 caster 是 BOSS（非玩家）⇒ 永远走「立体」分支；我们的弹幕 caster 是玩家且在第一人称
  ⇒ 永远走「纸片」分支。**与伤害、时长、radius 参数全都无关，纯粹是 caster 类型触发的渲染分支。**

### 29. 修法（只改客户端渲染，两处 compat mixin）

- 新增 `mixin/compat/AnnihilationBeamCrossRenderMixin`、`mixin/compat/DeathLaserCrossRenderMixin`
  （登记进 `lensouls.compat.mixins.json` 的 **client** 数组）：
  `@Inject(method = "render", require = 0, at = @At(value = "INVOKE", target = "…renderBeam:(FFFILcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;I)V"))`
  → 处理器拿到实体，判定「是我们标记过的弹幕」后把 `@Shadow private boolean clearerView;` 置 **false**。
- **注入点必须选在 `renderBeam` 调用之前，而不是 render 开头**：`renderStart` 已经带着原值跑过了
  （玩家 caster 时为 true → 贴脸光斑照旧跳过），于是**既拿到立体光束、又不会让起点光斑糊满屏幕**。
  改这个注入点位置前先想清楚这条（LM 的 `render()` 里 renderStart 在 renderBeam 之前，已 javap 核实；
  灾变那边 `render()` 压根不调 `renderStart`，那是个死方法）。
- `require = 0` + 字符串 `targets` + `remap = false`：对方缺席或改内部方法名时整条静默跳过。
  注入点字符串**逐字对着 javap 描述符**写，并做了交叉校验：目标 class 常量池里确实存在
  `renderBeam:(FFFILcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;I)V`，
  我们 mixin class 里的 target 字符串与之逐字节一致，且 `clearerView` 字段两边都存在。
- **只认我们自己的弹幕**：官方玩家武器 `AtomSplitterItem` 也是玩家当 caster（同样吃「纸片」分支），
  本 mixin 刻意不碰它——判定走 `PhotoProjMarker.isBarrage`。

### 30. 顺带修掉的结构性问题：照片弹幕标记以前**到不了客户端**

- `PhotoProjMarker` 原来只写实体 `persistentData`，而 **NeoForge 的 `persistentData` 不随实体同步**
  （反编译 `Entity` 只有存档读写 + getter，没有任何下发路径）。所以「客户端渲染器按标记区分我们的弹幕」
  这条路以前是**不成立**的——现有 `isBarrage` 调用点恰好全在服务端，才一直没暴露。
- 新注册**同步布尔附件** `lensouls:photo_proj`（`boss/ModAttachments.PHOTO_PROJ`：
  `AttachmentType.<Boolean>builder(() -> false).sync(ByteBufCodecs.BOOL).build()`）：
  - `mark()` 照旧写 persistentData（服务端逻辑 + 存档持久化，行为不变）**并** `setData` + `syncData`；
  - `isBarrage()` 先看附件，客户端直接返回（顺带省掉每帧一次 NBT 复制），服务端再兜底 persistentData。
  - 时序已核实：`mark()` 发生在 `addFreshEntity` **之前**，此刻实体还没进追踪表，
    `ChunkMap.getPlayersWatching(Entity)` 按 `entityMap.get(id)` 查 → 返回 `List.of()`，
    所以那一刻的 `syncData` 是**无害空操作**（不会给客户端发未知实体的包、不刷 WARN）；
    真正下发的是实体开始被追踪时的
    `ServerEntity.sendPairingData → AttachmentSync.syncInitialEntityAttachments`
    （客户端 `SyncAttachmentsPayload` 对未知实体只是 WARN 不抛错，我们这条路径也不会出现未知实体）。

### 31. 交付

- 版本 `1.5.6`：`.\gradlew.bat build` 通过；
  jar 复制到 `C:/Users/volans/Desktop/lensouls-1.5.6.jar`（5,903,847 字节），
  MD5 `CF5F900735BF3FFE0CE437C4CBFBDF46`。**1.4.91 ~ 1.5.5 作废，别分发**。
- **待实机验证**：发射湮灭激光 / 死亡激光后**随手把视角转开**，应看到两面对插的立体光束
  （不再是单张大平板）；第一人称正对光束看仍是细线（正常，面与视线共面）；
  死亡激光的红黄闪电、末端光斑、伤害与时长不变；官方玩家武器（原子分裂者）的光束观感保持原样。
- 刻意没做：没把弹幕生成点往前挪（光束仍从相机位置发出），没动任何伤害/时长/radius 参数——
  本次只改渲染分支；若之后嫌「光束从眼睛里冒出来」再单独调生成点。

## 需求批次（`1.5.7`）：套装 tooltip 成员名单动态识别（已装 → 绿）

### 32. 需求与实现

- 需求（用户）：照片 tooltip 的套装效果加动态识别——玩家安装对应照片后，灰色显示为绿色。
  口径已确认（用户选「成员名逐个变绿」）：成员清单行**逐个成员染色，已装=绿、未装=灰**，行尾给 `已装 X/N` 进度。
- 「已装」判定 = **Curios 照片栏 + 相册内容物**（与「照片效果」面板同一套统计），且
  **当前悬停的这张照片算已装**——否则在背包里悬停一张没装的照片，会显示它自己「缺失」。
- 落点：
  - `integration/PhotoSetRegistry.appendTooltip`：那一行由「一个 §7 灰字面量」改为 `MutableComponent` 逐名拼接
    （`集齐 ` + 每个成员一个 sibling（绿/灰）+ ` 照片，触发效果` + ` 已装 X/N`）；进度未达标用 `DARK_GRAY`、
    达标转 `GREEN`。
  - 新增 common 侧 `PhotoSetRegistry.collectInstalledEntities(Player)` / `countInstalledBossPhotos(Player)`
    （方法体即原 `client.tabs.PhotoSetClient` 的 `collectGearEntities` / `countBossPhotos`），
    `PhotoSetClient` 改为**纯转发** ⇒ tooltip 与面板共用一份口径，**不要再写第二份**否则两边会漂移。
    Curios 是 `implementation` 硬依赖（`CuriosIntegration` 无条件注册），所以 common 侧直接引用 CuriosApi 是安全的。
  - 新增私有 `norm(String)`：实体 id 过 `ResourceLocation.parse` 归一化（补默认命名空间），
    「成员清单 ↔ 已装照片」两侧都归一化后再比。
  - 首领套（`boss_barrage`）没有成员名单：保留 `N 张首领`，进度用已装首领照片**种类数**（同 Boss 多张只算一次），
    悬停的这张若是首领照片且尚未装就 +1（预览才准）。
- 进度分母 `need`：普通套装取该套装的**最低档**张数（多档套装的语义是「还差几张触发第一档」）；
  首领套取**最高档**（沿用原逻辑）。
- 边界：`ItemTooltipEvent.getEntity()` 在**启动期建搜索树时是 null**（javadoc 明写），Curios 也可能出问题 ⇒
  收集整体 `try/catch (Throwable)`，`viewer == null` 时**完全退化成老行为**（全灰、无进度）。
  着色一律用**独立 Component + `withStyle`**，不再往字面量里塞 `§` 码——老代码每处 `§` 码都恰好与 `withStyle`
  同色，一旦哪天冲突（`§a` 套在 GRAY 组件里到底谁赢）会很难查。
- 未动：效果行的类型配色（红/绿/蓝/紫）、`（Shift 查看套装效果）`提示行、「照片效果」面板。

### 33. 交付

- 版本 `1.5.7`：`.\gradlew.bat build` 通过（中途一次编译失败：新局部变量 `line` 与下方档位循环里的
  `Component line = effectLine(eff)` 重名，改名 `head` 后通过）；
  jar 复制到 `C:/Users/volans/Desktop/lensouls-1.5.7.jar`（5,904,193 字节），
  MD5 `20346DAC33DB94363E615AD36D642C23`。**1.4.91 ~ 1.5.6 作废，别分发**。
- 字节核验：`PhotoSetRegistry.class` 含 `collectInstalledEntities` / `countInstalledBossPhotos` / `norm`
  与 `集齐 `、` 已装 ` 两个字面量；`PhotoSetClient.class` 只剩转发（不再引用 Curios API）且三个公开方法都在；
  TOML `version="1.5.7"`。
- **待实机验证**：装 2 张「苍白猎魂」（三件套）→ 悬停该套任意照片并按 Shift：两个名字绿、第三个灰、
  行尾 `已装 2/3`（未达标暗灰、达标绿）；一张没装时全灰 + `已装 0/3`；首领套显示 `已装 X/N 张首领`。

## 需求批次（`1.5.8`）：光束「纸片」的真正根因（1.5.6 白改了）+ 离线 mixin 自检工具

### 34. 为什么 1.5.6 一点用都没有：mixin 压根没 apply

- 用户实测「还是纸片」。查整合包日志（`latest.log`，装的确实是 1.5.7）：
  `Mixin apply for mod lensouls failed lensouls.compat.mixins.json:AnnihilationBeamCrossRenderMixin ...`
  `Caused by: InvalidMemberDescriptorException: Invalid name: renderBeam:`
- **根因是我写的 `@At` target 选择器语法错了**：我照抄了 **javap 的显示格式**
  `...AnnihilationBeamRenderer;renderBeam:(FFFIL...)V`——那是 javap 自己的写法、**带冒号**。
  Mixin 的选择器是 `Lowner;name(args)ret`，**方法没有冒号**；**只有字段**才是 `Lowner;name:Desc`
  （所以 `clearerView:Z` 那种带冒号反而是对的）。带冒号时 Mixin 把方法名解析成 `renderBeam:`，
  抛 `InvalidMemberDescriptorException`，**整个 mixin apply 失败**——镜头上看起来就是「改了跟没改一样」。
- 教训：`@At` target 一律**手写**，或在交付前用下面的自检工具过一遍；**永远不要从 javap 输出里复制粘贴**。

### 35. 真正该改的是什么：光源几何（用真 JOML 复算，不是读源码猜）

- 光束本体 = 若干张**包含光束轴**的面片（`drawBeam` 把四边形撑在光束轴的局部 XY 平面上；
  复算确认局部 +Y 映射到世界方向 = `calculateEndPos` 口径的瞄准方向）。
  渲染器开头那行 `clearerView = caster instanceof Player && ... FIRST_PERSON` 决定画几张：
  - `false`（BOSS 发的那种）：两张，各绕光束轴滚转 ±(相机俯仰+90°)；
  - `true`（玩家自己发的）：**只画一张且不滚转**——法线恒为 `(0,-1,0)`，就是一张**水平大平板**。
- **复算出来的关键事实**：那两张面片的夹角随相机俯仰变化，
  **0° → 0.0°（完全重合）**、30° → 60°、45° → 90°、**90° → 0.0°（又重合）**。
  ⇒ **原作那套 ±(俯仰+90) 本身就是退化的**：平视/俯视时两张面片重合、只有斜着看才成十字。
  所以**光把 `clearerView` 置 false 是不够的**（1.5.6 就算 apply 成功也只是把纸从"横放"变"竖放"）。
- 最终修法（两件事缺一不可）：
  1. `@Inject` 在 `renderBeam` 调用点前把 `clearerView` 置 false（走"两面对插"分支；
     放在这个位置而非 render 开头，是因为 `renderStart` 已带着原值跑过 ⇒ 起手那个 camera-facing
     光斑照旧跳过、不会糊满屏幕）；
  2. `@ModifyArg` 把两张面片的滚转角**钉成 ±45°**（恒定正交十字，不再随俯仰退化）：
     LM 的 `MathUtils.quatFromRotationXYZ`（**ordinal 3/4**，该方法里共 5 处调用，前 3 处是基础位姿）、
     灾变 3.32 的 `Quaternionf.rotationY`（**ordinal 0/1**，共 2 处；注意它第一张传的是**弧度**却忘了乘
     π/180，是原作 bug，被我们一并绕开）。
- **相机不能再压在光束轴上**：面片都包含光束轴 ⇒ 相机在轴上时同时落在面片里，投影只剩一条线
  （这就是第一人称几乎看不见自己光束的原因）。所以两个光束的生成点从眼睛挪到
  「右手 0.35 / 下 0.15」（`BossPhotoProjHelper.beamOrigin`）：伤害射线只横移 0.35 格
  （30 格射程上约 0.7°，且两个光束命中盒都带 `inflate(1,1,1)`），但玩家第一人称立刻能看到十字截面。
- 判定仍只认 `PhotoProjMarker.isBarrage`。实体只在 `render` 签名里、`@ModifyArg` 挂在 `renderBeam` 上
  拿不到实体 ⇒ 新增客户端静态开关 `client/render/PhotoBeamRenderFlag`（渲染线程单线程；
  每次 `render` 在调 `renderBeam` 前都会重写，不是自己的光束写 false）。
- 这两处 mixin 由 `require = 0` 改为 **`require = 1`**：以后对方改内部方法名会在日志里**大声报错**，
  而不是静默不生效（静默正是这次连着两版白改的原因之一）。

### 36. `tools/MixinSelfCheck.java`：交付前必跑的离线自检

- 作用（跑在**已构建的 jar** 上，所以数的是运行字节码，不受源码树版本差异影响）：
  1. 用 Mixin 自己的 `TargetSelector.parseAndValidate` 校验每个 `@At target`
     —— 直接抓「带冒号」这类语法错（本次事故）；
  2. 把 target 解析成 `Lowner;name(args)ret`，到 mixin 目标类的对应方法里数匹配的 INVOKE 条数，
     并检查 `ordinal` 是否越界 —— 抓「ordinal 数错 / 注入点不存在」。
- 运行方式（`<mix>`=sponge-mixin jar、`<asm>`/`<asm-tree>`=ASM jar，都在 Gradle 缓存里）：
  ```
  javac -proc:none -cp "<mix>;<asm>;<asm-tree>" -d build/mixincheck tools/MixinSelfCheck.java
  $env:MIXIN_CHECK_VERBOSE='1'   # 想看成每条的命中条数就设它
  java -cp "<mix>;<asm>;<asm-tree>;build/mixincheck" MixinSelfCheck \
       build/libs/lensouls-<版本>.jar libs/legendary_monsters.jar libs/cataclysm.jar <joml.jar>
  ```
  退出码非 0 = 有 target 会 apply 期失败，**别交付**。当前结果：47 条 target、语法错误 0、注入点错误 0。
- 工具本身踩的两个坑（已修，别重犯）：
  - **注解保留级别**：Mixin 的 `@Mixin` 是 **CLASS 保留**（在 `RuntimeInvisibleAnnotations`），
    `@Inject`/`@ModifyArg`/`@At` 是 RUNTIME（`RuntimeVisibleAnnotations`）⇒ 只读 visible 会一条都读不到；
  - **方法描述符必须带返回类型**：`Lowner;name(args)ret` 里 `name(` 之后整段都是描述符，
    截到 `)` 会让**所有**检查误报「找不到」（我第一版就这样，39 个假失败）。

### 37. 交付

- 版本 `1.5.8`：`.\gradlew.bat build` 通过；`tools/MixinSelfCheck` 全绿（47 条 target）；
  jar 复制到 `C:/Users/volans/Desktop/lensouls-1.5.8.jar`（5,905,494 字节），
  MD5 `12E3BEED8CCCD6C5BD375217662ACA55`。**1.4.91 ~ 1.5.7 作废，别分发**。
- 字节核验：jar 内两个 mixin 类 + `PhotoBeamRenderFlag` 都在；选择器字符串**无冒号**
  （`renderBeam(FFFIL...`；旧写法 `renderBeam:(FFFIL...` 已不存在）；
  `quatFromRotationXYZ(FFFZ)Lorg/joml/Quaternionf;`、`rotationY(F)Lorg/joml/Quaternionf;` 目标都在；
  `BossPhotoProjHelper` 含 `beamOrigin`；TOML `version="1.5.8"`。
- **待实机验证**：发射湮灭激光 / 死亡激光 → 应看到**正交十字的立体光束**（不再是单张平板），
  而且**平视、抬头、低头看都是十字**（旧版那套 ±(俯仰+90) 在这些角度会退化成一张）；
  光束从手侧一点发出、伤害与时长不变；官方玩家武器（原子分裂者）与 BOSS 本体的光束观感保持原样。

## 需求批次（`1.5.9`→`1.5.10`）：光束改为「信标同款 4 面方管」+ 时间定格定身的提前解冻

### 38. 光束几何定案（用户纠偏：不是正交十字，是「信标光束那种四面包围的立方体」）

- 用户口径：原作光束看起来像**信标光束那种四面包围的立方体**，「四面」可能不严谨，但**不是正交十字**。
  据此把我们的光束本体直接画成**信标同款几何**：绕轴 0°/90°/180°/270° 的 **4 张面**围成方管。
- 关键对照证据（原版 `BeaconRenderer.renderPart`）：信标光束每方向连画 **4 个 `renderQuad`**
  （四角 x1z1→x2z2→x4z4→x3z3 围成一圈方管）✓ 这就是「四面包围」的几何本体；
  而 LM/灾变的光束体只有 **2 张面**（`drawBeam` 只发 4 顶点 = 1 张面，`renderBeam` 调 2 次，
  jar 字节码逐条核实），LM 的三个光束渲染器（`AnnihilationBeamRenderer`/`EnergyBeamRender2d` 等）
  结构完全相同，**没有**四面画法可抄。
- 贴图事实（放大像素核对）：光束体每帧是 **20×1 像素的对称渐变条**（边缘绿、中心白，帧号越大越粗，
  是「出现动画」）；原版信标贴图则是整张噪声渐变、每个面都映射整条。
- **实现（1.5.9 起改名 Box）**：`mixin/compat/AnnihilationBeamBoxRenderMixin` /
  `DeathLaserBoxRenderMixin`（`Cross` 两个已删，`lensouls.compat.mixins.json` client 数组同步改名）：
  - 保留 `@Inject`（renderBeam 调用点前）：打标 + 我们的光束把 `clearerView` 置 false
    （让 `renderBeam` 里两处 `drawBeam` 调用执行；`renderStart` 已带原值跑过 → 起手光斑照旧跳过）；
  - **`@Redirect` ordinal 0 的 `drawBeam`**：我们的光束 → 抵消调用方刚施加的滚转（LM 用**度**、
    灾变第一张是**弧度**且忘了乘 π/180）后连画 4 面（0/90/180/270）；别人的光束 → 原样调用；
  - **`@Redirect` ordinal 1 的 `drawBeam`**：我们的光束 → 跳过（方管已完整）；别人的 → 原样调用。
  - **`@Redirect` 处理器签名**（Mixin javadoc 官方规则，照抄例子
    `barProxy(Foo someObject, int abc, int def)`）：**接收者类型必须在参数最前**
    （`drawBeam` 是 target 自己的 private 方法、接收者是 `this`，也要写）；
  - **`@Shadow` 私有方法**：带 `throw new AssertionError()` 方法体即可（shadow 方法不会被拷贝，
    只重映射调用点；`conformVisibility` 只要求可见性不低于目标，private 对 private 正好）。
- 自检：`tools/MixinSelfCheck` 全绿（47 条 target；两个 Box mixin 的 `Inject renderBeam` 命中 1、
  `Redirect drawBeam` 命中 2、ordinal 0/1 均有效）。
- `beamOrigin`（右手 0.35 / 下 0.15 的手部偏移）沿用——相机不压在光束轴上，方管从手侧发出。

### 39. 时间定格定身：16% 阈值提前解冻（伤害免伤口径**保留**）

- 需求两条：
  1. **时间定格的定身也模仿破韧做「16% 伤害阈值提前退出」**：定身期间累计实体实际受到的伤害
     （`LivingDamageEvent.Post`，只统计玩家造成的），达到「最大生命 × `toughStunBreakDamagePercent`
     （默认 0.16，与破定共用同一配置项）」→ **提前解冻这一个实体**（其余照旧），并广播剩余定身集。
  2. ~~时间定格定身不吃韧性免伤~~ → **用户纠正：恰恰要保留免伤**（定格 ≠ 破定，对面还剩韧性就照常免伤）。
     我第一版做反了（在 `applyDamageReduction` 里旁路了），1.5.9 未交付即纠回，1.5.10 落地正确口径。
- 落点：
  - `ability/util/TimeFreezeManager`：新增 `accumulatedDamage`（Int2FloatOpenHashMap，键=实体 id，
    freeze/unfreeze 时清空）+ `addFreezeDamage(entity, amount)`（累计达标 → `unfreezeEntity(id)`：
    移出定身集 + `broadcast(true)` 广播剩余集）+ `unfreeze()` 同步清空累计表；
  - `boss/ToughnessDamageHandler.onLivingDamagePost`：在既有 `addStunDamage` 之后加一行
    `TimeFreezeManager.addFreezeDamage(target, event.getNewDamage())`（同一套守卫：
    服务端 / LivingEntity / 伤害>0 / 来源是 ServerPlayer）；
  - `boss/BossToughnessManager.applyDamageReduction`：**不加**任何定格旁路（javadoc 写明口径）。
- 语义细节：累计的是 `Post` 的 `getNewDamage()` = 减伤后实际伤害（与韧性定身的 `addStunDamage`
  同口径）；16% 是该实体自身最大生命的比例；阈值 ≤0 时该功能关闭。

### 40. 交付

- 版本 `1.5.10`：`.\gradlew.bat build` 通过；`tools/MixinSelfCheck` 全绿；
  jar 复制到 `C:/Users/volans/Desktop/lensouls-1.5.10.jar`（5,907,384 字节），
  MD5 `176F63D2E755DE623286CFDA60179B63`。**1.4.91 ~ 1.5.9 作废，别分发**（1.5.9 未交付即作废）。
- 字节核验：TOML `version="1.5.10"`；client 数组为 Box 两个名字、旧 Cross 类已不在 jar；
  `TimeFreezeManager` 含 `addFreezeDamage`/`accumulatedDamage`；`ToughnessDamageHandler` 含钩子；
  `BossToughnessManager` **不含** `isEntityFrozen`（免伤旁路已移除）。
- **待实机验证**：
  - 光束：发射湮灭激光 / 死亡激光 → 应是**四面封闭的方管**（信标那种），平视/俯视都是；
    BOSS 本体与原子分裂者的光束观感不变；
  - 时间定格：定格期间打 BOSS → 伤害仍按剩余韧性比例免伤（不旁路）；同一实体累计实伤达
    最大生命 16% → 该实体提前解冻（其余仍定身到 100 tick 到期）。

## 需求批次（`1.5.11`）：弹射物弱点的武器匹配惩罚细化

### 41. 需求与实现

- 用户口径：**弹射物打中 → 额外增伤；非弹射物不吃亏**（即"弹射物弱点不该触发『非匹配弱点 → 10%』的武器匹配惩罚"）。
- 现状核实（先说清哪些本来就对，避免重复修）：
  - **"只有弹射物弱点"的怪物对非弹射物攻击从不惩罚**——`DamageHandler` 武器匹配块里 `needsMatch`
    只统计非弹射物弱点（`if (weakElem == PROJECTILE) continue;`），git 里 `cc4a268` 就是为此改的；
  - 本次真正修掉的是**两个相邻缺口**（都会造成"吃亏"）：
    1. **0 值占位弱点照样触发惩罚**：弱点表形如 `{fire: 0, projectile: 1.5}` 时，`fire` 倍率为 0、
       本来就不提供任何增伤，却把 `needsMatch` 置真 → 非火武器整刀被拦成 10%。
       修法：`needsMatch` 只统计**倍率 > 0** 的非弹射物弱点（0 值条目跳过）。
    2. **{某元素 + 弹射物} 弱点的怪物，弹射物命中反被砍成 10%**：弹射物弱点的活性是隐含 1.0
       （本次攻击是弹射物/远程即满足），但武器匹配检查只看"武器有没有其它弱点元素活性" →
       弓箭打 {火 + 弹射物} 的怪 = 不匹配 = 整发箭伤害 ×0.1，弹射物增伤被惩罚吃掉。
       修法：`matches` 初始值 = `rangedHit && weaknesses.getOrDefault(PROJECTILE, 0) > 0`——
       弹射物命中自带匹配（前提是目标确实显式配了弹射物弱点）。
- `rangedHit` 从弹射物增伤段上提为局部变量复用（`RangedAttackHelper.isRanged(source) || isGunBullet`）。
- 保持不变：`isGytrinket` / 克拉肯船炮 / DoT 跳伤三类豁免；空手与无元素武器对"有元素弱点"目标仍拦截；
  非 player 攻击方本就不进此惩罚块。

### 42. 交付

- 版本 `1.5.11`：`.\gradlew.bat build` 通过；`tools/MixinSelfCheck` 全绿（47 条 target）；
  jar 复制到 `C:/Users/volans/Desktop/lensouls-1.5.11.jar`（5,907,546 字节），
  MD5 `AB89E312B636E689BD3D2ED5731B80FC`。**1.4.91 ~ 1.5.10 作废，别分发**。
- 字节核验：TOML `version="1.5.11"`；`DamageHandler.class` 含 `rangedHit` 与 `getOrDefault`
  （弹射物命中自带匹配的新逻辑）。
- **待实机验证**：
  - 只有弹射物弱点的怪物：弹射物命中 = 基础伤害 + 弹射物弱点增伤；剑/魔法等非弹射物攻击 = 全额（无 10% 拦截）；
  - {火 + 弹射物} 双弱点怪物：弓箭命中 = 全额 + 弹射物增伤（不再 ×0.1）；非火武器近战仍按原口径 ×0.1
    （除非武器带火活性/弱点透镜照片）；
  - 0 值占位条目（`"fire": 0`）不再把非火武器拦成 10%。

## 需求批次（`1.5.12`~`1.5.15`）：弹幕开关 / 击杀归属 / 驯服宠物免伤（随行改动，与 1.5.17 一起交付）

### 43. 三条诉求与落点

- **照片弹幕开关（默认 `B` 键，玩家级，架构照搬转换器的模式切换：客户端上报 + 服务端权威 + 动态键名提示）**：
  - `util/BarrageToggle`：状态落玩家 `persistentData`（键 `lensouls:barrage_enabled`），**键不存在 = 开启**
    —— 老存档与没切过的玩家手感一律不变。与转换器模式的唯一结构差异：转换器模式挂在**物品组件**（逐物品、
    随物品走），弹幕没有载体物品，所以落玩家数据（NeoForge 会写进玩家存档，重进游戏记得选择）。
  - `key/KeyBindings` 新增 `BARRAGE_KEY`（`key.lensouls.barrage`，默认 `GLFW_KEY_B`，`KeyConflictContext.IN_GAME`）：
    按下 → 本地乐观翻转 `client/ClientBarrageState`（保证提示即时）→ C2S `BarrageTogglePacket` →
    服务端权威翻转 + 回发 S2C `BarrageStatePacket`（登录时补发对齐）。裁决点在 `BossPhotoProjHelper.trigger`。
  - 提示显示在**物品栏上方**（`displayClientMessage(..., true)` 动作栏）：`§a弹幕：开启（按 <实际键名> 切换）`，
    键名取 `KeyMapping.getTranslatedKeyMessage()` —— 玩家在「操作设置」里改键后提示跟着变，不写死「B」
    （与 `ConverterItem` 的动态键名同款做法）。
- **幻灵击杀归属玩家**：`mixin/PhantomKillCreditMixin` 对 `CommonHooks.onLivingDeath` 里
  `new LivingDeathEvent(...)` 做 `@Redirect`，用 `PhantomDamageHandler.creditOwner(src, entity)`
  只把**造成者**换成召唤者玩家（**直接实体仍是幻灵、伤害类型不变**）。
  - 为什么必须 mixin：1.21.1 的 `DamageSource` 不可变、`DamageContainer.source` 是 final 且无 setter，
    事件阶段（`LivingDamageEvent.Pre`）改不动它；`LivingAttackEvent` 在 1.21.1 已被移除。
  - 为什么在这一刻：`LivingEntity.die` 第一行就调 `CommonHooks.onLivingDeath`，之后才
    `getKillCredit()` / `dropAllDeathLoot()` / `dropExperience()`；FTB Quests 等判
    `source.getEntity() instanceof ServerPlayer` 的模组全挂在这一个事件上。
  - 为什么**只**在这一刻：`LivingDamageEvent` 阶段拿到的仍是原始 source，所以本模组十几处
    「玩家造成」判定（元素活性/弱点、命中率掷骰、韧性减伤、灵魂口哨、弹幕远程触发…）全部维持原样：
    幻灵打人不触发照片弹幕、也不吃玩家元素加成。
  - 坑：回调**必须 static**（目标是 static 方法，否则启动期
    `InvalidInjectionException: non-static callback method ... has a static target`）；NeoForge 类不混淆 ⇒
    `remap = false`。
- **弹幕不再误伤驯服生物**：`handler/PhotoProjSafetyHandler` 的免除名单由「玩家」扩为「玩家 + 任何驯服生物」
  （`PhantomDamageHandler.isTamedPet`：狼/猫/鹦鹉/马…）。理由是选敌 `findNearestNonPlayer` 只排除玩家，
  玩家身边的狗会挤进弹道；口径取「只要驯服就不打」（队友的宠物挡弹道同样算误伤）。
  只掐 `PhotoProjMarker.isBarrageDamage` 的弹幕：玩家自己用剑/弓打自己的狗照旧掉血。
- 同批随行：强化界面（`gui/ReinforceMenu`、`gui/ReinforceSelectScreen`、`reinforce/ReinforceDataLoader`、
  `reinforce/ReinforceTooltipHandler`、`data/lensouls/reinforcement/blacklist.json`）、
  `entity/BossPhantomManager`、`entity/PhantomDamageHandler`、`handler/WhistlePhantomHandler`、
  `event/GunKillHandler`、`integration/PhotoSpecialEffects`、`integration/PhotographEffectRegistry`、语言文件。

## 需求批次（`1.5.16`→`1.5.17`）：湮灭/先驱者激光回到「原作两片」+ 爆点持续动画

### 44. 光束几何：撤掉 4 面方管，回到原作 2 片，只修「平视退化」

- 用户口径（1.5.9 的方管之后又一轮纠偏）：**「就按原作两片吧」+「修复平视 bug，但两片」**。
- 字节码事实（LM 2.1.20 `AnnihilationBeamRenderer.renderBeam`、灾变 3.32 `Death_Laser_beam_Renderer.renderBeam`，
  两边结构一致）：
  - `drawBeam` 各恰好 2 处 ⇒ 光束体本来就是 **2 张面片**；
  - 两张面片的滚转角是 `+(相机俯仰+90°)` 与 `−(相机俯仰+90°)` ⇒ **两面片夹角 = 2×滚转角**：
    俯仰 0° → **0°（完全重合 = 用户说的「纸片」）**、−45° → 90°（正交）、90° → 0°（又重合）
    ⇒ **这个退化是原作自身的 bug**，不是我们改出来的；
  - `clearerView`（= 施法者是本地玩家且第一人称）为 true 时：第 1 张**不滚转**、第 2 张**整段跳过** ⇒ 只剩 1 张；
  - 灾变第一张的 `rotationY` 传的是**弧度**却按度数写（`相机俯仰+90` 忘了 ×π/180），是原作另一处 bug。
- 实现（`1.5.8` 那套已被实机确认的写法，这次从方管回退到它）：
  1. `@Inject` 在 `render` 调 `renderBeam` 之前：`PhotoProjMarker.isBarrage` 打标 + 我们的弹幕
     `clearerView = false`（两张都要画；放在这个位置而非 `render` 开头，是因为 `renderStart` 已带原值跑过
     ⇒ 起手贴脸光斑照旧跳过）；
  2. `@ModifyArg` 把两个滚转角**钉成 ±45°**（"原作在相机俯仰 −45° 时才成立的理想夹角"，钉死后任何俯仰都是正交十字）：
     LM 用 `MathUtils.quatFromRotationXYZ` 的 **ordinal 3/4**、角度在 **index 1**；灾变用
     `Quaternionf.rotationY` 的 **ordinal 0/1**、**index 0**，且直接给 ±π/4 **弧度**（顺带绕开上面那个单位 bug）。
- 只改角度、**不改面片数量**：方管的 `@Redirect`（`lensouls$drawFourFaceBox` / `lensouls$skipSecondQuad`）
  与那两个 `Box` 类已删除，改回 `AnnihilationBeamCrossRenderMixin` / `DeathLaserCrossRenderMixin`
  （`lensouls.compat.mixins.json` 的 client 数组同步改名）。BOSS 本体与原子分裂者的光束角度原样放行。
- `@ModifyArg` 处理器签名**只有被改的那个参数**（不像 `@Redirect` 要加接收者）；`@Shadow` 私有字段照旧。

### 45. 爆点（收尾特效）整条命都在动

- 现象（用户）：`湮灭激光射地上那个特效只在消失时有动画，前边的生命都是静帧，很丑`。
- 根因（字节码）：`render` 里爆点与光束体**共用同一个帧号**
  `frame = Mth.floor((beam.appear.getTimer() − 1 + partialTick) × 2)`，而 `appear` 是
  `ControlledAnim(3)` —— `increaseTimer()` 涨到 duration 即**饱和**（不循环）⇒ 爆点只在前 3 tick 变一次，
  之后整条命都是同一帧；唯一还会动的是结束时 `on = false` → `decreaseTimer()` 的倒放。
  （`ControlledAnim` 字节码：`increaseTimer` 有 `if (timer < duration)` 守卫、`decreaseTimer` 下限 0。）
- 贴图实据（两张图逐像素一致）：`the_warped_one/annihilation_beam.png` 与
  `cataclysm:harbinger/death_laser_beam.png` 上半区是 16 张 16×16（`u = 0.0625 × frame`），
  **只有 frame 0~5 是火花**（不透明像素 12/32/52/88/132/164，由小到大），**frame 6 起全空白**
  —— 原作 `if (frame < 0) frame = 6;` 就是拿空白帧当「隐身」。
- 实现：`client/render/PhotoBeamRenderFlag` 扩成「本帧光束状态」（加 `endFrame` 与
  `impactCapFrame(tickCount, partialTick)` = 0→5→0 三角波，周期 `IMPACT_PERIOD_TICKS = 12` tick）；
  两个 Cross mixin 各加一个 `@ModifyArg` 只改 `render` 里 **`renderEnd(...)` 的 index 0（帧号）**：
  我们的弹幕用动画帧、别人的原样。实体只在 `render` 签名里，所以帧号在同一个 `@Inject` 里顺手写入。
- 只改爆点帧：光束本体/起手光斑/贴图/伤害/长度一律不动。

### 46. 交付

- 版本 `1.5.16`（十字版）构建通过但**未交付即被取代，作废**（jar 已从桌面删除）；
  最终 `1.5.17`：`.\gradlew.bat build` 通过；`tools/MixinSelfCheck` 全绿（47 条 target、语法错误 0、注入点错误 0；
  两个 Cross mixin 分别命中 `Inject renderBeam` 1、`ModifyArg renderEnd` 1、
  `ModifyArg quatFromRotationXYZ` 5（ordinal 3/4）/ `ModifyArg rotationY` 2（ordinal 0/1））；
  jar 复制到 `C:/Users/volans/Desktop/lensouls-1.5.17.jar`（5,921,178 字节），
  MD5 `5D065BF5CBDCD70F39BC4CC6D08A5973`。**1.5.16 及更早的 1.5.x 作废，别分发**。
- 字节核验：TOML `version="1.5.17"`；compat json client 数组为
  `BetterCombatRangeMixin` / `GytrinketCameraChargeMixin` / `AnnihilationBeamCrossRenderMixin` /
  `DeathLaserCrossRenderMixin`（**无** `Box` 类）；jar 内两个 Cross 类与 `PhotoBeamRenderFlag` 均在。
- **待实机验证**：平视/抬头/低头看都是正交十字（不再退化成一张）；爆点 0.6 秒一涨一缩持续动、
  消失时照旧收束；`B` 键开关的动作栏提示与动态键名、关掉后不再触发弹幕；幻灵击杀算玩家；
  狗/猫等驯服生物不再被弹幕误伤。

## 需求批次（`1.5.18`）：湮灭激光命中地面的「爆点」不再定格（共享时钟帧）

### 47. 真凶不是光束收尾面片，而是 `annihilation_explosion` 粒子

- 用户口径：截图里那团**绿色穹顶**才是他说的「爆点」，「不是紧贴地面那个」，贴图认成
  `annihilation_explosion_3.png` —— **认对了**（第 45 节改的 `renderEnd` 帧号是光束自己的收尾面片，与它无关，保留）。
- 追查过程（全部对着实机 jar 2.1.20）：
  - `AnnihilationExplosionEntity` / `AnnihilationGroundNukeStrikeEntity` / `AnnihilationGeyserEntity`
    都 `extends INoRendererEntity` ⇒ **没有自己的渲染器**，画面全是粒子；
  - 命中判定在光束自己身上：`AnnihilationBeamEntity.tick()` 里 `if (blockSide != null) spawnExplosionParticles(5)`
    —— **每 tick 一次**（只要还插在方块上）；
  - `spawnExplosionParticles(int)`：`for (i < count)` 循环，5 个 `ModParticles.ANNIHILATION_EXPLOSION` 全部
    `addParticle` 在**同一个命中点**（后三个参数是速度，而 `AnnihilationExplosion` 的构造器把速度丢了
    ⇒ 粒子**完全不移动**，`tick()` 里只把 xo/yo/zo 抄一遍）；
  - `AnnihilationExplosion`：`lifetime = 12`、`quadSize = 2.0`、每 tick `setSpriteFromAge()` 按**自己的年龄**
    在 8 张 `annihilation_explosion_*` 里挑帧（`index = age × 7 ÷ 12`，整数除法 ⇒ 实际只走得到 0~6）；
  - ⇒ 命中点同时叠着 ~60 个「年龄各不相同」的粒子，统计上是一个**恒定混合** ⇒ 看着定格；
    光束消失、生成停下后，剩下的粒子才把最后几帧走完（用户原话：「只在消失时有动画，前边的生命都是静帧」）。
- 修法（把挑帧的**年龄**换成**共享时钟**）：`client/render/AnnihilationBurstClock`
  + `mixin/client/AnnihilationExplosionClockMixin`：
  - 落点是原版 `TextureSheetParticle.setSpriteFromAge(SpriteSet)` 方法体里的
    `sprites.get(this.age, this.lifetime)`，用 `@ModifyArg(index = 0)` 改 age
    （javap 核实 vanilla 该方法的字节码就这一处 `SpriteSet.get(II)` 调用）；
  - 用**原版类**做宿主是为了不必碰 protected 的 `setSprite`（否则要 `@Shadow` 继承成员或加 invoker）；
    判定按**类名惰性解析**（`Class.forName(name, false, loader)` + `isInstance`），
    LM 缺席时永远 false、纹理帧原样放行 ⇒ 放在主配置 `lensouls.mixins.json` 的 client 数组里；
  - 伪年龄查表 `{4, 6, 7, 9, 11}` ↔ 帧 `{2,3,4,5,6}`（反解自 `index = age × (size−1) ÷ lifetime`，
    size = 8、lifetime = 12），**跳过帧 0/1**（一个亮点、一个星芒：同步后整团一起换帧，落在那两帧上
    穹顶会几乎看不见）；每帧 120 ms ⇒ 一轮 0.6 秒（用户选定口径）；
  - **粒子真实 `age` 不动** ⇒ 生命周期/移除时机/数量/位置/伤害全不受影响；
    对照片弹幕与 BOSS 本体自己放的湮灭激光**一律生效**（同一套粒子）。
- 交付：版本 `1.5.18`：`.\gradlew.bat build` 通过；`tools/MixinSelfCheck` 全绿（47 条 `targets` 项；
  本条用的是 `@Mixin(TextureSheetParticle.class)` 按类引用，工具按设计跳过 → 已手工 javap 核对注入点）；
  jar 复制到 `C:/Users/volans/Desktop/lensouls-1.5.18.jar`（5,923,277 字节），
  MD5 `165F41D5AC6318A6A90B2C91ED90A49E`。**1.5.17 作废，别分发**。
- 字节核验：TOML `version="1.5.18"`；`lensouls.mixins.json` client 数组含
  `client.AnnihilationExplosionClockMixin`；实读注解为
  `@Mixin(TextureSheetParticle.class)` +
  `@ModifyArg(method="setSpriteFromAge", target="Lnet/minecraft/client/particle/SpriteSet;get(II)Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;", index=0)`。
- **待实机验证**：湮灭激光打在地面/方块上时，那团绿色爆点应**整团一起**在
  「实心环 → 虚线环 → 翻滚消散」之间 0.6 秒一轮循环，而不是定格到消失才动。

### 48. 收尾对齐整轮（`1.5.19`）：光束寿命 3 整轮 + 爆点只死在轮边界

- 用户口径（1.5.18 之后）：**「做个优化，至少等这轮动画放完再结束生命」** → 追问后选「两个都做」。
- 时间线事实：爆点一轮 = 5 帧 × 120 ms = 600 ms = **12 tick**；而照片弹幕的湮灭激光本体原本
  `ANNIHILATION_BEAM_TICKS = 30`（= 2.5 轮）⇒ 光束本体在整轮中途结束，收尾永远是「截动画」。
- 两处落点：
  1. `BossPhotoProjHelper.ANNIHILATION_BEAM_TICKS` 30 → **36**（= 3 × 12 tick），本体寿命对齐整轮边界
     （命中判定窗口随之 +6 tick，用户已知晓）；
  2. 新 `mixin/compat/AnnihilationExplosionRoundMixin`：只改 `AnnihilationExplosion.tick()` 里那次
     `lifetime` 读取 ——
     - 还在粒子出生那一轮 → 返回 `Integer.MAX_VALUE - 1`：**不许死**，于是 `setSpriteFromAge`
       照常每 tick 执行、帧继续跟着共享时钟走（这点必须保住，否则第二轮起粒子会冻在最后一帧）；
     - 已跨过轮边界 → 返回真实寿命 `AnnihilationBurstClock.BURST_LIFETIME = 12`：年龄 ≥ 12 的粒子
       这时才一起消失。
     合起来：粒子寿命落在 **12~24 tick** 且**只会在轮边界上消失** ⇒ 光束停止补粒子后，那一坨把
     当前这一整轮（含最后的消散帧）放完才收尾；密度也不会出现「刚跨轮就空掉」的锯齿
     （出生轮的粒子还活着，同点同帧互相重叠，观感与 1.5.18 一致）。
  - 出生轮号用 `@Unique private long lensouls$spawnRound`（`@Inject(method = "<init>", at = @At("RETURN"))` 写入）。
- **关键字节码坑（差点写错）**：LM 的常量池把继承来的成员记成了**它自己的类**：
  `#7 = Fieldref …Particle/custom/AnnihilationExplosion.lifetime:I`、
  `#69 = Methodref …AnnihilationExplosion.remove:()V`。所以 `@At` 的 target **必须**照抄
  `Lnet/miauczel/legendary_monsters/Particle/custom/AnnihilationExplosion;lifetime:I`；
  写成 `Lnet/minecraft/client/particle/Particle;lifetime:I` 会一条都匹配不上（`require = 1` 会当场报错）。
  另核实：`tick()` 里 `lifetime` 只读 1 次（offset 36）、`age` 只读 1 次（读的是自增前的值，
  语义是 `if (age >= lifetime)`）；vanilla 的 `setSpriteFromAge` 自己读真 `lifetime`(12)，
  所以第 47 节那张伪年龄查表（`age × 7 ÷ 12`）不受影响。
- **自检口径提醒**：`tools/MixinSelfCheck` 对**字段选择器**只做语法校验
  （`parseMemberRef` 只解析方法选择器，字段会 `continue` 跳过存在性检查），所以字段注入点要自己
  `javap -v` 对常量池 owner —— 本次已核。
- 交付：版本 `1.5.19`：`.\gradlew.bat build` 通过；`tools/MixinSelfCheck` 全绿；
  jar 复制到 `C:/Users/volans/Desktop/lensouls-1.5.19.jar`（5,924,417 字节），
  MD5 `ABF45A99C8B9CCD9C8FD3B950D4B2BF7`。**1.5.18 及更早作废，别分发**。
- 字节核验：TOML `version="1.5.19"`；compat json client 数组含 `AnnihilationExplosionRoundMixin`；
  `AnnihilationBurstClock` 含 `ROUND_MILLIS` / `BURST_LIFETIME` / `currentRound`；
  mixin 内 `@At` target 字符串实读为 `Lnet/miauczel/legendary_monsters/Particle/custom/AnnihilationExplosion;lifetime:I`，
  `@Unique` 字段 `lensouls$spawnRound:J` 在位。
- **待实机验证**：湮灭激光命中地面时爆点整团随共享时钟翻滚；光束消失后，那一坨**把当前这一整轮放完**
  才在轮边界一起消失（不再中途截断），且整段时间密度不塌。

## 需求批次（`1.5.25`）：照片套装 tooltip「按件点亮」改成严格口径

### 49. 悬停的那张照片不再冒充已装

- 用户口径：「照片套装按件点亮有问题，我在 JEI 拿到的版本，没装上时 tooltip 已经对该照片点绿」
  → 追问后选「严格口径：没装就是灰」。
- 根因（1.5.7 当时是**刻意**写的，见第 32 节）：
  ```java
  Set<String> shown = new HashSet<>(owned);
  shown.add(norm(entityId));   // 「正在看的这张也算已装」
  ```
  再加上首领套的 `have++` 预览 ⇒ **悬停任何一张照片，它自己那一格永远绿**。
  JEI 里 `ItemTooltipEvent.getEntity()` 常常是 null（NeoForge javadoc：启动期建搜索树即 null），
  那条路径虽然跳过「已装 X/N」，但**强制点绿那一步照样执行** ⇒ JEI 看没拿到的照片也是绿的。
- 改法（`integration/PhotoSetRegistry.appendTooltip`）：删掉 `shown` 与首领套 `+1` 预览；
  成员名与 `已装 X/N` 一律只读**真实已装**（Curios 照片栏 + 相册内容物，
  `collectInstalledEntities` / `countInstalledBossPhotos`）；`viewer == null` 时全灰且不显示进度
  （与 1.5.7 的退化口径一致）。代码里留了「**不要**退回预览写法」的注释，防止以后又手滑加回去。
- 交付：版本 `1.5.25`（**工作区当时叠着另一路 1.5.20~1.5.24 的未提交改动**——幻灵淡出 /
  BlessingCrystal / BossHealthOverlay 等；本次编辑是唯一晚于 1.5.24 构建时刻（02:59:40）的改动，
  所以 `1.5.25` 内容 = `1.5.24` + 本次修复）：`.\gradlew.bat build` 通过；
  `tools/MixinSelfCheck` 全绿（**54** 条 target，另一路新增的 7 条也一起过了）；
  jar 复制到 `C:/Users/volans/Desktop/lensouls-1.5.25.jar`（5,931,351 字节），
  MD5 `581ABFBFF0DF363EF6B5E9116FD23A06`；`PhotoSetRegistry.class` 27,288 → **27,169** 字节
  （预览逻辑删干净）。桌面上的 `1.5.24` 由另一路交付、**不含本次修复**，别拿它验这一条。
- **待实机验证**：背包 / JEI 里悬停一张**没装**的照片 → 它自己那一格是灰的、行尾 `已装 X/N` 不虚高；
  把同一张装进 Curios 照片栏（或收进相册）后再悬停 → 变绿且 X 加一。

## 需求批次（`1.5.26`）：苦力怕照片的爆炸免疫（描述 vs 实现对账）

### 50. 苦力怕：爆炸改成真免疫

- 用户口径：「苦力怕照片，免疫爆炸伤害，实测没用」→ 追问后**只修爆炸**（不加全局 −10%；也不往描述里补交互距离
  —— 交互距离那条属性 Curios 佩戴在栏位上时会自己显示）。
- 根因（描述 vs 实现三处不一致；`git log -L` 查证 `0.8f` 从 `d98aee9` 引入起**从未**是免疫）：
  - 描述 `PhotographEffectRegistry:132`：`免疫 爆炸 伤害；受到所有来源伤害 -10%`；
  - 代码 `PhotoSpecialEffects:126`：爆炸伤害 **×0.8（−20%）** ⇒ 玩家按描述期望免疫、实际还吃 80% = 「实测没用」；
  - 代码 `PhotoSpecialEffects:317`：`ENTITY_INTERACTION_RANGE −0.5`（`4d085aa` 那版描述里本来有、后来被删，本次不动）。
- 改法（一行）：
  `addRule("minecraft:creeper", new DamageRule(e -> e.getSource().is(DamageTypeTags.IS_EXPLOSION), 0.0f));`
  —— 倍率 `0.0f` = 完全免疫；判定由 `EXPLOSION || PLAYER_EXPLOSION` 换成 **`DamageTypeTags.IS_EXPLOSION`** 标签，
  连床/重生锚的 `bad_respawn_point` 一起免疫。
- **仍未对齐（本次刻意不动）**：描述里「受到所有来源伤害 -10%」没有实现；**恶魂**（`PhotoSpecialEffects:127`）也是
  ×0.8 + 描述「免疫 爆炸 伤害」，属同一类矛盾 ⇒ 需要时再开一批「全量描述 ↔ 实现对账」。
- 交付：版本 `1.5.26`：`.\gradlew.bat build` 通过；`tools/MixinSelfCheck` 全绿（54 条 target）；
  jar 复制到 `C:/Users/volans/Desktop/lensouls-1.5.26.jar`（5,931,369 字节），
  MD5 `A4601C7B31613EF7D070A5A00CF9F9BF`。**1.5.25 及更早作废**（1.5.25 的 tooltip 严格口径修复已含在 1.5.26 里）。
- 字节核验：`PhotoSpecialEffects.class` 78,460 → **78,450** 字节；`javap -v` 显示 `minecraft:creeper` 注册点用的是
  BootstrapMethods **#22** → `lambda$static$4`，该 lambda 体引用 `DamageTypeTags.IS_EXPLOSION`，紧随其后是
  `fconst_0`（倍率 0.0f = 免疫）。
- **待实机验证**：佩戴苦力怕照片 → 站在苦力怕爆炸 / TNT 里应**完全不掉血**；摘掉后照常掉血。

## 需求批次（`1.5.27`）：苦力怕「所有来源 −10%」实现 + 恶魂描述订正

### 51. 两条收尾

- 用户口径（接 1.5.26）：「一块修恶魂的描述」「受到所有来源伤害 -10% 做出实际实现」。
- 改动两处：
  1. `PhotoSpecialEffects`：给苦力怕**加第二条规则** `new DamageRule(e -> true, 0.9f)` = 受到**所有来源**伤害 −10%，
     对应描述第二句（与爆炸那条叠加时 `0 × 0.9` 仍是 0 ⇒ 爆炸依旧完全免疫）。
  2. `PhotographEffectRegistry`：**恶魂描述订正**——
     `§a免疫 爆炸 伤害；§a你受到的爆炸伤害 -20%` → `§a你受到的爆炸伤害 -20%`。
     恶魂代码（`PhotoSpecialEffects:127`）一直是 ×0.8 = −20%，描述里那句「免疫」是**谎报**；
     本次按「代码为准」修文本（要真免疫得改那条倍率，不是改文本）。注释里写清了这个岔路。
- 交付：版本 `1.5.27`：`.\gradlew.bat build` 通过；`tools/MixinSelfCheck` 全绿（54 条 target）；
  jar 复制到 `C:/Users/volans/Desktop/lensouls-1.5.27.jar`（5,931,407 字节），
  MD5 `CF5C1888A53122ABE2C9222F8065FC18`；桌面 `1.5.26` 已删（**作废**）。
- 字节核验：`javap -c` 里 `String minecraft:creeper` 出现 **3** 次 —— 第 1 处（offset 422）
  `invokedynamic #22`（IS_EXPLOSION 谓词）+ **`fconst_0`**（免疫）、第 2 处（offset 441）
  `invokedynamic #23` + **`ldc 0.9f`**（全局 −10%）、第 3 处（offset 3081）是 `ENTITY_INTERACTION_RANGE` 属性 ✓；
  `PhotoSpecialEffects.class` 78,450 → **78,581**、`PhotographEffectRegistry.class` 39,833 → **39,807**；
  类常量池里恶魂新串「你受到的爆炸伤害 -20%」出现 1 次、旧合并串（含「免疫 爆炸 伤害；」）**0 次**。
- **仍未对账**：照片描述（`PhotographEffectRegistry` 里 300+ 条硬编码中文）与 `PhotoSpecialEffects` 的
  「属性 / 减伤 / 免疫」实现之间可能还有同类漂移 ⇒ 需要时开一批「全量描述 ↔ 实现对账」。
- **待实机验证**：佩戴苦力怕照片 → 爆炸完全不掉血、其它来源伤害掉血比原来少 10%；恶魂照片 tooltip 不再显示「免疫 爆炸 伤害」。

## 需求批次（`1.5.28`）：折翼（禁复制羽毛）× 复制之魂 × 超越维度磁铁「网络里无限增长」修复

### 52. 现象与根因（对着实机存档 NBT 逐条核实）

- 用户口径：**没戴羽毛也会**「网络磁铁吸不动掉落物 / 地面复制之魂实体不消失 / 网络里的复制之魂一直涨」，
  且**摘除折翼后仍继续增长**；只影响复制之魂，不影响其它物品。
- 实机存档证据（`saves/新的世界/data/BDNet_0.dat` 解 NBT）：
  - 11:04 快照：`lensouls:copy_soul` 未封印 **8416** ＋ 同物品**带 `lensouls:copy_soul_sealed` 组件** **8352**
    （同一物品两条不同键）；11:27 快照（本轮测试后）：未封印 **64** ＋ 封印 **1024**；
  - 该键 `slotCapacity = Long.MAX_VALUE`、`slotMaxSize = Integer.MAX_VALUE` ⇒ **不是容量/槽位满**。
- 结论（三层）：
  1. **能往网络里写复制之魂的只有本模组的「禁复制封印」迁移那一条路**：折翼在
     `CopySoulSealHandler.wearsForbiddenFeather` 名单里 → `sweep` 每 20 tick →
     `BeyondDimensionsCompat.sealCopySouls`。BD 的 `NetMagnetItem` 自己是「先模拟 → discard → 后真插」，
     逐行核对（0.7.24 反编译 + `javap`）**不会**重复入库。
  2. 旧迁移是**两步非原子写**：`setAmountByKey(新键, 新键活值+迁移量)` → `setAmountByKey(旧键, 旧键活值-实际量)`，
     **旧键那一步的结果从不核对**（`invoke` 会把异常吞成 null，BD 的 `onContentChanged` 回调还可能重入），
     于是「迁移」静默退化成「复制」：每 20 tick 给封印键加一份 ⇒ 观感就是**无限递归/连发**；
     并且**摘掉羽毛后**只要再发生一次扫描（登录兜底/饰品变化）就继续涨。
  3. **`componentEntries` 把任何带 `beyonddimensions:istack_slots` 的物品都当容器** —— 而 BD 的
     `net_magnet_item` / `net_feeder` / `net_restocker` 用**同一个组件装它们的过滤槽**（36/41 个 `KeyAmount`）。
     旧实现因此会去改写磁铁/馈送器的过滤组件（机器每 tick 又写回 ⇒ 组件乒乓写），
     次元锤的材料统计/消耗也会把过滤槽里的东西当成「玩家可用材料」。

### 53. 修法（`BeyondDimensionsCompat` + `CopySoulSealHandler`）

- **只认物质压缩球**：新增 `isMatterBall`（按 `beyonddimensions:matter_compress_ball` 解析一次并缓存），
  `componentEntries` 对其它物品一律返回空表 ⇒ 不再碰机器过滤组件（连带修掉材料误统计/误消耗）。
- **迁移改为「写—回读—核对—必要时回滚」**：写新键 → **回读真值**算 `gained`（不信返回值）→
  `旧键 = max(0, 旧值 - gained)` → **回读旧键核对**；若旧键没降到位，则把
  `gained - (旧值 - 旧后值)` 从新键扣回（`Math.max(新键原值, …)`）并 WARN（30s 降频）。
  ⇒ 任何异常/回调/钳制下都**守恒**，迁移永不变成复制。
- **可重入闸**：`SEALING`（`AtomicBoolean`）护住 `sealCopySouls` 整段，`SWEEPING` 护住 `sweep` 整段。
- **新旧键相同直接不动**（`oldKey.equals(newKey)`）：先加后减会把自己清零，属于丢魂而不是迁移。
- **标志维护进 `finally`**：`SEALED_IN_CONTAINERS` 无论中途是否抛错都要更新，
  否则「摘羽毛后每 20 tick 继续扫」成为永久状态（正是用户说的「摘除后仍增长」）。
- **登录兜底解封**：`PlayerLoggedInEvent` 时若无禁复制羽毛 → 做一次 `sweep(player, false)`，
  把旧会话留在终端里的封印魂解掉（旧实现只在「本次会话封过东西」时才回扫，重登即丢标志，
  那个封印键会永远躺着 —— 实机存档里同物品两条键就是这么来的）。

### 54. 交付

- 版本 `1.5.28`（**工作区同时有另一路会话在改**：1.5.26/1.5.27 是他们的批次；本次构建**先把
  `mod_version` 误设为 1.5.26、已改为 1.5.28**，被覆盖的桌面 `lensouls-1.5.26.jar` 已删除 ——
  它在另一路批次里本就是**作废**号，1.5.27 保持不动）：
  `.\gradlew.bat build` 通过（只剩既有 JEI 弃用告警）；jar 5,932,944 字节，
  MD5 `ADDAE7C833413333DB0F61B72825A233`，TOML 内 `version="1.5.28"` 已核；
  交付 `C:/Users/volans/Desktop/lensouls-1.5.28.jar`。
- **待实机验证**：戴折翼 → 终端里复制之魂数量不再自己涨（每轮只有磁铁真正吸进来的量）；
  摘除/重登后封印版复制之魂被解封回一条；磁铁/馈送器的过滤槽内容不再被本模组改写。
- **已知未动（要修再开）**：BD 磁铁默认 `hopper_nbt_mode = DENY`（跳过带组件物品，且 0.7.24 的 GUI
  没有这个开关），所以**带封印组件的复制之魂（戴羽期间丢出去那堆）依然吸不进去**；要连这个也修，
  得给 BD 的 `ItemStackHelper.hasExtraComponents` 加兼容 mixin（把我们的封印组件排除），
  或落魂时摘掉封印组件 —— 两条都会改封印语义，等用户点头再做。

## 需求批次（`1.5.29`）：照片栏位扩缩的收尾改走 Curios 官方路径

### 55. 不再自己 resetSlots + 全量同步（纯 lensouls 侧优化）

- 起因：L2 FIX 那边的分析提到「客户端槽位数少于服务端」。核完结论：**那是 L2Tabs 自己的设计缺口**——
  - Curios 客户端只在容器实现了 `ICuriosMenu` 时才重建槽位：`CuriosClientPackets:196`（`SPacketSyncModifiers`）
    与 `:234`（`SPacketSyncCurios`）两处都是 `if (localPlayer.containerMenu instanceof ICuriosMenu curiosMenu)
    curiosMenu.resetSlots();`；
  - L2 饰品页菜单 `dev.xkmc.l2tabs.compat.common.BaseCuriosListMenu`（在 `l2library` 的 jarjar 里）**不实现任何接口**，
    整个 l2tabs 70 个类里引用 `ICuriosMenu` 的 **0 个** ⇒ 即时重建对页面永不触发；
  - L2Tabs 的补偿是 `CuriosEventHandler.onSlotModifierUpdate(SlotModifiersUpdatedEvent)` → 塞 `MAP` →
    **下一个** `EntityTickEvent.Post` 才 `openMenuWrapped` + `switchPage` 重开页面 ⇒ 至少慢一拍；
  - Curios 的 resize 还是惰性的（`getStacks/getCosmeticStacks/getRenders/getActiveStates/getSlots` 里才 `update()`），
    窗口可能更长。期间越界的容器更新由修好的 `l2curiospagefix` 兜住（重定向到 `DiscardSlot`）。
  ⇒ 按用户口径「是别人的锅就先不管」，本轮只做 lensouls 侧的无谓开销清理。
- 改动（`PhotoSpecialEffects.updatePhotoSlots`）：
  1. 改完槽位修饰符后**就地落实惰性 resize**：`handler.getCurios().get("photograph").update()` ——
     `update()` 才会 resize + `loseStacks` 处理越界物品 + post `SlotModifiersUpdatedEvent`
     （后者是 L2Tabs 重开页面的触发源 ⇒ 排队早一拍），也保证随后那个包带的是**新尺寸**；
  2. 收尾换成 Curios 官方两行：`new SPacketSyncModifiers(player.getId(), handler.getUpdatingInventories())`
     + `updates.clear()`（与 `CuriosEventHandler:761-768` 装备变更流程同款）。
- 移除：原来的 `containerMenu instanceof ICuriosMenu → resetSlots()` 与**全量** `SPacketSyncCurios`。
  理由：全量包是「卡一下」的来源；且绕过官方流程会在 `getUpdatingInventories()` 里**留一条脏条目**，
  之后任意一次 Curios 变更都会把它再搭发一次。同时删掉不再使用的 `ICuriosMenu` import。
- 明确不变量：**只减少我们自己的同步，不改变 L2Tabs 那个窗口**（那是它的锅）。
- 交付：版本 `1.5.29`（`gradle.properties` 里的 1.5.28 是另一路顶上去的；`build/libs` 已有他们 11:34 的
  `lensouls-1.5.28.jar`，为免同号两套字节本路取 1.5.29，内容 = 1.5.28 + 本次优化）：
  `.\gradlew.bat build` 通过；`tools/MixinSelfCheck` 全绿；
  jar 复制到 `C:/Users/volans/Desktop/lensouls-1.5.29.jar`（5,932,948 字节），
  MD5 `90699029F1DB0BCB89041C96BE03C266`；桌面 `1.5.27`（本路旧包）已删。
- 字节核验：`PhotoSpecialEffects.class` 常量池 `SPacketSyncModifiers` 1 次、`SPacketSyncCurios` **0** 次、
  `ICuriosMenu` **0** 次、`ICurioStacksHandler` 3 次、`getUpdatingInventories` 1 次。
- **待实机验证**：戴/摘加栏照片（绵羊 +3、vindicator +1…）→ 照片栏位当场扩/缩、栏内物品按 Curios 规则处理、
  不再有整份 Curios 同步引起的卡顿；越界更新继续由 `l2curiospagefix` 兜住。

## 排查批次（`1.5.30-probe` → `1.5.38-probe`，**已全部撤销**）：Iris 光影界面切换时「闪黑」

### 56. 结论：那 0.7 s 是一次卡帧，黑不在帧里

- 现象（用户口径）：在 Iris 光影界面「列表 ↔ 设置」切换时出现约 **0.7 s 的整屏闪黑**（像眨一下眼），随后稳定；
  纯净环境（仅 Iris + Sodium）不出现；整包二分查不到单一凶手、「每个模组看起来都无辜」。
- 排查手段（临时探针逐版加深，只写 `logs/lensouls-iris-probe.log` + PNG，不参与游戏逻辑）：
  逐帧 6 点亮度/细节度；交换前整幅 `PRESENT`（mean / 黑像素占比 / 8×5 网格 / detail）；
  截图；`GuiGraphics.fill|fillGradient` 全屏填充（带调用者栈）；`GameRenderer.processBlurEffect`/`renderBlur`、
  `PostChain.process`、`RenderTarget.bindWrite`；`RenderSystem.clear`/`GlStateManager._clear`；
  `Minecraft.resizeDisplay`、`Window.setWindowed|setFullscreen|toggleFullscreen|changeFullscreenVideoMode|setVsync`；
  `Iris.reload|toggleShaders`、`IrisConfig.setShadersEnabled`、`PipelineManager.preparePipeline|destroyPipeline`、
  `Iris.loadShaderpack`；`OptionInstance.set`；`GlStateManager._viewport`；鼠标/按键标记（把「点的那一下」对齐帧号）；
  6 个可疑模组版本快照。
- **实测结论**（632 帧 PRESENT + 12654 条事件）：
  - `f=494 dt=731 ms` —— 打开光影界面那一帧主线程卡住 **0.73 s，期间一帧都没有呈现**（与用户「0.7 s」吻合）；
    同类卡帧：4547 / 2886 ms（世界加载）、792 / 741 / 414 / 133 ms（各界面切换）。
  - **632 帧交换前读屏没有一帧变黑**（除启动黑屏），`mean` 全程 27~34，`black%` 最高只是 Iris 面板自身的 13~24%。
  - **窗口层零动作**：`WINDOW` 仅启动 3 条（854×480 → 1920×1080）；运行期无窗口模式/垂直同步切换。
  - **管线层零动作**：`pipeId` 全程不变，`pipeline.*` 仅启动 `loadShaderpack` 一次。
  - `option.set` 仅启动与「完成（重载）」时出现；`VIEWPORT` 4445/4448 为正常 `0,0,1920,1080`
    （唯一异常是启动时 iceberg 物品渲染的 96×96）。
  - ⇒ **用户看到的黑发生在卡帧期间（游戏根本没出帧）**，是显示器/合成器层面的表现，**不在帧缓冲里** ——
    这正是前几版探针怎么找都找不到的原因。
- **撤销**（按用户要求）：删除 `debug/IrisFlickerProbe.java` 与 10 个 `mixin/compat/*ProbeMixin.java`，
  并 `git checkout` 还原 `LenSoulsClient`、`GameRendererFrameEndMixin`、`FrozenOutlineManager` 三处调用点与
  `lensouls.compat.mixins.json`（残留引用检查 = **0**）。探针源码只留在桌面 `lensouls-1.5.30~1.5.38-probe.jar` 里，
  需要时从 jar 反编译即可取回。
- **后续方向（未做）**：要定位那 0.73 s 卡在哪，只能靠主线程 profiler（spark / F3 / 对「界面首次打开」分段计时），
  **不是「谁画了黑」的问题**。
- 交付：清理版 `1.5.39`（= 1.5.29 内容、无任何探针代码）：build 通过、`tools/MixinSelfCheck` 全绿。

## 需求批次（`1.5.40`）：破韧拍脚 / 扭曲羽毛清零口径 / 员工战区票

### 57. 破韧拍脚无效：根因在「入镜列表」而不是削韧判定

- 用户回报：「破韧 拍脚没用（之前承诺修复但实测没有修好）」。
- 排查结论（运行时 1.9.18 jar `javap -c` 核实）：Exposure 的 `EntitiesInFrame.get` **自己先用眼睛点预筛**
  （`frustum.contains(entity.getEyePosition())` + `calculateVisibleDistance` + 对**眼睛**的 `hasLineOfSight`）。
  拍高大 BOSS 的脚 / 下半身时相机俯视，它的眼睛远在画面上沿之外 ⇒ **实体在进入我们的逻辑之前就被丢掉**。
  而 `EntitiesInFrameMixin` 是 TAIL「只收窄不放宽」⇒ 1.4.97 / 1.4.98 加的多身体采样点
  （`CameraVisibility.BODY_SAMPLE_FRACTIONS`）**对削韧完全空转**；「照片里看得见脚」只是渲染截图，
  与 `entitiesInFrame` 无关 —— 之前那次修复是**假阳性**。
- 修法（`mixin/EntitiesInFrameMixin`，同一个 TAIL 注入里一次扫描做两件事）：
  1. 保留原收窄；2. **补回**：对附近 `LivingEntity` 先过 Exposure 同口径焦距阈值
  （`EntitiesInFrame.calculateVisibleDistance ≤ Fov.fovToFocalLength(fov × 0.95)`，70° 镜头 ≈ 26 格），
  再过多身体采样点的 `CameraVisibility.isVisible`（视锥 + 逐点视线、`ClipContext.Block.COLLIDER` 遇方块即止）；
  子部件 → 父实体逻辑合并进同一趟扫描（省一次实体遍历）；最后按到相机距离升序重排，
  保持 `get(0)` = 最近主体（`PhotoInjectionHandler:110` 的能力窃取主体口径依赖它）。
- **不会**隔墙削韧（每个采样点都要过方块遮挡）；**不要**动只服务要害打击 / 断魂的 `hasClearSight`
  （改多点会导致隔墙锁头）。
- 波及面（预期）：`entitiesInFrame` 的所有消费者一起变宽 —— 弱点透镜 / 能力窃取主体 / 时间定格 /
  图鉴解锁 / **削韧**，从今以后「拍到脚」都算拍到。
- 递归提醒：`ToughnessPhotoHandler` 没有中心点 / 准星判定（全遍历 `getEntitiesInFrame()`），
  所以「削韧不灵」先查**列表生成端**；另有两个次级干扰项（与瞄准无关）：
  `!bossTierManager.contains` 且 tier = 0 时静默不削、以及 `BossToughnessData` 每次削韧后 60t 无敌窗。

### 58. 羽·扭曲之人：死亡改为 +10，清零只保留「击杀扭曲者」

- 用户口径：「扭曲羽毛改动，清除方法只保留击杀扭曲者时清除，每次死亡会涨 10 扭曲值」。
- 改动（`handler/FeatherTwitcherHandler`）：
  - 新增 `DEATH_TWIST_GAIN = 10`；`onDeath` 里原来的 `setTwist(player, 0)` **删除**，
    改为 `addTwist(player, DEATH_TWIST_GAIN)`（封顶 100）；
  - 召唤判定改为「**加完这 10 点之后** ≥ 100」才尝试召唤扭曲者 + 写 `FORCE_DROP`（去重逻辑不变）；
  - `onTwitcherDeath` 的 `setTwist(killer, 0)` **保留** ⇒ 全仓再无其它清零点
    （另一处 `setTwist(0)` 在 `FeatherAbyssHandler`，属**羽·深渊**独立机制，刻意未动）。
  - 文案 `item.lensouls.feather_twitcher.desc5`（zh / en）同步改写；两个 lang JSON 已 `json.load` 校验。
- 字节核验：`javap -c FeatherTwitcherHandler.onDeath` = `bipush 10` → `addTwist` → `bipush 100` 比较，
  方法体内**没有** `setTwist` 调用 ✓。

### 59. 员工（`lensouls:level2_staff_boss`）被区块卸载：补两条腿

- 先纠正前提（本次排查）：员工**不是** `block_factorys_bosses` 的实体，是**本模组自研测试 BOSS**
  （commit `8662398` 引入，`entity/Level2StaffBossEntity`，GeckoLib 模型 / 动画）；
  `block_factorys_bosses-2.1.2` 里根本没有 staff 实体类。⇒ 可直接改普通 Java，**无需 mixin**。
- 现状：全仓 `setChunkForced` / `TicketType` / `addRegionTicket` / `setPersistenceRequired` **0 命中** ⇒
  员工原先既没有防卸载、也没有防 despawn。
- 原版机制（`javap` 核实，真实 FQCN `net.minecraft.world.level.dimension.end.EndDragonFight`）：
  末影龙靠 `tick()` 里 `level.getChunkSource().addRegionTicket(TicketType.DRAGON, new ChunkPos(0, 0), 9, Unit.INSTANCE)`
  钉住战区（有玩家时加、无玩家时 remove），**没有**用 `setChunkForced`；`EnderDragon.checkDespawn` 是空实现。
  关键事实：**`Mob.checkDespawn()` 是 `isPersistenceRequired()` 的唯一读取点**，而它只在 entity-ticking
  区块里跑；区块卸载由 `DistanceManager` 的 ticket level 决定、从不读实体 NBT ⇒
  **persistenceRequired 只防 despawn，不防卸载**，必须两条腿。
- 修法（`entity/Level2StaffBossEntity`，无 mixin）：
  1. 构造器 `this.setPersistenceRequired()`（防自然刷除：MONSTER 类别
     `noActionTime > 600 && nextInt(800) == 0` 仍会 discard）；
  2. 新增静态 `TicketType<ChunkPos> LENSOULS_ARENA_TICKET = TicketType.create("lensouls_boss_arena",
     Comparator.comparingLong(ChunkPos::toLong), 100)`，在 `tick()` 的**所有 early-return 之前**
     （客户端守卫之后）对自身 `chunkPosition()` 续期
     `addRegionTicket(LENSOULS_ARENA_TICKET, chunkPos, 2, chunkPos)`
     （level = 33 − 2 = 31 = ENTITY_TICKING ⇒ 5×5 区块）。
     `timeout = 100` 的票在每次 add 时刷新计时，实体一旦停止 tick（卸载 / 崩服）100 tick 后自动过期
     ⇒ **零泄漏、无需手动释放**；刻意不用 `setChunkForced`（写 `ForcedChunksSavedData`、跨重启残留、
     会与玩家 `/forceload` 互相踩）。
- 已知未做：员工战斗状态字段（`fightState` / `meleeHits` / 窗口 tick 等）没有 `addAdditionalSaveData` 落盘，
  区块不再卸载后这个问题不再暴露；若将来仍需「重载后接续战斗」，得单独补持久化。
- 交付：版本 `1.5.40`（三处改动一起构建）：`tools/MixinSelfCheck` 全绿；
  jar 复制到 `C:/Users/volans/Desktop/lensouls-1.5.40.jar`（5,934,043 字节），
  MD5 `E1D0C38FB50365AC0090266B7F474E8A`。字节核验：`Level2StaffBossEntity.class` 含
  `lensouls_boss_arena` / `addRegionTicket` / `setPersistenceRequired` 各 1 处；`EntitiesInFrameMixin.class`
  含 `calculateVisibleDistance` / `fovToFocalLength` 各 1 处；`onDeath` 内无 `setTwist` 调用。
- **待实机验证**：① 拍高大 BOSS 的脚/下半身 → 韧性条照掉；② 佩戴扭曲羽毛死亡 → 扭曲值 +10 不清零，
  击杀扭曲者才清零；③ `/summon lensouls:level2_staff_boss` 后玩家跑远/换维度再回来 → 员工仍在原地
  且战斗状态未重置（区块被票钉住）。

### 57.1 补记（`1.5.41`）：把「入镜列表」策略从 mixin 抽到 `util/FrameEntities`，并修掉架空的早退

- 用户提问：「拍脚修复能不能合并到之前的视锥里？哪种做法更干净？」
- 结论：**判定原语早已共用**（收窄与补回调的是同一个 `CameraVisibility.isVisible`），没有第二套视锥。
  没合并、也**不该**合并的只有两件 Exposure 领域的事：①焦距口径
  （`EntitiesInFrame.calculateVisibleDistance` / `Fov.fovToFocalLength`）；②列表语义
  （子部件→父实体、去重、按距离排序、`get(0)`=最近主体）。
- 为什么**不**塞进 `CameraVisibility`：它被 `AimTargetUtil`（要害打击 / 断魂）共用，那里**必须**只认包围盒
  中心（`hasClearSight`）；让这个类承担「放宽 / 组列表」的策略，下一个人就会在瞄准路径误用多采样 ⇒
  **隔墙锁头**。另外焦距规则属于 Exposure 相机模型，纯几何类不该知道 fov。
- 为什么**不**改回 `@Redirect` Exposure 的预筛：`FrustumCheck.contains(Vec3)` 的 redirect 拿不到
  「正在被检测的是哪个实体」——上一版的 ThreadLocal + 隐式参数捕获即由此而来，且曾**静默失效**。
  TAIL「事后对齐」从结构上避开这类 bug。
- 落地（方案 A，行为不变部分）：新增 `util/FrameEntities.assemble(cameraHolder, pov, fov, exposureResult)`
  = 收窄 + 补回 + 子部件 + 排序 + 焦距口径；`mixin/EntitiesInFrameMixin` 缩成 8 行适配
  （取参数 → `assemble` → `setReturnValue`），类 javadoc 保留「不要再退回 @Redirect」的告诫；
  `CameraVisibility` 一行未改。字节核验：mixin 内已无 `CameraVisibility` / `Fov` /
  `calculateVisibleDistance` 引用；`FrameEntities` 内各 1 处。
- **顺带修掉一个会架空补回的 bug（重要）**：1.5.40 的补回前面有
  `if (original == null || original.isEmpty()) return;` ⇒ Exposure 返回空列表时补回完全不跑，
  而「高大 BOSS 只露脚」恰恰就是 Exposure 全刷掉、列表为空的情形 ⇒ 那一版在**最典型场景**下
  依旧不削韧。现改为**空列表也照常扫描补回**。
- 交付：版本 `1.5.41`；jar 复制到 `C:/Users/volans/Desktop/lensouls-1.5.41.jar`（5,934,482 字节），
  MD5 `AF287FABBF5D6D29FD928A6DD59810F2`；`1.5.40` 作废。
- **待实机验证**：对着高大 BOSS 的脚拍，且**画面里没有别的生物**（照片主体只有它）—— 这正是前两版失效的场景。

### 59.1 更正（`1.5.42`）：员工弃用战区票，改「与灾变 BOSS 一致」的持久化方案

- 用户实测：「员工还是会被卸载掉。可以看看灾变模组的 boss 怎么做的」。
- **灾变的做法（`参考项目的源码\灾变` 全树核实）**：**根本不用区块票** —— 全树只有
  `setPersistenceRequired()`（在结构生成处，如 `Cursed_Pyramid_Structure:196/206/216/228`）+
  `LLibrary_Boss_Monster:243 removeWhenFarAway → false`、`:247 shouldDespawnInPeaceful → false`
  + `:57/:62 addAdditionalSaveData/readAdditionalSaveData`（战斗状态落盘）。
  ⇒ **灾变的 BOSS 一样会被区块卸载**，他们保证的是「重载后状态照旧 + 永不自然消失」。
  因此 §59 里那条「对齐末影龙钉住 5×5 区块」的方向被废弃（更重且不是这条路的正解）。
- **上一版为什么没修好（`javap -c net.minecraft.world.entity.Mob` 核实）**：
  `readAdditionalSaveData` 里有 `ldc "PersistenceRequired" → getBoolean → putfield persistenceRequired`
  —— 即**存档加载会用 NBT 覆写该字段**。上一版只在构造器调 `setPersistenceRequired()`，
  实体一旦经历「卸载 → 重新加载」，标记就可能变回 `false` ⇒ `Mob.checkDespawn()` 判它「可自然刷除」
  ⇒ 玩家走远（>128 格）即 `discard()` —— 这才是用户看到的「还是会被卸载掉」。
- **本次改法（`entity/Level2StaffBossEntity`，全部普通 Java，无 mixin）**：
  1. **删除战区票**：`LENSOULS_ARENA_TICKET` / `ARENA_TICKET_DISTANCE` / `tick()` 里的续期三处全撤；
  2. **永不自然消失**：`removeWhenFarAway(double) → false`、`shouldDespawnInPeaceful() → false`、
     **`isPersistenceRequired() → true`（覆写 getter，与 NBT 彻底解耦 ⇒ 连改动前就存在的旧实体也安全）**；
  3. **战斗状态落盘**：`addAdditionalSaveData`/`readAdditionalSaveData` 序列化 L101–141 的 27 个字段
     （`fightState`/`meleeHits`/`cameraCooldown`/`spikeMidCooldown`/`spikeFromMid`/各窗口 tick/
     `meleeDamaged`/`meleeSounded`/`meleeLanded`/动画名/`spikeShotsDone`/`rayTicksLeft`/
     `rayHead{X,Y,Z}`/`rayDir{X,Y,Z}`/`rayResult`/`lastSpikeHit`），键名统一 `lensouls:` 前缀，
     动画名空串占位、读回还原 `null`。
- 字节核验：`Level2StaffBossEntity.class` 内 `lensouls_boss_arena`/`addRegionTicket` **各 0 处**；
  `removeWhenFarAway`/`isPersistenceRequired`/`shouldDespawnInPeaceful`/`addAdditionalSaveData`/
  `readAdditionalSaveData` 各 1 处；`lensouls:fight_state`/`lensouls:ray_result`/`lensouls:melee_anim` 各 1 处。
- 交付：版本 `1.5.42`；jar 复制到 `C:/Users/volans/Desktop/lensouls-1.5.42.jar`（5,935,357 字节），
  MD5 `DB20B43346C25FAA9ABC4D5A78847AA8`；`1.5.41` 作废。
- **待实机验证**：① 召唤后打一会儿 → 走远约 200 格（让其区块卸载）→ 回来：仍在原地、状态接续、血条恢复；
  ② 重启存档后仍存在不消失；③（预期）它会随区块卸载，只是「你不在时它不 tick」。

## 需求批次（`1.5.44`）：洞穴蜘蛛移出「剧毒逆转」+ 套装张数 count 改动态识别

### 61. 需求与实现

- 需求（用户）：① 洞穴蜘蛛从「剧毒逆转」套装要求中移除；② `count` 从硬编码改为**动态识别**。
- 数据现状（脚本全量核对 54 套）：
  - `venom_twist`（剧毒逆转，`photo_set_defs/conversion.json`）成员原为 bee / **cave_spider** /
    silverfish / spider 共 4，档位 `count: 4`；`photo_set/conversion.json` 里删除 cave_spider 那一项。
  - **有 4 套的 count 故意大于成员数**（允许同种照片重复凑数），动态化绝不能一把梭：
    `boss_barrage` 5/0（由照片 Boss 标记驱动，特殊）、`molten_oath` 4/3、`cinder_swarm` 3/2、
    `magma_kin` 3/2。其余 49 套 count == 成员数。
- 动态口径（`count` 省略 ⇒ 动态；显式写 ⇒ 覆盖）：
  - `config/PhotoSetLoader`：新增 `memberCounts`（setId → 成员实体数，随成员表在 `apply` /
    `setClientCache` 后由 `rebuildMemberCounts()` 重建）+ 访问器 `memberCount(setId)`；
  - `config/PhotoSetDefs`：解析时**不写 count ⇒ 0**（显式写非正数仍按无效档位跳过），
    新增 `effectiveCount(setId, tier)`：声明 >0 用声明值，否则 `max(1, memberCount(setId))`；
  - `integration/PhotoSetRegistry`：3 处消费点（激活判定 `getActiveSets`、tooltip 的 min/max `need`）
    全部改走 `effectiveCount`。
  - 网络同步无需改动：defs 与 membership 都会同步，两端各自按同一套口径现算（`count=0` 原样发过去）。
- 数据清理：用行级脚本把**「count 恰等于成员数」的冗余 `count` 行**从 7 个 defs 文件里删掉
  （attack 14 / balanced 8 / conversion 3 / daynight 8 / defense 5 / mobility 8 / survival 8 处），
  保留那 4 个覆盖值；`venom_twist` 因为删了 cave_spider 后已不等（4≠3）而未被自动删掉，
  额外手工删除 —— 否则它会变成「成员 3 却要 4 张」的不可达套装。
- **验证（脚本对比 HEAD vs 改后）**：54 套的 effective count **只有 `venom_twist` 由 [4] → [3]**，
  其余一字未变；仍显式写 count 的恰为那 4 套 ✓；14 个 JSON 全部 `json.load` 通过 ✓。
- 交付：版本 `1.5.44`：`.\gradlew.bat build` 通过、`tools/MixinSelfCheck` 全绿；
  jar 复制到 `C:/Users/volans/Desktop/lensouls-1.5.44.jar`（5,935,974 字节），
  MD5 `936C7FC379A8517C5AB2523E07E22AB4`；`1.5.43` 作废。
- 未动：`balanced.json` 里 cave_spider 仍挂在 `twin_spider`（双蛛噬影）名下（用户只要求剧毒逆转）。
- **待实机验证**：集齐蜜蜂 / 蠹虫 / 蜘蛛三张照片即触发「剧毒逆转」；
  `cinder_swarm`（2 成员要 3 张）、`molten_oath`（3 成员要 4 张）维持原强度。

### 61.1 更正（`1.5.45`）：普通套装 count≠成员数 = 死套装，那 3 条不是「故意设计」

- 用户纠偏：「count 不等于成员数本身就是 Bug！除了首领套按件计数，而且首领套也有去重」。
- 事实核对（本次读码确认）：
  - `PhotoSetRegistry.collectInstalledEntities` 里已有去重（`if (!ids.contains(n)) ids.add(n)`）⇒
    喂给 `getActiveSets` 的成员列表**本来就是不重复的成员集合**，因此每套的计数**上限 = 成员数**；
    `count` 一旦大于成员数（`molten_oath` 4/3、`cinder_swarm` 3/2、`magma_kin` 3/2），
    该档位**永远凑不齐** —— 是**死套装**（tooltip 上表现为卡在「已装 2/3」永不点绿），
    而 §61 里把它们当成「故意允许同种照片重复凑数」是**误判**，此处更正。
  - 首领套 `boss_barrage` 才是唯一例外：`countInstalledBossPhotos` 用 `HashSet seen` 去重，
    **同 Boss 多张只算一次**，即「按件计数 + 去重」，成员表里没有它（成员 0），故必须保留显式 `count: 5`。
- 修法：删掉 `molten_oath` / `cinder_swarm` / `magma_kin` 这 3 条 `count`（改走动态 = 成员数）；
  全量复核结果 `normal sets where effective != members: none`（54 套里除 `boss_barrage` 外，
  生效张数一律等于成员数）。
- 防回归：`PhotoSetDefs.effectiveCount` 新增一次性 WARN —— 普通套装显式 `count` > 成员数时提示
  「该档位永远无法激活（同种照片不重复计数），普通套装请省略 count」，每 `套装#count` 只报一次
  （`WARNED_UNREACHABLE`，避免每 tick 刷屏）。
- 口径统一说明：**激活判定与 tooltip 都按「已安装的不同成员数」**（去重），
  所以「同一种照片堆两张」不会再点亮套装。
- 交付：版本 `1.5.45`；jar 复制到 `C:/Users/volans/Desktop/lensouls-1.5.45.jar`（5,936,535 字节），
  MD5 `32A2224263FCD88D4336AD10817930D4`；`1.5.44` 作废。
  jar 内核验：仅存显式 count `boss_barrage: 5` ✓；`cave_spider` 已不在成员表 ✓；`venom_twist` 成员 3 ✓。
- **待实机验证**：炽粉虫群集齐 2 种、岩浆之裔 2 种、熔心誓约 3 种即可触发（此前永不触发）；
  剧毒逆转集齐蜂/蠹虫/蜘蛛；首领套仍是 5 种不同首领。

## 修复（`1.5.46`）：弹幕打不到多子部件 BOSS（九头蛇）

### 62. 根因：命中判据只认 LivingEntity + 本体无条件吞伤害

- 现象（用户）：照片弹幕（如「湮灭构造体」射线）打不到九头蛇这种多子部件 BOSS；次元枪自带的一套碰撞算法实测有效。
- 逐字核实（传奇怪物源码 2.1.15 + 运行时 jar 2.1.20 `javap`、暮色 4.8.3345 源码）：
  - `AnnihilationBeamEntity:623` 索敌是 `world.getEntitiesOfClass(LivingEntity.class, 射线包围盒)` ⇒
    九头蛇的 `HydraHead`/`HydraNeck` 是 `TFPart extends PartEntity`（**不是 LivingEntity**）⇒ 永不进候选；
  - 候选里只剩**本体**（是 LivingEntity、巨大 AABB）⇒ 射线必然与之相交 ⇒ `hydra.hurt(...)`，而
    `Hydra.hurt` = `return src.is(BYPASSES_INVULNERABILITY) && super.hurt(...)` ⇒ **无条件吞伤害**；
  - 真正该走的 `HydraPart.hurt → parent.attackEntityFromPart(...)`（头部减伤/头血/断裂）**永远走不到**；
  - 暮色自己的设计注释：`Hydra.isPickable() → false`（"enormous bounding box"）、`TFPart.isPickable() → true`
    —— 即**本体故意不可命中、部件才是碰撞体**。次元枪之所以灵，是因为 `GunBulletEntity.checkEntity`
    自己遍历 `getParts()` 直接命中部件，绕开了本体这一层。
  - 连带事实：`ModDamageTypes.causeAnnihilationDamage` 把 direct entity 写成 **caster**（不是射线本体），
    所以「查 `getDirectEntity().persistentData["lensouls:photo_proj"]`」的标记判定对 LM 两条射线静默失效
    （灾变死亡激光的 direct 才是光束本体，标记有效）。
  - 全整合包 javap 逐个核过：**只有九头蛇是无条件吞伤害**（娜迦走 `super.hurt`；湮灭者/潜影贝/利维坦/
    下界合金巨兽都只在特定状态或特定来源下吞）⇒ 爆点集中在这一型。
- **为什么不做「逐弹幕 mixin」**：LM 三条 Beam 各自 `extends Entity`、各有私有 `raytraceEntities`
  （无共同父类，无法合并）；每个 mixin 还要在类内部重算伤害公式（`getDamage() + 目标最大HP×HpDamage%`）
  有漂移风险，且必须写在 HEAD 且**不能 cancel**（`collidePos`/`blockSide` 是它内部写的，粒子渲染与已有的
  `ObliteratorBeamNoAutoExplosionMixin` 都依赖）。改 `Level#getEntitiesOfClass` 则**结构上不可能**：
  `EntityTypeTest.forClass(LivingEntity.class)` 对部件返回 null，硬塞进 `List<LivingEntity>` 会在调用方
  `checkcast` 崩。
- 解法（一个单点，覆盖所有弹幕）：
  1. 新增 `util/PartHitUtil` —— 把次元枪那套「部件优先」算法抽出来共用（此前仓里有三份拷贝：
     `GunBulletEntity.checkEntity` / `AimTargetUtil` / `CameraVisibility`）：`clipParts`（线段×部件求交，
     薄部件补 0.3 与次元枪同口径）、`nearestPart`、`resolveRoot`（部件→父实体）、
     `hurtResolvingParts`（把「打在本体上必被吞掉」的伤害改派给最近部件）、
     `livingTargetsInBox`（AoE 取目标时把部件折算成父实体并去重）。
  2. 新增 `mixin/compat/HydraPartDamageMixin`（`targets = "twilightforest.entity.boss.Hydra"`、
     `remap=false`、`require=0`、compat 配置 `required:false`）：在 `Hydra.hurt` HEAD，
     **原逻辑注定返回 false 时**用 `hurtResolvingParts` 把命中改派给最近部件（部件 hurt 转发父实体、
     跑本家头部逻辑）并 `setReturnValue(true)`。
     **闸门**：`BYPASSES_INVULNERABILITY` 原本就能生效 ⇒ 不插手；`directEntity instanceof Projectile`
     或 `direct != entity` ⇒ 不插手（LM 射线把 direct 写成施法者 `direct == entity`，而原版弓箭/投掷物
     direct 就是投射物本身）⇒ **原版弓箭行为一字不变**（射身体照旧无效，必须射头）。
  3. D2 自家代码：`BossPhotoProjHelper` 两处只查 `getEntitiesOfClass(LivingEntity.class, box)` 的 AoE
     （地震践踏 `:398`、巨剑斩击 `:1327`）改用 `PartHitUtil.livingTargetsInBox`；
     动能力场按 UUID 取本体（拿到的是父实体）与「要害打击/断魂」（`getEntities` + `AimTargetUtil.isAimedAt`，
     后者本就遍历部件）**核实无需改动**。
- 覆盖：我方全部弹幕 + 其它模组「无投射物本体」的射线/范围伤害/爆炸（凡走 `Hydra.hurt` 本体的）✓；
  不覆盖：真正带投射物本体的第三方弹幕打在本体上（保持原设计）✓。
- 交付：版本 `1.5.46`；`tools/MixinSelfCheck` 全绿（把暮色 jar 一并放进 classpath 复核 target 选择器 54 个、
  0 语法错误）；jar 复制到 `C:/Users/volans/Desktop/lensouls-1.5.46.jar`（5,940,550 字节），
  MD5 `58EA00061AA187345E0869145A2E6683`；`1.5.45` 作废。
- **待实机验证**：① 湮灭构造体射线 / 云筑魔像能量射线 / 先驱者死亡激光 打九头蛇**头** → 掉血
  （头部减伤、头血结算都应生效，而不是 0）；② 用**弓射身体**仍然无效（必须射头）——闸门没有被放宽；
  ③ 娜迦/其它多部件 BOSS 行为不变。

### 62.1 更正（`1.5.47`）：§62 的改派加了「回退本体」⇒ 无限递归 StackOverflowError

- 实测崩溃（客户端 `crash-2026-10-02_00.19.28-client.txt`，566 KB）：`java.lang.StackOverflowError`，
  栈里反复出现 `PartHitUtil.hurtResolvingParts:115` → `Hydra.hurt`（我们的注入
  `handler$iih000$lensouls$rerouteToPart`）→ `PartHitUtil.hurtResolvingParts:115` …。
- **根因**：`hurtResolvingParts` 里那条「部件拒收就回退 `victim.hurt`」的分支。而本方法**就是从受害者
  自己的 `hurt` 里调用的** ⇒ 形成
  「改派给部件 → `HydraPart.hurt` → `Hydra.attackEntityFromPart` → 本体 `hurt` → 我们的注入再改派…」
  的无限递归。触发条件很普通：九头蛇处于无敌帧时 `Hydra.isInvulnerableTo` 判定失败 ⇒ 部件返回 `false`。
- **修法（两处）**：
  1. `PartHitUtil.hurtResolvingParts` → 改名 **`hurtNearestPart`**，并**删除回退分支**：
     部件拒收就返回 `false`，由调用方决定（九头蛇那里即保持原有「吞掉」行为）。
     javadoc 写死这条坑：*本方法会从受害者自己的 hurt 内被调用，绝不可回退 victim.hurt*。
  2. `HydraPartDamageMixin` 增加 `ThreadLocal<Boolean> LENSOULS_IN_REROUTE` **重入保护**：
     改派链路内再进 `Hydra.hurt` 直接不插手（部件 hurt 内部还会走回父实体）——与第 1 条双重兜底。
- 复核：`tools/MixinSelfCheck` 全绿（把暮色 jar 一起放进 classpath）；jar 内核验：`hurtResolvingParts`
  已无残留、`hurtNearestPart` 存在、重入保护在位。
- 交付：版本 `1.5.47`；jar 复制到 `C:/Users/volans/Desktop/lensouls-1.5.47.jar`（5,941,031 字节），
  MD5 `B49E29395FA91F9FECA217C42ABFD19E`；`1.5.46` 作废。
- **教训（通用）**：任何「在 A 的 `hurt` 里改派给 B、失败再回退 `A.hurt`」的写法都是**自递归**。
  在实体的 `hurt` 内做伤害转发时，只能**单向派发 + 重入保护**，绝不回退到入口方法。

## 兼容（`1.5.48`）：gytrinket 无人机伤害豁免「非弱点 ×0.1 惩罚」

### 63. 需求与实现

- 需求（用户）：「让该模组几种无人机造成的伤害，绕过非弱点的伤害损失」
  （模组源码：`E:\volans\Downloads\新建文件夹 (3)\gytrinket-1.21.1neoforge`，mod id `gytrinket`）。
- 现状：`DamageHandler` 里原本只有一行
  `boolean isGytrinket = sourceEntity != null && sourceEntity.getClass().getName().contains("gytrinket");`
  —— 只看伤害源的 `getEntity()`（攻击者）类名。
- 逐条核对该模组的无人机伤害路径（源码实测）：
  | 路径 | 伤害源 | 旧判定 |
  |---|---|---|
  | `DroneBullet:223`、`ExplosiveProjectile:74/85`（无人机子弹/爆炸弹） | 类型 `gytrinket:drone_bullet`，causing entity 常是**玩家**（构造体归玩家所有） | **漏** ✗ |
  | `DroneBeamProjectile:160-169`（光束） | 原版 `mobAttack` / `indirectMagic`，攻击者=无人机实体 | 命中 ✓ |
  | `SwarmConstructEntity:566`（蜂群电弧） | 类型 `gytrinket:swarm_damage`（direct=entity=蜂群） | 命中 ✓ |
  | `MeleeWeaponMode:118`、`InterceptorChargedHandler:385`、`AbstractConstructEntity:488`（近战/横扫） | 原版 `mobAttack(构造体)` | 命中 ✓ |
  | `ModDamageTypes.getExecuteDamageSource(player, player)`（僚机斩杀） | 类型 `gytrinket:execute_damage`，direct=entity=**玩家** | **漏** ✗ |
- 修法：把那一行换成 `isGytrinketDamage(DamageSource)`（与既有 `isKrakenCannonball` 同款写法），
  三种特征任一命中即认定：
  1. **伤害类型命名空间 = `gytrinket`**（覆盖 `drone_bullet` / `swarm_damage` / `execute_damage` /
     `siphon_damage` 等自有类型）；
  2. **直接实体或攻击实体的类名以 `com.gytrinket.` 开头**（覆盖用原版伤害类型的光束与近战）；
  3. **直接实体或攻击实体的实体类型命名空间 = `gytrinket`**（与类名互为兜底，对方改包名也稳）。
  三者缺一必漏：只看 ① 会漏原版类型的光束/近战；只看 ②（旧实现）会漏「causing entity 是玩家」的子弹与斩杀。
- 复核：`tools/MixinSelfCheck` 全绿；jar 内核验 `isGytrinketDamage` 在位、`gytrinket` 命名空间比对 2 处、
  `com.gytrinket.` 前缀 1 处、旧 `sourceEntity` 写法已无残留。
- 交付：版本 `1.5.48`；jar 复制到 `C:/Users/volans/Desktop/lensouls-1.5.48.jar`（5,941,136 字节），
  MD5 `EEBBF9DFB9DF6DDA1966B476E201C75F`；`1.5.47` 作废。**未提交**。
- **待实机验证**：① 无人机（子弹/蜂群/光束/僚机斩杀）打有非弱点配置的目标 → 不再被砍成 10%；
  ② 玩家自己的普通武器打同一目标 → 仍然吃 ×0.1（豁免不能泄漏到玩家武器上）。

## 修复（`1.5.49`）：弱点透镜「记录弱点永远是火」+ 耐久条改原版款式

### 64. 根因：弱点顺序被 Map 打散，平手时退化成「枚举顺序 = 火」

- 现象（用户）：弱点透镜照片**记录的弱点永远是火**；实测生物明明配了水弱点；
  另外物品栏里的耐久条「比原版粗」。
- 排查（读代码 + 语言文件，**不依赖开发环境的数据包**）：
  - 语言文件正常：`item.lensouls.weakness_lens.element` = `§7记录弱点：%s`、`element.lensouls.*.short`
    火/水/土/末影 齐全 ⇒ **不是文案写死**；
  - 写入口正常：`AbilityBehavior.writePhotoData`（WEAKNESS_LENS 分支）与
    `WeaknessLensPhoto.writeInstalled` / `PhotoGuiMenu` 都是**动态**取 `weaknessElementOf(entityId)`，
    没有 `ElementDamage.FIRE` 之类的兜底（全仓 grep 确认）；
  - `ElementDamage.byName` 返回 null 而非默认值；`parseElement` 对空/非法串返回 null；
    `getAllWeaknesses` 只返回**显式配置**项（`getWeakness` 的 0.1 兜底不在其中）⇒ 这些都没问题；
  - **真正的坑在顺序**：`weaknessElementOf` 原本遍历 `ElementDamage.values()`（**枚举顺序**）去查 Map，
    于是「倍率平手」时永远取到枚举里最靠前的 **fire** ✗；而更上游还有三处**把数据包书写顺序丢掉**：
    1. `DataPackLoader.parseElementMap` 用 `new HashMap<>()`；
    2. 同文件多文件合并用 `new HashMap<>(oldMap)`；
    3. 客户端同步 `DatapackSyncPacket.decodeWeakness` 用 `HashMap` + `Map.copyOf`（两者都不保序）。
    ⇒ 数据包里「水在前、火在后（或同倍率）」的意图被抹掉，最终一律记录成火。
- 修法：
  1. 三层全部改为 **保序**：`LinkedHashMap`（解析 / 合并）+ `Collections.unmodifiableMap(LinkedHashMap)`
     （客户端解码，替换 `Map.copyOf`）；
  2. `WeaknessLensPhoto.weaknessElementOf` 改为**按数据包书写顺序**遍历 `entrySet()`，
     取倍率最高者、**平手取先写者**；并在 javadoc 里写明「**不要退回 `ElementDamage.values()` 枚举遍历**」；
  3. 「无显式（非弹射物）弱点 ⇒ 返回 null ⇒ 照片**不记录任何弱点**」这一语义在 javadoc 里明确写出
     （调用方本来就只在非 null 时写键，行为不变）。
- 耐久条（`client/WeaknessLensDurabilityDecorator`）：原版 `ItemRenderer.renderGuiItemDecorations` 是
  **底色 13×2 纯黑 + 前景彩色只占上面 1 行**；此前我们把前景也画满 2 行 ⇒ 视觉上粗一倍。
  现前景改为 `y + 1`（1 像素），其余（+2/+13 偏移、HSV 绿→红、满耐久不画）保持与原版一致。
- 复核：`tools/MixinSelfCheck` 全绿；jar 内核验 `DataPackLoader` 用 `LinkedHashMap`、
  `WeaknessLensPhoto` 走 `entrySet`（不再是枚举 `values()`）、`DatapackSyncPacket` 用 `LinkedHashMap`。
- 交付：版本 `1.5.49`；jar 复制到 `C:/Users/volans/Desktop/lensouls-1.5.49.jar`（5,941,355 字节），
  MD5 `C2B8686D7B8E3C53DA836C431AF70B11`；`1.5.48` 作废。**未提交**。
- 顺带：按用户要求删除工作区里的临时脚本/中间产物目录 `.probe`（270 个文件）；
  后续排查记录一律直接写进 `AGENTS.md`，不再留临时脚本。
- **待实机验证**：① 拍有水弱点的生物 → tooltip 显示「记录弱点：水」；
  ② 拍没有显式弱点的生物 → 不显示该行（也不该写进照片 NBT）；
  ③ 物品栏耐久条与原版同宽同高（彩色部分 1 像素）。

### 64.1 更正（`1.5.50`）：弱点「全是火」的真因是**主体取成了玩家自己**

- 结论：上述 §64 的「平手取枚举顺序」修复本身没错（弱点优先级现在按数据包书写顺序取最高），
  但**不是**「记录弱点全是火」的原因。真因是**照片主体被解析成了相机持有者（玩家自己）**。
- 机制（逐步可复现）：
  1. Exposure 的 `EntitiesInFrame.get` 用**眼睛**预筛，玩家自己天然被排除（眼睛就在相机位置，depth ≈ 0）；
  2. 但 `FrameEntities.assemble` 的**补回**扫描附近 `LivingEntity` 时**没有排除相机持有者** ⇒
     视线朝下时玩家自己的脚/身体就落在视锥里，而且「对自己的射线」不会被自己的碰撞箱挡住
     （`ClipContext` 的 context 就是目标自身）⇒ `CameraVisibility.isVisible(...)` **判定通过** ⇒ 玩家被补进列表；
  3. 列表随后按「到相机的距离」升序排序，玩家距离 ≈ 0 ⇒ **必然落在 `get(0)`**；
  4. 下游所有「取第 0 个当主体」的消费者（`WeaknessLensPhoto.subjectEntityId` = 弱点透镜记录弱点、
     `PhotoInjectionHandler` 的能力窃取主体、时间定格…）于是都指向**玩家自己** ⇒
     弱点透镜记录的是 `minecraft:player` 的弱点配置 ⇒ **每张照片都是同一个元素**（用户看到「全是火」）。
- 修法：`FrameEntities.assemble` **三处**排除相机持有者——收窄循环、子部件→父实体、本体补回
  （`holderIsLiving && living == cameraHolder`），并在方法体里写死警告注释，说明「补回最容易漏的就是持有者本身、
  以及它为什么必然落到 `get(0)`」。字节核验：`assemble` 内 `if_acmpne` 3 处（修复前 0 处）、
  局部变量 `holderIsLiving` 在位。
- 交付：版本 `1.5.50`；jar 复制到 `C:/Users/volans/Desktop/lensouls-1.5.50.jar`（5,941,418 字节），
  MD5 `1E73A8C79E795FA9ED5A27DC9FFA35F9`；`1.5.49` 作废。**未提交**。
- **待实机验证**：① 拍有水弱点的生物 → 「记录弱点：水」；换一只弱点不同的生物再拍 → 显示对应元素
  （不再是恒定同一个）；② 拍无显式弱点的生物 → 不显示该行；③ 顺带确认「能力窃取」不会再窃取到玩家自己。
- **教训**：任何「在已有结果上做补回/放宽」的逻辑，都必须显式排除**发起者自身**；
  且下游若用 `get(0)` 表达「最近主体」，排序前的候选集合就必须先保证语义正确。

### 66. 需求：更换成功不再提示（`1.5.52`）

- 需求（用户）：更换剑槽照片成功后**不要发送消息**。
- 改法（`handler/WeaknessLensHandler.install`）：把原来「按 `replaced.isEmpty()` 二选一」的
  `displayClientMessage` 改成**只在首次装入时**发动作栏消息；**更换路径完全不发消息**
  （旧照片是原地对调回那只手，玩家自己看得见，再弹提示是噪音）。
- 语言键 `message.lensouls.weakness_lens.swapped` 保留但**已无人引用**——按「lang 只追加键」的惯例不删，
  以免以后又需要时找不到文案。
- 复核：jar 内 `WeaknessLensHandler` 的 `displayClientMessage` 只剩 **1 处**（首次装入），
  常量池内已无 `weakness_lens.swapped`；`tools/MixinSelfCheck` 全绿。
- 交付：版本 `1.5.52`；jar 复制到 `C:/Users/volans/Desktop/lensouls-1.5.52.jar`（5,941,431 字节），
  MD5 `6F0D379BA9825A37CF638C06C9A12BC5`；`1.5.51` 作废。**未提交**
  （待提交批次共 5 个：`1.5.48` gytrinket 豁免 / `1.5.49` 弱点顺序 + 耐久条 / `1.5.50` 主体修正 /
  `1.5.51` 换照片回手 / `1.5.52` 更换不提示）。
