# 工程规格 — AiReplayMate

> 本文档合并原 `SYSTEM_DESIGN.md` 与 `IMPLEMENTATION_SPEC.md`，作为项目唯一的工程规格入口。产品边界以 `PRD.md` 为准，版本路线与任务拆解以 `DELIVERY_PLAN.md` 为准。

## 1. 文档定位

本文档统一约束以下内容：

- 系统目标与设计原则
- 整体架构、分层与模块职责
- MVP 当前实现边界
- 数据模型、状态机、接口契约
- 微信适配规则、错误与降级策略
- LLM 约束、日志、隐私、存储与验收基线

当文档内容发生冲突时，按以下优先级处理：

1. `PRD.md`
2. 本文档
3. `DELIVERY_PLAN.md`

## 2. 当前实现边界

### 2.1 当前代码现状

- Android 工程目前仍为 `android/app` 单模块骨架
- 已有 Compose 入口页面
- 已注册 `ReplyAccessibilityService`
- 已具备基础权限设置入口
- 已有微信聊天页初判、结构化消息采样、输入框定位与最小 `ACTION_SET_TEXT` 尝试
- 已有 Demo 版 `SessionManager` 状态流骨架和候选面板演示
- 已有最小 `ContextBuilder`，用于清洗、去重并截断 Accessibility 上下文
- 已有最小 Overlay 悬浮按钮和系统级候选面板入口
- 尚未落地多模块拆分、DI、Room、DataStore、LLM、OCR

### 2.2 本文默认适用范围

- 仅覆盖 MVP（微信单聊、手动触发、生成候选、用户选择、自动填入、不自动发送）
- 默认目标平台：Android 10+（`minSdk 29`）
- 默认目标 App：微信
- 默认语言：中文

### 2.3 MVP 期间不处理的场景

- 群聊语义理解
- 自动发送消息
- 语音、图片、文件、转账等复杂消息内容理解
- Root / Hook / 注入
- 多 App 并行适配

## 3. 技术目标

构建一个 Android 应用，支持：

- 微信聊天页面识别与节点解析
- 基于 AccessibilityService 的聊天内容提取
- 截图 + OCR 兜底方案
- LLM 生成回复候选
- 自动填入目标输入框
- 全流程可观测、可诊断

## 4. 设计原则

- **可用性优先**：自动化提取优先，低延迟、高成功率
- **安全优先**：绝不自动发送，隐私数据不落地
- **降级设计**：每层都有降级路径（Accessibility → OCR → 手动操作）
- **模块化架构**：模块通过接口解耦，便于测试和替换
- **App 适配器模式**：微信等目标 App 的解析逻辑通过 Adapter 隔离
- **可观测性**：关键步骤产出结构化日志，支持诊断页面回看

## 5. 整体架构

```text
┌─────────────────────────────────────────────────┐
│                  Trigger Layer                   │
│  悬浮按钮 / 通知入口 / (未来: 快捷设置磁贴)       │
├─────────────────────────────────────────────────┤
│              SessionManager                      │
│  协调整个回复会话的生命周期 (状态机)              │
├──────────┬──────────────────────┬───────────────┤
│ Context  │  Intelligence Layer  │ Presentation  │
│ Layer    │                      │ Layer         │
│          │                      │               │
│  Accessibility Extractor       │ Candidate     │
│  └─ WeChatAdapter              │ Presenter     │
│  OCR Fallback                  │ (候选面板)     │
│  └─ MediaProjection + ML Kit   │               │
│  ContextBuilder                │               │
│  (合并/去重/排序)               │               │
├──────────┴──────────────────────┴───────────────┤
│              Autofill Layer                      │
│  ACTION_SET_TEXT → 输入框焦点 → 粘贴兜底          │
├─────────────────────────────────────────────────┤
│              Support Layer                       │
│  Storage(Room) / Logging / Settings / Diagnostics│
└─────────────────────────────────────────────────┘
```

## 6. 分层说明

### 6.1 Trigger Layer

- 悬浮按钮是 MVP 主入口
- 仅在微信聊天页面激活主工作流
- 处理中显示 loading，避免重复触发

### 6.2 Session Manager

- 管理一次“触发 → 候选展示 → 自动填入”的完整状态机
- 负责超时、取消、重试和流程收口
- 状态定义和迁移规则见第 10 章

### 6.3 Context Layer

- `AccessibilityExtractor` 优先从节点树提取消息
- `WeChatAdapter` 封装微信页面识别、消息抽取、输入框定位
- `OCRFallback` 使用 `MediaProjection + ML Kit`
- `ContextBuilder` 负责合并、去重、截断和统一输出 `ChatContext`

### 6.4 Intelligence Layer

- `PromptBuilder` 将结构化上下文组装为 LLM 请求
- `LlmGateway` 统一封装 OpenAI 兼容接口
- 支持用户自定义 `system prompt`、`baseUrl`、模型和推理参数

### 6.5 Presentation Layer

- `CandidatePresenter` 负责展示 3 条候选回复
- MVP 使用 BottomSheet 作为候选面板
- 支持重新生成和候选选择

### 6.6 Autofill Layer

- 首选 `ACTION_SET_TEXT`
- 填入后回读输入框做验证
- 失败时回退到剪贴板，并提示用户粘贴

### 6.7 Support Layer

- `Settings`：用户配置和权限引导
- `Storage`：偏好设置、会话摘要
- `Logging`：结构化诊断日志
- `Diagnostics`：查看最近会话和错误信息

## 7. 模块划分

| 模块 | 职责 |
|------|------|
| `app` | 主入口、DI、导航 |
| `feature-permission` | 权限引导页 |
| `feature-overlay` | 悬浮按钮 |
| `feature-session` | 会话管理和 UI |
| `core-accessibility` | AccessibilityService |
| `core-capture` | 截图能力 |
| `core-ocr` | OCR 引擎封装 |
| `core-context` | ContextBuilder |
| `core-prompt` | Prompt 构建 |
| `core-llm` | LLM API 网关 |
| `feature-candidate` | 候选展示面板 |
| `feature-settings` | 设置页面 |
| `core-storage` | 本地持久化 (Room / DataStore) |
| `core-logging` | 日志系统 |
| `target-adapter-wechat` | 微信页面解析适配器 |

## 8. 模块设计要点

### 8.1 `app`

- Application 初始化 DI
- Navigation Compose 管理路由
- 全局异常捕获与基础入口页

### 8.2 `core-accessibility`

- 继承 `AccessibilityService`
- 监听窗口变化与目标页面切换
- 提供页面识别、消息抓取、输入框定位能力

### 8.3 `core-capture`

- 基于 `MediaProjection` 截图
- 截图仅缓存内存，不写磁盘
- 负责权限申请结果与生命周期协同

### 8.4 `core-ocr`

- 封装 ML Kit 文本识别
- 输出识别文本和坐标信息
- 为左右气泡识别提供位置基础

### 8.5 `core-context`

- 合并 Accessibility 与 OCR 消息
- 去重、排序、截断
- 输出统一 `ChatContext`

### 8.6 `core-prompt`

- 构建默认与自定义 Prompt
- 统一输出 `LlmRequest`
- 默认目标为中文、简洁、可直接发送

### 8.7 `core-llm`

- 统一请求 OpenAI 兼容接口
- 处理超时、重试、响应解析
- 区分配置错误与网络错误

### 8.8 `feature-candidate`

- Compose BottomSheet 候选面板
- 展示 3 条候选并支持重新生成
- 选中后触发 Autofill

### 8.9 `feature-settings`

- API Key、Base URL、模型、温度、Max Tokens、Prompt 配置
- OCR 开关、日志入口、权限引导入口

### 8.10 `core-storage`

- `DataStore` 保存偏好设置
- `Room` 保存会话摘要与诊断日志

### 8.11 `core-logging`

- 结构化日志字段至少包含 `sessionId / step / timestamp / success / detail`
- 诊断页面默认展示最近 200 条

### 8.12 `target-adapter-wechat`

- 定义 `ChatAppAdapter`
- 实现微信聊天页识别、消息遍历规则、输入框定位
- 隔离微信版本差异

## 9. 核心数据模型

### 9.1 `ChatRole`

```kotlin
enum class ChatRole {
    ME,
    FRIEND,
    SYSTEM,
    UNKNOWN
}
```

说明：

- `ME` 表示我方发言
- `FRIEND` 表示对方发言
- `SYSTEM` 表示“以上是打招呼的内容”等系统提示
- `UNKNOWN` 仅允许出现在原始提取阶段，进入 Prompt 前必须尽量归一或过滤

### 9.2 `MessageSource`

```kotlin
enum class MessageSource {
    ACCESSIBILITY,
    OCR,
    MERGED
}
```

### 9.3 `ChatMessage`

```kotlin
data class ChatMessage(
    val id: String,
    val role: ChatRole,
    val content: String,
    val timestamp: Long?,
    val source: MessageSource,
    val confidence: Float,
    val boundsHint: String? = null
)
```

字段约束：

- `id`：原始阶段可用临时 ID，推荐 `source + index + hash(content)` 生成
- `content`：必须是清洗后的纯文本，去除首尾空白；空字符串不得进入 `ChatContext`
- `timestamp`：微信节点通常拿不到稳定时间时允许为 `null`
- `confidence`：范围 `0f..1f`
- `boundsHint`：仅用于调试，可存储节点区域或 OCR 行坐标摘要，不进入 LLM 请求

### 9.4 `ChatContext`

```kotlin
data class ChatContext(
    val messages: List<ChatMessage>,
    val targetApp: String,
    val conversationType: ConversationType,
    val collectedAt: Long
)
```

```kotlin
enum class ConversationType {
    SINGLE_CHAT,
    GROUP_CHAT,
    UNKNOWN
}
```

字段约束：

- MVP 仅允许 `targetApp = "wechat"`
- MVP 仅允许 `conversationType = SINGLE_CHAT` 进入生成流程
- `messages` 默认保留最近 `20` 条；不足 `3` 条也允许继续，但需要记录低置信度日志

### 9.5 `ReplyCandidate`

```kotlin
data class ReplyCandidate(
    val id: String,
    val text: String,
    val tone: String? = null,
    val sourceModel: String? = null,
    val rank: Int
)
```

字段约束：

- 候选条数目标固定为 `3`
- `rank` 从 `0` 开始
- `text` 不允许为空，长度建议限制在 `2..200` 字符

### 9.6 `SessionState`

```kotlin
enum class SessionState {
    IDLE,
    VALIDATING_TARGET,
    COLLECTING_ACCESSIBILITY,
    COLLECTING_OCR,
    BUILDING_CONTEXT,
    REQUESTING_LLM,
    CANDIDATE_READY,
    AUTOFILLING,
    FALLBACK_CLIPBOARD,
    DONE,
    FAILED,
    CANCELLED
}
```

### 9.7 `ReplySession`

```kotlin
data class ReplySession(
    val sessionId: String,
    val state: SessionState,
    val targetPackage: String?,
    val startedAt: Long,
    val updatedAt: Long,
    val errorCode: String? = null
)
```

### 9.8 `AppSettings`

```kotlin
data class AppSettings(
    val apiKey: String,
    val baseUrl: String,
    val model: String,
    val temperature: Float,
    val maxTokens: Int,
    val customSystemPrompt: String?,
    val enableOcrFallback: Boolean,
    val sessionTimeoutMs: Long
)
```

MVP 默认值：

- `baseUrl = "https://api.openai.com/"`
- `model = "gpt-4o-mini"`
- `temperature = 0.7f`
- `maxTokens = 200`
- `enableOcrFallback = true`
- `sessionTimeoutMs = 15000`

### 9.9 `DiagnosticLog`

```kotlin
data class DiagnosticLog(
    val id: Long = 0,
    val sessionId: String,
    val step: String,
    val success: Boolean,
    val message: String,
    val detailJson: String?,
    val createdAt: Long
)
```

约束：

- `message` 必须可读，面向诊断页面展示
- `detailJson` 可包含耗时、节点数量、错误码等，但默认不得记录完整聊天全文和 API Key

## 10. 会话状态机

### 10.1 主流程

```text
IDLE
  -> VALIDATING_TARGET
  -> COLLECTING_ACCESSIBILITY
  -> COLLECTING_OCR (仅在必要时)
  -> BUILDING_CONTEXT
  -> REQUESTING_LLM
  -> CANDIDATE_READY
  -> AUTOFILLING
  -> DONE
```

异常终态：

- 任意阶段失败且不可恢复：`FAILED`
- 用户主动取消：`CANCELLED`

### 10.2 状态迁移规则

| 当前状态 | 触发条件 | 下一状态 |
|---|---|---|
| `IDLE` | 用户点击悬浮按钮 | `VALIDATING_TARGET` |
| `VALIDATING_TARGET` | 当前页面确认是微信单聊 | `COLLECTING_ACCESSIBILITY` |
| `VALIDATING_TARGET` | 非微信聊天页 | `FAILED` |
| `COLLECTING_ACCESSIBILITY` | 提取成功且消息足够 | `BUILDING_CONTEXT` |
| `COLLECTING_ACCESSIBILITY` | 提取失败或消息不足且启用 OCR | `COLLECTING_OCR` |
| `COLLECTING_ACCESSIBILITY` | 提取失败且未启用 OCR | `FAILED` |
| `COLLECTING_OCR` | OCR 成功 | `BUILDING_CONTEXT` |
| `COLLECTING_OCR` | OCR 超时或失败 | `FAILED` |
| `BUILDING_CONTEXT` | 生成上下文成功 | `REQUESTING_LLM` |
| `BUILDING_CONTEXT` | 无有效消息 | `FAILED` |
| `REQUESTING_LLM` | 返回候选成功 | `CANDIDATE_READY` |
| `REQUESTING_LLM` | 失败且可重试 | `REQUESTING_LLM` |
| `REQUESTING_LLM` | 最终失败 | `FAILED` |
| `CANDIDATE_READY` | 用户选中候选 | `AUTOFILLING` |
| `CANDIDATE_READY` | 用户关闭面板 | `CANCELLED` |
| `AUTOFILLING` | `ACTION_SET_TEXT` 成功 | `DONE` |
| `AUTOFILLING` | `ACTION_SET_TEXT` 失败 | `FALLBACK_CLIPBOARD` |
| `FALLBACK_CLIPBOARD` | 剪贴板写入成功 | `DONE` |
| `FALLBACK_CLIPBOARD` | 剪贴板写入失败 | `FAILED` |

### 10.3 超时规则

- 整体会话超时：`15s`
- Accessibility 提取超时：`2s`
- OCR 链路超时：`5s`
- LLM 请求超时：`10s`
- Autofill 验证超时：`1s`

处理原则：

- 子阶段超时优先走降级
- 整体超时直接结束会话并提示用户重试
- 超时必须写入诊断日志并包含阶段名和耗时

### 10.4 并发约束

- 同一时刻仅允许一个活跃回复会话
- 当状态不是 `IDLE / DONE / FAILED / CANCELLED` 时，新的触发请求直接忽略，并提示“正在处理中”
- 候选面板展示期间，不再接受新的生成请求，除非用户点击“重新生成”

## 11. 核心执行管道

```text
User tap → TriggerCoordinator
  → SessionManager.start()
    → AccessibilityExtractor.collect()
      → if failed or not enough: OCRFallback.captureAndOcr()
    → ContextBuilder.build(rawMessages)
    → PromptBuilder.build(context)
    → LlmGateway.request(prompt)
    → CandidatePresenter.show(candidates)
      → User selects one
    → AutofillEngine.fill(selectedText)
      → verify content
      → if fail: clipboard fallback
    → SessionManager.end()
```

## 12. 模块接口契约

### 12.1 `ChatAppAdapter`

```kotlin
interface ChatAppAdapter {
    fun canHandle(packageName: String): Boolean
    fun isChatPage(root: AccessibilityNodeInfo): Boolean
    fun detectConversationType(root: AccessibilityNodeInfo): ConversationType
    fun extractMessages(root: AccessibilityNodeInfo): List<ChatMessage>
    fun findInputNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo?
}
```

约束：

- `isChatPage` 应优先使用包名 + 稳定结构联合判断，不能只靠标题文案
- `extractMessages` 允许返回空列表，但不得抛出未处理异常
- `findInputNode` 若返回 `null`，Autofill 必须走失败或剪贴板降级

### 12.2 `AccessibilityExtractor`

```kotlin
interface AccessibilityExtractor {
    suspend fun collect(): ExtractionResult
}
```

```kotlin
data class ExtractionResult(
    val messages: List<ChatMessage>,
    val enoughForReply: Boolean,
    val targetPackage: String?,
    val conversationType: ConversationType,
    val reason: String? = null
)
```

`enoughForReply` 判定规则：

- 至少提取到 `1` 条 `FRIEND` 消息
- 总消息数建议 `>= 3`
- 若不满足，允许继续，但应标记低置信度或走 OCR

### 12.3 `OcrCollector`

```kotlin
interface OcrCollector {
    suspend fun captureAndRecognize(): OcrResult
}
```

```kotlin
data class OcrResult(
    val messages: List<ChatMessage>,
    val screenshotWidth: Int,
    val screenshotHeight: Int,
    val success: Boolean,
    val reason: String? = null
)
```

约束：

- 截图只驻留内存
- OCR 原始块信息默认不落库
- OCR 结果必须按屏幕纵向顺序重新排序

### 12.4 `ContextBuilder`

```kotlin
interface ContextBuilder {
    fun build(
        accessibilityMessages: List<ChatMessage>,
        ocrMessages: List<ChatMessage>,
        targetApp: String,
        conversationType: ConversationType
    ): ChatContext
}
```

合并规则：

- 优先保留 `ACCESSIBILITY` 结果
- 同内容高度相似时，保留 `confidence` 更高的一条
- 若 OCR 与 Accessibility 内容互补，可合并后再截断至最近 `20` 条
- `SYSTEM` 消息默认过滤

### 12.5 `PromptBuilder`

```kotlin
interface PromptBuilder {
    fun build(context: ChatContext, settings: AppSettings): LlmRequest
}
```

```kotlin
data class LlmRequest(
    val systemPrompt: String,
    val userPrompt: String,
    val temperature: Float,
    val maxTokens: Int,
    val candidateCount: Int
)
```

默认 Prompt 约束：

- 输出语言默认为中文
- 不冒充用户已执行动作
- 不生成“已发送”“已付款”等高风险表述
- 候选风格以“简洁、自然、可直接发送”为目标

### 12.6 `LlmGateway`

```kotlin
interface LlmGateway {
    suspend fun generateReplies(request: LlmRequest): Result<List<ReplyCandidate>>
}
```

约束：

- 统一返回 `3` 条候选；若上游只返回 1 条，需要本地包装失败并记日志，不做静默降级
- 默认重试 `1` 次，仅对超时、5xx、网络瞬时错误生效
- 4xx 配置错误不重试，直接提示用户检查设置

### 12.7 `AutofillEngine`

```kotlin
interface AutofillEngine {
    suspend fun fill(text: String): AutofillResult
}
```

```kotlin
data class AutofillResult(
    val method: FillMethod,
    val success: Boolean,
    val verified: Boolean,
    val reason: String? = null
)

enum class FillMethod {
    ACCESSIBILITY_SET_TEXT,
    CLIPBOARD
}
```

验证规则：

- 首选 `ACTION_SET_TEXT`
- 填入后重新读取输入框文本，验证前缀或全文匹配
- 验证失败则进入剪贴板兜底

## 13. 微信适配实现规则

### 13.1 支持范围

- 仅支持微信单聊会话页面
- 仅处理可见区域内文本消息
- 仅提取最近一屏可读消息，不主动滚动列表

### 13.2 暂不支持的消息类型

- 图片
- 语音
- 视频
- 文件
- 红包 / 转账
- 小程序卡片
- 位置消息
- 撤回提示后的语义恢复
- 引用消息的嵌套结构理解

处理原则：

- 可识别为非文本时直接跳过
- 不因单条复杂消息导致整次提取失败

### 13.3 页面识别建议

页面识别以“包名 + 输入区 + 消息区 + 聊天控件”的组合特征为主，详细判定规则见 `13.5 ~ 13.11`。

### 13.4 群聊处理

- 若检测为群聊，MVP 默认不进入生成流程
- 用户提示使用“当前仅支持单聊”
- 记录 `conversationType = GROUP_CHAT`

### 13.5 页面识别详细规则

聊天页判定建议至少命中以下 `3` 项：

- 包名为 `com.tencent.mm`
- 页面存在可编辑输入框
- 页面存在消息列表容器或多个纵向重复消息块
- 页面存在“发送”、加号、语音切换等聊天控件
- 页面顶部存在联系人标题栏

快速排除场景：

- 微信首页会话列表
- 通讯录
- 朋友圈
- 小程序容器页
- 公众号文章页
- 支付、红包详情页

### 13.6 消息提取详细规则

- 仅提取当前屏幕可见的文本消息
- 不主动滚动聊天列表
- 忽略头像、昵称、已读状态、发送状态等非必要节点
- 对同一消息块的多文本节点先局部拼接，再作为一条消息输出
- 空字符串、纯时间文本、无意义装饰文案不进入结果

MVP 跳过的消息类型：

- 图片
- 表情大图
- 语音
- 视频
- 文件
- 名片
- 小程序卡片
- 转账 / 红包
- 位置
- 引用消息中的嵌套预览

### 13.7 发送方识别规则

- 优先依据气泡的水平位置判断左右
- 靠右优先判定为 `ME`
- 靠左优先判定为 `FRIEND`
- 方向不明确时可标记 `UNKNOWN`，但进入 `ContextBuilder` 前应尽量过滤或归一

### 13.8 时间与排序规则

- 微信节点拿不到稳定时间戳时，`timestamp` 允许为 `null`
- 排序优先使用屏幕 `top` 坐标，而非时间戳
- 相邻重复文本且位置高度一致时，仅保留一条

### 13.9 输入框识别规则

目标输入框应尽量满足：

- 可编辑
- 可聚焦或已聚焦
- 位于页面底部输入区
- 属于聊天输入，而不是搜索框

以下节点应排除：

- 全局搜索框
- 顶部搜索栏
- 联系人资料编辑框
- 弹窗中的临时输入框

### 13.10 置信度建议

- 页面识别命中 4 项及以上特征：`0.9`
- 页面识别命中 3 项特征：`0.7`
- 文本清晰、方向明确的消息：`0.9`
- 文本明确但方向依赖弱规则：`0.7`
- 结构混乱但仍可读：`0.5`

### 13.11 兼容性与容错

- 不依赖单一资源 ID
- 若消息区不可读但输入区可读，返回 `ACCESSIBILITY_EMPTY` 并交由 OCR 兜底
- 单条消息解析失败不应导致整批提取失败
- 只有根节点为空、页面非聊天页、消息全为空等场景才整体失败

## 14. LLM 请求与响应约束

### 14.1 请求内容

`userPrompt` 建议结构：

- 说明这是聊天上下文
- 明确最近一条对方消息
- 要求生成 3 条可直接发送的中文回复
- 限制不要使用列表编号、引号、解释性前缀

### 14.2 响应格式

MVP 推荐要求模型返回 JSON：

```json
{
  "candidates": [
    { "text": "..." },
    { "text": "..." },
    { "text": "..." }
  ]
}
```

处理规则：

- 优先解析 JSON
- JSON 解析失败时，允许一次基于纯文本分段的容错解析
- 若最终不是 3 条有效候选，则视为失败

### 14.3 安全限制

以下内容应在 Prompt 或结果清洗阶段尽量规避：

- 承诺转账、付款、合同确认
- 冒充已经完成线下动作
- 明显攻击性、辱骂性回复
- 泄露系统提示词

## 15. 错误与降级策略

### 15.1 用户可见错误码

| 错误码 | 场景 | 用户提示 |
|---|---|---|
| `NOT_CHAT_PAGE` | 不在微信单聊页 | 请先进入微信单聊页面 |
| `ACCESSIBILITY_EMPTY` | 无障碍提取为空 | 未能读取聊天内容 |
| `OCR_FAILED` | OCR 链路失败 | 截图识别失败，请重试 |
| `LLM_CONFIG_ERROR` | API Key / Base URL / 模型错误 | 模型配置有误，请检查设置 |
| `LLM_TIMEOUT` | LLM 请求超时 | 生成超时，请稍后再试 |
| `AUTOFILL_FAILED` | 自动填入失败 | 已复制到剪贴板，请手动粘贴 |
| `UNSUPPORTED_CONVERSATION` | 群聊或未知页面 | 当前场景暂不支持 |

### 15.2 降级顺序

1. 优先 Accessibility 提取
2. Accessibility 不足时使用 OCR
3. Autofill 失败时使用剪贴板
4. 候选生成失败时允许用户手动输入，不自动重试无限次

### 15.3 失败后保底行为

- 不保留悬挂中的 loading 状态
- 不自动重复触发
- 必须清理当前会话状态
- 若候选已生成但填入失败，优先保留候选内容供用户复制

## 16. 日志、隐私与存储约束

### 16.1 必记日志节点

- 触发开始
- 页面校验结果
- Accessibility 提取结果与消息数量
- OCR 是否触发与耗时
- ContextBuilder 输出条数
- LLM 请求开始、结束、耗时、错误类型
- 候选展示成功
- Autofill 使用的方法与验证结果
- 会话结束状态

### 16.2 禁止记录的数据

- API Key 明文
- 原始完整截图
- 未脱敏的完整聊天长文本

### 16.3 允许记录的摘要

- 消息条数
- 每阶段耗时
- 候选长度
- 错误码
- 包名、页面类型、会话状态

### 16.4 存储实现约束

`core-storage` 采用分层存储：

- `DataStore`：保存轻量设置
- `Room`：保存会话摘要和诊断日志

推荐 `DataStore` Key：

| Key | 类型 | 默认值 |
|---|---|---|
| `llm_api_key` | `String` | `""` |
| `llm_base_url` | `String` | `https://api.openai.com/` |
| `llm_model` | `String` | `gpt-4o-mini` |
| `llm_temperature` | `Float` | `0.7` |
| `llm_max_tokens` | `Int` | `200` |
| `llm_custom_system_prompt` | `String` | `""` |
| `feature_enable_ocr_fallback` | `Boolean` | `true` |
| `feature_show_overlay` | `Boolean` | `true` |
| `feature_enable_diagnostics` | `Boolean` | `true` |
| `session_timeout_ms` | `Long` | `15000` |

Room MVP 表：

- `reply_session`
- `diagnostic_log`

`reply_session` 建议字段：

- `session_id`
- `target_package`
- `conversation_type`
- `start_time`
- `end_time`
- `final_state`
- `error_code`
- `message_count`
- `used_ocr`
- `candidate_count`
- `autofill_method`
- `duration_ms`

`diagnostic_log` 建议字段：

- `id`
- `session_id`
- `step`
- `success`
- `message`
- `detail_json`
- `created_at`

保留与清理策略：

- 默认保留最近 `100` 条会话摘要
- 诊断页默认展示最近 `200` 条日志
- 库内日志最多保留 `1000` 条，超出后裁剪最旧记录
- “清除日志”不应删除用户配置

敏感信息约束：

- API Key 不写入日志
- 不保存完整聊天原文
- 不保存原始 OCR 文本块
- 不保存原始截图

Repository 接口建议：

```kotlin
interface SettingsRepository {
    val settingsFlow: Flow<AppSettings>
}

interface SessionRepository {
    fun observeRecentSessions(limit: Int = 50): Flow<List<ReplySessionEntity>>
}

interface DiagnosticLogRepository {
    fun observeRecentLogs(limit: Int = 200): Flow<List<DiagnosticLogEntity>>
}
```

## 17. 验收基线

### 17.1 功能验收

以下流程全部通过，才算 MVP 主链路可用：

1. 用户打开微信单聊页面
2. 点击悬浮按钮后进入 loading
3. 成功提取上下文并生成 3 条候选
4. 用户点击其中一条后成功填入输入框
5. 应用不自动发送消息

### 17.2 异常验收

- 非聊天页面点击触发时有明确提示
- 无障碍提取失败时可按设置进入 OCR
- LLM 配置错误时可定位到设置问题
- 自动填入失败时可回退到剪贴板

## 18. 推荐技术栈

| 组件 | 选型 | 原因 |
|------|------|------|
| 语言 | Kotlin | Android 官方推荐，协程支持好 |
| UI | Jetpack Compose | 现代化声明式 UI，开发效率高 |
| 异步 | Coroutines / Flow | 天然适配 Android 生命周期 |
| 本地存储 | Room + DataStore | Room 存结构化数据，DataStore 存偏好 |
| 无障碍 | AccessibilityService | 系统标准方案，无需 Root |
| 截图 | MediaProjection | Android 官方截屏 API |
| OCR | ML Kit Text Recognition | Google 官方，设备端运行 |
| LLM | OpenAI 兼容 API | 易于切换多家 Provider |
| DI | Hilt | 官方推荐 |
| 网络 | OkHttp + Retrofit | Android 常用组合 |

## 19. 技术风险与对策

| 风险 | 影响 | 对策 |
|------|------|------|
| 微信 UI 改版 | Accessibility 解析失效 | Adapter 隔离，多条件判定，持续补规则 |
| 某些 ROM 限制 Accessibility | 提取失败 | OCR 兜底 + 权限引导 |
| ML Kit OCR 耗时过长 | 影响体验 | 异步执行 + 超时降级 |
| `ACTION_SET_TEXT` 无效 | 填入失败 | 剪贴板兜底 |
| 网络延迟或 LLM 不可用 | 无候选 | 超时提示 + 手动输入兜底 |
| 设备兼容性问题 | 功能异常 | 测试矩阵 + 厂商适配记录 |

## 20. 待后续补充

以下内容建议在功能开始落地时继续细化：

- 微信节点树样例和解析规则快照
- Room Entity 精确字段与索引定义
- DAO 查询语句
- Prompt 默认文案
- 诊断页面字段定义
- ROM 兼容性问题清单
