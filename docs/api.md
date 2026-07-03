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

## Gateway

以下接口是后续阶段规划，当前暂未实现。

```text
POST /v1/chat/completions
```

### Implemented gateway endpoints

#### Non-streaming chat completion

```text
POST /api/chat/completions
```

Request headers:

```text
Content-Type: application/json
Authorization: Bearer <platformApiKey>
```

Request body:

```json
{
  "providerCode": "OPENROUTER",
  "model": "openrouter/free",
  "messages": [
    {
      "role": "user",
      "content": "Say hello in one short sentence."
    }
  ],
  "temperature": 0.7,
  "max_tokens": 64
}
```

Success response:

```json
{
  "requestId": "req_xxx",
  "id": "chatcmpl_xxx",
  "model": "openrouter/free",
  "message": {
    "role": "assistant",
    "content": "Hello, glad to meet you."
  },
  "finishReason": "stop",
  "usage": {
    "promptTokens": 12,
    "completionTokens": 8,
    "totalTokens": 20
  }
}
```

curl:

```powershell
curl.exe -X POST "http://localhost:8080/api/chat/completions" -H "Content-Type: application/json" -H "Authorization: Bearer <platformApiKey>" -d "{\"providerCode\":\"OPENROUTER\",\"model\":\"openrouter/free\",\"messages\":[{\"role\":\"user\",\"content\":\"Say hello in one short sentence.\"}],\"max_tokens\":64}"
```

#### SSE streaming chat completion

```text
POST /api/chat/completions/stream
```

Request headers:

```text
Content-Type: application/json
Accept: text/event-stream
Authorization: Bearer <platformApiKey>
```

Request body uses the same `ChatRequest` shape as the non-streaming endpoint. The service forces `stream=true` before calling the upstream provider.

curl:

```powershell
curl.exe -N -X POST "http://localhost:8080/api/chat/completions/stream" -H "Content-Type: application/json" -H "Accept: text/event-stream" -H "Authorization: Bearer <platformApiKey>" -d "{\"providerCode\":\"OPENROUTER\",\"model\":\"openrouter/free\",\"messages\":[{\"role\":\"user\",\"content\":\"Count from one to three.\"}],\"max_tokens\":64}"
```

Notes:

- Management APIs use JWT access tokens.
- Gateway chat APIs use platform API keys created by `POST /api/api-keys`.
- OpenRouter uses the OpenAI-compatible adapter with `OPENROUTER_BASE_URL=https://openrouter.ai/api/v1`.
- Configure the upstream key with `OPENROUTER_API_KEY`; do not store raw keys in source code.
- Provider failures are returned as unified errors such as `PROVIDER_TIMEOUT`, `PROVIDER_RATE_LIMITED`, `PROVIDER_AUTH_FAILED`, or `PROVIDER_UPSTREAM_ERROR`.
- `request_log` records `providerId`, `modelId`, `apiKeyId`, `statusCode`, `latencyMs`, and `errorCode`; it does not record Authorization headers or provider keys.
