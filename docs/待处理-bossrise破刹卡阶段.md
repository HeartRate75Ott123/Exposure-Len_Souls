# 待处理：首领崛起（BossRise）BOSS 破刹卡阶段

> 状态：**待处理**（用户要求先忽略，待后续指示）。记录于 2026-08。

## 问题描述

模组：`block_factorys_bosses-2.1.2-neo-1.21.1`（首领崛起，net.unusual.block_factorys_bosses）。

破定（破刹）期间将 BOSS 打到转阶段线会导致卡阶段：

- **冥界骑士**（UnderworldKnightEntity）：转阶段在 1 血（假死复活），破定期间打到 1 血卡阶段
- **雪怪**（YetiEntity）：半血转（狂暴），破定期间打过半血卡阶段
- **飞龙**（InfernalDragonEntity）：破定期间打下去卡阶段（解冻后可能永久卡 1 血）

## 已确认机制（反编译 2026-08，vineflower）

### 我们这边

- `mixin/BossStunTickMixin.java`：破刹（韧性清空）与时间定格期间 `LivingEntity.tick` HEAD `ci.cancel()` **完全跳过实体刻**；`isDeadOrDying()` 放行（tickDeath 需要执行）
- `mixin/BossStunHurtMixin.java`：暂停期间清无敌帧（每击必中）+ RETURN 清 hurtTime
- 伤害不暂停、不锁血（用户确认："我们的破定没有锁血，但是暂停了实体刻"）
- 判定入口：`boss/StunPauseHelper.java`（服务端 `BossToughnessData.isBroken()` / `TimeFreezeManager.isEntityFrozen`）

### BossRise 三 BOSS（反编译结论）

| BOSS | 转阶段触发点 | 推进位置 | 破刹期间后果 |
|---|---|---|---|
| 冥界骑士 | hurt 内死亡取消：`shouldCancelDeath()` phase0→1 设 `fake_dead` + `setHealth(0.1F)`（锁 1 血）；phase2→3 跳走；phase2 有 0.75/0.5/0.25 hpGate | **tick**：`fake_dead`→`resurrect`→回满血 + phase2 + hpGate 重置 | 卡在 fake_dead / 1 血 |
| 雪怪 | hurt 内：死亡直接 `setState(DEATH)` + 锁 0.1 + 掉宝；半血 `enrage 2→3`（狂暴）；20% 终极（ICE_BARRAGE） | **tick**：dieAnimtime 递减 → `hurt(999)` 真杀；enrage 3→4→5 | 卡在狂暴/死亡动画入口 |
| 飞龙 | **转阶段全在 tick**：healthRatio 0.85→knocked_down+phase1、0.5→phase3、0.25→`performPhaseTransition`（phase4→5 电影化）；死亡取消→dead+锁 0.1+dieAnimtime=88 | tick 内 | 卡 1 血；解冻后 `if (!(health <= 0.1) && !isDeath())` **永久跳过**转阶段 |

**统一根因**：转阶段/死亡动画推进全部依赖 `LivingEntity.tick`；破刹暂停 tick → 状态（fake_dead/enraged/dead）已设置但推进不执行 → 卡在触发瞬间；飞龙解冻后永久卡死。

**修复方向（未定稿，曾给用户三个选项）**：
- A：破刹期间所有韧性 BOSS tick 照跑 + 只停 AI（新增 `Mob.serverAiStep` mixin early-return）；时停保持完全暂停。一劳永逸，但破刹视觉从"完全静止"变"站桩但动画/受击照常"
- B：白名单只豁免 BossRise 三 BOSS（类名匹配，无编译依赖）
- C：破刹期间伤害 clamp 到转阶段线以上（视觉保持，但输出被砍 + 每 BOSS 配置线）

## 新发现（未调查）

用户反馈：**BossRise 的 BOSS 破刹时其实没有被定住，仍然会攻击玩家**——说明韧性系统对它们未生效或 `isStunPaused` 判定失败（可能韧性未注册/未挂上）。这与"卡阶段"现象并存，原因未查。

## 恢复调查的起点

- 反编译产物：`C:\Users\volans\AppData\Local\Temp\opencode\bfboss\src\`（vineflower；temp 目录可能被清理，需要时重新反编译 jar 即可，jar 在 `E:\volans\Downloads\[首领崛起] block_factorys_bosses-2.1.2-neo-1.21.1.jar`）
- 关键源码文件：`knight\UnderworldKnightEntity.java`、`yeti\YetiEntity.java`、`dragon\boss\InfernalDragonEntity.java`、`AbstractBossEntity.java`（maybeCancelDeath/shouldCancelDeath）、`procedures\BossCancelDieProcedure.java`
- 我们这边：`mixin/BossStunTickMixin.java`、`mixin/BossStunHurtMixin.java`、`boss/StunPauseHelper.java`、`boss/BossToughnessManager.java`