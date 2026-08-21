package com.cheeseocean.im.common.api.protocol;

import com.cheeseocean.im.common.api.dto.dispatch.DispatchPayload;
import com.cheeseocean.im.common.api.enums.CommandType;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class ServerEnvelope implements Serializable {

    private static final long serialVersionUID = 1L;

    private CommandType command;
    private String      requestId;
    private Object      body;

    public static ServerEnvelope of(CommandType command, String requestId, Object body) {
        ServerEnvelope envelope = new ServerEnvelope();
        envelope.setCommand(command);
        envelope.setRequestId(requestId);
        envelope.setBody(normalizeRpcValue(body));
        return envelope;
    }

    public static ServerEnvelope connect(String requestId, Object body) {
        return of(CommandType.CONNECT, requestId, body);
    }

    public static ServerEnvelope auth(String requestId, Object body) {
        return of(CommandType.AUTH, requestId, body);
    }

    public static ServerEnvelope heartbeat(String requestId, Object body) {
        return of(CommandType.HEARTBEAT, requestId, body);
    }

    public static ServerEnvelope chatSendAck(String requestId, Object body) {
        return of(CommandType.CHAT_SEND_ACK, requestId, body);
    }

    public static ServerEnvelope error(String requestId, Object body) {
        return of(CommandType.ERROR, requestId, body);
    }

    public static ServerEnvelope error(String requestId, int code, String message) {
        return error(requestId, Map.of("code", code, "message", message));
    }

    public static ServerEnvelope chatRecv(String requestId, DispatchPayload payload) {
        return of(CommandType.CHAT_RECV, requestId, payload);
    }

    public static ServerEnvelope forceLogout(String requestId, String reason) {
        return of(CommandType.FORCE_LOGOUT, requestId, Map.of(
                "reason", reason,
                "message", "您的账号在其他设备登录，已被强制下线"));
    }

    /**
     * 将 JDK 不可变集合复制成 Dubbo 严格序列化模式可识别的稳定实现。
     *
     * <p>{@code Map.of/List.of} 通过内部 {@code CollSer} 代理序列化，跨模块 injvm 调用同样会做
     * 参数深拷贝并被安全白名单拒绝；在 envelope 边界统一递归归一化，避免每个控制事件调用方重复处理。
     */
    private static Object normalizeRpcValue(Object value) {
        if (value instanceof Map<?, ?> source) {
            Map<Object, Object> normalized = new LinkedHashMap<>(source.size());
            source.forEach((key, item) -> normalized.put(key, normalizeRpcValue(item)));
            return normalized;
        }
        if (value instanceof Collection<?> source) {
            Collection<Object> normalized = new ArrayList<>(source.size());
            source.forEach(item -> normalized.add(normalizeRpcValue(item)));
            return normalized;
        }
        return value;
    }
}
