# Security Notes

本文档记录当前阶段的密钥存储、日志脱敏、错误响应和接口边界规则。

## 密钥存储

### 用户密码

- 用户注册时只保存密码哈希，不能保存明文密码。
- 密码字段不能出现在日志、响应 DTO、请求日志表中。

### 平台 API Key

- 平台 API Key 是用户调用网关时使用的密钥。
- 创建时只在响应中返回一次明文 `apiKey`。
- 数据库 `api_key` 表只保存 `key_hash` 和 `prefix`，不保存明文 API Key。
- 列表、更新、禁用接口只能返回 `id`、`name`、`prefix`、`enabled`、时间字段，不能返回 `keyHash` 或明文 `apiKey`。
- 日志中只能记录 `apiKeyId`、`userId`、`prefix` 等非敏感定位信息。

### Provider API Key

- Provider API Key 是调用上游服务商时使用的官方密钥。
- 入库前必须通过 `ProviderKeyCrypto` 加密。
- 数据库 `provider_key.encrypted_key` 保存加密后的密文，不能保存明文 Provider Key。
- `key_hint` 只能保存脱敏后的提示值，例如 `sk-a****1234`。
- 响应 DTO 只能返回 `id`、`providerId`、`keyHint`、`enabled`，不能返回 `encryptedKey` 或明文 Provider Key。

## DTO 边界

- 创建平台 API Key 使用 `CreateApiKeyRequest`。
- 更新平台 API Key 使用 `UpdateApiKeyRequest`。
- 创建 Provider API Key 使用 `CreateProviderKeyRequest`。
- 更新 Provider API Key 使用 `UpdateProviderKeyRequest`。
- Entity 可以包含数据库字段，例如 `keyHash`、`encryptedKey`。
- Response DTO 不能暴露敏感数据库字段。

## 日志规则

项目使用 SLF4J + Logback。

- `INFO`：记录正常业务动作，例如创建、更新、禁用成功，只记录 ID、状态、前缀或脱敏提示。
- `WARN`：记录可预期的异常动作，例如资源不存在、越权访问、重复用户名。
- `ERROR`：只用于不可预期的系统错误，不能拼接完整请求头、请求体或密钥。

禁止记录以下字段的完整值：

- `Authorization`
- 平台 API Key
- Provider API Key
- 密码
- Token、Refresh Token
- `key_hash`
- `encrypted_key`

如确实需要定位密钥，只能使用 `SensitiveDataMasker.maskSecret(...)` 生成脱敏值，或使用数据库 ID、`prefix`、`keyHint`。

## 请求日志

请求日志用于审计和查询调用情况，不用于保存请求密钥。

允许保存：

- `requestId`
- `userId`
- `apiKeyId`
- `providerId`
- `modelId`
- `statusCode`
- `latencyMs`
- `errorCode`
- `createdAt`

禁止保存：

- `Authorization`
- 平台 API Key 明文
- Provider API Key 明文
- 密码
- 完整请求体中的敏感字段

## 统一错误响应

所有接口错误响应使用统一结构：

```json
{
  "code": "ERROR_CODE",
  "message": "Human readable message",
  "details": null,
  "timestamp": "2026-06-28T12:00:00+08:00"
}
```

当前约定的状态码：

| HTTP 状态码 | 使用场景 |
| --- | --- |
| `400` | 请求参数、JSON 请求体、DTO 校验失败 |
| `401` | 缺少认证信息，例如缺少或传入无效 `Authorization` |
| `403` | 已识别用户，但访问了不属于自己的资源 |
| `404` | 用户、API Key、Provider Key 或接口路径不存在 |
| `409` | 业务状态冲突，例如用户名已存在、余额不足 |
| `429` | 请求过于频繁或后续限流触发 |
| `500` | 系统内部错误或当前阶段归类为服务端失败的上游调用失败 |

## 验收自检

- `api_key` 表没有明文平台 API Key，只保存 `key_hash` 和 `prefix`。
- 平台 API Key 创建接口只返回一次明文，列表、更新、禁用接口不返回明文或 hash。
- `provider_key.encrypted_key` 保存加密后的 Provider Key。
- Provider Key 创建和更新响应不返回明文或密文，只返回 `keyHint`。
- 日志中不会出现完整密钥、Token、密码、`Authorization`。
- 错误响应结构稳定，400、401、403、404、409、429、500 都走统一格式。
