# 文档索引 — AiReplayMate

如果主要是给 AI 代理持续开发，建议先看 [AI_DEV_GUIDE.md](AI_DEV_GUIDE.md)。

当前建议只把下面 4 份文档视为主入口：

1. [AI_DEV_GUIDE.md](AI_DEV_GUIDE.md)
   AI 阅读顺序、文档优先级、代码入口、常见需求改动路径与验证清单。
2. [PRD.md](PRD.md)
   产品目标、范围、非目标、成功指标。
3. [ENGINEERING_SPEC.md](ENGINEERING_SPEC.md)
   系统分层、模块职责、执行管道、数据模型、状态机、接口契约、技术选型与风险。
4. [DELIVERY_PLAN.md](DELIVERY_PLAN.md)
   版本路线、任务拆解、依赖关系、排期建议。

补充文档：

5. [DEV_SETUP.md](DEV_SETUP.md)
   开发环境、构建方式、真机调试和当前真实可验证链路。
6. [CURRENT_STATUS.md](CURRENT_STATUS.md)
   当前仓库真实进度、Now / Next / Later、近期建议。
7. [CODE_REVIEW.md](CODE_REVIEW.md)
   面向当前阶段的 Code Review 基线，覆盖 MVP 边界、架构分层、微信适配、自动填入、安全与验证要求。
8. [REAL_DEVICE_TEST_PLAN.md](REAL_DEVICE_TEST_PLAN.md)
   真实链路与稳定 smoke 的分层策略、真机关键场景补齐顺序与阶段计划。

推荐阅读顺序：

1. AI 先看 `AI_DEV_GUIDE.md`。
2. 再看 `PRD.md`，确认产品边界。
3. 再看 `ENGINEERING_SPEC.md`，理解整体方案和实现约束。
4. 需要排期或拆任务时看 `DELIVERY_PLAN.md`。

当前不再保留旧拆分文档，工程文档统一收敛为以上 4 份主文档。

如果目标是“尽快上手开发并确认仓库现状”，建议在主文档之外补看：

1. `DEV_SETUP.md`
2. `CURRENT_STATUS.md`
