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
4. 上下文不足时，通过 `AccessibilityService.takeScreenshot + ML Kit OCR` 做兜底识别
5. 通过 `DefaultPromptBuilder` 构造 OpenAI 兼容请求
6. 通过 `OpenAiCompatibleLlmGateway` 请求模型接口
7. 模型异常时切换 `LocalFallbackReplyGenerator`
8. 通过 `OverlayButtonService` 展示候选、诊断和填入入口
9. 通过 `AccessibilityActionBridge` 执行最小 Autofill 尝试

这条链路由 [RealReplySessionRunner.kt](../android/app/src/main/java/com/lonquanzj/aireplaymate/session/RealReplySessionRunner.kt:17) 统一承接。

### 首页能力

首页当前不是静态设置页，而是一个集成调试面板，已经具备：

- 无障碍权限、悬浮窗权限的状态查看和快捷跳转
- 启动 / 刷新 / 停止悬浮气泡
- LLM 配置输入、配置校验、测试连接
- 回复风格配置：快速回复、话术宝典、润色表达
- Accessibility 调试样本查看与复制
- OCR 无障碍截图链路、识别测试、过滤摘要、候选消息与诊断
- 悬浮窗真实触发诊断
- 最近摘要级诊断日志查看、复制和清空
- Demo 会话状态机与预览区域

首页入口位于 [MainActivity.kt](../android/app/src/main/java/com/lonquanzj/aireplaymate/MainActivity.kt:127)。

### 悬浮窗与回复交互

悬浮能力已经不只是“一个按钮”：

- 已有圆形紫色风格 AI 气泡
- 短按触发默认风格候选生成
- 长按打开风格菜单
- 生成时显示加载态和等待面板
- 候选面板展示候选来源和候选内容
- 用户选择候选后尝试填入微信输入框
- 已通过服务内锁避免并发重复触发

当前悬浮交互入口位于 [OverlayButtonService.kt](../android/app/src/main/java/com/lonquanzj/aireplaymate/overlay/OverlayButtonService.kt:45)。

#### Overlay 拆分现状（最近）

为降低大文件复杂度，overlay 已完成一轮“服务编排化”拆分：

- 会话执行：`OverlaySessionOrchestrator`
- 浮球交互：`OverlayFloatingBubbleController`
- 布局计算：`OverlayPanelLayoutCalculator`
- 动画管理：`OverlayPanelAnimationController`
- 悬浮按钮工厂：`OverlayFloatingButtonViewFactory`
- 面板构建：`OverlayCandidatePanel`、`OverlayStyleMenuPanel`、`OverlayFailurePanel`、`OverlayProgressPanel`
- 通用 UI：`OverlayUiDimens`、`OverlayUiEffects`、`OverlayUiStyles`、`OverlayPanelUiTokens`

最近还补充了这批文件与 `OverlayButtonService` 的注释（文件职责、关键函数、调用链索引），当前更适合按职责跳读。

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

- 已切换为无障碍服务截图，不再依赖 MediaProjection 授权
- 已实现测试无障碍截图链路
- 已接入 ML Kit 中文 OCR
- 已实现 `OcrTextPostProcessor`，用于过滤控件文案、时间和非聊天文本
- 已支持把 OCR 文本聚合为候选聊天消息
- 已支持在诊断窗口展示 OCR Filter Summary 和 OCR Messages
- Debug 构建会保存有限数量的 OCR 调试截图；Release / 非调试构建不保存调试图
- 只有 LLM 请求实际包含 OCR 上下文时，Prompt 才会提示模型温和纠正常见 OCR 近形错
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
  负责无障碍截图、OCR 识别、OCR 后处理和诊断
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

- [MainActivity.kt](../android/app/src/main/java/com/lonquanzj/aireplaymate/MainActivity.kt:127)
- [ReplyAccessibilityService.kt](../android/app/src/main/java/com/lonquanzj/aireplaymate/accessibility/ReplyAccessibilityService.kt:1)
- [RealReplySessionRunner.kt](../android/app/src/main/java/com/lonquanzj/aireplaymate/session/RealReplySessionRunner.kt:17)
- [OverlayButtonService.kt](../android/app/src/main/java/com/lonquanzj/aireplaymate/overlay/OverlayButtonService.kt:45)

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
- 无障碍截图、ML Kit 中文 OCR、后处理和 OCR 诊断已接入
- 悬浮窗诊断、OCR 诊断、LLM 诊断与摘要日志已落地
- Autofill 最小填入链路已接入，并具备失败分类
- 应用品牌资源和悬浮 UI 样式已做第一轮打磨
- Overlay 大文件拆分完成第一轮，服务层职责已收敛为编排
- Overlay 拆分文件与 Service 已补充阅读导向注释

最近一次回归（2026-05-23）：

- 真机稳定 smoke（7 条）通过
- `:app:testDebugUnitTest` 通过
- 两个 Diagnostic Only 的 Activity 探针在当前 MIUI 设备失败（系统后台拉起限制），仍不作为默认门禁

## 5. 仍未完成的部分

以下内容仍未真正做完，或者还需要更强的工程化补强。考虑到中期目标是一台手机上的个人自用版本，下面这些事项不再都视为近期必须推进：

- 多模块拆分、DI / Hilt、复杂导航结构
- Room / DataStore 等更完整的持久化方案
- 群聊场景支持和更多聊天 App 适配
- 引用消息、图片消息、表情消息等复杂消息提取校准
- 表情包 / 图片回应链路尚未接入：第一版计划只识别“对方发了表情 / 图片”，后续再接百炼 `qwen3-vl-flash` 做粗粒度视觉意图分类
- 本地表情库尚未接入：后续计划支持 emoji、微信文本表情、本地图片 / 动图表情包，以及软件内上传 / 导入表情包
- OCR 在当前自用手机上的质量继续调参，重点是过滤、聚合与 prompt 侧纠错提示
- Autofill 在当前自用手机上的成功率和失败回退体验
- 悬浮窗服务恢复、前台通知和更稳的生命周期管理
- 完整日志页面、筛选、导出文件
- 更系统化的测试覆盖

## 6. 当前风险与现实边界

当前最真实的风险点不是“功能完全没有”，而是以下这些地方仍依赖当前自用手机上的真机反馈：

- 微信 UI 结构和节点语义在当前微信版本上可能变化
- OCR 结果很依赖当前手机的无障碍截图质量、字体、主题和消息布局
- 输入框 Autofill 在当前 ROM 上是否稳定
- LLM 输出质量仍依赖提示词和风格参数调优
- 目前日志偏摘要化，对个人排障仍可能不够直观

另外，以下属于刻意边界，而不是缺陷：

- 不自动发送
- 以微信单聊为 MVP 主场景
- OCR 只在 Accessibility 不足时兜底，而不是默认主链路
- Vision API 默认不进入主链路；只有检测到图片 / 表情消息且用户开启视觉理解时，才裁剪必要区域调用
- 表情包推荐默认只做候选展示，图片 / 动图表情发送优先走用户确认和手动发送 / 分享兜底
- 中期只服务一台自用手机，不追求多机型、多厂商兼容矩阵

## 7. 文档与代码的关系

当前仓库里的文档和代码已经比较接近，但仍需注意：

- `PRD.md` 和 `ENGINEERING_SPEC.md` 描述的是目标形态与实现约束
- 代码已经覆盖一条可运行闭环，但工程层面还没完全追上目标结构
- `CURRENT_STATUS.md` 应该优先代表“仓库现在真的有哪些能力”

因此后续开发时，建议先以当前代码为准，再用规格文档判断下一步该补什么。

## 8. Now / Next / Later

### Now

当前最值得继续做的是“把已经打通的 MVP 链路在自用手机上变得顺手、稳定”：

1. 用当前手机继续校准微信消息提取、OCR 后处理和 OCR 上下文 prompt 提示
2. 把 Autofill 成功率和失败回退体验打磨到日常可用
3. 优化悬浮候选体验，减少个人使用时的多余操作
4. 让诊断日志能快速回答“这次为什么没生成 / 没填入”

### Next

当自用主链路更稳定后，建议推进：

1. 收敛首页配置与真实链路，让个人使用路径更短
2. 引入更稳的配置持久化，优先覆盖 API、模型、风格和常用开关
3. 把 Demo 链路和真实链路继续收敛，减少维护负担
4. 为核心纯逻辑补最小测试，避免单人项目改动时反复踩旧坑
5. 增加表情 / 图片消息占位识别：先把“对方发了表情 / 图片”进入上下文，不接 Vision

### Later

基础稳定后再考虑：

1. 群聊支持
2. 更丰富的候选历史与体验增强
3. 更完整的日志中心
4. 多机型和厂商兼容适配
5. 接入百炼 `qwen3-vl-flash` 视觉分类，并升级到本地表情库推荐、软件内上传表情包和发送兜底能力

## 9. 最近建议

如果只选几个最值得做的近一步任务，我会建议：

1. 在当前手机上补微信样本，继续调 Accessibility、OCR 规则和 OCR 触发后的提示词表现
2. 把 Autofill 失败链路做得更清晰、更稳，并保留复制兜底
3. 提升悬浮窗服务的生命周期稳定性，优先解决自用时会遇到的系统回收问题
4. 为 `session / accessibility / ocr / llm` 补最小单元测试

## 10. 当前默认假设

这份状态文档默认基于以下前提：

- MVP 仍然只聚焦微信单聊辅助回复
- 默认不自动发送
- Android 最低版本维持 `minSdk 29`
- 中期定位是一台手机上的个人自用工具，而不是面向多机型、多用户发布的产品
- 当前仓库优先目标是“把已有闭环在自用手机上做稳、做顺手”，而不是马上扩张功能面
