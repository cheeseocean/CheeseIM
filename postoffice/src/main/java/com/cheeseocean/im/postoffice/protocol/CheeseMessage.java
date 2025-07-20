package com.cheeseocean.im.postoffice.protocol;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * TCP自定义协议消息实体
 * 
 * 协议格式：
 * +--------+--------+--------+--------+--------+--------+--------+--------+
 * | Magic  | Version| MsgType| Length |    OperationID (16 bytes)        |
 * +--------+--------+--------+--------+--------+--------+--------+--------+
 * |                    Timestamp (8 bytes)                               |
 * +--------+--------+--------+--------+--------+--------+--------+--------+
 * |                    Data (Length bytes)                               |
 * +--------+--------+--------+--------+--------+--------+--------+--------+
 * 
 * Magic: 0xCEEE (2 bytes) - 协议标识
 * Version: 0x01 (1 byte) - 协议版本
 * MsgType: (1 byte) - 消息类型
 * Length: (4 bytes) - 数据长度
 * OperationID: (16 bytes) - 操作ID，用于请求追踪
 * Timestamp: (8 bytes) - 时间戳
 * Data: (Length bytes) - 消息数据，JSON格式
 * 
 * @author CheeseIM
 */
public class CheeseMessage implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    // 协议常量
    public static final short MAGIC = (short) 0xCEEE; // CheeseIM 协议标识
    public static final byte VERSION = 0x01;
    public static final int HEADER_LENGTH = 32; // 固定头部长度
    public static final int MAX_DATA_LENGTH = 1024 * 1024; // 最大数据长度 1MB
    
    /**
     * 协议版本
     */
    private byte version = VERSION;
    
    /**
     * 消息类型
     */
    private byte msgType;
    
    /**
     * 数据长度
     */
    private int dataLength;
    
    /**
     * 操作ID
     */
    private String operationID;
    
    /**
     * 时间戳
     */
    private long timestamp;
    
    /**
     * 消息数据（JSON字符串）
     */
    private String data;
    
    public CheeseMessage() {
        this.timestamp = System.currentTimeMillis();
    }
    
    public CheeseMessage(byte msgType, String operationID, String data) {
        this();
        this.msgType = msgType;
        this.operationID = operationID;
        this.data = data;
        this.dataLength = data != null ? data.getBytes(StandardCharsets.UTF_8).length : 0;
    }
    
    /**
     * 将消息编码为字节数组
     */
    public byte[] encode() {
        byte[] dataBytes = data != null ? data.getBytes(StandardCharsets.UTF_8) : new byte[0];
        byte[] operationIDBytes = operationID != null ? operationID.getBytes(StandardCharsets.UTF_8) : new byte[0];
        
        // 确保operationID不超过16字节
        byte[] operationIDFixed = new byte[16];
        System.arraycopy(operationIDBytes, 0, operationIDFixed, 0, 
                        Math.min(operationIDBytes.length, 16));
        
        ByteBuffer buffer = ByteBuffer.allocate(HEADER_LENGTH + dataBytes.length);
        
        // 写入头部
        buffer.putShort(MAGIC);           // Magic (2 bytes)
        buffer.put(version);              // Version (1 byte)
        buffer.put(msgType);              // MsgType (1 byte)
        buffer.putInt(dataBytes.length);  // Length (4 bytes)
        buffer.put(operationIDFixed);     // OperationID (16 bytes)
        buffer.putLong(timestamp);        // Timestamp (8 bytes)
        
        // 写入数据
        buffer.put(dataBytes);
        
        return buffer.array();
    }
    
    /**
     * 从字节数组解码消息
     */
    public static CheeseMessage decode(byte[] bytes) {
        if (bytes.length < HEADER_LENGTH) {
            throw new IllegalArgumentException("Invalid message length: " + bytes.length);
        }
        
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        
        // 读取头部
        short magic = buffer.getShort();
        if (magic != MAGIC) {
            throw new IllegalArgumentException("Invalid magic number: " + Integer.toHexString(magic));
        }
        
        byte version = buffer.get();
        byte msgType = buffer.get();
        int dataLength = buffer.getInt();
        
        // 验证数据长度
        if (dataLength < 0 || dataLength > MAX_DATA_LENGTH) {
            throw new IllegalArgumentException("Invalid data length: " + dataLength);
        }
        
        if (bytes.length != HEADER_LENGTH + dataLength) {
            throw new IllegalArgumentException("Message length mismatch");
        }
        
        // 读取operationID
        byte[] operationIDBytes = new byte[16];
        buffer.get(operationIDBytes);
        String operationID = new String(operationIDBytes, StandardCharsets.UTF_8).trim();
        
        // 读取时间戳
        long timestamp = buffer.getLong();
        
        // 读取数据
        String data = null;
        if (dataLength > 0) {
            byte[] dataBytes = new byte[dataLength];
            buffer.get(dataBytes);
            data = new String(dataBytes, StandardCharsets.UTF_8);
        }
        
        CheeseMessage message = new CheeseMessage();
        message.version = version;
        message.msgType = msgType;
        message.dataLength = dataLength;
        message.operationID = operationID;
        message.timestamp = timestamp;
        message.data = data;
        
        return message;
    }
    
    /**
     * 转换为WebSocket消息
     */
    public WSMessage toWSMessage() {
        WSMessage wsMessage = new WSMessage();
        wsMessage.setMsgType((int) msgType);
        wsMessage.setOperationID(operationID);
        wsMessage.setSendTime(timestamp);
        
        // 解析JSON数据
        if (data != null && !data.isEmpty()) {
            try {
                // 这里可以根据需要解析JSON数据
                wsMessage.setData(data);
            } catch (Exception e) {
                wsMessage.setData(data);
            }
        }
        
        return wsMessage;
    }
    
    /**
     * 从WebSocket消息创建TCP消息
     */
    public static CheeseMessage fromWSMessage(WSMessage wsMessage) {
        if (wsMessage == null) {
            return null;
        }

        CheeseMessage cheeseMessage = new CheeseMessage();

        // 安全地转换消息类型
        if (wsMessage.getMsgType() != null) {
            cheeseMessage.msgType = CheeseMessageType.wsToTcpMessageType(wsMessage.getMsgType());
        } else {
            cheeseMessage.msgType = CheeseMessageType.TCP_ERROR_RESP;
        }

        cheeseMessage.operationID = wsMessage.getOperationID() != null ? wsMessage.getOperationID() : "unknown";
        cheeseMessage.timestamp = wsMessage.getSendTime() != null ? wsMessage.getSendTime() : System.currentTimeMillis();

        // 将数据转换为JSON字符串
        if (wsMessage.getData() != null) {
            try {
                if (wsMessage.getData() instanceof String) {
                    cheeseMessage.data = (String) wsMessage.getData();
                } else {
                    // 这里可以使用Jackson等JSON库序列化
                    cheeseMessage.data = wsMessage.getData().toString();
                }
                cheeseMessage.dataLength = cheeseMessage.data.getBytes(StandardCharsets.UTF_8).length;
            } catch (Exception e) {
                cheeseMessage.data = "{}";
                cheeseMessage.dataLength = 2;
            }
        } else {
            cheeseMessage.data = null;
            cheeseMessage.dataLength = 0;
        }

        return cheeseMessage;
    }
    
    // ============ 静态工厂方法 ============
    
    public static CheeseMessage connectSuccess(String operationID) {
        return new CheeseMessage((byte) CheeseMessageType.TCP_CONNECT_SUCCESS, operationID, "连接成功");
    }
    
    public static CheeseMessage connectFailed(String operationID, String reason) {
        return new CheeseMessage((byte) CheeseMessageType.TCP_CONNECT_FAILED, operationID, reason);
    }
    
    public static CheeseMessage authSuccess(String operationID, String userID) {
        return new CheeseMessage((byte) CheeseMessageType.TCP_AUTH_SUCCESS, operationID,
                             "{\"userID\":\"" + userID + "\",\"message\":\"认证成功\"}");
    }
    
    public static CheeseMessage authFailed(String operationID, String reason) {
        return new CheeseMessage((byte) CheeseMessageType.TCP_AUTH_FAILED, operationID, reason);
    }
    
    public static CheeseMessage heartbeatResp(String operationID) {
        return new CheeseMessage((byte) CheeseMessageType.TCP_HEARTBEAT_RESP, operationID, "pong");
    }
    
    public static CheeseMessage errorResp(String operationID, int errorCode, String errorMsg) {
        return new CheeseMessage((byte) CheeseMessageType.TCP_ERROR_RESP, operationID,
                             "{\"errCode\":" + errorCode + ",\"errMsg\":\"" + errorMsg + "\"}");
    }
    
    // ============ Getter and Setter ============
    
    public byte getVersion() {
        return version;
    }
    
    public void setVersion(byte version) {
        this.version = version;
    }
    
    public byte getMsgType() {
        return msgType;
    }
    
    public void setMsgType(byte msgType) {
        this.msgType = msgType;
    }
    
    public int getDataLength() {
        return dataLength;
    }
    
    public void setDataLength(int dataLength) {
        this.dataLength = dataLength;
    }
    
    public String getOperationID() {
        return operationID;
    }
    
    public void setOperationID(String operationID) {
        this.operationID = operationID;
        // 重新计算长度
        if (data != null) {
            this.dataLength = data.getBytes(StandardCharsets.UTF_8).length;
        }
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    public String getData() {
        return data;
    }
    
    public void setData(String data) {
        this.data = data;
        this.dataLength = data != null ? data.getBytes(StandardCharsets.UTF_8).length : 0;
    }
    
    @Override
    public String toString() {
        return "TcpMessage{" +
                "version=" + version +
                ", msgType=" + msgType +
                ", dataLength=" + dataLength +
                ", operationID='" + operationID + '\'' +
                ", timestamp=" + timestamp +
                ", data='" + data + '\'' +
                '}';
    }
}
