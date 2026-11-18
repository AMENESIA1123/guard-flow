# PROGRESS.md · guard-flow

> 每轮工作结束必须更新此文件。AI 跨会话只有这份记录能接续上下文。

---

## 当前状态

- 阶段：**T12 完成，项目收口**
- 已完成任务卡：`T0`、`T1`、`T2`、`T3`、`T4`、`T5`、`T6`、`T7`、`T8`、`T9`、`T10`、`T11`、`T12`
- 下一个任务卡：无（按 README Roadmap 继续）
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
| T11 | Redis 分布式实现 | `feat(T11): Redis 分布式实现`；`feat(T11): 分布式时间语义锁 1x` | `mvn -q clean verify` 退出码 0；IT 6 skipped（Windows 无 Docker pipe）；distributed 下 `speed=1/speedAdjusted=true/backend=redis`、角标 local→redis；reset 后 `guardflow:rl:*` 为空；Redis 不可用降级 local + WARN |
| T12 | 文档、README、发布 | `feat(T12): 文档、README 与发布` | `mvn -q clean verify` 退出码 0；README 三条命令启动成功（`/` 200、health UP）；README/docs 链接检查 `LINKS_OK`；CHANGELOG 与 GIF 分镜完成 |

---

## 下一步

项目已按 T0~T12 收口；后续按 README Roadmap 迭代。

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
- Windows 侧无 Docker CLI/pipe；WSL Ubuntu 有 Docker 29.1.3，但 Windows JVM 的 Testcontainers 不能直接使用 WSL daemon，`RedisLimiterIT` 本机仍 6 skip；WSL 未装 `docker compose` 插件。
- 分布式 speed 锁 1x：创建响应 `speed:1`/`speedAdjusted:true`；前端倍速控件 disabled 并显示「Redis 版按服务器真实时间计时，不支持倍速」；Redis 不可用时降级 local 但 speed 仍锁 1。
- Lua 生产路径用 `redis.call('TIME')`；测试通过 `guardflow:test:clock` 注入逻辑时间复用 T4 向量；令牌桶/漏桶两套脚本均已实现。
- 分布式 `backend` / `speedAdjusted` 已加入创建响应与 `ExperimentView`；Redis key 为 `guardflow:rl:tb:<id>` / `guardflow:rl:lb:<id>`，TTL 1h，delete/reset 清理。
- D8/D9 已写入决策记录；`spec-algorithms.md` §6、`api.md` §6、T11/T12 任务卡已补分布式时间语义与验收。
- T12：README 的 demo 仍是 `docs/demo.png` 截图占位（GIF 分镜在 `docs/README.md` 附录 5 步）；`docs/benchmark.md` 为可选项，未创建。
- 发布前检查：README 本地链接 `LINKS_OK`；`mvn -q clean package -DskipTests` + `java -jar` 启动后 `/` 200、`/api/health` UP；分布式验收见 T11。

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
| D8 | 分布式模式 speed 锁定 1x | Redis Lua 用服务器墙钟，倍速会让逻辑时间与令牌补充速率不同基准；锁 1x 保证页面语义可解释 | 2026-11-18 |
| D9 | 不把逻辑时间传入 Lua | 多实例虚拟时钟不同步会破坏分布式语义；不伪造服务端时间基准，也不按倍速放大 Redis 速率 | 2026-11-18 |
