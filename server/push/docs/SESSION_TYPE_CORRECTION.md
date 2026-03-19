# Session Type Notes

This repository no longer uses the removed listener-based push strategy document.

For the current architecture, session type affects:

- how `postman` resolves recipients
- whether a message fans out as single-chat, group, or system delivery
- whether offline push is considered after online delivery remains unconfirmed

The authoritative behavior is the current code and tests, especially:

- `postman/src/test/java/com/cheeseocean/im/postman/service/GroupDeliveryFlowTest.java`
- `postman/src/test/java/com/cheeseocean/im/postman/service/MessageDeliveryAckFlowTest.java`
- `push/src/test/java/com/cheeseocean/im/push/service/MessagePushServiceImplTest.java`
