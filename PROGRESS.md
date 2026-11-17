# PROGRESS.md · guard-flow

> 每轮工作结束必须更新此文件。AI 跨会话只有这份记录能接续上下文。

---

## 当前状态

- 阶段：**T2 固定窗口完成，待进入 T3**
- 已完成任务卡：`T0`、`T1`、`T2`
- 下一个任务卡：`T3`
- 构建状态：`mvn -q clean verify` 通过（Maven 3.10.1 + JDK 21.0.6）

---

## 已完成

| 任务卡 | 内容 | 提交 | 验收结果 |
|---|---|---|---|
| T0 | 工程骨架与 CI（空多模块） | `feat(T0): 工程骨架` | `mvn -q clean verify` 退出码 0；全仓库无 `.java` 文件 |
| T1 | 核心契约与 FakeClock | `feat(T1): 核心契约与 FakeClock` | `mvn -q -pl algorithm-core compile` 退出码 0；grep 无输出；`mvn -q clean verify` 退出码 0 |
| T2 | 固定窗口 | `feat(T2): 实现固定窗口` | `mvn -pl algorithm-core test -Dtest=FixedWindowLimiterTest` 5 个用例全绿；`mvn -q clean verify` 退出码 0 |

---

## 下一步

从 `docs/tasks.md` 的 **T3 · 滑动窗口（日志法）** 开始。

---

## 已知问题 / 阻塞

- 本地 Maven 为 3.10.1（`AGENTS.md` 标注 3.9.x），已由人确认使用；全局 settings 指向 `D:\local-repo` 与阿里云镜像。
- 在 DSH 工作区沙箱下，Maven 需完全权限写 `D:\local-repo`，git 需完全权限写 `.git`。
- `SystemClock` 是 algorithm-core 中唯一允许调用 `System.currentTimeMillis()` 的类（T1 人确认的例外，已写入 `AGENTS.md` §5.1）。
- `FakeClock` 初始 `t=0`，`advanceTo` 直接设值、`advanceBy` 累加，不禁止回拨。
- `tryAcquire(t, n)` 统一返回最后一个许可的 `Decision`；`permits <= 0` 返回 `INVALID_PARAM` 且不改变状态（规格 §0.2 未明说聚合语义，T2 起按此实现）。

---

## 决策记录（Decision Log）

> 已定死的设计决策，后续会话不得推翻，除非人明确要求。

| # | 决策 | 理由 | 日期 |
|---|---|---|---|
| D1 | 引入虚拟时钟，支持 1x~50x 倍速 | 真实时间下观察滑动窗口需数秒，体验不可接受 | 2026-11-17 |
| D2 | 前端零依赖，原生 Canvas | 保证 `docker compose up` 即用，且体积可控 | 2026-11-17 |
| D3 | 滑动窗口采用「日志法」精确实现 | 计数器近似法无法与测试向量严格对齐 | 2026-11-17 |
| D4 | 漏桶采用「排队 + 固定速率出队」模型 | 与令牌桶形成真实差异，可视化更有信息量 | 2026-11-17 |
| D5 | 指标推送用 SSE，不用 WebSocket | 单向、Spring 原生支持、实现成本低 | 2026-11-17 |
| D6 | 分布式版为可选 profile，默认单机内存实现 | 不能让没装 Redis 的人打不开页面 | 2026-11-17 |
| D7 | 实验状态纯内存，不做持久化 | 演示型产品，无需历史回溯 | 2026-11-17 |
