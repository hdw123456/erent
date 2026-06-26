# Pricing

第一版按模型配置价格规则。

建议字段：

- provider
- model
- input token price
- output token price
- currency
- effective time
- enabled flag

扣费规则先保持简单：Provider 调用成功后，根据用量记录计算金额，扣减钱包余额，并写入钱包流水。

