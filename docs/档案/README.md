# docs/档案 —— 历史与已落地方案

这里放的是**不再作为事实源**的文档：写完就落地的执行方案、被推翻的设计、旧架构笔记、逐版本改动记录。
现行文档在 `docs/` 根目录，索引见 `AGENTS.md`「资料在哪」。

| 文件 | 是什么 | 状态 |
|---|---|---|
| `旧AGENTS-需求批次历史（1.4.91–1.8.89）.md` | 旧 AGENTS.md 全文（3720 行）：1.4.91→1.8.89 每批需求的根因、改法、交付记录，外加顶部的旧手册章节 | **考古主干**。想知道「某个功能为什么长这样」，先搜这里 |
| `CLAUDE-旧架构笔记（最后一次提交版）.md` | 2026-09 之前的 `CLAUDE.md`（git 最后一次提交的版本） | 「四能力」「BOSS 描边 mask FBO」等章节已与代码不符，仅作考古 |
| `描边系统-旧方案/` | 旧描边管线四篇（`soul-outline-system`、`glow-fbo-pipeline`、`frozen-outline`、`重构方案-双FBO解耦`）+ 旧 zip | 涉及的 `BossOutlineManager` / `BossMaskRenderTypes` / `SoulOutlineCompat` / `ItemInHandRendererMixin` **都不在 `src/main`**，备份在仓库的 `outline-system/` |
| `time-freeze-grayscale-plan（2026-08，已落地）.md` | 时间定格改原版全局 freeze + 破韧渲染修复的执行方案 | 已落地（现行实现看 `ability/util/TimeFreezeManager`、`ability/client/ClientFreezeCache`） |
| `build-guide（2026-07，旧构建速查）.md` | 早期给另一个 agent 写的构建/运行速查 | 内容已并入 `AGENTS.md` 与 `docs/机制与坑位.md` |
| `照片套装与相册设计（2026-10，22 套已全部废弃）.md` | 套装系统的早期设计：22 套特殊套装、`defs.json` 单文件、`rule: same_element` | 主体已废（22 套在现行 53 套成员表里 0 命中，字段不再解析）；仅「相册 = 栏位来源」「相册等效装备的三条不变量」仍有参考价值 |
| `强化界面背景-目标图规格（GLSL 版已放弃，留作美术参考）.md` | 「玻璃花」背景的目标图分析与三维着色器方案 | 实现改为预渲染贴图（`textures/gui/reinforce_background.png` + 40% 柔纱）；本文只剩像素统计与美术口径，别再照它写 GLSL |

约定：**往这里放东西只搬不改**（保持原样，便于回溯）；如果要修订口径，改 `docs/` 根的现行文档。
