# Session Type Notes

This repository no longer uses the removed listener-based push strategy document.

For the current architecture, session type affects:

- how `postman` resolves recipients
- whether a message fans out as single-chat, group, or system delivery
- whether offline push is considered after online delivery remains unconfirmed

The authoritative behavior is the current code and tests, especially:

- `postman/src/test/java/com/cheeseocean/im/postman/listener/IngressEventListenerTest.java`
- `postman/src/test/java/com/cheeseocean/im/postman/service/MessageStateServiceTest.java`
- `push/src/test/java/com/cheeseocean/im/push/service/MessagePushServiceImplTest.java`
- `push/src/test/java/com/cheeseocean/im/push/listener/DeliveryEventListenerTest.java`
