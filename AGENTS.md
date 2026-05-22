# AiReplayMate Agent 初始化说明

这份文件记录 AI 代理进入本仓库后应该优先读取和复用的本地环境事实，避免每次重复探测 Gradle、JDK 和模块路径。

## 当前本地环境

- 工作区根目录：仓库根目录 / 当前工作目录
- 默认 Shell：Windows PowerShell
- 时区：`Asia/Shanghai`
- Android SDK 路径：`C:\Users\lonqu\AppData\Local\Android\Sdk`
- 命令行构建优先使用的 JDK：Android Studio JBR，路径为 `C:\Program Files\Android\Android Studio\jbr`
- 本机可用的备选 JDK 17：`C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot`

在当前 Windows 命令行环境里跑 Gradle 前，先初始化 Java 环境：

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
```

这是当前 PowerShell 会话内的临时设置，不会修改 Windows 全局环境变量，也不会影响 Android Studio 自己的 JDK 选择。

## Gradle 工程形态

- Gradle 根工程是仓库根目录。
- 当前只有一个 Android App 模块，Gradle 路径是 `:app`。
- `settings.gradle.kts` 中通过 `include(":app")` 声明模块，并把 `:app` 映射到物理目录 `android/app`。
- Gradle 任务路径统一使用 `:app:*`，不要使用 `:android:app:*`。

已验证可用的构建命令：

```powershell
.\gradlew.bat :app:assembleDebug
```

最近一次使用 Android Studio JBR 本地验证结果：`BUILD SUCCESSFUL`。

## Agent 启动清单

1. 先读本文件。
2. 编辑前确认工作区状态：

   ```powershell
   git status --short
   ```

3. 需要产品或架构上下文时，优先读取：

   - `docs/CURRENT_STATUS.md`
   - `docs/PRD.md`
   - `docs/ENGINEERING_SPEC.md`
   - `docs/AI_DEV_GUIDE.md`

4. 需要环境和构建上下文时，优先读取：

   - `docs/DEV_SETUP.md`
   - `settings.gradle.kts`
   - `android/app/build.gradle.kts`

5. 代码改动后的最小验证优先使用：

   ```powershell
   $env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
   $env:Path="$env:JAVA_HOME\bin;$env:Path"
   .\gradlew.bat :app:assembleDebug
   ```

## 注意事项

- `local.properties` 是本机配置文件，不要提交到版本库。
- 不要把 API Key、Token、截图、私聊内容等敏感信息写进文档。
- 本应用的产品边界是辅助生成和填入回复；默认不应自动发送微信消息。
