# API Design

本文档记录当前阶段已经实现或规划中的 HTTP API。

当前已接入 Spring Security + JWT。除注册、登录、刷新 Token 和健康检查外，业务接口需要通过 `Authorization: Bearer <accessToken>` 识别当前用户。

统一错误响应：

```json
{
  "code": "USERNAME_EXISTS",
  "message": "Username already exists",
  "details": null,
  "timestamp": "2026-06-27T13:00:00+08:00"
}
```

常用错误状态码：

| HTTP 状态码 | 场景 |
| --- | --- |
| `400` | 请求参数或 JSON 请求体校验失败 |
| `401` | 缺少认证信息，例如缺少或传入无效 Access Token |
| `403` | 当前用户无权访问该资源 |
| `404` | 资源或接口路径不存在 |
| `409` | 业务状态冲突 |
| `429` | 请求过于频繁 |
| `500` | 服务端内部错误 |

## Auth

### 注册用户

```text
POST /api/auth/register
```

请求头：

```text
Content-Type: application/json
```

请求体：

```json
{
  "username": "alice",
  "password": "123456",
  "email": "alice@test.com"
}
```

成功响应：

```text
HTTP 200
```

错误示例：

```json
{
  "code": "USERNAME_EXISTS",
  "message": "Username already exists",
  "details": null,
  "timestamp": "2026-06-27T13:00:00+08:00"
}
```

curl：

```powershell
curl.exe -X POST "http://localhost:8080/api/auth/register" -H "Content-Type: application/json" -d "{\"username\":\"alice\",\"password\":\"123456\",\"email\":\"alice@test.com\"}"
```

## User

### 查询当前用户

```text
GET /api/users/me
```

请求头：

```text
Authorization: Bearer <accessToken>
```

成功响应：

```json
{
  "id": 1,
  "username": "alice",
  "email": "alice@test.com",
  "enabled": true,
  "createdAt": "Sat Jun 27 13:00:00 CST 2026"
}
```

错误示例：

```json
{
  "code": "USER_NOT_FOUND",
  "message": "User not found",
  "details": null,
  "timestamp": "2026-06-27T13:00:00+08:00"
}
```

curl：

```powershell
curl.exe "http://localhost:8080/api/users/me" -H "Authorization: Bearer <accessToken>"
```

## API Key

### 创建平台 API Key

```text
POST /api/api-keys
```

请求头：

```text
Content-Type: application/json
Authorization: Bearer <accessToken>
```

请求体：

```json
{
  "name": "local-test-key"
}
```

成功响应：

```json
{
  "id": 1,
  "name": "local-test-key",
  "prefix": "ak_12345",
  "apiKey": "ak_1234567890abcdef1234567890abcdef",
  "enabled": true
}
```

说明：

- `apiKey` 明文只在创建时返回一次。
- 数据库只保存 API Key hash。
- 后续列表、更新、禁用接口只返回 `prefix`，不会返回明文或 hash。

curl：

```powershell
curl.exe -X POST "http://localhost:8080/api/api-keys" -H "Content-Type: application/json" -H "Authorization: Bearer <accessToken>" -d "{\"name\":\"local-test-key\"}"
```

### 查询平台 API Key 列表

```text
GET /api/api-keys
```

请求头：

```text
Authorization: Bearer <accessToken>
```

成功响应：

```json
[
  {
    "id": 1,
    "name": "local-test-key",
    "prefix": "ak_12345",
    "enabled": true,
    "createdAt": "Sat Jun 27 13:00:00 CST 2026",
    "lastUsedAt": null
  }
]
```

curl：

```powershell
curl.exe "http://localhost:8080/api/api-keys" -H "Authorization: Bearer <accessToken>"
```

### 更新平台 API Key

```text
PATCH /api/api-keys/{id}
```

请求头：

```text
Content-Type: application/json
Authorization: Bearer <accessToken>
```

路径参数：

```text
id: API Key ID
```

请求体：

```json
{
  "name": "new-key-name"
}
```

成功响应：

```json
{
  "id": 1,
  "name": "new-key-name",
  "prefix": "ak_12345",
  "enabled": true,
  "createdAt": "Sat Jun 27 13:00:00 CST 2026",
  "lastUsedAt": null
}
```

curl：

```powershell
curl.exe -X PATCH "http://localhost:8080/api/api-keys/1" -H "Content-Type: application/json" -H "Authorization: Bearer <accessToken>" -d "{\"name\":\"new-key-name\"}"
```

### 禁用平台 API Key

```text
PATCH /api/api-keys/{id}/disable
```

路径参数：

```text
id: API Key ID
```

成功响应：

```text
HTTP 200
```

错误示例：

```json
{
  "code": "API_KEY_NOT_FOUND",
  "message": "API key not found",
  "details": null,
  "timestamp": "2026-06-27T13:00:00+08:00"
}
```

curl：

```powershell
curl.exe -X PATCH "http://localhost:8080/api/api-keys/1/disable" -H "Authorization: Bearer <accessToken>"
```

## Provider Key

### 创建 Provider API Key

```text
POST /api/provider-keys
```

请求头：

```text
Content-Type: application/json
Authorization: Bearer <accessToken>
```

请求体：

```json
{
  "providerId": 1,
  "rawProviderKey": "sk-provider-secret"
}
```

成功响应：

```json
{
  "id": 1,
  "providerId": 1,
  "keyHint": "sk-p****cret",
  "enabled": true
}
```

说明：

- `rawProviderKey` 只用于请求入参，入库前会加密。
- 响应不会返回明文 Provider Key，也不会返回数据库密文。

curl：

```powershell
curl.exe -X POST "http://localhost:8080/api/provider-keys" -H "Content-Type: application/json" -H "Authorization: Bearer <accessToken>" -d "{\"providerId\":1,\"rawProviderKey\":\"sk-provider-secret\"}"
```

### 更新 Provider API Key

```text
PATCH /api/provider-keys/{id}
```

请求头：

```text
Content-Type: application/json
Authorization: Bearer <accessToken>
```

路径参数：

```text
id: Provider Key ID
```

请求体：

```json
{
  "rawProviderKey": "sk-provider-secret-new"
}
```

成功响应：

```json
{
  "id": 1,
  "providerId": 1,
  "keyHint": "sk-p****-new",
  "enabled": true
}
```

curl：

```powershell
curl.exe -X PATCH "http://localhost:8080/api/provider-keys/1" -H "Content-Type: application/json" -H "Authorization: Bearer <accessToken>" -d "{\"rawProviderKey\":\"sk-provider-secret-new\"}"
```

## Model

### 查询模型和价格

```text
GET /api/models
```

请求头：

```text
Authorization: Bearer <accessToken>
```

查询参数：

```text
providerCode: 可选，例如 OPENAI
```

成功响应：

```json
[
  {
    "providerId": 1,
    "providerCode": "OPENAI",
    "providerName": "OpenAI",
    "modelId": 1,
    "modelCode": "gpt-4.1-mini",
    "modelDisplayName": "GPT-4.1 Mini",
    "inputTokenPrice": 0.000001,
    "outputTokenPrice": 0.000004,
    "currency": "CNY"
  }
]
```

curl：

```powershell
curl.exe "http://localhost:8080/api/models" -H "Authorization: Bearer <accessToken>"
curl.exe "http://localhost:8080/api/models?providerCode=OPENAI" -H "Authorization: Bearer <accessToken>"
```

## Request Log

### 分页查询请求日志

```text
GET /api/request-logs
```

请求头：

```text
Authorization: Bearer <accessToken>
```

查询参数：

```text
page: 可选，默认 1，最小 1
size: 可选，默认 20，范围 1 到 100
modelId: 可选，模型 ID
statusCode: 可选，HTTP 状态码
startTime: 可选，格式 yyyy-MM-dd HH:mm:ss
endTime: 可选，格式 yyyy-MM-dd HH:mm:ss
```

成功响应：

```json
{
  "items": [
    {
      "id": 1,
      "requestId": "req_001",
      "userId": 1,
      "apiKeyId": 1,
      "providerId": 1,
      "modelId": 1,
      "statusCode": 200,
      "latencyMs": 320,
      "errorCode": null,
      "createdAt": "Sat Jun 27 13:00:00 CST 2026"
    }
  ],
  "page": 1,
  "size": 10,
  "total": 1,
  "totalPages": 1
}
```

错误示例：

```json
{
  "code": "INVALID_PAGE_SIZE",
  "message": "size must be between 1 and 100",
  "details": null,
  "timestamp": "2026-06-27T13:00:00+08:00"
}
```

curl：

```powershell
curl.exe "http://localhost:8080/api/request-logs?page=1&size=10" -H "Authorization: Bearer <accessToken>"
curl.exe "http://localhost:8080/api/request-logs?page=1&size=10&modelId=1&statusCode=200&startTime=2026-06-01%2000:00:00&endTime=2026-06-30%2023:59:59" -H "Authorization: Bearer <accessToken>"
```

## Wallet

```text
GET /api/wallets/me
```

Request headers:

```text
Authorization: Bearer <accessToken>
```

Success response:

```json
{
  "id": 1,
  "userId": 1,
  "balance": 20.000000
}
```

The endpoint always uses the current authenticated user and does not accept a `userId` request parameter.

## Admin

```text
GET /api/admin/health
```

Request headers:

```text
Authorization: Bearer <adminAccessToken>
```

Only users with `ROLE_ADMIN` can access `/api/admin/**`. A normal user receives `403`.

## Gateway

Gateway endpoints use platform API keys, not JWT access tokens.

Accepted API key headers:

```text
Authorization: Bearer <platformApiKey>
x-api-key: <platformApiKey>
x-goog-api-key: <platformApiKey>
```

### Agent-compatible endpoints

```text
GET  /v1/models
POST /v1/chat/completions
POST /chat/completions
POST /v1/messages
POST /v1/messages/count_tokens
POST /v1/responses
POST /responses
POST /backend-api/codex/responses
```

`/v1/chat/completions` accepts OpenAI-compatible request bodies. Top-level fields such as `tools`, `tool_choice`, and `stream_options` are preserved when forwarding to OpenAI-compatible providers. The project-private fields `providerCode`, `provider_code`, and `provider` may be used to choose a configured provider and are removed before upstream forwarding.

`/v1/messages` accepts Anthropic Messages-style bodies and maps `system`, `messages`, `max_tokens`, and `stream` into the internal request model.

`/v1/responses` accepts OpenAI Responses-style bodies and maps `instructions`, `input`, `max_output_tokens`, and `stream` into the internal request model.

Gateway requests, including streaming requests, may include `Idempotency-Key`. If present, the same API key + same idempotency key + same raw protocol payload replays the stored JSON response or exact SSE frames and will not call the provider or charge twice. If absent, the request is processed normally without strict retry de-duplication.

Streaming responses are formatted for the requested public protocol:

- Chat Completions uses data-only JSON chunks followed by `[DONE]`.
- Anthropic Messages uses `message_start`, content-block, message-delta, and `message_stop` events.
- Responses uses typed events such as `response.created`, `response.output_text.delta`, and `response.completed`.

The Responses paths also accept an authenticated WebSocket upgrade. Send one `response.create` JSON event at a time; subsequent turns on the same connection may use the previous `response_id` as `previous_response_id`. A failed turn invalidates that connection-local history, and closing the connection cancels any in-flight provider stream.

### OpenAI Chat Completions example

```powershell
curl.exe -X POST "http://localhost:8080/v1/chat/completions" -H "Content-Type: application/json" -H "Authorization: Bearer <platformApiKey>" -H "Idempotency-Key: demo-001" -d "{\"providerCode\":\"OPENROUTER\",\"model\":\"openrouter/free\",\"messages\":[{\"role\":\"user\",\"content\":\"Say hello.\"}],\"tools\":[{\"type\":\"function\",\"function\":{\"name\":\"lookup\"}}],\"tool_choice\":\"auto\",\"max_tokens\":64}"
```

Success response shape:

```json
{
  "id": "chatcmpl_xxx",
  "object": "chat.completion",
  "created": 1783290000,
  "model": "openrouter/free",
  "choices": [
    {
      "index": 0,
      "message": {
        "role": "assistant",
        "content": "Hello."
      },
      "finish_reason": "stop"
    }
  ],
  "usage": {
    "prompt_tokens": 12,
    "completion_tokens": 8,
    "total_tokens": 20
  }
}
```

### Internal compatibility endpoint

```text
POST /api/chat/completions
POST /api/chat/completions/stream
```

These endpoints keep the project's internal `ChatRequest` and `ChatResponse` shapes.

Notes:

- Management APIs use JWT access tokens.
- Gateway APIs use platform API keys created by `POST /api/api-keys`.
- Requests are rejected before upstream calls when the wallet is missing or has no positive balance.
- Final successful billing writes `request_log`, `usage_record`, `wallet_transaction`, and the idempotency result in one transaction for both non-streaming and streaming calls.
- Final balance deduction uses the model's `pricing_rule` prices and locks the wallet row with `FOR UPDATE`.
- Streaming asks OpenAI-compatible providers for a terminal usage chunk. If usage is unavailable, the gateway stores an explicit `ESTIMATED` fallback in `usage_record.usage_source`.
- A stream that fails after provider output began records and charges partial usage; a failure before the first provider event is not charged.
- Stream failover is allowed only before the first provider event, so output from different provider keys is never spliced together.
