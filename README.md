# AI Gateway

AI API 聚合网关与用量计费平台学习项目。

当前实现是一个单体后端闭环：

- 用户注册、登录
- 平台 API Key 管理
- Provider Key 加密存储
- OpenAI Chat Completions、Anthropic Messages、OpenAI Responses 兼容接口
- HTTP SSE 与 Responses WebSocket 流式调用
- Provider Key 调度、调用前 failover 和健康状态
- 请求日志、流式用量、幂等计费、并发余额扣减
- Redis 限流
- MyBatis 持久化
- 单元测试、Web 测试、Mapper 集成测试

## 技术栈

- JDK 21
- Maven
- Spring Boot 3.5.x
- MyBatis
- MySQL 8.4 LTS
- Redis
- Spring Security
- WebClient
- JUnit 5 / Mockito / Testcontainers

## 项目结构

```text
ai-gateway/
  ARCHITECTURE.md
  CODEBASE.md
  docs/
    requirements.md
    database.md
    api.md
    security.md
    pricing.md
  sql/
    schema.sql
    data.sql
  src/main/java/com/example/aigateway/
    common/
    config/
    controller/
    dto/
    entity/
    exception/
    gateway/
    mapper/
    security/
    service/
    provider/
    ratelimit/
    client/
  src/main/resources/
    application.yml
    mapper/
  src/test/java/
  docker-compose.yml
  pom.xml
```

## 本地运行

可以用 Docker Compose 启动 MySQL 和 Redis：

```powershell
docker compose up -d
```

也可以自行准备服务，然后设置环境变量：

```powershell
$env:DB_HOST="localhost"
$env:DB_PORT="3306"
$env:DB_NAME="ai_gateway"
$env:DB_USERNAME="root"
$env:DB_PASSWORD="your_password"
$env:REDIS_HOST="localhost"
$env:REDIS_PORT="6379"
```

启动项目：

```powershell
mvn spring-boot:run
```

健康检查：

```powershell
curl.exe http://localhost:8080/api/health
```
