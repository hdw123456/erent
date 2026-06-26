# Database Design

第一版核心表：

- `user_account`：平台用户。
- `role`：角色。
- `user_role`：用户和角色的多对多关系。
- `api_key`：平台 API Key 记录，数据库只保存哈希。
- `provider`：上游 AI 服务商。
- `provider_key`：用户或平台绑定的 Provider Key，数据库保存加密值。
- `model`：模型。
- `pricing_rule`：模型价格规则。
- `wallet`：用户钱包余额。
- `wallet_transaction`：钱包流水。
- `request_log`：请求日志。
- `usage_record`：用量记录。

敏感字段原则：

- 用户密码只保存 BCrypt 哈希。
- 平台 API Key 只保存哈希，明文只在创建时展示一次。
- Provider Key 必须加密存储。
- 日志中不能打印完整密码、Token、API Key、Provider Key。

