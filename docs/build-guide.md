# 构建与运行指南（Trae Agent 用）

## 项目位置

```
E:\volans\Documents\GitHub\lensouls\lensouls-template-1.21.1
```

**所有 Gradle 命令必须在上述目录下执行。** Trae agent 默认 workspace 是仓库根目录，但构建只在 `lensouls-template-1.21.1/` 下有效。

## 常用命令

```bash
# === 编译 ===
./gradlew compileJava          # 只编译 Java（最快，不改资源）
./gradlew build                # 完整构建（编译 + 资源处理 + 打包 jar）
./gradlew assemble             # 打包 jar 但不跑测试

# === 运行 ===
./gradlew runClient            # 启动 Minecraft 客户端（自动编译）
./gradlew runServer            # 启动专用服务器

# === 数据生成 ===
./gradlew runData              # 运行数据生成器（配方、loot table 等）

# === 清理 ===
./gradlew clean                # 清空 build/
./gradlew --refresh-dependencies  # 刷新缓存依赖

# === 调试 ===
./gradlew compileJava --info   # 详细编译日志
./gradlew build --stacktrace   # 出错时打印完整堆栈
```

## 常见问题

### 编译通过但 runClient 没有生效
Gradle 有增量编译，如果只有 `runClient`、`build` 等 task 会触发重新编译。但**如果改了 resources（着色器 JSON、纹理、mixins.json），必须 `clean` 再 build**，否则旧资源可能被缓存。

```bash
./gradlew clean runClient      # 稳妥做法：清缓存再启动
```

### 重复的 refmap 警告
`build/refmap` 下有自动生成的文件可能导致困惑。**无视它**——手动维护的是 `src/main/resources/lensouls.mixins.refmap.json`（空占位文件），NeoForge 处理器会自动插桩。

### 找不到符号 / 编译错误

常见原因：
1. JDK 版本不对——必须 **Java 21**
2. 依赖模组的 jar 未更新——`./gradlew --refresh-dependencies`
3. 映射表过期——`./gradlew clean --refresh-dependencies`

### runClient 卡在 "Downloading..."
首次运行会下载 Minecraft assets 和依赖模组（约 1-2GB），需要联网。之后会缓存。

### 配置文件位置

```
run/config/          # 运行时配置
run/logs/            # 运行日志
build/reports/       # 编译问题报告
```

### 多模组模组开发环境
此项目引用了 `compileOnly` 的灾变和传奇怪物模组。它们不需要在运行时存在（有 `ModList.get().isLoaded()` 保护），但编译时需要它们的 jar 在 gradle 缓存中。

## Windows 特有

- 在终端（cmd、PowerShell、Git Bash）中执行——**不要**在 VS Code 内置终端之外的特殊 shell 中运行
- 路径中的反斜杠和空格：`./gradlew` 会自动处理，不要手动 cd 到带空格的路径
- 如果 `./gradlew` 提示权限被拒绝，用 `bash gradlew` 或 `cmd /c gradlew.bat`

## Trae Agent 常见失误

1. **工作目录不对** — 先 `ls lensouls-template-1.21.1/build.gradle` 确认在正确子目录
2. **编译时没有看错误输出** — `compileJava` 会打印具体错误行号，滚动到输出末尾附近看
3. **只改了代码没重启 runClient** — runClient 是热启动，但改 mixin 或着色器时必须重启
4. **用 `cd` 切换目录而不是 `workdir` 参数** — 在 bash tool 中使用 `workdir` 参数指定目录
