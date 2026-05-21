# AiReplayMate

`AiReplayMate` 是一个面向 Android 的微信辅助回复项目，当前仓库里的应用展示名为 `AiChat`。项目目标是在不自动发送消息的前提下，帮助用户在微信单聊场景里更快生成候选回复，并把选中的候选安全地填入输入框。

项目当前已经具备一条可运行的 MVP 主链路：

- 通过 Accessibility 读取微信聊天页上下文
- 当 Accessibility 上下文不足时，回退到截图 + OCR
- 调用 OpenAI 兼容接口生成候选回复
- LLM 不可用时切换到本地兜底候选
- 通过悬浮气泡在微信上方展示候选并尝试填入输入框
- 默认不自动发送，只辅助填入

## 项目定位

这个项目不是“聊天机器人自动代聊”，而是一个强调边界和安全的辅助工具：

- 当前 MVP 目标只覆盖微信单聊
- 只辅助生成和填入，不自动点击发送
- 优先使用结构化上下文，无法提取时再走 OCR
- 提供诊断信息，帮助定位权限、提取、OCR、LLM 和填入问题
- 支持多种回复风格，但不会绕过用户最终确认

## 当前能力

截至当前仓库状态，已经落地的能力包括：

- Android 应用基础工程可编译，`debug` 包可正常构建
- Compose 首页可配置 LLM、回复风格、OCR、悬浮窗等能力
- 已注册无障碍服务、悬浮窗服务、基础前台相关权限
- 可识别当前是否处于微信聊天页，并抽取可用聊天消息
- 已有上下文整理器，可对 Accessibility / OCR 消息去重、过滤、截断
- 已接入 OpenAI 兼容 `Base URL + API Key + Model` 配置
- 已有真实 LLM 请求、响应解析、失败分类和诊断展示
- 已接入 ML Kit 中文 OCR，用于上下文不足时的兜底识别
- 已有悬浮气泡，可短按快速生成，长按打开风格菜单
- 已支持快速回复、话术宝典、润色表达三类回复模式
- 已支持候选来源标识，例如 `LLM`、`本地兜底`、`含 OCR 上下文`
- 已支持 Autofill 尝试链路和最小剪贴板粘贴兜底
- 已有 LLM / OCR / Overlay 三类诊断与摘要日志
- 已补充一套紫色风格应用图标和更轻量的悬浮 UI 样式

更具体的当前进度见 [docs/CURRENT_STATUS.md](/home/percy/AiReplayMate/docs/CURRENT_STATUS.md)。

## 功能说明

### 1. 微信辅助回复

项目会先尝试通过 Accessibility 从当前微信聊天页提取最近消息，整理出最小 `ChatContext`，再基于配置好的风格和上下文生成候选回复。

### 2. OCR 兜底

当 Accessibility 提取到的消息不够支撑生成时，项目会通过 MediaProjection 抓取当前屏幕，再使用 ML Kit 中文 OCR 识别聊天文本，并与已有上下文合并。

### 3. 候选生成

候选生成优先走 OpenAI 兼容 LLM；如果配置缺失、网络失败、HTTP 异常或解析失败，则会切换到本地兜底候选，以保证悬浮链路不至于完全失效。

### 4. 悬浮气泡

微信上方会出现一个圆形紫色悬浮气泡：

- 短按：使用默认风格快速生成候选
- 长按：打开轻量风格菜单
- 等待中：显示加载态和进度面板
- 完成后：展示候选列表，用户点击后尝试填入输入框

### 5. 回复风格

当前已支持三类风格：

- `快速回复`：结合聊天上下文，按默认角色生成回复
- `话术宝典`：按固定场景生成更偏模板化的话术
- `润色表达`：读取当前输入框草稿，对原文进行润色

可选角色和目标包括：

- 角色：`情场高手`、`霸道总裁`、`成熟大叔`、`细腻暖男`
- 润色目标：`更自然`、`更暧昧`、`更稳妥`
- 话术宝典场景：破冰、问候、升温、兴趣互动等

### 6. 诊断与调试

首页已提供多种诊断能力：

- 无障碍状态与样本查看
- LLM 配置校验、测试连接、最近请求诊断
- OCR 授权状态、截图链路、识别结果诊断
- 悬浮窗服务状态、真实触发阶段、候选来源、失败摘要
- 最近诊断日志复制与清空

## 项目架构

当前还是单模块 Android 工程，但代码职责已经按领域拆开。核心目录如下：

```text
android/app/src/main/java/com/lonquanzj/aireplaymate
├── MainActivity.kt                  # Compose 首页与调试入口
├── accessibility/                   # 微信页面识别、消息提取、输入框填入
├── context/                         # ChatContext 构建与上下文整理
├── diagnostics/                     # 摘要级诊断日志
├── llm/                             # OpenAI 兼容网关、响应解析、LLM 诊断
├── ocr/                             # 截图授权、截图、OCR 识别与后处理
├── overlay/                         # 悬浮气泡、候选面板、Overlay 诊断
├── prompt/                          # Prompt 构建、回复风格模型、请求模型
├── session/                         # Demo 状态机、真实会话执行器、本地兜底
└── settings/                        # LLM 配置与风格配置持久化
```

### 主链路执行顺序

当前真实悬浮链路大致如下：

1. 用户在微信单聊页点击悬浮气泡
2. `OverlayButtonService` 发起真实会话
3. `RealReplySessionRunner` 校验当前页面和服务状态
4. `ContextBuilder` 基于 Accessibility 整理聊天上下文
5. 如果上下文不足，则切换 `OCR` 兜底
6. `PromptBuilder` 根据上下文和风格构造请求
7. `LlmGateway` 请求 OpenAI 兼容接口
8. 如果失败，则切换 `LocalFallbackReplyGenerator`
9. 悬浮面板展示候选，用户选择后执行 Autofill

关键入口文件：

- [MainActivity.kt](/home/percy/AiReplayMate/android/app/src/main/java/com/lonquanzj/aireplaymate/MainActivity.kt:127)
- [OverlayButtonService.kt](/home/percy/AiReplayMate/android/app/src/main/java/com/lonquanzj/aireplaymate/overlay/OverlayButtonService.kt:45)
- [RealReplySessionRunner.kt](/home/percy/AiReplayMate/android/app/src/main/java/com/lonquanzj/aireplaymate/session/RealReplySessionRunner.kt:17)
- [ReplyAccessibilityService.kt](/home/percy/AiReplayMate/android/app/src/main/java/com/lonquanzj/aireplaymate/accessibility/ReplyAccessibilityService.kt:1)

## 技术栈

- Kotlin
- Jetpack Compose
- Android AccessibilityService
- MediaProjection
- ML Kit Chinese Text Recognition
- OpenAI 兼容 HTTP API
- SharedPreferences
- Gradle Kotlin DSL
- Java 17 / Kotlin JVM 17

当前 Android 目标配置：

- `minSdk = 29`
- `targetSdk = 35`
- `compileSdk = 35`

## 快速开始

### 环境要求

- Android Studio 新版本
- JDK 17
- 一台可调试的 Android 真机
- 可用的 OpenAI 兼容接口，或至少接受本地兜底效果

### 构建

项目使用标准 Gradle 多项目结构，根目录即为构建入口。

在仓库根目录执行：

```bash
./gradlew :app:assembleDebug
```

### 安装与验证建议

首次真机验证建议按下面顺序走：

1. 安装 `debug` APK
2. 打开 App，确认首页可正常渲染
3. 配置 `Base URL`、`API Key`、`Model`
4. 先点击 `测试连接`，确认 LLM 接口可用
5. 授予无障碍权限
6. 授予悬浮窗权限，并启动悬浮气泡
7. 进入微信单聊页测试候选生成
8. 如上下文不足，再测试 OCR 授权和 OCR 识别

## 配置项

首页当前可配置的项目包括：

- `Base URL`
- `API Key`
- `Model`
- 默认回复角色
- 话术宝典默认场景
- 润色目标

这些配置目前主要通过 `SharedPreferences` 持久化。

## 当前边界与已知限制

虽然项目已经不是纯骨架，但仍处于 MVP 向可用工程过渡阶段，主要限制包括：

- 当前只面向微信单聊，群聊支持仍未完善
- OCR 质量和聊天气泡分组仍需要更多真机样本校准
- Accessibility 规则仍需针对引用消息、表情、图片提示等继续调参
- 仍是单模块工程，尚未拆分成更清晰的多模块架构
- 尚未接入 DataStore / Room / DI
- 诊断日志目前以摘要为主，还没有完整日志页和导出文件
- Autofill 兼容性仍需更多机型验证
- 不自动发送是刻意设计，不属于缺失功能

## 文档导航

如果你准备继续开发，建议阅读顺序如下：

1. [docs/README.md](/home/percy/AiReplayMate/docs/README.md)
2. [docs/CURRENT_STATUS.md](/home/percy/AiReplayMate/docs/CURRENT_STATUS.md)
3. [docs/PRD.md](/home/percy/AiReplayMate/docs/PRD.md)
4. [docs/ENGINEERING_SPEC.md](/home/percy/AiReplayMate/docs/ENGINEERING_SPEC.md)
5. [docs/DELIVERY_PLAN.md](/home/percy/AiReplayMate/docs/DELIVERY_PLAN.md)
6. [docs/DEV_SETUP.md](/home/percy/AiReplayMate/docs/DEV_SETUP.md)

如果是给 AI 代理继续接力开发，优先看 [docs/AI_DEV_GUIDE.md](/home/percy/AiReplayMate/docs/AI_DEV_GUIDE.md)。
