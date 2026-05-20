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
- 已有最小 `PromptBuilder`，可把 `ChatContext` 转换为 OpenAI 兼容请求文本
- 已有最小 Overlay 悬浮按钮与系统级候选面板，可在微信上方选择候选并尝试填入
- 已有 OpenAI 兼容 `LlmGateway`、响应解析器和首页最小 LLM 设置入口
- 悬浮面板已支持优先请求真实 LLM，失败或未配置 API Key 时自动回退到本地候选
- 已有悬浮窗真实触发诊断，可记录页面校验、上下文整理、OCR 兜底、LLM、本地兜底、候选和填入阶段
- 已有 OCR 兜底入口、状态模型、诊断 UI、MediaProjection 截图授权入口、测试截图数据流和 ML Kit 中文 OCR 识别引擎

对应代码入口：

- [MainActivity.kt](/home/percy/AiReplayMate/android/app/src/main/java/com/lonquanzj/aireplaymate/MainActivity.kt:1)
- [ReplyAccessibilityService.kt](/home/percy/AiReplayMate/android/app/src/main/java/com/lonquanzj/aireplaymate/accessibility/ReplyAccessibilityService.kt:1)
- [OpenAiCompatibleLlmGateway.kt](/home/percy/AiReplayMate/android/app/src/main/java/com/lonquanzj/aireplaymate/llm/OpenAiCompatibleLlmGateway.kt:1)
- [AndroidManifest.xml](/home/percy/AiReplayMate/android/app/src/main/AndroidManifest.xml:1)

## 2. 已完成

当前可以认为已经完成或至少落了骨架的内容：

- Android 应用基础工程可编译
- Compose 入口页已存在
- Accessibility Service 已注册到 Manifest
- Accessibility 配置文件已存在
- App 首页提供系统设置跳转入口
- 首页可展示真实无障碍调试状态、消息提取调试样本与填入结果
- 消息提取调试样本已展示角色、置信度、bounds 和文本，方便真机校准 Accessibility 规则
- 首页已提供 `复制调试样本`，可把页面判定、输入框状态、提取消息和可见文本采样复制到剪贴板
- 已有微信页面分析器 `WeChatAccessibilityAnalyzer`，并已增强顶部/底部区域过滤、控件文案过滤、重复节点去重和左右角色判定
- 已有最小 `ChatMessage` / `ChatRole` / `MessageSource` 模型，并能按节点位置粗分“我 / 对方 / 系统”
- 已有最小 `ChatContext` / `ConversationType` / `DefaultContextBuilder`
- 已有最小 `AppSettings` / `LlmRequest` / `ReplyCandidate` / `DefaultPromptBuilder`
- 已有最小 `LlmGateway` / `OpenAiCompatibleLlmGateway` / `LlmResponseParser`
- 首页已提供 `API Key` / `Base URL` / `Model` 配置，并通过 `SharedPreferences` 持久化
- 首页 LLM 设置已支持本地配置校验，可提示空 Key、无效 Base URL、空模型和 http 调试提醒
- 首页已提供 `测试连接` 按钮，会复用真实 LLM Gateway 发起最小 JSON 候选请求
- 首页已提供最小 LLM 诊断面板，可展示最近一次请求阶段、接口、模型、HTTP 状态、候选数、错误摘要和返回预览
- 首页 LLM 诊断面板已支持内存态最近 8 次请求历史，并对失败进行配置、网络、HTTP、解析、候选不足、未知等粗分类
- 首页 LLM 诊断已支持 `复制 LLM 诊断`，可导出当前请求状态、返回预览、错误摘要和最近请求历史
- 已有最小 `SessionManager` 演示骨架，统一管理校验、提取、候选、填入几个阶段
- 已有最小 `OverlayButtonService`、`OverlayTriggerStore` 和候选面板
- 微信悬浮候选面板已支持真实 LLM 生成，异常时保留本地兜底候选
- 已新增 `OverlayDiagnosticsStore`，用于记录 AI 气泡点击后的真实链路阶段、上下文数量、候选来源、失败原因和步骤 trace
- 首页已新增 `悬浮窗诊断` 卡片，可展示上一次真实气泡触发结果，并支持复制诊断样本
- 已有最小 Autofill 尝试链路：定位输入框、执行 `ACTION_SET_TEXT`、回读确认，并在必要时使用剪贴板粘贴兜底
- Autofill 调试已支持失败分类和步骤 trace，可区分无障碍未连接、内容为空、窗口为空、输入框未找到、SET_TEXT 失败、粘贴失败和回读不一致
- 已新增最小 `OcrEngine` / `OcrAttemptResult` / `OcrDebugStore`，作为 Accessibility 上下文不足时的 OCR 兜底接入点
- 首页已新增 `OCR 兜底` 诊断卡片，可展示截图授权、OCR 引擎状态、最近分类、触发原因、步骤 trace 和复制 OCR 诊断
- 首页已接入系统屏幕截图授权入口，授权结果通过 `OcrCapturePermissionStore` 保存在内存态，供后续 MediaProjection 截图实现使用
- 首页已新增 `测试截图数据流`，会用 MediaProjection / VirtualDisplay / ImageReader 抓取一帧当前屏幕图像，并只记录尺寸、stride 和步骤，不保存图片
- 截图授权 token 使用后会标记为 `已使用`，避免重复使用旧 token 导致模糊失败
- 已接入 `com.google.mlkit:text-recognition-chinese`，可把截图帧转换为 ML Kit `InputImage` 并识别中文文本
- 首页已新增 `测试 OCR 识别`，识别结果会转换为内存态 `ChatMessage` 并进入 OCR 诊断，不保存截图
- 微信悬浮候选面板在 Accessibility 上下文不足时，已从占位 OCR 切换为 ML Kit 中文 OCR 兜底
- 已新增 `OcrTextPostProcessor`，会过滤顶部/底部 UI、常见微信控件、时间日期、数字角标、重复文本，并按同侧相邻文本行聚合为候选聊天气泡
- OCR 诊断步骤已记录 ML Kit 文本块数、原始行数、保留行数、过滤行数和聚合消息数，方便真机调参
- Demo 状态机已新增 `OCR 兜底` 阶段；微信悬浮按钮在上下文不足时会先尝试 OCR 兜底，再决定是否提示失败

## 3. 未完成

以下仍然没有真正落地：

- 多模块拆分
- Hilt / DI
- Navigation 完整路由
- OCR 结果的聊天气泡分组、角色推断和坐标回填仍需真机样本校准
- OCR 真机质量校准，包括更多微信控件、引用消息、表情/图片提示、群聊昵称和非聊天文本
- Accessibility 消息提取规则仍需基于真机样本继续校准，包括群聊/引用/图片/表情等复杂消息形态
- LLM 诊断日志持久化与更细粒度错误建议
- 悬浮窗诊断仍是内存态，尚未持久化为可导出的完整日志
- Autofill 多机型兼容性验证与机型差异样本积累
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

1. 把当前会话骨架从 Demo 推进到真实可复用链路
2. 基于真机 OCR 诊断样本校准结果清洗、聊天气泡分组和角色推断
3. 把 OCR 兜底与 Accessibility 上下文合并策略继续细化为可观测规则

对应任务建议：

- Gradle 多模块拆分
- Hilt DI 接入
- Navigation 骨架
- `ChatAppAdapter` 与 `WeChatAdapter` 接口抽象化
- Accessibility 消息结构化提取真机校准
- LLM 诊断日志持久化与失败建议细化
- 悬浮窗诊断持久化与真实失败样本归档
- OCR 真机样本回放、清洗规则调参、气泡分组和角色推断规则

### Next

在主链路骨架稳定后，再接提取和生成能力：

1. 收集真机 OCR 诊断样本，校准过滤规则和底部输入区比例
2. 增强 OCR 气泡分组，覆盖多行长消息、引用消息、图片/表情提示和群聊昵称
3. OCR 与 Accessibility 上下文合并时的来源、置信度和降级提示
4. 设置页从最小表单升级为正式页面

这样可以先打通最短闭环：

- 页面识别
- 抽取最近消息
- 生成候选
- 面板展示
- 选择后填入

### Later

这些能力适合在基础链路稳定后再补：

- OCR 真机识别质量优化
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
