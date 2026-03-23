package com.cheeseocean.im.postoffice.api;

import com.cheeseocean.im.common.api.dto.dispatch.DispatchMessageReq;
import com.cheeseocean.im.common.api.dto.dispatch.DispatchMessageResp;
import com.cheeseocean.im.common.api.dto.dispatch.DispatchPayload;
import com.cheeseocean.im.common.api.dto.dispatch.DispatchResult;
import com.cheeseocean.im.common.api.protocol.ServerEnvelope;
import com.cheeseocean.im.common.api.rpc.OnlineDispatchRpc;
import com.cheeseocean.im.postoffice.connection.ConnectionManager;
import com.cheeseocean.im.postoffice.connection.UserConnection;
import org.apache.dubbo.config.annotation.DubboService;

import java.util.ArrayList;
import java.util.List;

@DubboService(interfaceClass = OnlineDispatchRpc.class)
public class OnlineDispatchRpcImpl implements OnlineDispatchRpc {

    private final ConnectionManager connectionManager;

    public OnlineDispatchRpcImpl(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public DispatchMessageResp dispatchMessage(DispatchMessageReq req) {
        DispatchMessageResp resp = new DispatchMessageResp();
        if (req == null || req.getUserId() == null || req.getPayload() == null) {
            return resp;
        }

        List<UserConnection> targets = resolveTargets(req);
        List<DispatchResult> results = new ArrayList<>();
        DispatchPayload payload = req.getPayload();

        if (req.getConnectionIds() != null && !req.getConnectionIds().isEmpty()) {
            for (String connectionId : req.getConnectionIds()) {
                UserConnection connection = connectionManager.getConnection(connectionId);
                if (connection == null || !req.getUserId().equals(connection.getUserID())) {
                    results.add(new DispatchResult(connectionId, false, "CONNECTION_NOT_FOUND", "connection not found"));
                    continue;
                }
                results.add(dispatch(connection, req.getUserId(), payload));
            }
            resp.setResults(results);
            return resp;
        }

        for (UserConnection connection : targets) {
            results.add(dispatch(connection, req.getUserId(), payload));
        }
        resp.setResults(results);
        return resp;
    }

    private List<UserConnection> resolveTargets(DispatchMessageReq req) {
        if (req.getConnectionIds() != null && !req.getConnectionIds().isEmpty()) {
            List<UserConnection> targets = new ArrayList<>();
            for (String connectionId : req.getConnectionIds()) {
                UserConnection connection = connectionManager.getConnection(connectionId);
                if (connection != null && req.getUserId().equals(connection.getUserID())) {
                    targets.add(connection);
                }
            }
            return targets;
        }
        return connectionManager.getUserConnections(req.getUserId());
    }

    private DispatchResult dispatch(UserConnection connection, String userId, DispatchPayload payload) {
        String connectionId = connection.getConnectionID();
        if (!connectionManager.markDeliveryIfAbsent(payload.getServerMsgId(), userId, connectionId)) {
            return new DispatchResult(connectionId, true, "DUPLICATE", "delivery already recorded");
        }
        ServerEnvelope envelope = ServerEnvelope.chatRecv(payload.getServerMsgId(), payload);
        boolean success = connectionManager.sendEnvelopeToConnection(
                connection,
                envelope);
        if (success) {
            return new DispatchResult(connectionId, true, "OK", "delivered");
        }
        return new DispatchResult(connectionId, false, "SEND_FAILED", "connection send failed");
    }
}
