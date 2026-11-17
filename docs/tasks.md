# 任务卡

> 一次会话只做一张卡。每张卡的验收命令必须真实执行并把输出贴出来。
> 「不做」栏是硬边界——越界即为返工。

| 卡 | 标题 | 依赖 | 预估 |
|---|---|---|---|
| T0 | 工程骨架与 CI | — | 0.5 天 |
| T1 | 核心契约与 FakeClock | T0 | 0.5 天 |
| T2 | 固定窗口 | T1 | 0.5 天 |
| T3 | 滑动窗口（日志法） | T1 | 0.5 天 |
| T4 | 令牌桶 | T1 | 0.5 天 |
| T5 | 漏桶 + 工厂补全 | T1 | 0.5 天 |
| T6 | 服务骨架与 REST | T2~T5 | 1 天 |
| T7 | SSE 指标流 | T6 | 0.5 天 |
| T8 | 前端骨架与静态托管 | T6 | 1 天 |
| T9 | Canvas 图表渲染 | T7, T8 | 1 天 |
| T10 | 控件联动与倍速 | T9 | 0.5 天 |
| T11 | Redis 分布式实现 | T5, T7 | 1 天 |
| T12 | 文档、README、发布 | 全部 | 0.5 天 |

---

## T0 · 工程骨架与 CI

**目标**：一个能 `mvn verify` 的空多模块工程。

- 参考：`docs/dependencies.md`、`docs/file-manifest.md` §1
- 涉及文件：`pom.xml`、`algorithm-core/pom.xml`、`server/pom.xml`、`.gitignore`、`LICENSE`(MIT)、`.github/workflows/ci.yml`
- 要求：
  - 根 POM `packaging=pom`，`<modules>` 含两个子模块
  - 父 POM 继承 `spring-boot-starter-parent:3.3.5`，`<java.version>21</java.version>`
  - 此时**不要**加 `spring-boot-maven-plugin`（还没有主类）
  - CI：`ubuntu-latest` + `temurin 21` + `mvn -B verify`

**验收**：`mvn -q clean verify` 退出码 0。
**不做**：不写任何 Java 类；不建 `src` 目录；不写 README。

---

## T1 · 核心契约与 FakeClock

**目标**：把 `Limiter` / `Decision` / `Snapshot` / `RejectReason` / `Clock` / `LimiterConfig` 落成代码。

- 参考：`spec-algorithms.md` §0、`file-manifest.md` §2
- 涉及文件：`core/` 下 7 个类 + 测试侧 `FakeClock.java`
- 要求：
  - `RejectReason` 恰好 5 个值，不得增删
  - `Decision`、`Snapshot` 用 `record`
  - `FakeClock` 放 `src/test/java`，提供 `advanceTo(long)` 与 `advanceBy(long)`
  - 全部为 `public`，包 `dev.guardflow.core`

**验收**：
1. `mvn -q -pl algorithm-core compile`
2. `grep -rE "springframework|redis|jakarta" algorithm-core/src/main/java` 无输出

**不做**：不写任何算法实现；不写 `LimiterFactory`。

---

## T2 · 固定窗口

**目标**：`FixedWindowLimiter` 通过 FW-01~FW-05。

- 参考：`spec-algorithms.md` §1
- 涉及文件：`limiter/FixedWindowLimiter.java`、`limiter/FixedWindowLimiterTest.java`、`LimiterFactory.java`（仅支持 `fixed_window`，其余抛 `UnsupportedOperationException`）
- 要求：
  - 测试方法命名 `fw01_...` ~ `fw05_...`，注释标注向量编号
  - FW-05 的注释必须写明「这是预期缺陷，不是 bug」

**验收**：`mvn -pl algorithm-core test -Dtest=FixedWindowLimiterTest` 全绿（5 个用例）。

**不做**：不实现其他三个算法；不改根 POM。

---

## T3 · 滑动窗口（日志法）

**目标**：`SlidingWindowLogLimiter` 通过 SW-01~SW-07。

- 参考：`spec-algorithms.md` §2
- 涉及文件：`limiter/SlidingWindowLogLimiter.java`、对应测试、`LimiterFactory` 增加分支
- 重点：边界 `(t - window, t]` 的左开语义；`Deque` 剪枝；`log.size()` 上界 `limit`

**验收**：`mvn -pl algorithm-core test -Dtest=SlidingWindowLogLimiterTest` 全绿（7 个用例）。

**不做**：不做计数器近似法。

---

## T4 · 令牌桶

**目标**：`TokenBucketLimiter` 通过 TB-01~TB-06。

- 参考：`spec-algorithms.md` §3
- 涉及文件：`limiter/TokenBucketLimiter.java`、对应测试、`LimiterFactory` 增加分支
- 重点：小数令牌累积；`elapsed <= 0` 不更新 `lastRefillMillis`；封顶 `capacity`
- TB-04 断言 `tokens ≈ 0.998`，容差 `1e-9`

**验收**：`mvn -pl algorithm-core test -Dtest=TokenBucketLimiterTest` 全绿（6 个用例）。

**不做**：不做 Redis 版本（T11）；不做预热。

---

## T5 · 漏桶 + 工厂补全

**目标**：`LeakyBucketLimiter` 通过 LB-01~LB-06，`LimiterFactory` 支持全部四个 id。

- 参考：`spec-algorithms.md` §4
- 涉及文件：`limiter/LeakyBucketLimiter.java`、对应测试、`LimiterFactory` 补分支
- 重点：出队按 `t / interval` 的整数间隔结算，**先出队后判定**
- 补一个 `LimiterFactoryTest`：未知 id / 非法参数 → `IllegalArgumentException`

**验收**：`mvn -pl algorithm-core test` 全绿（四个测试类 + 工厂测试）。

**不做**：不做排队等待的真实延迟模拟。

---

## T6 · 服务骨架与 REST

**目标**：能 `POST /api/experiments` 并 `GET` 到快照；`GET /api/algorithms` 返回四个算法的元数据。

- 参考：`api.md` §1~§3、`file-manifest.md` §2~§3
- 涉及文件：`server/pom.xml`（加 `spring-boot-maven-plugin`）、`GuardFlowApplication`、`api/*`、`api/dto/*`、`runtime/{ExperimentRegistry, ExperimentRuntime, InputGenerator, SampleAggregator, RunStatus}`、`application.yml`
- 要求：
  - `InputGenerator` 四种模式，`poisson` 用 `new Random(seed)` 保证可复现
  - `ExperimentRuntime.tick()` 只在 `running` 时推进，逻辑帧固定 100ms
  - 参数校验失败 → 400 `INVALID_PARAM`
  - `ApiSmokeTest` 用 `MockMvc`：建实验 → start → 手动 tick 若干次 → GET 校验 `passed + rejected == offered`

**验收**：
1. `mvn -q clean package -DskipTests`
2. `java -jar server/target/guard-flow-server-1.0.0-SNAPSHOT.jar` 后 `curl localhost:8080/api/algorithms` 返回 4 个算法

**不做**：不做 SSE；不做前端；不接 Redis。

---

## T7 · SSE 指标流

**目标**：`/stream` 每逻辑 100ms 推送一个 `Sample`，断线不崩。

- 参考：`api.md` §4~§5
- 涉及文件：`api/SseHub.java`、`runtime/TickerConfig.java`、`runtime/ExperimentRuntimeTest.java`
- 要求：
  - 单个 `ScheduledExecutorService`（1 线程）驱动所有实验，`fixedRate=100ms`，实际推进量按 `speed` 折算
  - `SseHub` 管理 `emitter` 生命周期，`onCompletion/onTimeout/onError` 都要移除
  - `ExperimentRuntimeTest`：给定恒定输入 + 令牌桶，`tick()` 20 次后断言 `totals` 具体数值

**验收**：`curl -N localhost:8080/api/experiments/{id}/stream` 能看到连续 `event: sample`，`Ctrl+C` 后服务日志无异常。

**不做**：不做 WebSocket；不做断线期间的数据补偿。

---

## T8 · 前端骨架与静态托管

**目标**：打开 `http://localhost:8080` 能看到完整布局，能选算法、建实验、点开始，数字在跳。

- 参考：`ui-spec.md` §1~§3、§6
- 涉及文件：`resources/static/index.html`、`styles.css`、`app.js`
- 要求：
  - 参数表单由 `/api/algorithms` **动态生成**
  - 此时图表区用 `<pre>` 显示最近 3 条 `Sample` 的 JSON 即可
  - 严格按 `ui-spec.md` 的布局尺寸与颜色值

**验收**：手动验证——选「令牌桶」→ 容量 10 / 速率 2 → 恒定 20qps → 开始，KPI 数字持续增长，暂停后停止，重置后归零。

**不做**：不写 Canvas（T9）；不引入任何 JS 库。

---

## T9 · Canvas 图表渲染

**目标**：两张大图 + 拒绝原因分布条，按 60fps 平滑滚动。

- 参考：`ui-spec.md` §4~§5
- 涉及文件：`charts.js`（新建）、`app.js`（接入）、`index.html`（canvas 标签）
- 要求：
  - `charts.js` 导出两个函数：`drawDecisionChart(ctx, samples, opts)`、`drawLevelChart(ctx, samples, opts)`
  - 支持 `devicePixelRatio` 高清屏
  - 环形缓冲 300 点，超出左移
  - `requestAnimationFrame` 重绘；数据到达与渲染解耦

**验收**：手动验证——两种算法切换后图形形态明显不同（固定窗口出现台阶、令牌桶出现锯齿）。

**不做**：不做 hover tooltip（T10）；不做导出图片。

---

## T10 · 控件联动与倍速

**目标**：改参数即时重建实验；倍速切换生效；hover 显示数值。

- 参考：`ui-spec.md` §6~§7
- 涉及文件：`app.js`、`charts.js`、`styles.css`
- 要求：
  - 参数变更 → 调 `DELETE` + `POST` 重建，**先暂停再重建**
  - 倍速切换调 `POST /control`？——不新增接口，倍速在重建时提交
  - hover：按鼠标 x 反查最近采样点，显示 `t / offered / passed / rejected`
  - 参数合法区间与 `api.md` 的 `min/max` 一致，越界时按钮禁用并提示

**验收**：手动验证——50 倍速下 30 秒逻辑时间在 0.6 秒内跑完，图不撕裂；越界参数无法提交。

**不做**：不做参数预设方案（preset）保存。

---

## T11 · Redis 分布式实现

**目标**：`--spring.profiles.active=distributed` 下令牌桶走 Redis + Lua，页面显示 `redis` 角标。

- 参考：`spec-algorithms.md` §3、`api.md` §6
- 涉及文件：`distributed/RedisTokenBucketLimiter.java`、`LuaScripts.java`、`docker-compose.yml`、`RedisLimiterIT.java`
- 要求：
  - Lua 脚本内用 `redis.call('TIME')` 取服务端时间，**不用**应用侧时间
  - `RedisLimiterIT` 用 `@Testcontainers(disabledWithoutDocker = true)`，复用 T4 的测试向量
  - Redis 不可用时降级为本地实现并 WARN，不影响创建实验

**验收**：
1. `docker compose up -d redis`
2. `mvn -q -pl server test -Dtest=RedisLimiterIT` 通过（无 Docker 时应 skip 而非 fail）
3. 页面上角标由 `local` 变为 `redis`

**不做**：不做集群/哨兵；不做窗口类算法的 Redis 版。

---

## T12 · 文档、README、发布

**目标**：仓库可对外。

- 涉及文件：`README.md`、`CHANGELOG.md`、`docs/benchmark.md`（可选）
- README 结构（顺序固定）：
  1. 一句话定位 + 一张 GIF（占位图先用 `docs/demo.png`）
  2. 为什么做这个（3 行）
  3. 快速开始（3 条命令）
  4. 四种算法对比表（含「固定窗口的 2 倍突发」现象说明）
  5. 架构图（ASCII 或 Mermaid）
  6. 指标口径与实现取舍链接到 `docs/`
  7. Roadmap
- 要求：GIF 分镜脚本写在 `docs/README.md` 附录（录屏步骤 5 步）

**验收**：新克隆仓库 → 按 README 三条命令能跑起来；README 中每条链接可点开。

**不做**：不写英文版；不做在线 Demo 部署。
