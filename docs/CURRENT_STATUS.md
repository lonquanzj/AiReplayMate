# 当前开发状态 — AiReplayMate

> 这份文档回答两个问题：仓库现在实际做到哪里了？接下来最值得继续推进什么？

## 1. 一句话结论

仓库当前已经不是“Android 空骨架”，而是已经具备一条可运行的微信辅助回复 MVP 主链路：

- 首页可配置 LLM、风格、OCR、悬浮窗和诊断能力
- 微信悬浮气泡可真实触发上下文整理、OCR 兜底、LLM 生成和本地兜底
- 候选可在悬浮面板中展示并尝试填入输入框
- 默认不自动发送

同时，它也还没有进入“架构稳定、功能完备”的阶段，仍然处于 MVP 闭环已打通、工程化仍在继续补强的状态。

## 2. 当前真实进度

### 已经打通的主链路

当前真实链路已经包括：

1. 通过 `ReplyAccessibilityService` 感知微信页面和聊天页上下文
2. 通过 `WeChatAccessibilityAnalyzer` 提取和过滤消息样本
3. 通过 `DefaultContextBuilder` 整理 Accessibility / OCR 消息
4. 上下文不足时，通过 `MediaProjection + ML Kit OCR` 做兜底识别
5. 通过 `DefaultPromptBuilder` 构造 OpenAI 兼容请求
6. 通过 `OpenAiCompatibleLlmGateway` 请求模型接口
7. 模型异常时切换 `LocalFallbackReplyGenerator`
8. 通过 `OverlayButtonService` 展示候选、诊断和填入入口
9. 通过 `AccessibilityActionBridge` 执行最小 Autofill 尝试

这条链路由 [RealReplySessionRunner.kt](/home/percy/AiReplayMate/android/app/src/main/java/com/lonquanzj/aireplaymate/session/RealReplySessionRunner.kt:17) 统一承接。

### 首页能力

首页当前不是静态设置页，而是一个集成调试面板，已经具备：

- 无障碍权限、悬浮窗权限的状态查看和快捷跳转
- 启动 / 刷新 / 停止悬浮气泡
- LLM 配置输入、配置校验、测试连接
- 回复风格配置：快速回复、话术宝典、润色表达
- Accessibility 调试样本查看与复制
- OCR 授权、截图链路、识别测试与诊断
- 悬浮窗真实触发诊断
- 最近摘要级诊断日志查看、复制和清空
- Demo 会话状态机与预览区域

首页入口位于 [MainActivity.kt](/home/percy/AiReplayMate/android/app/src/main/java/com/lonquanzj/aireplaymate/MainActivity.kt:127)。

### 悬浮窗与回复交互

悬浮能力已经不只是“一个按钮”：

- 已有圆形紫色风格 AI 气泡
- 短按触发默认风格候选生成
- 长按打开风格菜单
- 生成时显示加载态和等待面板
- 候选面板展示候选来源和候选内容
- 用户选择候选后尝试填入微信输入框
- 已通过服务内锁避免并发重复触发

当前悬浮交互入口位于 [OverlayButtonService.kt](/home/percy/AiReplayMate/android/app/src/main/java/com/lonquanzj/aireplaymate/overlay/OverlayButtonService.kt:45)。

### LLM 与本地兜底

当前 LLM 能力已包括：

- OpenAI 兼容 `Base URL + API Key + Model` 配置
- 最小请求构造和返回解析
- 失败分类与摘要诊断
- 最近请求历史
- 首页测试连接
- 悬浮链路里真实使用 LLM 生成候选

本地兜底已不是临时占位：

- 首页 Demo 和真实悬浮链路共用同一套本地兜底生成器
- 本地兜底会结合当前风格模式、角色、场景和草稿上下文返回候选
- 候选来源会标明是 `LLM` 还是 `本地兜底`

### OCR 兜底

OCR 目前已经接入真实识别，不再只是留接口：

- 已接入截图授权入口
- 已实现测试截图数据流
- 已接入 ML Kit 中文 OCR
- 已实现 `OcrTextPostProcessor`，用于过滤控件文案、时间和非聊天文本
- 已支持把 OCR 文本聚合为候选聊天消息
- 悬浮链路中，当 Accessibility 上下文不足时会真实走 OCR 兜底

### 品牌与界面状态

最近已经补充并接入：

- 应用展示名更新为 `AiChat`
- Android launcher 图标已替换为新的紫色风格版本
- 悬浮气泡改成更轻量的圆形图标按钮
- 候选面板和等待面板已从深色重阴影改为更浅的淡紫系样式

## 3. 当前代码结构

虽然还是单模块 Android 工程，但代码已经按职责做了目录分层：

- `accessibility/`
  负责微信页面识别、消息提取、输入框查找与填入桥接
- `context/`
  负责把 Accessibility / OCR 原始消息整理成 `ChatContext`
- `llm/`
  负责 OpenAI 兼容请求、返回解析、LLM 调试状态
- `ocr/`
  负责截图授权、截图、OCR 识别和 OCR 后处理
- `overlay/`
  负责悬浮气泡、候选面板、进度面板和悬浮诊断
- `prompt/`
  负责请求模型、PromptBuilder、回复风格模型
- `session/`
  负责 Demo 状态机、真实执行器、本地兜底生成
- `settings/`
  负责配置与风格持久化
- `diagnostics/`
  负责摘要级日志记录

当前的核心代码入口：

- [MainActivity.kt](/home/percy/AiReplayMate/android/app/src/main/java/com/lonquanzj/aireplaymate/MainActivity.kt:127)
- [ReplyAccessibilityService.kt](/home/percy/AiReplayMate/android/app/src/main/java/com/lonquanzj/aireplaymate/accessibility/ReplyAccessibilityService.kt:1)
- [RealReplySessionRunner.kt](/home/percy/AiReplayMate/android/app/src/main/java/com/lonquanzj/aireplaymate/session/RealReplySessionRunner.kt:17)
- [OverlayButtonService.kt](/home/percy/AiReplayMate/android/app/src/main/java/com/lonquanzj/aireplaymate/overlay/OverlayButtonService.kt:45)

## 4. 已完成事项

目前可以明确认为“已经落地”的事项包括：

- Android 应用可以成功构建 `debug` 包
- 首页 Compose UI 已具备配置、诊断、调试和 Demo 区
- Accessibility Service 已注册并参与实际链路
- 微信页面识别、消息样本提取、输入框定位已接入
- 最小上下文整理能力已落地
- OpenAI 兼容 LLM 请求与响应解析已接入
- LLM 配置校验、测试连接和诊断已落地
- 回复风格 v1 已落地，并贯穿首页和悬浮链路
- 真实悬浮链路已优先走 LLM，失败时回退到本地兜底
- OCR 授权、截图、ML Kit 中文 OCR 和后处理已接入
- 悬浮窗诊断、OCR 诊断、LLM 诊断与摘要日志已落地
- Autofill 最小填入链路已接入，并具备失败分类
- 应用品牌资源和悬浮 UI 样式已做第一轮打磨

## 5. 仍未完成的部分

以下内容仍未真正做完，或者还需要更强的工程化补强：

- 多模块拆分
- DI / Hilt
- Room / DataStore
- 更正式的页面路由与设置结构
- 群聊场景支持
- 引用消息、图片消息、表情消息等复杂消息提取校准
- OCR 真机质量继续调参
- Autofill 多机型兼容性验证
- 悬浮窗服务恢复、前台通知和更稳的生命周期管理
- 完整日志页面、筛选、导出文件
- 更系统化的测试覆盖

## 6. 当前风险与现实边界

当前最真实的风险点不是“功能完全没有”，而是以下这些地方仍依赖真机反馈：

- 微信 UI 结构和节点语义在不同版本上可能变化
- OCR 结果很依赖截图质量、字体、主题和消息布局
- 输入框 Autofill 在不同 ROM 上兼容性不完全一致
- LLM 输出质量仍依赖提示词和风格参数调优
- 目前日志偏摘要化，对复杂线上问题的追溯能力还不够

另外，以下属于刻意边界，而不是缺陷：

- 不自动发送
- 以微信单聊为 MVP 主场景
- OCR 只在 Accessibility 不足时兜底，而不是默认主链路

## 7. 文档与代码的关系

当前仓库里的文档和代码已经比较接近，但仍需注意：

- `PRD.md` 和 `ENGINEERING_SPEC.md` 描述的是目标形态与实现约束
- 代码已经覆盖一条可运行闭环，但工程层面还没完全追上目标结构
- `CURRENT_STATUS.md` 应该优先代表“仓库现在真的有哪些能力”

因此后续开发时，建议先以当前代码为准，再用规格文档判断下一步该补什么。

## 8. Now / Next / Later

### Now

当前最值得继续做的是“把已经打通的 MVP 链路变得更稳”：

1. 继续校准微信消息提取和 OCR 后处理
2. 增强 Autofill 成功率和失败回退体验
3. 优化悬浮候选体验和真实真机交互
4. 让诊断日志对排障更有帮助

### Next

当主链路更稳定后，建议推进：

1. 把单模块工程拆成更清晰的模块边界
2. 引入更稳的配置与状态持久化方案
3. 把 Demo 链路和真实链路继续收敛
4. 开始沉淀更系统的测试和机型兼容性样本

### Later

基础稳定后再考虑：

1. 群聊支持
2. 更丰富的候选历史与体验增强
3. 更完整的日志中心
4. 更强的厂商兼容适配

## 9. 最近建议

如果只选几个最值得做的近一步任务，我会建议：

1. 真机补样本，继续调 Accessibility 和 OCR 规则
2. 把 Autofill 失败链路做得更清晰、更稳
3. 提升悬浮窗服务的生命周期稳定性
4. 为 `session / accessibility / ocr / llm` 补最小单元测试

## 10. 当前默认假设

这份状态文档默认基于以下前提：

- MVP 仍然只聚焦微信单聊辅助回复
- 默认不自动发送
- Android 最低版本维持 `minSdk 29`
- 当前仓库优先目标是“把已有闭环做稳”，而不是马上扩张功能面
