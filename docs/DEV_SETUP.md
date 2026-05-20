# 开发环境与启动 — AiReplayMate

> 这份文档只描述当前仓库可验证的开发环境和启动方式，不预设尚未落地的模块能力。

## 1. 当前工程状态

当前 Android 工程仍是单模块骨架：

- 模块：`android/app`
- 已有 Compose 首页
- 已注册 `ReplyAccessibilityService`
- 首页可跳转到无障碍设置、悬浮窗设置
- 首页可展示真实无障碍调试状态与 Demo 会话主链路
- 已有微信聊天页初判、结构化消息采样、输入框定位与最小 Autofill 尝试
- 已有最小 ContextBuilder，可整理 Accessibility 上下文后再进入候选生成演示
- 已有最小 Overlay 悬浮按钮和系统级候选面板，可从微信页直接选择候选并尝试填入
- 尚未接入 OCR、真实 LLM、Room、DataStore、Hilt、多模块拆分

如果你要了解产品边界和后续实现约束，请继续看：

1. [PRD.md](/home/percy/AiReplayMate/docs/PRD.md)
2. [ENGINEERING_SPEC.md](/home/percy/AiReplayMate/docs/ENGINEERING_SPEC.md)
3. [CURRENT_STATUS.md](/home/percy/AiReplayMate/docs/CURRENT_STATUS.md)

## 2. 环境要求

基于当前 Gradle 配置，建议本地环境满足：

- Android Studio：近两年稳定版即可
- JDK：`17`
- Android SDK：
  - `compileSdk = 35`
  - `targetSdk = 35`
  - `minSdk = 29`
- Kotlin：`1.9.24`
- Android Gradle Plugin：`8.5.2`

建议准备一台真机用于调试：

- Android 10 及以上
- 已安装微信
- 可手动开启无障碍权限和悬浮窗权限

说明：

- 目前仓库没有提交 `gradlew` / `gradlew.bat` / `gradle-wrapper` 文件
- 如果你本地没有全局 Gradle，先用 Android Studio 生成或补齐 Wrapper 再构建

## 3. 目录入口

常用目录：

- 仓库根目录：`/home/percy/AiReplayMate`
- Android 工程目录：`/home/percy/AiReplayMate/android`
- 主模块：`/home/percy/AiReplayMate/android/app`

关键文件：

- [android/build.gradle.kts](/home/percy/AiReplayMate/android/build.gradle.kts:1)
- [android/app/build.gradle.kts](/home/percy/AiReplayMate/android/app/build.gradle.kts:1)
- [AndroidManifest.xml](/home/percy/AiReplayMate/android/app/src/main/AndroidManifest.xml:1)
- [MainActivity.kt](/home/percy/AiReplayMate/android/app/src/main/java/com/lonquanzj/aireplaymate/MainActivity.kt:1)
- [ReplyAccessibilityService.kt](/home/percy/AiReplayMate/android/app/src/main/java/com/lonquanzj/aireplaymate/accessibility/ReplyAccessibilityService.kt:1)

## 4. 首次启动方式

### 4.1 用 Android Studio

1. 打开 `android/` 目录作为工程根目录。
2. 等待 Gradle Sync 完成。
3. 选择 `app` 模块，运行 `debug`。
4. 安装到真机后，打开应用。
5. 在首页确认悬浮窗权限已开启后，点击“启动气泡”。
6. 切回微信单聊页，点击屏幕上的 `AI` 小气泡触发会话演示。

### 4.2 用命令行

在 `android/` 目录执行：

```bash
gradle assembleDebug
```

如果已经有可用设备，也可以安装：

```bash
gradle installDebug
```

如果后续补齐了 Gradle Wrapper，优先使用：

```bash
./gradlew assembleDebug
./gradlew installDebug
```

## 5. 当前可验证内容

安装并启动后，当前版本应该只能验证这些点：

1. 应用可以正常打开。
2. 首页显示 Demo 主链路卡片、权限状态和真实无障碍调试信息。
3. 可以点击“打开无障碍设置”跳转系统设置。
4. 可以点击“打开悬浮窗设置”跳转系统设置。
5. 启用无障碍服务后，服务能识别是否像微信聊天页，并展示带角色的消息采样。
6. 在微信聊天页可尝试把草稿通过 `ACTION_SET_TEXT` 写入真实输入框。

当前还不能验证：

- 悬浮按钮触发
- OCR 截图识别
- 真实 LLM 候选生成
- Autofill 剪贴板兜底与结果验证

## 6. 真机调试建议

### 6.1 开启开发者能力

- 打开 USB 调试
- 连接 Android Studio 或 `adb`
- 安装 Debug 包

### 6.2 开启无障碍服务

安装应用后：

1. 打开 App 首页。
2. 点击“打开无障碍设置”。
3. 在系统无障碍页面找到 `AiReplayMate`。
4. 手动启用服务。

### 6.3 查看日志

当前 Accessibility 服务会打印基础日志，建议过滤关键字：

```bash
adb logcat | grep AiReplayMate
```

可重点关注：

- `Accessibility service connected`
- `event=... package=... class=...`
- `Accessibility service interrupted`

## 7. 当前已声明的权限

见 [AndroidManifest.xml](/home/percy/AiReplayMate/android/app/src/main/AndroidManifest.xml:1)：

- `SYSTEM_ALERT_WINDOW`
- `POST_NOTIFICATIONS`
- `FOREGROUND_SERVICE`

注意：

- `MediaProjection` 相关授权流程还没落地
- Overlay 目前只有权限入口，还没有真正的悬浮按钮服务

## 8. 常见问题

### 8.1 Android Studio Sync 失败

优先检查：

- JDK 是否为 `17`
- Android SDK 35 是否已安装
- Gradle 依赖是否能正常下载

### 8.2 App 能打开，但功能很少

这是当前仓库的真实状态，不是你本地跑错了。现阶段仍是骨架工程。

### 8.3 无障碍服务已开启，但没有业务效果

目前服务已经接入微信页面初判、文本采样和最小自动填入尝试，但还没有打通真实候选生成、OCR 和完整降级链路。

## 9. 下一步推荐

如果你准备继续开发，建议优先看：

1. [CURRENT_STATUS.md](/home/percy/AiReplayMate/docs/CURRENT_STATUS.md)
2. [DELIVERY_PLAN.md](/home/percy/AiReplayMate/docs/DELIVERY_PLAN.md)
3. [ENGINEERING_SPEC.md](/home/percy/AiReplayMate/docs/ENGINEERING_SPEC.md)
