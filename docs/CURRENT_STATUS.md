# 当前开发状态 — AiReplayMate

> 这份文档回答两个问题：仓库现在实际做到哪里了？接下来最应该做什么？

## 1. 当前代码快照

截至当前仓库状态，Android 侧仍是早期骨架：

- 仅有 `android/app` 单模块
- 已有 Compose 首页
- 已注册 `ReplyAccessibilityService`
- 已有无障碍设置、悬浮窗设置两个快捷入口
- 已声明基础权限：悬浮窗、通知、前台服务
- 已有 Demo 形态的会话主链路状态机
- 已接入微信聊天页初判、结构化消息采样、输入框定位与最小 `ACTION_SET_TEXT` 填入尝试
- 已有最小 `ContextBuilder`，可过滤、去重、截断 Accessibility 上下文
- 已有最小 Overlay 悬浮按钮与系统级候选面板，可在微信上方选择候选并尝试填入
- 尚未打通真实 LLM / OCR 闭环

对应代码入口：

- [MainActivity.kt](/home/percy/AiReplayMate/android/app/src/main/java/com/lonquanzj/aireplaymate/MainActivity.kt:1)
- [ReplyAccessibilityService.kt](/home/percy/AiReplayMate/android/app/src/main/java/com/lonquanzj/aireplaymate/accessibility/ReplyAccessibilityService.kt:1)
- [AndroidManifest.xml](/home/percy/AiReplayMate/android/app/src/main/AndroidManifest.xml:1)

## 2. 已完成

当前可以认为已经完成或至少落了骨架的内容：

- Android 应用基础工程可编译
- Compose 入口页已存在
- Accessibility Service 已注册到 Manifest
- Accessibility 配置文件已存在
- App 首页提供系统设置跳转入口
- 首页可展示真实无障碍调试状态、消息提取预览与填入结果
- 已有微信页面分析器 `WeChatAccessibilityAnalyzer`
- 已有最小 `ChatMessage` / `ChatRole` / `MessageSource` 模型，并能按节点位置粗分“我 / 对方 / 系统”
- 已有最小 `ChatContext` / `ConversationType` / `DefaultContextBuilder`
- 已有最小 `SessionManager` 演示骨架，统一管理校验、提取、候选、填入几个阶段
- 已有最小 `OverlayButtonService`、`OverlayTriggerStore` 和本地候选面板
- 已有最小 Autofill 尝试链路：定位输入框并执行 `ACTION_SET_TEXT`

## 3. 未完成

以下仍然没有真正落地：

- 多模块拆分
- Hilt / DI
- Navigation 完整路由
- MediaProjection 截图
- ML Kit OCR
- PromptBuilder
- LLM API 集成
- 基于真实 LLM 的候选生成
- Autofill 剪贴板兜底与回读验证
- DataStore / Room
- 日志诊断页面

## 4. 文档和代码的关系

当前文档仍然是“目标规格清晰，代码实现早期”的状态，但已经不再是纯空骨架：

- [PRD.md](/home/percy/AiReplayMate/docs/PRD.md) 定义了 MVP 范围
- [ENGINEERING_SPEC.md](/home/percy/AiReplayMate/docs/ENGINEERING_SPEC.md) 定义了目标架构和接口约束
- 当前代码已覆盖最小会话演示、微信页面初判和基础 Autofill 尝试，但大部分核心模块仍属于待实现设计

所以后续开发时要注意：

- 不要把工程规格误读成“这些模块已经存在”
- 开发任务应优先选能快速形成闭环的骨架工作

## 5. Now / Next / Later

### Now

最建议优先推进的，是能把骨架变成“可继续迭代的工程底座”的工作：

1. 补齐项目基础设施
2. 把当前会话骨架从 Demo 推进到真实可复用链路
3. 补齐真实消息提取、候选生成与 Autofill 降级

对应任务建议：

- Gradle 多模块拆分
- Hilt DI 接入
- Navigation 骨架
- `ChatAppAdapter` 与 `WeChatAdapter` 接口抽象化
- Accessibility 消息结构化提取
- `AutofillEngine` 回读验证与剪贴板兜底

### Next

在主链路骨架稳定后，再接提取和生成能力：

1. Accessibility 消息提取
2. ContextBuilder
3. PromptBuilder
4. LLM Gateway

这样可以先打通最短闭环：

- 页面识别
- 抽取最近消息
- 生成候选
- 面板展示
- 选择后填入

### Later

这些能力适合在基础链路稳定后再补：

- OCR 兜底
- Room / DataStore
- 日志诊断页面
- 厂商兼容性适配
- 候选历史与体验优化

## 6. 推荐的最近两周目标

如果按“先跑通最短 MVP 闭环”的思路，建议目标是：

1. 从单模块骨架过渡到可维护的模块结构。
2. 完成微信单聊页识别。
3. 完成输入框定位与最小 Autofill 骨架。
4. 留出可插拔的上下文提取和 LLM 接口。

这会比一开始就接 OCR、日志、历史记录更稳，因为它先解决了主链路最硬的集成点。

## 7. 当前开发假设

这份状态文档默认采用以下假设：

- MVP 目标不变：微信单聊、辅助回复、不自动发送
- Android 最低版本保持 `minSdk 29`
- 当前仓库尚未进入“边做边重构”的复杂阶段
- 近期最重要的是形成第一条可运行主链路，而不是补全所有配套能力
