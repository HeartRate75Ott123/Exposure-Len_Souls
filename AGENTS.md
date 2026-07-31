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

## 工作区结构（只读参考项目）

| 目录 | 用途 |
|------|------|
| `lensouls-template-1.21.1/` | **主力项目**（git 仓库根） |
| `Exposure/` `ExposurePolaroid/` | 相机模组源码，出片/帧流程查阅 |
| `[灾变] L_Ender's Cataclysm 1.21.1-3.32/` | 灾变解压 class，`javap -p -c` 反编译查桶/上限逻辑 |
| `Legendary-Monsters-1.21.1-NeoForge/` | 传奇怪物源码 |

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

## 跨模组依赖

- Exposure/ExposurePolaroid = implementation（必装）；JEI/Jade = compileOnly（jar 在 `run/mods/`）
- 灾变/传奇怪物相关代码用反射/类名判断（`BossPhantomType.isModLoaded()`），无编译依赖
- 新增元素：改 `ElementDamage` 枚举 + `ModEffects` + `ModItems` + `ModCreativeTabs` + 弱点数据包 + 语言文件（见 CLAUDE.md）
