# AI 开发指南 — AiReplayMate

> 这份文档不是重复描述需求，而是为了让后续 AI 代理在最少上下文下，也能快速拿到正确约束并开始开发。

## 1. 建议 AI 首先阅读

1. [PRD.md](/home/percy/AiReplayMate/docs/PRD.md)
   明确产品目标、MVP 范围、非目标、成功指标。
2. [ENGINEERING_SPEC.md](/home/percy/AiReplayMate/docs/ENGINEERING_SPEC.md)
   查系统分层、模块拆分、数据模型、状态机、接口契约、微信适配规则、错误与存储约束。
3. [DELIVERY_PLAN.md](/home/percy/AiReplayMate/docs/DELIVERY_PLAN.md)
   确认当前阶段应优先做什么，以及任务依赖关系。

如果上下文预算很紧，只读这份文档和 `PRD.md`，然后按任务需要再局部查 `ENGINEERING_SPEC.md` 与 `DELIVERY_PLAN.md`。

## 2. 文档优先级

当多个文档内容不一致时，按下面顺序处理：

1. `PRD.md`
2. `ENGINEERING_SPEC.md`
3. `DELIVERY_PLAN.md`

解释：

- `PRD.md` 决定产品边界，尤其是做什么、不做什么。
- `ENGINEERING_SPEC.md` 决定整体结构、模块职责和实现约束。
- `DELIVERY_PLAN.md` 只负责排期和任务顺序，不单独定义产品或架构事实。

## 3. 当前 AI 最容易踩错的点

- MVP 只做微信单聊，不默认扩展到群聊或多 App。
- 只做“辅助回复 + 自动填入”，不自动发送。
- 提取链路优先级固定为 `Accessibility -> OCR fallback`。
- 适配逻辑要通过 Adapter 隔离，不要把微信规则散落在通用模块里。
- 当前工程仍偏骨架状态，开发时不要默认多模块、存储、OCR、LLM 都已接好。

## 4. 推荐的开发决策顺序

收到开发任务后，建议按下面顺序判断：

1. 先看任务是否落在 `PRD.md` 的 MVP 范围内。
2. 再看 `DELIVERY_PLAN.md` 中该任务属于哪一阶段，是否有前置依赖。
3. 涉及模块边界、状态机、接口、错误码、微信规则时看 `ENGINEERING_SPEC.md`。
5. 如果实现中必须偏离文档，先更新文档，再改代码。

## 5. 最适合 AI 的文档合并方式

当前建议已经收敛为最适合 AI 的 3 个主文档：

1. `PRODUCT_SPEC.md`
   当前对应 `PRD.md`，只保留产品目标、范围、非目标、成功指标。
2. `ENGINEERING_SPEC.md`
   当前已经落地，统一维护架构、模块职责、数据模型、状态机、接口契约、错误策略、存储规则。
3. `EXECUTION_PLAN.md`
   当前对应 `DELIVERY_PLAN.md`，负责版本路线、任务拆解、依赖关系、当前优先级。

这样做对 AI 最友好，因为：

- 产品、技术、执行三个维度各只有一个权威入口。
- AI 不需要在“系统设计”和“实现规格”之间来回判断哪个更该信。
- 做任务时更容易把“该做什么”和“怎么做”分开读取。

## 6. 现在不建议继续合并的部分

- `PRD.md` 不建议并入技术文档，否则 AI 容易把产品边界和工程细节混读。
- `DELIVERY_PLAN.md` 不建议并入 `PRD.md`，因为路线图和任务拆解变化更频繁。
- 已合并的旧文档继续保留跳转页即可，避免历史链接失效。

## 7. 如果后面要继续整理，优先顺序

1. 给 `DELIVERY_PLAN.md` 增加 “Now / Next / Later” 区块，减少 AI 自己推断优先级。
2. 在工程根目录保留一个稳定入口，让 AI 默认先看本文件。
3. 若工程继续扩大，再考虑把 `ENGINEERING_SPEC.md` 拆成“稳定规则”和“变动实现计划”两个层次。

## 8. 当前建议

现在建议 AI 入口固定为：

- `AI_DEV_GUIDE.md`
- `PRD.md`
- `ENGINEERING_SPEC.md`
- `DELIVERY_PLAN.md`

这套结构已经足够支持后续持续让 AI 接任务开发。
