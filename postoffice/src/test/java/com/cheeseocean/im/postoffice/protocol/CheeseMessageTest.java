package com.cheeseocean.im.postoffice.protocol;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TCP消息协议测试
 * 
 * @author CheeseIM
 */
public class CheeseMessageTest {
    
    private static final Logger logger = LoggerFactory.getLogger(CheeseMessageTest.class);
    
    @Test
    public void testMagicValue() {
        // 验证MAGIC值是有效的十六进制数
        short magic = CheeseMessage.MAGIC;
        logger.info("MAGIC value: 0x{}", Integer.toHexString(magic & 0xFFFF));
        
        // MAGIC应该是0xCEEE
        assertEquals((short) 0xCEEE, magic);
        
        // 验证MAGIC值在有效范围内
        assertTrue(magic != 0);
        assertTrue((magic & 0xFFFF) == 0xCEEE);
    }
    
    @Test
    public void testMessageEncodeAndDecode() {
        // 创建测试消息
        String operationID = UUID.randomUUID().toString();
        String testData = "{\"test\":\"data\"}";
        
        CheeseMessage originalMessage = new CheeseMessage(CheeseMessageType.TCP_HEARTBEAT_REQ, operationID, testData);
        
        // 编码消息
        byte[] encodedBytes = originalMessage.encode();
        assertNotNull(encodedBytes);
        assertTrue(encodedBytes.length >= CheeseMessage.HEADER_LENGTH);
        
        logger.info("Encoded message length: {} bytes", encodedBytes.length);
        
        // 验证编码后的MAGIC值
        short encodedMagic = (short) (((encodedBytes[0] & 0xFF) << 8) | (encodedBytes[1] & 0xFF));
        assertEquals(CheeseMessage.MAGIC, encodedMagic);
        
        // 解码消息
        CheeseMessage decodedMessage = CheeseMessage.decode(encodedBytes);
        assertNotNull(decodedMessage);
        
        // 验证解码后的数据
        assertEquals(originalMessage.getMsgType(), decodedMessage.getMsgType());
        assertEquals(originalMessage.getOperationID().trim(), decodedMessage.getOperationID().trim());
        assertEquals(originalMessage.getData(), decodedMessage.getData());
        assertEquals(originalMessage.getDataLength(), decodedMessage.getDataLength());
        
        logger.info("Original message: {}", originalMessage);
        logger.info("Decoded message: {}", decodedMessage);
    }
    
    @Test
    public void testMessageTypes() {
        // 测试各种消息类型
        testMessageType(CheeseMessageType.TCP_CONNECT_REQ, "连接请求");
        testMessageType(CheeseMessageType.TCP_AUTH_REQ, "认证请求");
        testMessageType(CheeseMessageType.TCP_HEARTBEAT_REQ, "心跳请求");
        testMessageType(CheeseMessageType.TCP_SEND_MSG_REQ, "发送消息请求");
    }
    
    private void testMessageType(byte msgType, String description) {
        String operationID = UUID.randomUUID().toString();
        String testData = "{\"type\":\"" + description + "\"}";
        
        CheeseMessage message = new CheeseMessage(msgType, operationID, testData);
        byte[]        encoded = message.encode();
        CheeseMessage decoded = CheeseMessage.decode(encoded);
        
        assertEquals(msgType, decoded.getMsgType());
        assertEquals(operationID.trim(), decoded.getOperationID().trim());
        assertEquals(testData, decoded.getData());
        
        logger.info("Message type {} ({}) test passed", msgType, description);
    }
    
    @Test
    public void testEmptyData() {
        // 测试空数据
        String operationID = UUID.randomUUID().toString();
        
        CheeseMessage message = new CheeseMessage(CheeseMessageType.TCP_HEARTBEAT_REQ, operationID, null);
        byte[]        encoded = message.encode();
        CheeseMessage decoded = CheeseMessage.decode(encoded);
        
        assertEquals(CheeseMessageType.TCP_HEARTBEAT_REQ, decoded.getMsgType());
        assertEquals(operationID.trim(), decoded.getOperationID().trim());
        assertNull(decoded.getData());
        assertEquals(0, decoded.getDataLength());
        
        logger.info("Empty data test passed");
    }
    
    @Test
    public void testLongOperationID() {
        // 测试长操作ID（超过16字节）
        String longOperationID = "this-is-a-very-long-operation-id-that-exceeds-16-bytes";
        String testData = "{\"test\":\"long-operation-id\"}";
        
        CheeseMessage message = new CheeseMessage(CheeseMessageType.TCP_SEND_MSG_REQ, longOperationID, testData);
        byte[]        encoded = message.encode();
        CheeseMessage decoded = CheeseMessage.decode(encoded);
        
        // 操作ID应该被截断到16字节
        assertTrue(decoded.getOperationID().length() <= 16);
        assertEquals(testData, decoded.getData());
        
        logger.info("Long operation ID test passed. Original: {}, Decoded: {}", 
                   longOperationID, decoded.getOperationID());
    }
    
    @Test
    public void testStaticFactoryMethods() {
        // 测试静态工厂方法
        String operationID = "test-op-id";
        
        CheeseMessage connectSuccess = CheeseMessage.connectSuccess(operationID);
        assertEquals(CheeseMessageType.TCP_CONNECT_SUCCESS, connectSuccess.getMsgType());
        assertEquals(operationID, connectSuccess.getOperationID());
        
        CheeseMessage connectFailed = CheeseMessage.connectFailed(operationID, "连接失败");
        assertEquals(CheeseMessageType.TCP_CONNECT_FAILED, connectFailed.getMsgType());
        assertEquals("连接失败", connectFailed.getData());
        
        CheeseMessage authSuccess = CheeseMessage.authSuccess(operationID, "user123");
        assertEquals(CheeseMessageType.TCP_AUTH_SUCCESS, authSuccess.getMsgType());
        assertTrue(authSuccess.getData().contains("user123"));
        
        CheeseMessage heartbeatResp = CheeseMessage.heartbeatResp(operationID);
        assertEquals(CheeseMessageType.TCP_HEARTBEAT_RESP, heartbeatResp.getMsgType());
        assertEquals("pong", heartbeatResp.getData());
        
        logger.info("Static factory methods test passed");
    }
    
    @Test
    public void testInvalidMagic() {
        // 测试无效的MAGIC值
        byte[] invalidMessage = new byte[CheeseMessage.HEADER_LENGTH];
        
        // 设置无效的MAGIC值
        invalidMessage[0] = (byte) 0xFF;
        invalidMessage[1] = (byte) 0xFF;
        
        // 设置其他必要字段
        invalidMessage[2] = CheeseMessage.VERSION;
        invalidMessage[3] = CheeseMessageType.TCP_HEARTBEAT_REQ;
        // Length = 0
        invalidMessage[4] = 0;
        invalidMessage[5] = 0;
        invalidMessage[6] = 0;
        invalidMessage[7] = 0;
        
        // 应该抛出异常
        assertThrows(IllegalArgumentException.class, () -> {
            CheeseMessage.decode(invalidMessage);
        });
        
        logger.info("Invalid magic test passed");
    }
}
