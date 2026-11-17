# 依赖清单（白名单）

> 只允许下表出现的依赖。需要新增时**先提出、等人确认**，不得直接改 POM。

---

## 1. 根 POM（照抄，只改版本需人确认）

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.3.5</version>
    <relativePath/>
  </parent>

  <groupId>dev.guardflow</groupId>
  <artifactId>guard-flow</artifactId>
  <version>1.0.0-SNAPSHOT</version>
  <packaging>pom</packaging>

  <modules>
    <module>algorithm-core</module>
    <module>server</module>
  </modules>

  <properties>
    <java.version>21</java.version>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <testcontainers.version>1.20.1</testcontainers.version>
  </properties>

  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>testcontainers-bom</artifactId>
        <version>${testcontainers.version}</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>
      <dependency>
        <groupId>dev.guardflow</groupId>
        <artifactId>algorithm-core</artifactId>
        <version>${project.version}</version>
      </dependency>
    </dependencies>
  </dependencyManagement>
</project>
```

## 2. `algorithm-core/pom.xml`

只依赖测试库，**运行期零依赖**：

```xml
<parent>dev.guardflow:guard-flow:1.0.0-SNAPSHOT</parent>
<artifactId>algorithm-core</artifactId>

<dependencies>
  <dependency><groupId>org.junit.jupiter</groupId><artifactId>junit-jupiter</artifactId><scope>test</scope></dependency>
  <dependency><groupId>org.assertj</groupId><artifactId>assertj-core</artifactId><scope>test</scope></dependency>
</dependencies>
```

## 3. `server/pom.xml`

```xml
<parent>dev.guardflow:guard-flow:1.0.0-SNAPSHOT</parent>
<artifactId>guard-flow-server</artifactId>

<dependencies>
  <dependency><groupId>dev.guardflow</groupId><artifactId>algorithm-core</artifactId></dependency>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-web</artifactId></dependency>

  <!-- T11 才加入；在此之前不要出现 -->
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-redis</artifactId></dependency>

  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
  <dependency><groupId>org.testcontainers</groupId><artifactId>junit-jupiter</artifactId><scope>test</scope></dependency>
  <dependency><groupId>org.testcontainers</groupId><artifactId>testcontainers</artifactId><scope>test</scope></dependency>
</dependencies>

<build>
  <plugins>
    <plugin>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-maven-plugin</artifactId>
    </plugin>
  </plugins>
</build>
```

> `spring-boot-maven-plugin` 在 **T6** 才加入（此时才有主类）。T0 阶段加会导致 `repackage` 失败。

---

## 4. 白名单总表

| 依赖 | 版本 | 范围 | 引入阶段 | 用途 |
|---|---|---|---|---|
| spring-boot-starter-parent | 3.3.5 | parent | T0 | 版本仲裁 |
| junit-jupiter | 由 Boot 管理 | test | T0 | 单元测试 |
| assertj-core | 由 Boot 管理 | test | T0 | 断言 |
| spring-boot-starter-web | 3.3.5 | compile | T6 | MVC + 内嵌 Tomcat + SSE |
| spring-boot-starter-test | 3.3.5 | test | T6 | MockMvc 等 |
| spring-boot-starter-data-redis | 3.3.5 | compile | T11 | Lettuce 客户端 |
| testcontainers junit-jupiter / testcontainers | 1.20.1 | test | T11 | Redis 集成测试 |
| Jackson | 随 web | compile | T6 | 已是传递依赖，不单独声明 |

前端依赖：**无**。`static/` 下只有 `.html` / `.css` / `.js`，全部手写。

---

## 5. 黑名单（出现即返工）

| 依赖 | 理由 |
|---|---|
| Lombok | 隐式生成代码，AI 生成时容易与显式构造混淆；本项目类型少，手写即可 |
| MapStruct / Guava / Hutool / Commons-Lang3 | 用标准库能写完，不必扩大依赖面 |
| spring-boot-starter-validation | 参数校验规则简单且需自定义错误码，手写更可控 |
| Thymeleaf / FreeMarker | 前端是纯静态 + fetch，不需要模板引擎 |
| MyBatis / JPA / H2 / 任何数据库 | 实验状态纯内存（决策 D7） |
| spring-boot-starter-security | 无用户体系 |
| Kafka / RabbitMQ / Quartz / Netty / Vert.x | 与项目目标无关 |
| 任何前端库（React/Vue/ECharts/Chart.js/D3/jQuery/lodash） | 决策 D2：零依赖、零构建 |
| 任何代码生成器、AI SDK | 本项目不接 LLM |

---

## 6. Docker（仅 T11）

`docker-compose.yml` 只允许一个服务：

```yaml
services:
  redis:
    image: redis:7.4-alpine
    ports: ["6379:6379"]
    command: ["redis-server", "--appendonly", "no"]
```

不写 `version:` 字段（已废弃），不加网络/卷/健康检查之外的编排。
