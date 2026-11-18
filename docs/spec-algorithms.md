# 算法规格与测试向量

> 本文件是 guard-flow 的唯一真相来源（Single Source of Truth）。
> 实现必须通过全部测试向量；**向量与实现不一致时，永远是实现错了。**

---

## 0. 全局约定

### 0.1 时间

- 所有时间单位为**毫秒**，类型 `long`，从实验开始计（`t=0` 为实验起点），可因倍速而不等于真实时间。
- 时间只能由注入的 `Clock` 提供：`long nowMillis()`。测试使用 `FakeClock`，可任意拨动。
- 算法**不自己推进时间**，只响应「在 `t` 时刻来了 `n` 个请求」。空闲时段的行为必须在下次请求时惰性结算。

### 0.2 统一接口

```java
public interface Limiter {
    /** 在 nowMillis 时刻尝试获取 permits 个许可；一次调用内 permits 个请求按顺序逐个判定。 */
    Decision tryAcquire(long nowMillis, int permits);

    /** 读取当前状态用于绘图，不改变任何内部状态。 */
    Snapshot snapshot(long nowMillis);
}

public record Decision(boolean allowed, RejectReason reason, double level) {}
public record Snapshot(long t, double level, double capacity, double used) {}
```

- `level`：归一化水位，含义因算法而异，**必须落在 `[0, 1]`**（见各算法定义）。绘图只依赖它。
- `allow` 为 true 时 `reason` 必须是 `PASSED`。
- `tryAcquire(t, n)` 等价于连续调用 `n` 次 `tryAcquire(t, 1)`，同一时间戳下不得出现「批量白名单」。

### 0.3 拒绝原因枚举（固定，不得增删）

| 枚举值 | 语义 | 使用算法 |
|---|---|---|
| `PASSED` | 放行 | 全部 |
| `WINDOW_LIMIT` | 窗口内配额已满 | 固定窗口、滑动窗口 |
| `QUEUE_FULL` | 漏桶队列已满 | 漏桶 |
| `NO_TOKEN` | 令牌不足（含不足 1 个） | 令牌桶 |
| `INVALID_PARAM` | 参数非法（容量 ≤0、速率 ≤0、permits ≤0） | 全部 |

### 0.4 参数合法性

构造时校验，非法直接抛 `IllegalArgumentException`，不进入判定流程：

| 参数 | 合法区间 |
|---|---|
| `capacity` / `limit` | `1 ~ 1_000_000` |
| `ratePerSecond` | `0.001 ~ 1_000_000`（可为小数） |
| `windowMillis` | `10 ~ 86_400_000` |

### 0.5 浮点

`double` 计算，断言容差 `1e-9`。**禁止**用 `Math.round` 抹平令牌数——累积误差会让长时间运行的行为漂移。

---

## 1. 固定窗口 Fixed Window

### 1.1 参数

| 参数 | 含义 | 示例 |
|---|---|---|
| `limit` | 每个窗口允许的最大请求数 | 10 |
| `windowMillis` | 窗口长度 | 1000 |

### 1.2 状态

```java
long windowStart;  // 当前窗口起点 = floor(t / windowMillis) * windowMillis
int count;         // 当前窗口内已放行数
```

### 1.3 判定逻辑

```
windowStart' = (t / windowMillis) * windowMillis      // 整数除法，向下取整
if (windowStart' != windowStart) { count = 0; windowStart = windowStart'; }
if (count < limit) { count++; 放行(PASSED, level = count / limit) }
else { 拒绝(WINDOW_LIMIT, level = 1.0) }
```

窗口区间为**左闭右开** `[windowStart, windowStart + windowMillis)`。`t` 恰好等于窗口右边界时，已属于下一窗口。

### 1.4 已知缺陷（必须保留，用于演示）

窗口切换瞬间可放行 **2×limit** 个请求：前一个窗口末尾打满 + 新窗口开头打满，两个窗口边界相距极近。这是本项目要展示的核心现象之一，**不得「修复」**。

### 1.5 测试向量（`limit=10, windowMillis=1000`）

每个用例独立新建限流器。

| 编号 | 操作序列 | 期望结果 | 断言要点 |
|---|---|---|---|
| FW-01 | `t=0` × 10 | 全放行 | `count=10` |
| FW-02 | `t=0` × 10，再 `t=0` × 1 | 最后 1 个拒绝 | `reason=WINDOW_LIMIT` |
| FW-03 | `t=0` × 10，再 `t=999` × 1 | 拒绝 | 未跨窗口 |
| FW-04 | `t=0` × 10，再 `t=1000` × 1 | 放行 | 新窗口重置，`count=1` |
| FW-05 | `t=900` × 10，再 `t=1000` × 10 | 20 个全部放行 | **缺陷演示**：`[900,1000)` 区间内通过 20 个，超出 limit |

### 1.6 不变量

- `passed + rejected == offered`
- 任一窗口 `[k*W, (k+1)*W)` 内 `passed <= limit`
- `level ∈ [0, 1]`

---

## 2. 滑动窗口（日志法）Sliding Window Log

### 2.1 参数

| 参数 | 含义 | 示例 |
|---|---|---|
| `limit` | 窗口内允许的最大请求数 | 10 |
| `windowMillis` | 滑动窗口跨度 | 1000 |

### 2.2 状态

```java
Deque<Long> log;   // 已放行请求的时间戳，严格递增
```

内存上界：`log.size() <= limit`。每次判定前先剪枝。

### 2.3 判定逻辑

```
prune: while (!log.isEmpty() && log.peekFirst() <= t - windowMillis) log.pollFirst();
if (log.size() < limit) { log.addLast(t); 放行(PASSED, level = log.size() / limit) }
else { 拒绝(WINDOW_LIMIT, level = 1.0) }
```

窗口区间为**左开右闭** `(t - windowMillis, t]`：恰好在 `t - windowMillis` 时刻的记录视为**已过期**。

> 为什么是左开右闭：这样 `t=0` 的记录在 `t=1000`、`window=1000` 时过期，语义上是「最近 1 秒内」，不重复计数。

### 2.4 测试向量（`limit=10, windowMillis=1000`）

| 编号 | 操作序列 | 期望结果 | 断言要点 |
|---|---|---|---|
| SW-01 | `t=0` × 10 | 全放行 | `log.size()=10` |
| SW-02 | `t=0` × 10，再 `t=0` × 1 | 拒绝 | |
| SW-03 | `t=0` × 10，再 `t=999` × 1 | 拒绝 | `0 > 999-1000=-1`，仍在窗口内 |
| SW-04 | `t=0` × 10，再 `t=1000` × 1 | **放行** | `0 <= 0`，恰好过期 |
| SW-05 | `t=500` × 10，再 `t=1000` × 1 | 拒绝 | `500 > 0`，未过期 |
| SW-06 | `t=0,100,200,...,1900` 各 1 个 | 20 个**全部放行** | 均匀流量无抖动 |
| SW-07 | `t=0` × 10，再 `t=999` × 5 | 5 个全拒绝 | 突发被削平 |

### 2.5 不变量

- 任意时刻 `log.size() <= limit`
- 任意 `t`，`log` 中元素均满足 `> t - windowMillis` 且 `<= t`
- 任意长度 `windowMillis` 的时间段内，放行数 `<= limit`（比固定窗口严格，这正是差异所在）

---

## 3. 令牌桶 Token Bucket

### 3.1 参数

| 参数 | 含义 | 示例 |
|---|---|---|
| `capacity` | 桶容量（最大突发量） | 10 |
| `ratePerSecond` | 每秒补充令牌数 | 2 |

初始状态：`tokens = capacity`，`lastRefillMillis = 0`（首帧由构造时间决定）。

### 3.2 状态

```java
double tokens;
long lastRefillMillis;
```

### 3.3 判定逻辑（惰性补充）

```
refill:
  elapsed = t - lastRefillMillis
  if (elapsed > 0) {
      tokens = min(capacity, tokens + elapsed * ratePerSecond / 1000.0)
      lastRefillMillis = t
  }

判定:
  if (tokens >= 1.0) { tokens -= 1.0; 放行(PASSED, level = tokens / capacity) }
  else               { 拒绝(NO_TOKEN, level = tokens / capacity) }
```

关键点：**小数令牌必须累积**。`elapsed` 为 0 时不更新 `lastRefillMillis`，避免同一时间戳多次调用丢失精度。

### 3.4 测试向量（`capacity=10, ratePerSecond=2`）

| 编号 | 操作序列 | 期望结果 | 断言要点 |
|---|---|---|---|
| TB-01 | `t=0` × 10 | 全放行 | `tokens=0` |
| TB-02 | 新建，`t=0` × 11 | 10 放行、1 拒绝 | `reason=NO_TOKEN` |
| TB-03 | `t=0` × 10，再 `t=500` × 1 | 放行 | 补充 1.0，`tokens=0` |
| TB-04 | `t=0` × 10，再 `t=499` × 1 | 拒绝 | 补充 0.998，`tokens≈0.998`（容差 1e-9） |
| TB-05 | `t=0` × 5，再 `t=10000` × 12 | 10 放行、2 拒绝 | 补充封顶 `capacity=10`，**不得**变成 25 |
| TB-06 | `t=0` × 10，再 `t=200,400,600` 各 1 | 拒绝、拒绝、放行 | 小数累积：0.4 → 0.8 → 1.2 |

### 3.5 不变量

- `0 <= tokens <= capacity`（容差 1e-9）
- 任意窗口 `[t0, t0+Δ]` 内放行数 `<= capacity + ratePerSecond * Δ / 1000 + 1`
- 长时间（≥ 100×capacity/rate 秒）平均放行速率收敛于 `ratePerSecond`

---

## 4. 漏桶（队列模型）Leaky Bucket

### 4.1 参数

| 参数 | 含义 | 示例 |
|---|---|---|
| `capacity` | 队列容量 | 10 |
| `ratePerSecond` | 出队（处理）速率 | 2 |

派生：`intervalMillis = 1000 / ratePerSecond`。示例为 500ms。

### 4.2 状态

```java
int queued;              // 当前排队数（不建模实际等待，仅计数）
long lastDrainMillis;    // 上次结算时间
```

### 4.3 判定逻辑

```
结算（在判定前执行）:
  if (t > lastDrainMillis) {
      long drained = (t / intervalMillis) - (lastDrainMillis / intervalMillis)   // 整数间隔数
      queued = max(0, queued - (int) drained)
      lastDrainMillis = t
  }

判定:
  if (queued < capacity) { queued++; 放行(PASSED, level = queued / capacity) }
  else                   { 拒绝(QUEUE_FULL, level = 1.0) }
```

**关键约定**：出队事件发生在 `t = k * intervalMillis`（k 为正整数）的**整点**，且「先出队、再判定」。
即 `t=500` 时先结算掉 1 个，再判断新请求。

### 4.4 与令牌桶的区别（必须能在页面上看出来）

- 令牌桶：允许**突发**——空闲后最多一次放行 `capacity` 个。
- 漏桶：**削峰填谷**——无论空闲多久，出口速率恒定为 `ratePerSecond`，突发只会在队列里排队（本演示中队列满即拒绝，不模拟真实等待）。

### 4.5 测试向量（`capacity=10, ratePerSecond=2` → `interval=500`）

| 编号 | 操作序列 | 期望结果 | 断言要点 |
|---|---|---|---|
| LB-01 | `t=0` × 10 | 全放行 | `queued=10` |
| LB-02 | `t=0` × 10，再 `t=0` × 1 | 拒绝 | `reason=QUEUE_FULL` |
| LB-03 | `t=0` × 10，再 `t=500` × 1 | **放行** | 先出队 1 → 9，再入队 → 10 |
| LB-04 | `t=0` × 10，再 `t=499` × 1 | 拒绝 | 未到出队整点 |
| LB-05 | `t=0` × 10，再 `t=5000` × 5 | 5 个全放行 | 队列已排空，`queued=5` |
| LB-06 | `t=0` × 10，再 `t=1000` × 1 | 放行 | 出队 2 → 8，再入队 → 9 |

### 4.6 不变量

- `0 <= queued <= capacity`
- 任意时刻，队列长度不得超过容量
- 连续满负荷运行时，长期放行速率 ≈ `ratePerSecond`

---

## 5. 跨算法一致性要求

这四个算法必须能被同一套指标口径观察（见 `api.md` 的 `Sample`）。为此：

1. `Decision.level` 必须归一化到 `[0,1]`，前端只画这个值。
2. 同一 `t` 多次调用，水位单调性由算法语义决定，但**不得出现负值或超过 1**。
3. `snapshot(t)` 必须与「不调用 `tryAcquire` 直接读状态」一致，且**不推进**任何时间。

## 6. 分布式模式的时间语义

Redis 版限流器（`token_bucket` / `leaky_bucket`）在 Lua 内用 `redis.call('TIME')` 取 Redis 服务器墙钟判定，**不读取应用传入的 `nowMillis`**。因此分布式模式与单机模式的虚拟时钟语义脱钩，必须承认以下三条后果：

1. **倍速下基准不同**：`Sample.t` 推进的是逻辑时间，而 Redis 的令牌补充/漏桶出队按服务器真实时间结算；倍速越高，两者偏差越大。同一组参数与输入，单机结果与分布式结果**不可直接比较**。
2. **指标只在 speed=1 时对齐**：`Sample.t` 仍是逻辑时间，`level` 读的是 Redis 里的真实状态。只有 `speed=1` 时，逻辑时间与 Redis 墙钟才近似同一基准，页面上的 `t` 与 `level` 才有一致的解释。
3. **reset 必须 DEL 键**：分布式 reset 必须删除该实验的 `guardflow:rl:*` 键；只清页面/运行时缓存而不删键，下一次 `tryAcquire` 会继续使用旧令牌或旧队列，页面看起来「归零失败」。

硬性约束：

- 分布式 profile 下 `speed` 强制为 `1`。请求携带 `5/10/20/50` 时按 `1` 创建，创建响应返回 `speed:1` 与 `speedAdjusted:true`；请求本来就是 `1` 时 `speedAdjusted:false`。
- 前端在分布式模式下把倍速控件整体置灰、不可点击，并提示「Redis 版按服务器真实时间计时，不支持倍速」。
- `backend` 为 `"local"` / `"redis"`；Redis 不可用时降级 `"local"` 并记录 WARN，但分布式 profile 的 speed=1 约束不变。
- 测试可通过 `guardflow:test:clock` 注入逻辑时间复用 T4 向量；生产路径不设置该键，始终使用 `redis.call('TIME')`。

为什么不把逻辑时间传给 Lua：

- 多实例部署时各实例的虚拟时钟可能不同步；让 Lua 信任应用传入的时间，会让同一 Redis 状态被不同基准读写，分布式判定失去一致性。
- 宁可锁 `1x`，也不伪造一个看起来像虚拟时间的服务端基准；伪造基准会让时间语义更难解释。
- 不采用「倍速下把 Redis 速率乘以 speed」：页面参数（例如 2/s）与真实生效速率（100/s）会不一致，调参演示失去意义。

## 7. 待补（本版不做，写在 README 的 Roadmap 里）

- 滑动窗口计数器近似法（Sliding Window Counter）
- 分布式令牌桶（Redis + Lua，见 `tasks.md` T11）
- 预热/冷启动（Warm-up）模式
