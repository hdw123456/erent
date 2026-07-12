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

扣费规则：

- Provider 调用完成后，根据 input/output token 和当前 `pricing_rule` 计算金额。
- 流式调用优先使用 Provider 终态 usage；缺失时使用显式标记为 `ESTIMATED` 的估算值。
- 已经产生输出的流在失败或客户端断开时按部分 usage 计费；首个 Provider 事件前失败不计费。
- 扣费去重、钱包行锁、余额更新、请求日志、usage、钱包流水和幂等终态在一个事务边界内完成。
