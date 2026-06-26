# AI Gateway

AI API 聚合网关与用量计费平台学习项目。

当前目标是配合第三、四阶段学习路线，先完成一个单体后端闭环：

- 用户注册、登录
- 平台 API Key 管理
- Provider Key 加密存储
- 统一模型调用接口
- 请求日志、用量记录、余额扣费
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
    mapper/
    security/
    service/
    provider/
    billing/
    ratelimit/
    audit/
    client/
  src/main/resources/
    application.yml
    mapper/
  src/test/java/
  docker-compose.yml
  pom.xml
```

## 本地运行

先准备 MySQL 和 Redis，然后设置环境变量：

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

