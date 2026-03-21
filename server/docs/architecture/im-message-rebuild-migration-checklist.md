# IM Message Rebuild Migration Checklist

1. Enable `cheeseim.message-flow.async-ingress-enabled=true` in one staging environment and compare accepted send counts between gateway ACKs and `im.message.ingress`.
2. Enable `cheeseim.message-flow.async-history-enabled=true` and confirm `postbox` history persistence matches legacy Mongo write counts and duplicate-history suppression is stable.
3. Enable `cheeseim.message-flow.async-delivery-enabled=true` only after `postoffice` online push success rate and duplicate-push rate match legacy behavior.
4. Enable `cheeseim.message-flow.async-receipt-enabled=true` only after delivered/read receipt volumes and `ConversationReadCursor` progression match legacy `DeliveryAck` outcomes.
5. Compare Kafka lag, DLQ count, offline push count, and gateway push success rate before increasing traffic percentage.
6. Keep legacy synchronous fallback enabled until accepted count parity, history parity, delivery parity, and read-cursor parity are all stable for at least one release window.
