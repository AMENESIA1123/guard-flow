# PROGRESS.md · guard-flow

> 每轮工作结束必须更新此文件。AI 跨会话只有这份记录能接续上下文。

---

## 当前状态

- 阶段：**T10 控件联动与倍速完成，待进入 T11**
- 已完成任务卡：`T0`、`T1`、`T2`、`T3`、`T4`、`T5`、`T6`、`T7`、`T8`、`T9`、`T10`
- 下一个任务卡：`T11`
- 构建状态：`mvn -q clean verify` 通过（Maven 3.10.1 + JDK 21.0.6）

---

## 已完成

| 任务卡 | 内容 | 提交 | 验收结果 |
|---|---|---|---|
| T0 | 工程骨架与 CI（空多模块） | `feat(T0): 工程骨架` | `mvn -q clean verify` 退出码 0；全仓库无 `.java` 文件 |
| T1 | 核心契约与 FakeClock | `feat(T1): 核心契约与 FakeClock` | `mvn -q -pl algorithm-core compile` 退出码 0；grep 无输出；`mvn -q clean verify` 退出码 0 |
| T2 | 固定窗口 | `feat(T2): 实现固定窗口`；`test(T2): 覆盖非法 permits` | `mvn -pl algorithm-core test -Dtest=FixedWindowLimiterTest` 6 个用例全绿（FW-01~FW-05 + 非法 permits）；`mvn -q clean verify` 退出码 0 |
| T3 | 滑动窗口（日志法） | `feat(T3): 实现滑动窗口` | `mvn -pl algorithm-core test -Dtest=SlidingWindowLogLimiterTest` 7 个用例全绿；`mvn -q clean verify` 退出码 0 |
| T4 | 令牌桶 | `feat(T4): 实现令牌桶` | `mvn -pl algorithm-core test -Dtest=TokenBucketLimiterTest` 6 个用例全绿；`mvn -q clean verify` 退出码 0 |
| T5 | 漏桶 + 工厂补全 | `feat(T5): 实现漏桶与工厂补全` | `mvn -pl algorithm-core test` 30 个用例全绿（四个算法测试 + 工厂测试）；`mvn -q clean verify` 退出码 0 |
| T6 | 服务骨架与 REST | `feat(T6): 服务骨架与 REST`；`test(T6): 覆盖参数校验 400` | `mvn -q clean verify` 退出码 0（核心 30 + server 8）；`mvn -q clean package -DskipTests` 退出码 0；启动后 `/api/algorithms` 返回 4 个算法 |
| T7 | SSE 指标流 | `feat(T7): SSE 指标流` | `mvn -q clean verify` 退出码 0（核心 30 + server 9）；`curl -N /stream` 收到 `snapshot` + 连续 `sample`；断开后日志无异常 |
| T8 | 前端骨架与静态托管 | `feat(T8): 前端骨架与静态托管` | `mvn -q clean verify` 退出码 0；headless 浏览器验证：4 张算法卡、动态参数、20qps 令牌桶启动后 KPI 增长、暂停冻结、重置归零 |
| T9 | Canvas 图表渲染 | `feat(T9): Canvas 图表渲染` | `mvn -q verify` 退出码 0；headless Chrome 验证固定窗口/令牌桶图形差异、DPR=2 画布 1644×422、暂停冻结、重置归零 |
| T10 | 控件联动与倍速 | `feat(T10): 控件联动与倍速` | `mvn -q verify` 退出码 0；headless Chrome：50x 下 900ms 内 offered=700、hover 显示 t/offered/passed/rejected/level、参数变更后重建为空闲、越界禁用开始 |

---

## 下一步

从 `docs/tasks.md` 的 **T11 · Redis 分布式实现** 开始。

---

## 已知问题 / 阻塞

- 本地 Maven 为 3.10.1（`AGENTS.md` 标注 3.9.x），已由人确认使用；全局 settings 指向 `D:\local-repo` 与阿里云镜像。
- 在 DSH 工作区沙箱下，Maven 需完全权限写 `D:\local-repo`，git 需完全权限写 `.git`。
- `SystemClock` 是 algorithm-core 中唯一允许调用 `System.currentTimeMillis()` 的类（T1 人确认的例外，已写入 `AGENTS.md` §5.1）。
- `FakeClock` 初始 `t=0`，`advanceTo` 直接设值、`advanceBy` 累加，不禁止回拨。
- `tryAcquire(t, n)` 统一返回最后一个许可的 `Decision`；`permits <= 0` 返回 `INVALID_PARAM` 且不改变状态（规格 §0.2 未明说聚合语义，T2 起按此实现）。
- `LimiterFactory` 的算法 id 以 `api.md` 为准：`fixed_window`、`sliding_window`、`token_bucket`、`leaky_bucket`。
- 滑动窗口日志按非递减时间戳存储，`t` 相同的多条记录是允许的（SW-01 要求 `t=0` × 10）；规格里的「严格递增」按非递减理解。
- 令牌桶的 `Snapshot.used` 返回剩余令牌数（与 `level = tokens / capacity` 一致），不是已消耗量。
- 漏桶的 `Snapshot.used` 返回当前排队数（`queued`）。
- `LimiterFactoryTest` 是按 T5 卡新增的，`file-manifest.md` 未列；工厂对未知算法 id 现抛 `IllegalArgumentException`（覆盖 T2 的 `UnsupportedOperationException` 占位行为）。
- REST 错误映射：`IllegalArgumentException` → 400 `INVALID_PARAM`；`NoSuchElementException` → 404 `EXPERIMENT_NOT_FOUND`；`IllegalStateException` → 409 `EXPERIMENT_STATE`；其他 → 500 `INTERNAL`。
- 输入生成：`constant` / `step` 用 ceil 把每秒 qps 摊到 10 帧（qps=1 时第一帧就到 1 个）；`spike` 在 `t=0` 及每个 `everyMillis` 整点叠加 burst；`poisson` 用单个 `Random(seed)`，必须按 tick 顺序调用。
- `ExperimentRuntime` 首帧 `t=0`，`view().t` 取最近一条 sample；重置依赖构造时的 Supplier，`ExperimentRegistry` 使用 Supplier 重建 limiter 与 input。
- `TickerConfig` 单线程 `fixedRate=100ms`，每轮按每个实验的 `speed` 推进对应逻辑帧；`guardflow.ticker.enabled=false` 可关闭（`ApiSmokeTest` 用它避免与手动 tick 竞争）。
- SSE：连接建立推 `snapshot`，每帧推 `sample`，控制接口变更状态推 `state`；`AsyncRequestNotUsableException` 在 `GlobalExceptionHandler` 中直接吞掉，避免向已提交的 `text/event-stream` 写 JSON。
- `ExperimentRuntimeTest` 固定 20qps + 令牌桶 10/2，20 次 tick 后 totals 为 offered=40 / passed=13 / rejected=27。
- T8 前端：`app.js` 250 行、零依赖；默认算法 `fixed_window`，卡片顺序按 ui-spec 固定为 fixed → sliding → token → leaky（不按 API 顺序）。
- T8 图表区仍是 `<pre>` 最近 3 条 Sample + 图 B 占位；Canvas 留给 T9。参数/算法/倍速变更只置 `needsNew`，下次 Start 时重建；运行中变更需先暂停（§7 的即时联动留到 T10）。
- T9 图表：`charts.js` 导出 `drawDecisionChart`、`drawLevelChart`，另有 `createChartScheduler` 统一 rAF/DPR；`app.js` 环形缓冲 300 点，数据到达只调度不直接重绘。
- T9 验收：固定窗口决策图呈周期性放行/拒绝块、令牌桶水位呈锯齿；DPR=2 时 canvas backing store 为 CSS 尺寸的 2 倍。
- T10 联动：合法参数/算法/倍速变更经 300ms 防抖后 pause → DELETE → POST idle；非法参数只暂停不删除；`start` 在 dirty/busy/running 时禁用。
- T10 倍速：50x = 每 100ms 推进 50 个逻辑帧，30s 逻辑约 600ms；实测 900ms 内 offered=700。
- T10 hover：`charts.js` 的 `locateSample` + DOM 参考线 + fixed tooltip，距视口边 8px 翻转；`index.html` 因此增加 tooltip/crosshair 容器。

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
