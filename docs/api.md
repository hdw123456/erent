# API Design

本文档记录当前阶段已经实现或规划中的 HTTP API。

当前第 4 阶段第 1 周暂未接入 JWT，临时使用请求头 `X-User-Id` 表示当前登录用户。第 7 周实现 Spring Security + JWT 后，再替换为真实认证上下文。

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
| `401` | 缺少认证信息，例如缺少 `X-User-Id` |
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
X-User-Id: 1
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
curl.exe "http://localhost:8080/api/users/me" -H "X-User-Id: 1"
```

## API Key

### 创建平台 API Key

```text
POST /api/api-keys
```

请求头：

```text
Content-Type: application/json
X-User-Id: 1
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
curl.exe -X POST "http://localhost:8080/api/api-keys" -H "Content-Type: application/json" -H "X-User-Id: 1" -d "{\"name\":\"local-test-key\"}"
```

### 查询平台 API Key 列表

```text
GET /api/api-keys
```

请求头：

```text
X-User-Id: 1
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
curl.exe "http://localhost:8080/api/api-keys" -H "X-User-Id: 1"
```

### 更新平台 API Key

```text
PATCH /api/api-keys/{id}
```

请求头：

```text
Content-Type: application/json
X-User-Id: 1
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
curl.exe -X PATCH "http://localhost:8080/api/api-keys/1" -H "Content-Type: application/json" -H "X-User-Id: 1" -d "{\"name\":\"new-key-name\"}"
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
curl.exe -X PATCH "http://localhost:8080/api/api-keys/1/disable" -H "X-User-Id: 1"
```

## Provider Key

### 创建 Provider API Key

```text
POST /api/provider-keys
```

请求头：

```text
Content-Type: application/json
X-User-Id: 1
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
curl.exe -X POST "http://localhost:8080/api/provider-keys" -H "Content-Type: application/json" -H "X-User-Id: 1" -d "{\"providerId\":1,\"rawProviderKey\":\"sk-provider-secret\"}"
```

### 更新 Provider API Key

```text
PATCH /api/provider-keys/{id}
```

请求头：

```text
Content-Type: application/json
X-User-Id: 1
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
curl.exe -X PATCH "http://localhost:8080/api/provider-keys/1" -H "Content-Type: application/json" -H "X-User-Id: 1" -d "{\"rawProviderKey\":\"sk-provider-secret-new\"}"
```

## Model

### 查询模型和价格

```text
GET /api/models
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
curl.exe "http://localhost:8080/api/models"
curl.exe "http://localhost:8080/api/models?providerCode=OPENAI"
```

## Request Log

### 分页查询请求日志

```text
GET /api/request-logs
```

请求头：

```text
X-User-Id: 1
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
curl.exe "http://localhost:8080/api/request-logs?page=1&size=10" -H "X-User-Id: 1"
curl.exe "http://localhost:8080/api/request-logs?page=1&size=10&modelId=1&statusCode=200&startTime=2026-06-01%2000:00:00&endTime=2026-06-30%2023:59:59" -H "X-User-Id: 1"
```

## Gateway

以下接口是后续阶段规划，当前第 1 周暂未实现。

```text
POST /api/auth/login
POST /api/auth/refresh
POST /v1/chat/completions
```
