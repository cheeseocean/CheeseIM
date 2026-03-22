# IM Message Rebuild Migration Checklist

> 状态：已过期。
> 当前 IM 重构按单分支一次性整体替换执行，不再采用灰度开关、兼容回退或双链路对比迁移。
> 当前有效架构说明以
> [2026-03-22-im-full-refactor-design.md](/Users/xxxcrel/Develop/backend/java/CheeseIM/server/docs/superpowers/specs/2026-03-22-im-full-refactor-design.md)
> 为准。本文保留仅用于历史迁移思路参考。

1. Enable `cheeseim.message-flow.async-ingress-enabled=true` in one staging environment and compare accepted send counts between gateway ACKs and `im.message.ingress`.
2. Enable `cheeseim.message-flow.async-history-enabled=true` and confirm `postbox` history persistence matches legacy Mongo write counts and duplicate-history suppression is stable.
3. Enable `cheeseim.message-flow.async-delivery-enabled=true` only after `postoffice` online push success rate and duplicate-push rate match legacy behavior.
4. Enable `cheeseim.message-flow.async-receipt-enabled=true` only after delivered/read receipt volumes and `ConversationReadCursor` progression match legacy `DeliveryAck` outcomes.
5. Compare Kafka lag, DLQ count, offline push count, and gateway push success rate before increasing traffic percentage.
6. Keep legacy synchronous fallback enabled until accepted count parity, history parity, delivery parity, and read-cursor parity are all stable for at least one release window.
