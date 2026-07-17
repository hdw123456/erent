# AI Gateway Codebase Guide

本文是项目代码地图，说明目录、文件、类型和关键方法的用途。纯 getter、setter、构造器和简单字段访问器不逐项列出。系统设计与调用时序见 [ARCHITECTURE.md](ARCHITECTURE.md)。

## 1. 根目录

```text
ai-gateway/
├── AGENTS.md                       仓库级开发与文档同步规则
├── pom.xml                         Maven 依赖和构建配置
├── Dockerfile                      后端应用镜像构建
├── docker-compose.yml              应用、MySQL、Redis、RabbitMQ 与 Nginx 编排
├── README.md                       项目入口和本地启动说明
├── ARCHITECTURE.md                 架构、依赖、调用链和一致性说明
├── CODEBASE.md                     文件与代码索引
├── docs/                           API、数据库、安全、定价和需求文档
├── nginx/
│   ├── default.conf                当前加载的 HTTP /api 与 SSE 代理
│   └── https.conf.example          未加载的 HTTPS/TLS 模板
├── sql/                            全量 schema、种子数据、增量迁移
├── src/main/java/                  Java 主代码
├── src/main/resources/             application.yml 和 MyBatis XML
└── src/test/java/                  单元测试和集成测试
```

`pom.xml` 的主要依赖：Spring MVC、WebFlux、WebSocket、Security、Validation、Redis、Spring AMQP、MyBatis、MySQL、Jasypt、JJWT、JCS、JUnit、Mockito 和 Testcontainers。

`docs/` 文件：

| 文件 | 用途 |
| --- | --- |
| `api.md` | HTTP、SSE、WebSocket 路径和请求响应示例 |
| `database.md` | 表关系、字段、索引和用量语义 |
| `pricing.md` | token 计价、流式 usage 与部分计费规则 |
| `security.md` | 密钥、DTO、日志和错误响应安全约束 |
| `requirements.md` | 初始 V1 范围与非目标，作为历史需求基线 |
| `nginx-deployment.md` | Nginx 当前入口、服务器同步和后续 HTTPS 启用步骤 |

## 2. 应用与配置

| 文件/类型 | 用途 | 关键方法 |
| --- | --- | --- |
| `AiGatewayApplication` | Spring Boot 启动类 | `main()` 启动应用 |
| `client/WebClientConfig` | 配置上游 Netty 连接、读写和响应超时 | `upstreamWebClient()` 创建专用 `WebClient` Bean |
| `client/UpstreamHttpClient` | 统一发起上游 JSON/SSE 请求 | `postJson()` 非流式请求；`postJsonStream()` SSE 数据流 |
| `config/SecurityConfig` | 配置无状态安全链、过滤器顺序和异常响应 | `securityFilterChain()`；`authenticationProvider()` |
| `config/ResponsesWebSocketConfig` | 注册 Responses WebSocket 地址 | `registerWebSocketHandlers()` |
| `security/PasswordConfig` | 提供 BCrypt 密码编码器 | 密码编码器 Bean |
| `common/ErrorResponse` | 稳定错误响应结构 | `of()` 创建错误对象 |

### RabbitMQ 消息骨架

| 文件/类型 | 用途 | 关键方法 |
| --- | --- | --- |
| `messaging/RabbitTopology` | 声明 `ai-gateway.events` Topic Exchange，日志、用量、通知三个持久 Queue，Binding 和 Jackson JSON converter | `gatewayEventsExchange()`；三个 Queue/Binding Bean；`rabbitMessageConverter()` |
| `messaging/GatewayEventPublisher` | 将事件以持久消息发布到 Topic Exchange | `publishRequestCompleted()`；`publishUsageRecorded()` |
| `messaging/event/RequestCompletedEvent` | 请求完成事件信封，包含 event/request/trace ID、版本、归因、状态和耗时 | record 访问器 |
| `messaging/event/UsageRecordedEvent` | 已记录用量事件信封，包含 token、成本、usage source、版本和追踪信息 | record 访问器 |
| `messaging/consumer/RequestLogConsumer` | 日志型最小 Consumer，成功后手动 ACK，运行时异常时 NACK 并重新入队 | `consume()` |
| `messaging/consumer/UsageStatisticsConsumer` | 用量型最小 Consumer，成功后手动 ACK，运行时异常时 NACK 并重新入队 | `consume()` |

`messaging`、`messaging.event` 和 `messaging.consumer` 各有 `package-info.java` 说明包边界。当前 Producer 接入非流式成功链路，核心事务首次提交后 best-effort 发布两个事件；两个 Consumer 只记录日志，不写业务表。

每个 Java 包都有 `package-info.java`，用于说明该包的边界和职责。

### 异常处理

| 文件/类型 | 用途 | 关键方法 |
| --- | --- | --- |
| `exception/BusinessException` | 带稳定错误码和 HTTP 状态的业务异常 | 构造器与状态访问器 |
| `exception/ProviderUpstreamException` | 额外保留上游状态、headers 和 body | 上游响应访问器 |
| `exception/GlobalExceptionHandler` | 将校验、缺失 Header、业务异常和未知异常映射为统一 JSON | 各 `@ExceptionHandler` 方法 |

## 3. Controller

### `controller/AuthController`

基础路径 `/api/auth`。

- `registerUser()`：注册用户并初始化默认角色、钱包。
- `loginUser()`：用户名密码认证，返回 access/refresh token。
- `refreshToken()`：验证 refresh token 并签发新 access token。

### `controller/ApiKeyController`

基础路径 `/api/api-keys`。

- `createApiKey()`：创建平台 API Key，明文只返回一次。
- `getUserApiKeyByUserId()`：列出当前用户的 API Key。
- `updateApiKey()`：修改 Key 名称等可变信息。
- `disableApiKey()`：禁用当前用户拥有的 Key。

### `controller/ProviderKeyController`

基础路径 `/api/provider-keys`。

- `saveProviderKey()`：保存加密后的上游凭证和类型/base URL/优先级。
- `updateProviderKey()`：轮换凭证、调整调度开关并清除错误冷却状态。

### `controller/ModelCallController`

模型调用总入口。

- `chat()`：项目内部非流式 `/api/chat/completions`。
- `stream()`：项目内部 SSE `/api/chat/completions/stream`。
- `chatCompletions()`：`/v1/chat/completions`、`/chat/completions`。
- `messages()`：Anthropic 风格 `/v1/messages`。
- `countTokens()`：`/v1/messages/count_tokens` 的轻量估算。
- `responses()`：Responses 风格 HTTP 入口及 Codex 兼容别名。
- `responsesWebsocketProbe()`：普通 GET 返回 426，提示客户端发起 WebSocket Upgrade。
- `currentApiKey()`：从 Spring Security Authentication 提取 `ApiKeyPrincipal`。
- `streamResponse()`：为 `SseEmitter` 设置 `text/event-stream`。

### 其他 Controller

| 类型 | 路径 | 用途 |
| --- | --- | --- |
| `GatewayModelController` | `/v1/models` | 返回 OpenAI 风格模型列表 |
| `ModelController` | `/api/models` | 返回内部模型和价格投影 |
| `RequestLogController` | `/api/request-logs` | 当前用户请求日志分页查询 |
| `WalletController` | `/api/wallets/me` | 当前用户钱包余额 |
| `UserController` | `/api/users/me` | 当前用户资料 |
| `HealthController` | `/api/health` | 公开健康检查 |
| `AdminController` | `/api/admin/health` | 管理员权限检查入口 |

## 4. 核心 Service

### `service/ModelCallService`

项目最主要的调用编排器。

- `chat(...)`：生成 fingerprint、可选幂等抢占、余额预检、模型解析、Provider Key failover、非流式调用和成功计费。
- `stream(...)`：创建 HTTP `SseEmitter`，使用 `SseGatewayStreamSink` 进入统一流式链路。
- `streamToSink(...)`：让 WebSocket 或其他传输复用同一流式业务逻辑，并返回取消回调。
- `startStream()`：抢占幂等记录、选择模型和候选 Key、订阅 Provider Flux。
- `providerStreamWithFailover()`：首个事件前失败时尝试下一把 Key；首个事件后禁止切换。
- `handleStreamEvent()`：累加 usage/文本并发送协议事件。
- `completeStream()`：先完成 usage 和钱包事务，再发送终止事件。
- `handleStreamFailure()`：有上游事件时写部分 usage 并扣费；无事件时只写失败日志。
- `writeUsageRecord()` / `toUsageRecord()`：将 `ChatResponse.Usage`、价格和 Provider Key 转为 `UsageRecord`；首次成功落库后发布请求与用量事件，MQ 异常不改变核心调用结果。
- `publishEventsBestEffort()`：分别发布 `request.completed` 和 `usage.recorded`，隔离并记录 `AmqpException`。
- `claimIdempotencyRequest()`：使用 `INSERT IGNORE` 获得一次执行权。
- `replayIdempotentResponse()`：重放非流式结果。
- `replayIdempotentStream()`：重放已缓存的完整 SSE frames。
- `canonicalizePayload()` / `buildRequestFingerprint()`：JCS 规范化并计算 SHA-256 fingerprint。
- `calculateCost()`：按 input/output token 单价计算费用。

### `service/BillingService`

拥有钱包和 usage 的事务边界。

- `recordSuccessfulUsage()`：原子写成功日志、usage、钱包余额、钱包流水和幂等完成结果，并返回本次是否首次落库。
- `recordPartialUsage()`：对已经发生的部分流式用量扣费，但把幂等结果保持为失败。
- `recordFailedRequestWithoutCharge()`：只写失败日志和幂等失败结果。
- `ensureWalletCanStartCall()`：调用前检查钱包存在且余额大于零。
- `lockWallet()`：`SELECT ... FOR UPDATE` 获取钱包行锁。
- `deduct()`：锁内检查余额并更新余额。
- `claimUsageBillingDedup()`：扣费前抢占 `(request_id, api_key_id)` 去重记录。
- `preDeductThenRollbackOnFailure()`：事务回滚教学/测试辅助路径，不是正常模型调用流程。

### Provider Key Service

| 类型 | 关键方法 | 用途 |
| --- | --- | --- |
| `ProviderKeyService` | `saveProviderKey()`、`updateProviderKey()` | 加密、脱敏、所有权校验和管理字段规范化 |
| `ProviderCredentialService` | `toCredential()`、`resolveEnvironmentCredential()` | 解密数据库 Key，合并 base URL/headers，处理环境变量回退 |
| `ProviderKeySelectorService` | `selectCredentials()` | 查询并构造有序候选凭证，限制 failover 次数 |
| `ProviderKeyAvailabilityService` | `markSuccess()`、`markFailure()` | 根据 401/403/429/timeout/5xx 更新错误、冷却和时间戳 |

### 其他 Service

| 类型 | 关键方法 | 用途 |
| --- | --- | --- |
| `ModelService` | `listAvailableModels()`、`getAvailableModelByCode()` | 查询启用模型及当前价格规则 |
| `StreamResponseAccumulator` | `accept()`、`result()` | 汇总流式文本、finish reason 和 usage；缺失 usage 时估算 |
| `UpstreamErrorService` | `toBusinessException()`、`toProviderResponseException()` | 统一上游错误并保留响应状态/headers/body |
| `ApiKeyService` | `createApiKey()`、`authenticateRawApiKey()`、`disableApiKey()` | 平台 API Key 生命周期和鉴权 |
| `JwtService` | `generateAccessToken()`、`parseClaims()` | JWT 签发与解析 |
| `RefreshTokenService` | `create()`、`verifyAndGetUsername()` | refresh token 生成和验证 |
| `CustomUserDetailsService` | `loadUserByUsername()` | Spring Security 用户加载 |
| `CurrentUserService` | `getCurrentUserId()`、`getCurrentUser()` | 当前 JWT 用户解析 |
| `UserService` | `register()`、`getUserById()`、`getRole()` | 用户注册、查询和角色读取 |
| `WalletService` | `getWalletByUserId()` | 钱包查询 |
| `RequestLogService` | `searchUserRequestLogs()` | 构造查询条件并分页映射日志响应 |

## 5. 协议适配

### `gateway/GatewayRequestAdapter`

- `fromChatCompletions()`：解析 OpenAI Chat body；保存 `openAiPayload` 以原样转发未知字段。
- `fromAnthropicMessages()`：将 `system`、Messages content 和 token 参数转为 `ChatRequest`。
- `fromResponses()`：将 `instructions`、`input` 和输出 token 参数转为 `ChatRequest`。
- `estimateInputTokens()`：按文本字符数进行轻量 token 估算。
- `readMessages()` / `readResponsesInput()`：处理各协议的消息容器。
- `contentText()`：从字符串、content block、数组或对象中抽取文本。

### `gateway/GatewayResponseAdapter`

- `toOpenAiChatCompletion()`：生成 Chat Completions JSON。
- `toAnthropicMessage()`：生成 Anthropic Message JSON。
- `toResponses()`：生成 Responses JSON。
- `toModels()`：生成 `/v1/models` 列表。
- `countTokens()`：生成 Anthropic token count 响应。

### `gateway.stream`

| 类型 | 用途 | 关键方法 |
| --- | --- | --- |
| `GatewayStreamProtocol` | 标识 OpenAI Chat、Anthropic Messages、OpenAI Responses 三种下游格式 | 枚举值 |
| `GatewayStreamFrame` | 一个下游事件名和 JSON data | `data()` 创建无事件名帧 |
| `GatewayStreamSink` | SSE/WebSocket 共用传输边界 | `send()`、`complete()`、`completeWithError()` |
| `SseGatewayStreamSink` | 将 frame 写入 `SseEmitter` | 实现 `GatewayStreamSink` |
| `GatewayStreamResponseAdapter` | 创建协议事件和错误事件 | `open()`、`replay()`、`error()` |
| `GatewayStreamResponseAdapter.Session` | 保存单条流的事件顺序、ID 和 sequence number | `accept()`、`finish()` |
| `StreamReplayResponse` | 幂等缓存的聚合响应和精确 frames | JSON 序列化模型 |

### `gateway.websocket/ResponsesWebSocketHandler`

- `afterConnectionEstablished()`：初始化连接内状态。
- `handleTextMessage()`：校验 `response.create`、限制单 in-flight turn、解析请求并启动 stream sink。
- `afterConnectionClosed()` / `handleTransportError()`：取消正在运行的 Provider 流。
- `WebSocketStreamSink`：把 Responses frame 作为 WebSocket TextMessage 发送，并读取 `response.completed`。
- `SessionState.prepareHistory()`：验证 `previous_response_id` 并拼接连接内历史。
- `SessionState.completeTurn()`：保存上一轮 assistant 文本和 response ID。
- `SessionState.failTurn()`：失败时使连接内历史失效；`registerCancellation()` / `cancelTurn()` 处理关闭连接与订阅建立之间的竞态。

## 6. Provider 与上游客户端

| 类型 | 用途 | 关键方法 |
| --- | --- | --- |
| `ProviderAdapter` | Provider 集成统一接口 | `providerCodes()`、`chat()`、`stream()` |
| `ProviderAdapterFactory` | Provider code 到 Adapter 的注册表 | `getAdapter()` |
| `ProviderCredential` | 单次调用使用的解密 Key、类型、base URL 和 headers | `requireApiKey()`、`requireBaseUrl()` |
| `ProviderStreamEvent` | Provider 中立流事件 | `done()` |
| `openai/OpenAiAdapter` | OpenAI/OpenRouter 非流式和 SSE 调用 | `chat()`、`stream()`、`upstreamRequest()` |
| `openai/OpenAiStreamEventParser` | 解析 OpenAI chunk 的 delta、finish reason 和 usage | `parse()` |
| `openai/OpenaiRequest` | Chat Completions 请求模型 | `from()`；`StreamOptions.includeUsage()` |
| `openai/OpenaiResponse` | Chat Completions 响应模型 | `toChatResponse()` |
| `claude/ClaudeAdapter` | 原生 Anthropic Adapter 占位边界 | 当前返回 `PROVIDER_ADAPTER_NOT_IMPLEMENTED` |
| `gemini/GeminiAdapter` | 原生 Gemini Adapter 占位边界 | 当前返回 `PROVIDER_ADAPTER_NOT_IMPLEMENTED` |
| `ClaudeRequest/ClaudeResponse` | Anthropic 原生 payload 占位模型 | 数据访问器 |
| `GeminiRequest/GeminiResponse` | Gemini 原生 payload 占位模型 | 数据访问器 |

`client/UpstreamHttpClient.postJsonStream()` 只负责读取 SSE data；Provider 专属 parser 负责解释 JSON。这样 HTTP 客户端不依赖任何模型协议。

## 7. 安全

| 类型 | 用途 | 关键方法 |
| --- | --- | --- |
| `ApiKeyAuthFilter` | 网关路径的平台 API Key 鉴权 | `shouldNotFilter()`、`doFilterInternal()`、`extractApiKey()` |
| `ApiKeyPrincipal` | API Key 认证后的 `apiKeyId/userId/prefix` | 只读访问器 |
| `ApiKeyHasher` | 生成和验证 API Key hash | hash/verify 方法 |
| `JwtAuthFilter` | 管理接口 JWT 鉴权 | `doFilterInternal()` |
| `ProviderKeyCrypto` | Provider Key 静态加密与调用时解密 | `encrypt()`、`decrypt()` |
| `SensitiveDataMasker` | 生成不可逆的显示提示 | `maskSecret()` |

## 8. 限流

| 类型 | 用途 | 关键方法 |
| --- | --- | --- |
| `ApiKeyRateLimitFilter` | 依次检查 API Key、用户和 IP 三个维度 | `doFilterInternal()` |
| `FixedWindowRateLimiter` | Redis Lua 原子 `INCR + EXPIRE` | `check()`、`buildKey()` |
| `GatewayRateLimitProperties` | 绑定 `gateway.rate-limit.*` | 配置访问器 |
| `RateLimitDecision` | 一次限流检查结果 | `allowed()`、`blocked()` 工厂方法 |

Redis Key 格式：

```text
rl:fixed:<dimension>:<identifier>:<window>s:<windowId>
```

## 9. DTO

### 通用查询投影 `dto/`

| 类型 | 用途 |
| --- | --- |
| `ProviderModelPricing` | Provider、模型、base URL、input/output 价格组合 |
| `ModelUsageStats` | 按模型聚合的调用、错误和延迟统计 |
| `LowBalanceUser` | 低余额用户查询投影 |
| `RequestLogQuery` | 请求日志分页和时间过滤条件 |
| `UserApiKeyQuery` | 用户/API Key 联合查询条件 |
| `UserDailyUsageSummary` | 用户每日调用数和费用汇总 |

### 请求 DTO `dto/request/`

| 类型 | 用途 |
| --- | --- |
| `ChatRequest` | 内部标准消息、model、provider、stream 和 OpenAI 原始 payload |
| `CreateApiKeyRequest` | 创建平台 API Key |
| `UpdateApiKeyRequest` | 更新平台 API Key |
| `CreateProviderKeyRequest` | 创建 Provider Key、类型、base URL 和 priority |
| `UpdateProviderKeyRequest` | 轮换/启停/调度 Provider Key |
| `LoginRequest` | 用户登录 |
| `UserRegister` | 用户注册 |

### 响应 DTO `dto/response/`

| 类型 | 用途 |
| --- | --- |
| `ChatResponse` | Provider 中立非流式结果和 usage |
| `ApiKeyResponse` | API Key 脱敏详情 |
| `CreateApiKeyResponse` | 创建时的一次性明文 Key 响应 |
| `ProviderKeyResponse` | Provider Key 状态、冷却和最后错误 |
| `RequestLogResponse` | 单条日志输出 |
| `RequestLogPageResponse` | 日志分页容器 |
| `TokenResponse` | access/refresh token |
| `UserResponse` | 用户资料 |
| `WalletResponse` | 钱包余额 |

## 10. Entity 与数据库表

| Entity | 表/用途 | 关键字段 |
| --- | --- | --- |
| `UserAccount` | `user_account` | username、passwordHash、email、enabled |
| `UserApiKey` | 用户和 API Key 联合投影 | 用户/API Key 展示字段 |
| `ApiKey` | `api_key` | keyHash、prefix、enabled、lastUsedAt |
| `ProviderKey` | `provider_key` | type、baseUrl、status、schedulable、priority、cooldowns |
| `ProviderKeyQuotaWindow` | `provider_key_quota_window` | windowType、limit、used、resetAt |
| `Wallet` | `wallet` | userId、balance |
| `WalletTransaction` | `wallet_transaction` | type、amount、balanceAfter、requestId |
| `RequestLog` | `request_log` | requestId、API/Provider/Key/model、status、latency |
| `UsageRecord` | `usage_record` | token counts、usageSource、costAmount、providerKeyId |
| `IdempotencyRecord` | `idempotency_record` | keyHash、fingerprint、status、responseJson |
| `UsageBillingDedup` | `usage_billing_dedup` | requestId、apiKeyId、fingerprint |

## 11. Mapper 与 XML

每个 `mapper/*.java` 接口与 `resources/mapper/*Mapper.xml` 同名配对。

| Mapper | 主要操作 |
| --- | --- |
| `UserMapper` | 用户 CRUD、角色查询和角色绑定 |
| `ApiKeyMapper` | API Key CRUD、hash 查询、last-used 更新 |
| `ModelMapper` | 启用模型和价格查询、按 code 精确解析 |
| `ProviderKeyMapper` | Key CRUD、候选调度查询、成功/失败/冷却状态更新 |
| `ProviderKeyQuotaWindowMapper` | quota window upsert、查询和用量累加 |
| `WalletMapper` | 钱包插入、普通查询、`FOR UPDATE` 查询和余额更新 |
| `WalletTransactionMapper` | 写钱包流水 |
| `RequestLogMapper` | 写日志、按 request/user 查询、分页和模型统计 |
| `UsageRecordMapper` | 写 usage、用户每日聚合 |
| `IdempotencyRecordMapper` | 幂等 claim、查询、锁定和终态更新 |
| `UsageBillingDedupMapper` | 扣费去重 claim、查询和锁定 |

关键 SQL 位于 XML 而不是注解中。修改 Mapper 方法签名时必须同步修改 XML 的 `id`、参数名、result map 和列列表。

## 12. SQL

| 文件 | 用途 |
| --- | --- |
| `sql/schema.sql` | 新数据库的完整结构，必须代表当前最终状态 |
| `sql/data.sql` | 本地模型、价格、用户、钱包和示例数据 |
| `sql/20260705_idempotency_constraints.sql` | 初始幂等约束迁移 |
| `sql/20260706_fingerprint_dedup.sql` | fingerprint 和 billing dedup 迁移 |
| `sql/20260706_provider_key_scheduler.sql` | Provider Key 状态、quota window 和归因字段迁移 |
| `sql/20260711_streaming_usage_source.sql` | `usage_record.usage_source` 迁移 |

增量脚本使用 `information_schema` 判断字段、索引或约束是否存在，允许重复执行。

## 13. Resources

`src/main/resources/application.yml`：数据库、Redis、RabbitMQ、MyBatis、server、限流、上游超时、Provider Key failover、日志、Jasypt、JWT 和 Provider 默认地址。Rabbit listener 当前使用手动 ACK，prefetch/concurrency 可由环境变量调整。

`src/main/resources/mapper/`：全部 MyBatis SQL。金额、余额和费用使用 `DECIMAL`/`BigDecimal`，禁止改成浮点数。

## 14. 测试代码

| 测试 | 覆盖内容 |
| --- | --- |
| `AiGatewayApplicationTests` | 最小应用测试 |
| `GatewayRequestAdapterTest` | 三种请求格式归一化 |
| `GatewayStreamResponseAdapterTest` | OpenAI/Anthropic/Responses 事件顺序 |
| `ResponsesWebSocketHandlerTest` | response.create、单 in-flight、previous_response_id、失败失效和断开取消 |
| `OpenAiStreamEventParserTest` | delta、usage 和 DONE 解析 |
| `BillingServiceTest` | 成功扣费、重复跳过、fingerprint 冲突、部分计费 |
| `ModelCallServiceFingerprintTest` | JCS 和 route fingerprint |
| `ModelCallServiceProviderKeyFailoverTest` | 非流式 Key 切换和最终 Key 归因 |
| `ModelCallServiceStreamingTest` | 流式 Key 切换、成功/部分计费和幂等重放 |
| `StreamResponseAccumulatorTest` | Provider usage 与估算 fallback |
| `ProviderKeyAvailabilityServiceTest` | 429 reset 和鉴权失效状态 |
| `UpstreamErrorServiceTest` | 上游错误映射 |
| `ApiKeyAuthFilterTest` | API Key header 鉴权 |
| `ApiKeyRateLimitFilterTest` | 多维限流 filter |
| `FixedWindowRateLimiterTest` | Redis Lua 计数和窗口行为 |
| `RabbitMessagingSkeletonTest` | RabbitMQ 持久拓扑、Producer 路由/持久属性和 Consumer 手动 ACK；无需真实 Broker |
| `ModelCallServiceMessagingTest` | 首次记录发布双事件、重复记录跳过发布、MQ 异常不逃逸 |
| `WalletServiceTest` | 钱包查询 |
| `UserMapperIntegrationTest` | Testcontainers MySQL/MyBatis 用户写读；Docker 不可用时明确跳过 |

## 15. 常见修改入口

| 需求 | 首先查看 |
| --- | --- |
| 新增兼容 API 路由 | `ModelCallController`、`GatewayRequestAdapter`、`GatewayResponseAdapter` |
| 修改 SSE 事件 | `GatewayStreamResponseAdapter`、对应协议测试 |
| 修改流结束计费 | `ModelCallService.completeStream()`、`BillingService` |
| 修改幂等规则 | `ModelCallService`、`IdempotencyRecordMapper`、`UsageBillingDedupMapper` |
| 修改 Key 选择 | `ProviderKeyMapper.xml`、`ProviderKeySelectorService` |
| 修改 Key 错误冷却 | `ProviderKeyAvailabilityService`、`ProviderKeyMapper.xml` |
| 新增 Provider | `ProviderAdapter`、新 Adapter、`ProviderAdapterFactory` 自动注册 |
| 修改价格或费用 | `ModelMapper.xml`、`ModelCallService.calculateCost()`、`BillingService` |
| 修改余额一致性 | `WalletMapper.xml`、`BillingService`、数据库唯一约束 |
| 修改限流 | `GatewayRateLimitProperties`、`ApiKeyRateLimitFilter`、`FixedWindowRateLimiter` |
| 修改鉴权路径 | `ApiKeyAuthFilter.isGatewayPath()`、`SecurityConfig` |
| 修改数据库字段 | Entity、Mapper XML、`schema.sql`、新日期迁移、测试 |
| 修改消息拓扑或事件 | `RabbitTopology`、`messaging/event`、`GatewayEventPublisher`、对应 Consumer 和 `RabbitMessagingSkeletonTest` |
| 修改 Nginx、SSE 代理或 HTTPS | `nginx/default.conf`、`nginx/https.conf.example`、`docker-compose.yml`、`docs/nginx-deployment.md` |
