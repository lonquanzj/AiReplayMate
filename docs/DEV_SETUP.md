# 开发环境与启动 — AiReplayMate

> 这份文档只描述当前仓库已经可验证的开发环境、构建方式和真机验证路径，不再把项目描述成“只有空骨架”。

## 1. 当前工程状态

当前 Android 工程仍是单模块，但已经具备可运行的 MVP 主链路：

- 首页可配置 LLM、回复风格、OCR、悬浮窗与诊断能力
- 已注册 `ReplyAccessibilityService`
- 已注册并可启动 `OverlayButtonService`
- 已有微信聊天页初判、消息提取、输入框定位与最小 Autofill 尝试
- 已有 `RealReplySessionRunner` 统一真实悬浮链路
- 已接入 OpenAI 兼容 LLM 请求与本地兜底候选
- 已接入 MediaProjection 截图链路和 ML Kit 中文 OCR
- 已有悬浮气泡、候选面板、等待态与多类诊断 UI

如果你要先确认整体进度，建议优先看：

1. [CURRENT_STATUS.md](/home/percy/AiReplayMate/docs/CURRENT_STATUS.md)
2. [README.md](/home/percy/AiReplayMate/README.md)
3. [AI_DEV_GUIDE.md](/home/percy/AiReplayMate/docs/AI_DEV_GUIDE.md)

## 2. 环境要求

基于当前 Gradle 配置，建议本地环境满足：

- Android Studio 近两年稳定版
- JDK `17`
- Android SDK：
  - `compileSdk = 35`
  - `targetSdk = 35`
  - `minSdk = 29`
- 一台 Android 10 及以上真机
- 真机上已安装微信

## 3. 目录入口

常用目录：

- 仓库根目录：`/home/percy/AiReplayMate`
- Android 工程目录：`/home/percy/AiReplayMate/android`
- 主模块：`/home/percy/AiReplayMate/android/app`

关键文件：

- [android/app/build.gradle.kts](/home/percy/AiReplayMate/android/app/build.gradle.kts:1)
- [AndroidManifest.xml](/home/percy/AiReplayMate/android/app/src/main/AndroidManifest.xml:1)
- [MainActivity.kt](/home/percy/AiReplayMate/android/app/src/main/java/com/lonquanzj/aireplaymate/MainActivity.kt:127)
- [ReplyAccessibilityService.kt](/home/percy/AiReplayMate/android/app/src/main/java/com/lonquanzj/aireplaymate/accessibility/ReplyAccessibilityService.kt:1)
- [OverlayButtonService.kt](/home/percy/AiReplayMate/android/app/src/main/java/com/lonquanzj/aireplaymate/overlay/OverlayButtonService.kt:45)

## 4. 构建方式

### Android Studio

1. 打开 `android/` 目录作为工程根目录
2. 等待 Gradle Sync 完成
3. 运行 `app` 模块的 `debug`

### 命令行

在 `android/` 目录执行：

```bash
./gradlew :app:assembleDebug
```

如果设备已连接，也可以安装：

```bash
./gradlew :app:installDebug
```

## 5. 首次真机验证顺序

建议第一次按这个顺序验证：

1. 安装 `debug` APK
2. 打开 App，确认首页渲染正常
3. 配置 `Base URL`、`API Key`、`Model`
4. 点击“测试连接”，确认 LLM 可用
5. 打开无障碍设置并启用服务
6. 打开悬浮窗权限，并在首页启动 AI 气泡
7. 进入微信单聊页，短按悬浮气泡测试候选生成
8. 如上下文不足，再验证 OCR 授权、截图链路和 OCR 识别

## 6. 当前可验证内容

当前版本已经可以验证：

1. 首页设置与诊断 UI
2. 无障碍服务连接状态
3. 微信聊天页识别和消息样本提取
4. LLM 测试连接与请求诊断
5. 悬浮气泡启动、等待态、候选面板
6. 真实悬浮链路的 LLM / 本地兜底切换
7. OCR 授权、截图链路和 OCR 识别测试
8. 输入框最小 Autofill 尝试

当前仍需谨慎看待的内容：

- OCR 真机质量仍需继续调参
- Autofill 兼容性因机型差异可能不稳定
- 群聊和复杂消息结构仍不算稳定支持

## 7. 权限与系统能力

当前重点能力依赖：

- 无障碍权限
- 悬浮窗权限
- MediaProjection 截图授权

相关声明见 [AndroidManifest.xml](/home/percy/AiReplayMate/android/app/src/main/AndroidManifest.xml:1)。

## 8. 真机调试建议

### 查看日志

建议过滤关键字：

```bash
adb logcat | grep AiReplayMate
```

可重点关注：

- 无障碍连接状态
- 微信页面识别
- OCR 触发和 OCR 步骤
- LLM 请求结果
- Overlay 诊断与 Autofill 结果

### 优先查看 App 内诊断

相比纯看 logcat，当前更推荐先看首页的诊断面板：

- LLM 诊断
- OCR 诊断
- 悬浮窗诊断
- 诊断日志摘要

## 9. 常见问题

### Android Studio Sync 失败

优先检查：

- JDK 是否为 `17`
- Android SDK 35 是否已安装
- Gradle 依赖是否能正常下载

### App 能打开，但悬浮气泡没有出现

优先检查：

1. 悬浮窗权限是否已开启
2. 首页是否点击了“启动/刷新气泡”
3. 首页悬浮窗状态卡片是否显示服务已运行

### 气泡点了没候选

优先检查：

1. 当前是否在微信单聊页
2. LLM 设置是否有效
3. 悬浮窗诊断里停在了哪个阶段
4. 是否已经自动回退到本地兜底

### OCR 看起来没生效

优先检查：

1. 是否已授予截图权限
2. 是否走到了 `Accessibility 上下文不足`
3. OCR 诊断里是否有截图和识别步骤记录

## 10. 下一步建议

如果是 AI 代理继续开发，建议优先看：

1. [AI_DEV_GUIDE.md](/home/percy/AiReplayMate/docs/AI_DEV_GUIDE.md)
2. [CURRENT_STATUS.md](/home/percy/AiReplayMate/docs/CURRENT_STATUS.md)
3. [ENGINEERING_SPEC.md](/home/percy/AiReplayMate/docs/ENGINEERING_SPEC.md)
