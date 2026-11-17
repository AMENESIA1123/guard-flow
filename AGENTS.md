# AGENTS.md · guard-flow

> 本文件是 AI 协作的唯一入口。**每次会话必须先读本文件 + `PROGRESS.md`**，再动任何代码。
> 规则冲突时，以本文件为准；本文件没写的，先提问，不要自行决定。

---

## 1. 项目是什么

guard-flow 是一个「限流算法可视化实验室」：用可调参数 + 可加速的虚拟时钟，把**固定窗口、滑动窗口、令牌桶、漏桶**四种限流策略的实时决策过程画成图表，让四种算法的差异与缺陷在 30 秒内可见。

**是**：一个自解释的教学/演示型工程。
**不是**：生产级 API 网关、压测工具、SaaS 产品。

---

## 2. 技术栈（版本锁定，禁止升级或替换）

| 项 | 版本 / 选型 |
|---|---|
| JDK | 21 |
| Maven | 3.9.x |
| Spring Boot | 3.3.5 |
| 单元测试 | JUnit 5 + AssertJ |
| 集成测试 | Testcontainers 1.20.1（仅 redis 模块） |
| 前端 | 原生 HTML + ES Module + Canvas 2D，**零依赖、零构建** |

---

## 3. 命令（只用这些，不要自创）

| 用途 | 命令 |
|---|---|
| 全量构建 + 测试 | `mvn -q clean verify` |
| 核心模块测试 | `mvn -pl algorithm-core test` |
| 单类测试 | `mvn -pl algorithm-core test -Dtest=TokenBucketLimiterTest` |
| 启动（唯一推荐方式） | `mvn -q clean package -DskipTests && java -jar server/target/guard-flow-server-1.0.0-SNAPSHOT.jar` |
| 分布式模式 | `docker compose up -d redis` 后加 `--spring.profiles.active=distributed` |
| 访问页面 | http://localhost:8080 |

---

## 4. 目录与职责（不得新建顶层目录）

```
guard-flow/
├─ pom.xml                    聚合 POM，锁定所有版本
├─ algorithm-core/            纯算法：零第三方依赖，禁止 import Spring
│  └─ src/main/java/dev/guardflow/core/
│     ├─ Clock.java           时间源接口
│     ├─ SystemClock.java     生产实现
│     ├─ Limiter.java         统一限流器接口
│     ├─ Decision.java        单次决策结果
│     ├─ RejectReason.java    拒绝原因枚举（固定，不得增删）
│     ├─ LimiterConfig.java   参数载体
│     └─ limiter/             四个算法实现
├─ server/                    HTTP 适配 + 实验编排 + 指标采集
│  └─ src/main/java/dev/guardflow/server/
│     ├─ GuardFlowApplication.java
│     ├─ api/                 控制器、SSE
│     ├─ runtime/             实验运行时、输入生成、虚拟时钟、采样聚合
│     ├─ distributed/         Redis 实现（profile=distributed 才装配）
│     └─ src/main/resources/static/   前端（index.html / app.js / charts.js / styles.css）
└─ docs/                      规格与任务书
```

**硬性边界**：`algorithm-core` 里出现 `org.springframework`、`redis`、`jakarta` 任一 import，视为任务失败。

---

## 5. 编码约定（违反即返工）

1. **时间只能来自注入的 `Clock`**。`algorithm-core` 内出现 `System.currentTimeMillis()`、`Instant.now()`、`new Date()` 一律不允许。
2. 限流器必须**无副作用、可重放**：同一时间序列 + 同一参数 → 同一结果序列。
3. 公开方法参数统一为 `long nowMillis`，不使用 `Instant`/`LocalDateTime`，避免时区问题。
4. 拒绝原因必须取自 `RejectReason` 枚举，禁止自由字符串。
5. 浮点（令牌数、水位）用 `double`，断言时容差 `1e-9`。
6. 一个类一个职责；单文件不超过 250 行。
7. 不写 `TODO` 占位后声称完成；未实现就不提交。
8. 中文注释解释「为什么」，不解释「是什么」。

---

## 6. 完成定义（DoD）

一个任务算完成，必须同时满足：

- [ ] `mvn -q clean verify` 通过
- [ ] 新增行为有对应测试，且断言**具体数值**（不允许只断言 `isTrue()`）
- [ ] 任务卡里列出的验收命令全绿
- [ ] `PROGRESS.md` 已更新（本轮做了什么、下轮从哪开始、有无遗留问题）
- [ ] 已 `git commit`，提交信息格式：`feat(T3): 实现令牌桶核心`

---

## 7. 禁止事项

- 禁止新增任何 Maven 依赖（需要时先提出，等人确认）
- 禁止引入 Lombok、MapStruct、Guava、Hutool
- 禁止引入 React / Vue / ECharts / Chart.js / D3 等前端库
- 禁止引入数据库、用户体系、鉴权、分页、国际化
- 禁止格式化与本次任务无关的文件
- 禁止修改 `docs/spec-algorithms.md` 里的测试向量去迁就实现——**是实现错了，不是文档错了**

---

## 8. 每轮工作流程

1. 读 `AGENTS.md`（本文件）+ `PROGRESS.md`
2. 复述本轮要做的任务卡编号，确认理解一致
3. **只做这一张卡**，不顺手改别的
4. 跑该卡的验收命令
5. 更新 `PROGRESS.md`
6. `git commit`

输出结论时必须包含：改了哪些文件、验收命令的实际输出、遗留问题。
