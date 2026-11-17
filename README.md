# docs 索引

guard-flow 采用「文档先行」：先把规格写死，再让实现去对齐。**文档是判据，代码是待判的对象。**

|文件|作用|谁看|何时读|
|-|-|-|-|
|[`../AGENTS.md`](../AGENTS.md)|工程约定、命令、禁区、DoD|AI / 人|每次会话开头|
|[`../PROGRESS.md`](../PROGRESS.md)|进度、决策记录|AI / 人|每次会话开头|
|[`spec-algorithms.md`](spec-algorithms.md)|四种算法的精确定义 + 测试向量|实现者|写算法前|
|[`api.md`](api.md)|HTTP / SSE 契约、参数元数据|前后端|写接口前|
|[`file-manifest.md`](file-manifest.md)|文件清单与类职责、关键签名|实现者|建骨架时|
|[`tasks.md`](tasks.md)|任务卡（T0\~T12）|AI|每轮取一张|
|[`ui-spec.md`](ui-spec.md)|布局、配色、图表坐标轴、交互|前端实现者|画页面前|
|[`dependencies.md`](dependencies.md)|依赖白名单与版本|实现者|写 pom 时|

## 阅读顺序（冷启动）

`AGENTS.md` → `PROGRESS.md` → `tasks.md` 找到当前卡 → 按卡上「参考文档」跳转。

## 维护规则

* 改动行为前先改规格，再改代码；**不允许**改测试向量来迁就实现。
* 规格里的编号（`FW-01`、`TB-05`…）是稳定标识，测试方法名必须包含它，便于双向追溯。



