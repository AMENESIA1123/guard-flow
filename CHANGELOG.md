# Changelog

本项目遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/) 的结构，版本号沿用 `MAJOR.MINOR.PATCH`。

## [1.0.0-SNAPSHOT] - 2026-11-18

### Added

- 四种限流算法：固定窗口、滑动窗口（日志法）、令牌桶、漏桶。
- 统一契约：`Limiter`、`Decision`、`Snapshot`、`RejectReason`、`Clock`、`LimiterConfig`，以及测试用 `FakeClock`。
- Spring Boot 服务：实验创建 / 状态控制 / 查询 / 删除、算法元数据、健康检查。
- SSE 指标流：连接建立推 `snapshot`，每逻辑帧推 `sample`，控制状态变化推 `state`。
- 前端：原生 HTML + CSS + ES Module + Canvas，零依赖；算法选择、动态参数、流量模式、倍速、KPI、两张图表、hover tooltip。
- 虚拟时钟与 1x/5x/10x/20x/50x 倍速；输入模式 `constant`、`step`、`spike`、`poisson`。
- 可选 distributed profile：令牌桶 / 漏桶走 Redis + Lua，窗口类回落 local；Redis 不可用时 WARN 降级。
- Testcontainers `RedisLimiterIT`，无 Docker 环境自动 skip。

### Changed

- 分布式模式 speed 锁定 1x：请求其他倍速时返回 `speed:1` 与 `speedAdjusted:true`，前端倍速控件禁用并提示原因。
- Redis 实验 key 统一为 `guardflow:rl:tb|lb:<id>`，TTL 1 小时；delete/reset 清理。
- 分布式 `backend` / `speedAdjusted` 字段加入创建响应、查询响应与 SSE `snapshot`。

### Notes

- 固定窗口在边界处的 2 倍突发是刻意保留的演示缺陷，不是 bug。
- Redis 版 Lua 用服务器墙钟判定，不读取应用逻辑时间；虚拟时钟只在单机 / 窗口类算法上完整生效。
- 当前不是生产级网关：无鉴权、无持久化、无集群治理。
