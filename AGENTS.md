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
