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
