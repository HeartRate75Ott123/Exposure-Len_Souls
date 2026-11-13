# 时间定格与破韧渲染修复 — 执行方案

## 目标

1. **时间定格**：改用原版全局 freeze（`ServerTickRateManager.setFrozen`）——静默、玩家可动、画面灰度（场景黑白 / 玩家·glint·描边彩色且遮挡正确）、视野内冻结生物蓝描边+蓝 glint、连击必中
2. **破韧**：实体级定格（现有机制）+ 渲染 `partialTicks` 固定 1.0（复刻 freeze 渲染语义，根治抽搐）；冻结中破韧 = 蓝描边 + 红 glint

## 架构总览

| 维度 | 时间定格 | 破韧 |
|------|---------|------|
| 服务端实体 tick | 全局不 tick（原版 freeze） | cancel 单个实体（`BossStunTickMixin`） |
| 无敌帧清除 | `BossStunHurtMixin` 加全局冻结判定 | 现有 `isToughnessBroken` 判定 |
| 客户端视觉 | 灰度 + 玩家 mask + glint FBO 叠加 + 描边 | `partialTicks→1.0`（`LevelRenderer.renderEntity`） |
| glint 颜色 | 蓝 | 冻结中：红（STUNNED 优先） |
| 描边 | 蓝（`FrozenOutlineManager`，穿透惯例） | 冻结中：蓝（mask 按冻结判定）；非冻结：BOSS 走 `BossOutlineManager` |

## 关键机制（原版 freeze 语义，已反编译确认）

- 服务端：`ServerTickRateManager.setFrozen(true)` → `ServerLevel.tick` 开头 `if (!runsNormally)` 跳过世界逻辑；实体 tick 循环检查 `runsNormally` → 实体不 tick（玩家例外，照常 tick）
- 客户端：`Minecraft.runTick` → `timer.updateFrozenState(isFrozen)` → `DeltaTracker.Timer.getGameTimeDeltaPartialTick(false)`：`if (!includeTickDelta && frozen) return 1.0F` —— **渲染 partialTicks 冻结时恒 1.0** → 所有 `lerp(p, old, cur)` 取当前值 → 不抖
- `setFrozen` 每次调用都广播 `ClientboundTickingStatePacket` → **只在开始/结束各调一次**

## 执行步骤

### Step 1 — `FreezeTracker` 改全局 freeze
`ability/util/FreezeTracker.java`
- `freeze(Player, int durationTicks)`：`server.tickRateManager().setFrozen(true)` 一次（静默）+ 记录来源 UUID + 广播 `FreezeSyncPacket(true)`
- `tick()`：只递减计时；到期 `setFrozen(false)` 一次 + 广播 `FreezeSyncPacket(false)`
- `isFrozen()` = server != null && `tickRateManager().isFrozen()`
- `isPlayerSource(Player)` = isFrozen && 来源 UUID 匹配（登出清理用）
- `unfreezePlayerSource(Player)`：来源匹配则全局解冻
- 删除：`isEntityFrozen`、`applyFreeze`、`unfreezeEntity`、`hasLiveFrozenEntities`、`forceUnfreeze`、targets 集合

### Step 2 — `FreezeSyncPacket` 载荷
`ability/network/FreezeSyncPacket.java`
- 载荷：`boolean frozen`；`handle` → `ClientFreezeCache.setTimeFrozen(frozen)`
- 发送：`PacketDistributor.sendToAllPlayers`

### Step 3 — `TimeFreezeHandler` 简化
`ability/handler/TimeFreezeHandler.java`
- 保留触发条件（`AbilityType.TIME_STOP` + `soul_photography` 附魔）
- `triggerTimeFreeze`：`tracker.isFrozen()` → 显示"已激活"文字拒绝；否则 `tracker.freeze(player, 100)`
- 删除：视锥筛选 `getEntitiesInFrustum`、韧性拦截（`BossToughnessManager`/`FreezeRejectParticlePacket` 相关）

### Step 4 — `BossToughnessManager` 移除互斥
`boss/BossToughnessManager.java:141`
- 删除 `unfreezeEntity(entity)`（时停中破韧并存）

### Step 5 — `BossStunHurtMixin` 无敌帧清除扩展
`mixin/BossStunHurtMixin.java`
- 判定：`isStunPaused(self) || isGlobalFreeze(self)`
- `isGlobalFreeze` = `!level().isClientSide && level().tickRateManager().isFrozen() && !(self instanceof Player)`
- HEAD 清 `invulnerableTime`、RETURN 清 `hurtTime=0`

### Step 6 — `ClientFreezeCache` 改造
`ability/client/ClientFreezeCache.java`
- `Set<Integer> FROZEN_IDS` → `boolean timeFrozen`（`setTimeFrozen`）
- `isFrozen(int id)` = `timeFrozen && level.getEntity(id) != null && !(entity instanceof Player)`（渲染路径天然视锥过滤）；保留 testMode 分支
- `hasAnyFrozen()` = `timeFrozen`（`FrozenOutlineManager` 合成闸门自动跟随）

### Step 6b — `StunPauseHelper` 客户端分支调整
`boss/StunPauseHelper.java`
- 客户端分支移除 `|| ClientFreezeCache.isFrozen(entity.getId())`（时间定格期间动画继续——原版行为；客户端不 cancel tick）
- 服务端分支 `isEntityFrozen` → `FreezeTracker.getInstance().isFrozen()`（防御性，freeze 期间实体本就不 tick，cancel 不触发）

### Step 7 — 视觉分离（蓝描边 + 红 glint）
`ability/client/StatusGlintBufferSource.java` + `mixin/client/EntityRenderDispatcherMixin.java`
- `StatusGlintBufferSource`：构造加 `boolean maskActive`；`getBuffer` mask 双写条件 `state == FROZEN` → `maskActive`；glint 颜色仍按 `state`（`resolveState` 互斥：STUNNED红 > FROZEN蓝 > INVINCIBLE白）
- `EntityRenderDispatcherMixin`：
  - HEAD 捕获判定：`resolveState(root)==FROZEN` → `ClientFreezeCache.isFrozen(root.getId())`（纯冻结）
  - `redirectRender`：`new StatusGlintBufferSource(buffer, state, ClientFreezeCache.isFrozen(root.getId()))`
- 效果：冻结中破韧 = 蓝描边(mask) + 红 glint；纯冻结 = 蓝描边+蓝 glint；纯破韧 = 红 glint 无蓝描边

### Step 8 — 破韧渲染修复（partialTicks 固定）
新文件 `mixin/client/LevelRendererMixin.java`，注册 `lensouls.mixins.json` client 段
- `@Mixin(LevelRenderer.class)` + `@ModifyVariable(method="renderEntity", at=@At("HEAD"), argsOnly=true)`（float partialTicks，第 5 参数，签名 `(Entity, double, double, double, float, PoseStack, MultiBufferSource)` mojmap 未混淆）
- handler：`StunPauseHelper.isToughnessBroken(entity) ? 1.0F : partialTicks`
- 等价原版 freeze 的 `getGameTimeDeltaPartialTick` 恒 1.0 → 所有插值取当前值 → 全静止

### Step 9 — 灰度渲染管线（遮挡正确）
#### 9a shader
- 新 `rendertype_gray_out`（vertex 复用 `rendertype_screen_quad.vsh`）：fragment 采样 `DiffuseSampler`(主画面) + `PlayerMaskSampler` + `PlayerMaskDepthSampler` + `MainDepthSampler`：
  ```
  playerFactor = maskAlpha>0.01 && maskDepth <= sceneDepth + 0.001 ? 1.0 : 0.0
  out = mix(vec3(luma(scene)), scene.rgb, playerFactor)
  ```
- 新 `rendertype_glint_overlay`（glint FBO 叠加）：采样 `GlintSampler` + `GlintDepthSampler` + `MainDepthSampler`，深度比较后混合
#### 9b 玩家 mask 收集（带深度）
- 新 FBO `playerMaskTarget`（`TextureTarget(w,h,true)`）——第三人称玩家实体渲染时双写（`EntityRenderDispatcherMixin` redirect 分支，`ClientFreezeCache.isTimeFrozen()` 时）
- 新 FBO `handMaskTarget`（`TextureTarget(w,h,false)`）——第一人称手部渲染时双写（`ItemInHandRendererMixin`，近景物理不可遮挡，不做深度比较）
#### 9c glint 独立 FBO（带深度）
- 冻结状态（`timeFrozen`）下 `StatusGlintBufferSource` glint 双写目标 → `glintTarget`（`TextureTarget(w,h,true)`），per-纹理 alpha 测试保留；破韧（非冻结）照旧写主 buffer
#### 9d 合成顺序与挂钩
- 冻结时每帧：主画面（含玩家）→ 灰度 pass（`grayOut`，playerMask 恢复玩家彩色）→ 叠加 glintTarget（深度比较）→ 描边合成（现有 `goldOutlineShader`）→ 输出
- 挂钩：`GameRendererMixin.renderItemInHand` RETURN（灰度 + glint 叠加在 `FrozenOutlineManager.compositeIfNeeded` 之前）+ `GameRendererFrameEndMixin.render` RETURN 兜底
- 主深度纹理：`Minecraft.getInstance().getMainRenderTarget().getDepthTextureId()`
- Iris：沿用现有 `mainRenderTarget` 绑定先例

## 不动
`BossStunTickMixin`（破韧 cancel 两分支）、`ToughnessSyncPacket`/`BossToughnessClientCache`、描边管线（CaptureState/MaskRenderTypes/FrozenBlueGlintRenderTypes/CompositeRenderTypes）、`EntityInvulnerableTimeAccessor`、`IrisBufferSourceGetBufferMixin`、`FreezeCleanupHandler`（调用签名不变）

## 验证清单
1. 编译：`.\gradlew compileJava --offline --no-daemon --console=plain -x createMinecraftArtifacts`
2. 时停：静默无消息；玩家可动可攻击；每击命中（无敌帧清）；5 秒到期恢复无跳变；不抽搐
3. 灰度：场景+冻结怪黑白；玩家（第一人称手臂/第三人称）彩色；遮挡正确（玩家站墙后不露彩色斑块；冻结怪被挡 glint 不穿透）；蓝描边+蓝 glint 彩色
4. 冻结中破韧：蓝描边 + 红 glint；纯破韧：红 glint、静止、无抽（九头蛇重点）
5. 回归：Boss 描边（BossOutlineManager）分离正常；Iris 下灰度/深度/描边正常

## 风险
- Step 8 `@ModifyVariable` 签名细节（编译报错提示为准）
- Iris 下主深度纹理绑定
- 第一人称手臂投影与主场景深度空间差异（handMask 直接恢复兜底）