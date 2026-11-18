# HTTP / SSE 接口契约

> 前后端唯一契约。字段名、单位、可空性以此为准，实现不得自创别名（如 `ts` / `timestamp` / `time` 混用）。
> 内容为 OpenAPI 3 子集的精简版，可直接转成 `openapi.yaml`。

---

## 0. 通用约定

- Base URL：`http://localhost:8080`
- 编码：UTF-8，JSON 字段 **camelCase**
- 时间字段：`long`，单位毫秒，**逻辑时间**（从实验起点算），不是 epoch
- 失败响应统一：

```json
{ "error": { "code": "INVALID_PARAM", "message": "ratePerSecond 必须大于 0" } }
```

| HTTP | code | 场景 |
|---|---|---|
| 400 | `INVALID_PARAM` | 参数越界、算法未知、permits ≤ 0 |
| 404 | `EXPERIMENT_NOT_FOUND` | 实验 id 不存在 |
| 409 | `EXPERIMENT_STATE` | 状态不允许该操作（如对 running 的重复 start） |
| 500 | `INTERNAL` | 未预期异常 |

---

## 1. 算法元数据

### `GET /api/algorithms`

前端据此**动态生成参数表单**，不得在前端硬编码参数。

```yaml
200:
  [
    {
      id: "token_bucket",
      displayName: "令牌桶",
      summary: "允许突发，空闲后最多一次性放行 capacity 个",
      params: [
        { key: "capacity",      label: "桶容量",   type: "int",    min: 1,   max: 1000, step: 1,    default: 10 },
        { key: "ratePerSecond", label: "补充速率", type: "double", min: 0.1, max: 1000, step: 0.1,  default: 2,  unit: "个/秒" }
      ]
    },
    {
      id: "fixed_window",
      displayName: "固定窗口",
      summary: "窗口边界处可能出现 2 倍突发",
      params: [
        { key: "limit",        label: "窗口配额", type: "int", min: 1,  max: 1000, step: 1,   default: 10 },
        { key: "windowMillis", label: "窗口长度", type: "int", min: 10, max: 60000, step: 100, default: 1000, unit: "ms" }
      ]
    },
    {
      id: "sliding_window",
      displayName: "滑动窗口",
      summary: "任意 1 秒内严格不超过配额，无边界突发",
      params: [
        { key: "limit",        label: "窗口配额", type: "int", min: 1,  max: 1000,  step: 1,   default: 10 },
        { key: "windowMillis", label: "窗口跨度", type: "int", min: 10, max: 60000, step: 100, default: 1000, unit: "ms" }
      ]
    },
    {
      id: "leaky_bucket",
      displayName: "漏桶",
      summary: "恒定速率流出，突发只能排队或被拒",
      params: [
        { key: "capacity",      label: "队列容量", type: "int",    min: 1,   max: 1000, step: 1,   default: 10 },
        { key: "ratePerSecond", label: "流出速率", type: "double", min: 0.1, max: 1000, step: 0.1, default: 2,  unit: "个/秒" }
      ]
    }
  ]
```

`id` 枚举固定：`fixed_window` | `sliding_window` | `token_bucket` | `leaky_bucket`。

---

## 2. 输入模式

创建实验时的 `input` 对象：

| mode | 字段 | 语义 |
|---|---|---|
| `constant` | `qps` (1~500) | 恒定速率，每个 100ms 逻辑帧均匀分配 |
| `step` | `segments: [{atMillis, qps}]` | 阶跃，按时间切换速率 |
| `spike` | `qps`, `burstSize` (1~500), `everyMillis` | 周期性尖峰 |
| `poisson` | `qps`, `seed` (long) | 泊松到达，给定 seed 必须可复现 |

生成算法由 `InputGenerator` 负责，**必须确定性**：同一 `(mode, 参数, seed, tick)` → 同一请求数。

---

## 3. 实验生命周期

### `POST /api/experiments`

```yaml
requestBody:
  {
    "algorithm": "token_bucket",
    "params": { "capacity": 10, "ratePerSecond": 2 },
    "input":   { "mode": "constant", "qps": 20 },
    "speed":   10
  }
201:
  {
    "id": "e_7f3a9c",
    "status": "idle",
    "algorithm": "token_bucket",
    "params": { "capacity": 10, "ratePerSecond": 2 },
    "input":   { "mode": "constant", "qps": 20 },
    "speed":   10,
    "createdAt": "2026-11-17T21:30:00+08:00"
  }
```

- `speed`：倍速，枚举 `1 | 5 | 10 | 20 | 50`，默认 `10`
- 创建后状态为 `idle`，**不自动开始**
- 每个实验独占一个限流器实例，互不干扰
- 实验数量上限 20，超出返回 409

### `POST /api/experiments/{id}/control`

```yaml
requestBody: { "action": "start" }   # start | pause | reset
200: { "id": "e_7f3a9c", "status": "running" }
```

| action | 前置状态 | 效果 |
|---|---|---|
| `start` | idle / paused | 恢复推进，`t` 继续累加 |
| `pause` | running | 冻结，`t` 停止 |
| `reset` | 任意 | 清空指标与算法状态，`t=0`，状态回 `idle` |

### `GET /api/experiments/{id}`

```yaml
200:
  {
    "id": "e_7f3a9c",
    "status": "running",
    "t": 4200,
    "totals": { "offered": 84, "passed": 60, "rejected": 24 },
    "level": 0.35,
    "samples": [
      { "t": 4100, "offered": 2, "passed": 2, "rejected": 0, "level": 0.4,  "reason": "PASSED" },
      { "t": 4200, "offered": 2, "passed": 0, "rejected": 2, "level": 0.3,  "reason": "NO_TOKEN" }
    ]
  }
```

`samples` 只返回最近 300 条（30 秒逻辑时间），供页面刷新后回填。

### `DELETE /api/experiments/{id}` → `204`

### `GET /api/health` → `{ "status": "UP", "profile": "default" }`

---

## 4. SSE 指标流

### `GET /api/experiments/{id}/stream`

`Content-Type: text/event-stream`，浏览器的 `EventSource` 直接消费。

| event | 频率 | data |
|---|---|---|
| `snapshot` | 连接建立时 1 次 | 同 `GET /api/experiments/{id}` 的响应体 |
| `sample` | 每逻辑 100ms 一帧（按倍速折算真实间隔） | 单个 `Sample` |
| `state` | 状态变化时 | `{ "status": "running" }` |

```
event: sample
data: {"t":4200,"offered":2,"passed":0,"rejected":2,"level":0.3,"reason":"NO_TOKEN"}

```

约束：
- 单帧 `Sample` 必须聚合该 100ms 内**全部**请求，不允许丢帧
- 断线重连后从 `snapshot` 重建，不做增量补偿
- 后端不得为单个客户端单独启动线程；用 Spring 的 `SseEmitter` + 单一定时任务广播

---

## 5. 指标口径（前端绘图直接依赖，禁止自行推算）

### 5.1 Sample 字段

| 字段 | 类型 | 含义 |
|---|---|---|
| `t` | long | 该帧结束时的逻辑时间（ms），等于 `帧序号 * 100` |
| `offered` | int | 本帧到达的请求数 |
| `passed` | int | 放行数 |
| `rejected` | int | 拒绝数 = `offered - passed` |
| `level` | double | 帧末归一化水位 `[0,1]` |
| `reason` | string | 本帧**主要**拒绝原因；无拒绝时为 `PASSED` |

### 5.2 硬性关系

- `passed + rejected == offered`（每帧都必须成立，前端可直接校验）
- `level` 单调无跳变要求：由算法语义决定，但必须 `0 <= level <= 1`
- 页面展示的 QPS = `offered / 0.1`（本帧瞬时值），**不做平滑**

### 5.3 状态枚举

`idle` | `running` | `paused` —— 前端按钮可用性依赖此枚举，不得新增。

---

## 6. 分布式模式差异（profile=distributed）

- 仅 `token_bucket` 与 `leaky_bucket` 提供 Redis 实现，窗口类算法回落到单机实现。
- `backend`：`"local"` | `"redis"`，默认 `"local"`；出现在创建响应、`GET /api/experiments/{id}` 与 SSE `snapshot` 中，前端据此显示角标。
- `speedAdjusted`：boolean，默认 `false`；分布式下请求倍速不是 1 时为 `true`，实际 `speed` 强制为 1。
- 分布式下 `speed` 强制为 1：请求 5/10/20/50 时按 1 创建，响应返回 `speed:1` 与 `speedAdjusted:true`；请求本来就是 1 时返回 `speedAdjusted:false`。
- 前端在分布式模式下把倍速控件整体置灰不可点，并提示「Redis 版按服务器真实时间计时，不支持倍速」。
- Redis 版 Lua 用 `redis.call('TIME')` 取服务器墙钟，不读应用逻辑时间；因此 `Sample.t` 仍是逻辑时间、`level` 是 Redis 真实状态，仅 speed=1 时语义对齐（详见 `spec-algorithms.md` §6）。
- reset 必须删除该实验的 `guardflow:rl:*` 键；否则旧令牌/旧队列会让页面看似归零失败。
- Redis 不可用时**不得**导致创建实验失败：降级为 `local` 并记录 WARN 日志；分布式 profile 的 speed=1 约束保持。
