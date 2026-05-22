# AI 开发指南 — AiReplayMate

> 这份文档面向“后续继续接需求的 AI 代理”。目标不是重复产品文档，而是让 AI 在最少上下文下，快速定位代码入口、理解约束，并按正确顺序完成改动。

## 1. 先读什么

建议阅读顺序：

1. [CURRENT_STATUS.md](CURRENT_STATUS.md)
   先确认仓库现在真实做到哪里，不要把项目误判成空骨架。
2. [PRD.md](PRD.md)
   看产品边界，尤其是 MVP 范围和非目标。
3. [ENGINEERING_SPEC.md](ENGINEERING_SPEC.md)
   看架构、模块职责、状态机、接口约束。
4. [DEV_SETUP.md](DEV_SETUP.md)
   看如何构建、安装、验证真机链路。
5. 当前任务涉及到的具体代码文件

如果上下文预算很紧，至少先读：

1. `AGENTS.md`
2. `CURRENT_STATUS.md`
3. `PRD.md`
4. 本文件

## 2. 信息优先级

当文档、代码、历史对话出现不一致时，按这个顺序判断：

1. 当前代码真实行为
2. `PRD.md`
3. `ENGINEERING_SPEC.md`
4. `CURRENT_STATUS.md`
5. `DELIVERY_PLAN.md`

解释：

- 代码决定“现在实际是什么”
- `PRD.md` 决定“哪些方向不该越界”
- `ENGINEERING_SPEC.md` 决定“应该怎么组织实现”
- `CURRENT_STATUS.md` 是对当前实现的人工摘要
- `DELIVERY_PLAN.md` 只负责优先级，不单独定义事实

## 3. 当前产品边界

后续 AI 最容易踩错的是这些点：

- 当前 MVP 主场景是微信单聊
- 默认不自动发送，只辅助填入
- 优先走 `Accessibility -> OCR fallback`
- OCR 是兜底，不是默认主链路
- 悬浮气泡是当前真实主入口之一
- LLM 失败时要尽量保住本地兜底，不要让整条链路直接失效
- 新增需求如果会影响边界，先更新文档再改代码

## 4. 当前代码地图

后续大多数需求都落在下面这些目录：

```text
android/app/src/main/java/com/lonquanzj/aireplaymate
├── MainActivity.kt
├── accessibility/
├── context/
├── diagnostics/
├── llm/
├── ocr/
├── overlay/
├── prompt/
├── session/
└── settings/
```

最常用入口：

- [MainActivity.kt](../android/app/src/main/java/com/lonquanzj/aireplaymate/MainActivity.kt:127)
  首页配置、调试面板、诊断 UI
- [OverlayButtonService.kt](../android/app/src/main/java/com/lonquanzj/aireplaymate/overlay/OverlayButtonService.kt:45)
  悬浮气泡、进度面板、候选面板、风格菜单
- [RealReplySessionRunner.kt](../android/app/src/main/java/com/lonquanzj/aireplaymate/session/RealReplySessionRunner.kt:17)
  真实会话执行器，负责把上下文整理、OCR、LLM、本地兜底串起来
- [PromptBuilder.kt](../android/app/src/main/java/com/lonquanzj/aireplaymate/prompt/PromptBuilder.kt:1)
  系统提示词、用户提示词、模式分支
- [ReplyStyleModels.kt](../android/app/src/main/java/com/lonquanzj/aireplaymate/prompt/ReplyStyleModels.kt:1)
  回复角色、话术宝典、润色目标的核心模型
- [ReplyStyleSettingsStore.kt](../android/app/src/main/java/com/lonquanzj/aireplaymate/settings/ReplyStyleSettingsStore.kt:1)
  风格配置持久化
- [LocalFallbackReplyGenerator.kt](../android/app/src/main/java/com/lonquanzj/aireplaymate/session/LocalFallbackReplyGenerator.kt:1)
  LLM 不可用时的本地候选兜底

## 5. 改需求时先判断属于哪类

收到需求后，先归类，再进对应改动路径：

- LLM 风格扩展
  例如新增角色、话术宝典场景、润色目标
- Prompt / 候选策略调整
  例如收紧安全边界、调候选长度、变更 JSON 约束
- LLM 配置项扩展
  例如新增 `temperature`、`candidateCount`、`customSystemPrompt`
- OCR 质量调优
  例如过滤控件文案、合并多行气泡、处理群聊昵称
- Autofill / 无障碍兼容性修复
  例如输入框定位、粘贴兜底、失败原因分类
- 悬浮 UI / 候选面板体验调整
- 诊断与日志增强
- 品牌、文案、资源与文档更新

## 6. 常见需求操作手册

### 6.1 新增“人设 / 角色”

典型需求：

- 新增一个快速回复角色
- 调整角色名称、文案、风格描述
- 让本地兜底也能体现这个角色

最少需要检查这些位置：

1. [ReplyStyleModels.kt](../android/app/src/main/java/com/lonquanzj/aireplaymate/prompt/ReplyStyleModels.kt:14)
   在 `ReplyPersona` 增加新枚举，补 `id`、`label`、`promptGuide`
2. [MainActivity.kt](../android/app/src/main/java/com/lonquanzj/aireplaymate/MainActivity.kt:871)
   首页角色选择 UI 会基于 `ReplyPersona.entries` 自动渲染，通常不需要单独写死
3. [OverlayButtonService.kt](../android/app/src/main/java/com/lonquanzj/aireplaymate/overlay/OverlayButtonService.kt:358)
   长按气泡菜单会基于 `ReplyPersona.entries` 自动展示，通常不用单独补 UI
4. [PromptBuilder.kt](../android/app/src/main/java/com/lonquanzj/aireplaymate/prompt/PromptBuilder.kt:26)
   确认系统提示词里角色描述是否已经足够，不够就补风格约束
5. [LocalFallbackReplyGenerator.kt](../android/app/src/main/java/com/lonquanzj/aireplaymate/session/LocalFallbackReplyGenerator.kt:152)
   在 `adjustForPersona` 或具体候选生成逻辑里补角色差异，否则 LLM 挂掉时新角色会退化得很像默认角色
6. `README.md` / `docs/CURRENT_STATUS.md` / 本文件
   如果属于对外能力变更，文档也要同步

建议做法：

- 先把 `promptGuide` 写清楚，避免角色只换名字不换行为
- 再补本地兜底的角色差异
- 最后看首页文案示例是否需要同步调整

### 6.2 新增“话术宝典”场景

典型需求：

- 新增一个场景，比如“复联开场”“邀约推进”“吵架缓和”

最少需要检查这些位置：

1. [ReplyStyleModels.kt](../android/app/src/main/java/com/lonquanzj/aireplaymate/prompt/ReplyStyleModels.kt:95)
   在 `ReplyStyleCatalog.scenes` 中增加 `ReplyPlaybookScene`
2. [PromptBuilder.kt](../android/app/src/main/java/com/lonquanzj/aireplaymate/prompt/PromptBuilder.kt:74)
   `buildPlaybookUserPrompt` 通常会自动读取场景描述；如果新场景需要特殊约束，再加分支
3. [LocalFallbackReplyGenerator.kt](../android/app/src/main/java/com/lonquanzj/aireplaymate/session/LocalFallbackReplyGenerator.kt:41)
   在 `buildPlaybookTexts` 里新增该 `sceneId` 的本地兜底话术
4. [MainActivity.kt](../android/app/src/main/java/com/lonquanzj/aireplaymate/MainActivity.kt:871)
   首页场景选择会基于 `ReplyStyleCatalog.scenes` 自动展示
5. [OverlayButtonService.kt](../android/app/src/main/java/com/lonquanzj/aireplaymate/overlay/OverlayButtonService.kt:358)
   长按菜单也会基于 `ReplyStyleCatalog.scenes` 自动展示

注意：

- 只改 `ReplyStyleCatalog` 不够，本地兜底也要补
- `sceneId` 一旦进入持久化和诊断，尽量不要随意改名
- 场景描述要偏“用途 + 边界 + 风格”，不要只写一个标签

### 6.3 新增“润色目标 / 润色人设”

典型需求：

- 新增一个润色目标，比如“更强势”“更礼貌”“更商务”

最少需要检查这些位置：

1. [ReplyStyleModels.kt](../android/app/src/main/java/com/lonquanzj/aireplaymate/prompt/ReplyStyleModels.kt:49)
   在 `PolishGoal` 里增加新枚举
2. [PromptBuilder.kt](../android/app/src/main/java/com/lonquanzj/aireplaymate/prompt/PromptBuilder.kt:86)
   `buildPolishUserPrompt` 会自动读取 `polishGoal`，特殊目标可补单独约束
3. [LocalFallbackReplyGenerator.kt](../android/app/src/main/java/com/lonquanzj/aireplaymate/session/LocalFallbackReplyGenerator.kt:93)
   在 `buildPolishTexts` 里增加该目标的本地兜底版本
4. [MainActivity.kt](../android/app/src/main/java/com/lonquanzj/aireplaymate/MainActivity.kt:919)
   首页会基于 `PolishGoal.entries` 自动渲染
5. [OverlayButtonService.kt](../android/app/src/main/java/com/lonquanzj/aireplaymate/overlay/OverlayButtonService.kt:395)
   长按菜单会自动基于 `PolishGoal.entries` 展示

注意：

- 如果是“润色人设”而不是“润色目标”，先判断应该放进 `ReplyPersona` 还是 `PolishGoal`
- 一般来说：
  `ReplyPersona` 更像长期角色风格
  `PolishGoal` 更像本次润色意图

### 6.4 新增或调整 LLM 配置项

典型需求：

- 新增 `temperature`
- 新增 `candidateCount`
- 新增 `customSystemPrompt`

最少需要检查这些位置：

1. `prompt` 下的 `AppSettings` 数据模型
2. [AppSettingsStore.kt](../android/app/src/main/java/com/lonquanzj/aireplaymate/settings/AppSettingsStore.kt:1)
   补持久化读写
3. [AppSettingsValidator.kt](../android/app/src/main/java/com/lonquanzj/aireplaymate/settings/AppSettingsValidator.kt:1)
   补校验和 warning
4. [MainActivity.kt](../android/app/src/main/java/com/lonquanzj/aireplaymate/MainActivity.kt:772)
   首页设置 UI 和交互
5. `llm/` 下真实网关
   确认该字段是否真正进请求体
6. `LlmDebugStore`
   如有必要，补诊断展示

常见遗漏：

- 只改了 UI，没改持久化
- 只改了 `AppSettings`，没改请求构造
- 只改了请求，没改校验和首页提示

### 6.5 调整 Prompt 或安全边界

典型需求：

- 收紧安全边界
- 改候选长度
- 禁止某类表达
- 优化 JSON 输出稳定性

主入口：

- [PromptBuilder.kt](../android/app/src/main/java/com/lonquanzj/aireplaymate/prompt/PromptBuilder.kt:11)

建议顺序：

1. 优先改 `buildSystemPrompt`
2. 再看是否要改 `buildUserPrompt` / `buildPlaybookUserPrompt` / `buildPolishUserPrompt`
3. 再看返回解析器是否需要更强容错
4. 最后跑一遍真实测试连接和悬浮链路验证

不要做的事：

- 不要把大量业务规则散落到多个 UI 层
- 不要只改首页 Demo 的提示，不改真实悬浮链路
- 不要改出和本地兜底完全不同的风格预期

### 6.6 调整悬浮气泡 / 候选框 / 等待态

UI 相关需求大多集中在：

- [OverlayButtonService.kt](../android/app/src/main/java/com/lonquanzj/aireplaymate/overlay/OverlayButtonService.kt:45)

典型可改内容：

- 气泡尺寸、透明度、阴影、图标
- 等待面板大小、颜色、指示器动画
- 候选卡片样式、面板宽度、文案层级
- 风格菜单布局和按钮外观

注意：

- 这里是系统悬浮窗，不是 Compose 页面
- 很多 UI 是代码动态生成的，不在 XML
- 改样式后最好真机看一眼，不要只看编译通过

### 6.7 调整 OCR 识别与后处理

典型需求：

- 过滤更多微信控件文案
- 提高 OCR 聚合质量
- 补群聊昵称、引用消息、图片提示等规则

主入口：

- `ocr/MlKitChineseOcrEngine.kt`
- `ocr/OcrTextPostProcessor.kt`
- `ocr/OcrDebugStore.kt`
- [RealReplySessionRunner.kt](../android/app/src/main/java/com/lonquanzj/aireplaymate/session/RealReplySessionRunner.kt:17)

建议顺序：

1. 先看 OCR 诊断里保留了什么文本
2. 再补过滤或聚合规则
3. 最后确认不会误伤正常聊天内容

### 6.8 调整 Autofill / 输入框交互

主入口：

- `accessibility/AccessibilityActionBridge.kt`
- `accessibility/ReplyAccessibilityService.kt`
- 相关调试状态和诊断导出

常见需求：

- 输入框定位不到
- `ACTION_SET_TEXT` 在某些机型失败
- 剪贴板粘贴回退不足
- 回读结果不稳定

注意：

- 这类问题很依赖真机 ROM
- 修复时要保留失败分类，不要把所有异常都吞掉

### 6.9 加诊断、加日志、加复制快照

主入口：

- `diagnostics/DiagnosticLogStore.kt`
- `llm/LlmDebugStore.kt`
- `ocr/OcrDebugStore.kt`
- `overlay/OverlayDiagnosticsStore.kt`
- `overlay/OverlayServiceStateStore.kt`

原则：

- 优先记录“排障必要信息”
- 不在 Release / 非调试构建保存截图原图；Debug 调试图必须限量、只用于排障
- 不写入 API Key
- 摘要级日志优先，避免过度持久化敏感数据

### 6.10 改品牌、图标、文案

典型入口：

- `android/app/src/main/res/values/strings.xml`
- `android/app/src/main/AndroidManifest.xml`
- `android/app/src/main/res/mipmap-*`
- `artwork/icons/`
- `README.md`
- `docs/CURRENT_STATUS.md`

只改资源不改文档，会导致后续 AI 继续沿用旧叫法。

## 7. 改完以后至少要做什么验证

不同类型需求，最少验证项不同。

### 文案 / 枚举 / 风格类改动

至少验证：

1. 首页能看到新增项
2. 长按悬浮气泡能看到新增项
3. 悬浮真实链路能生成候选
4. LLM 失败时，本地兜底也能覆盖新增项

### LLM 请求类改动

至少验证：

1. 首页 `测试连接`
2. 微信悬浮气泡真实触发一次
3. 候选来源和失败摘要是否合理

### OCR / Autofill 类改动

至少验证：

1. 真机调试
2. OCR 或 Autofill 诊断面板
3. 至少一个成功路径和一个失败路径

### 纯悬浮 UI 改动

至少验证：

1. `assembleDebug`
2. 真机看气泡
3. 真机看等待面板
4. 真机看候选面板

常用构建命令：

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :app:assembleDebug
```

Gradle 根工程是仓库根目录，模块路径是 `:app`，物理目录是 `android/app`。不要使用 `:android:app:*`。

## 8. AI 开发时最容易漏的文件

以下是高频遗漏清单：

- 新增角色后，忘记补本地兜底
- 新增配置项后，忘记补 `Store` 和 `Validator`
- 改了 Prompt 后，忘记看悬浮真实链路
- 改了 OCR 规则后，忘记看 OCR 诊断快照
- 改了悬浮 UI 后，忘记真机看
- 改了对外能力后，忘记同步 `README.md` / `CURRENT_STATUS.md`

## 9. 提交前自检清单

提交前建议自查：

1. 是否越过了 `PRD.md` 的产品边界
2. 是否同时覆盖了真实悬浮链路，而不只是 Demo
3. 是否考虑了 LLM 失败时的本地兜底
4. 是否补了必要的诊断信息
5. 是否更新了相关文档
6. 是否做了至少一轮构建或真机验证

## 10. 推荐的任务处理方式

收到新增需求时，建议按这个节奏做：

1. 先用 `CURRENT_STATUS.md` 判断当前是否已有类似能力
2. 用本文件定位代码入口和改动清单
3. 改代码
4. 做最小验证
5. 更新 `README.md` / `CURRENT_STATUS.md` / 本文件中受影响的描述

如果后面这个项目继续让 AI 持续接需求，这份文档应该优先保持“任务可操作性”，而不是写成抽象原则集合。
