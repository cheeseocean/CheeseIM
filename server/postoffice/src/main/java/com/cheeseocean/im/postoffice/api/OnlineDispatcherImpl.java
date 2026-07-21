package com.cheeseocean.im.postoffice.api;

import com.cheeseocean.im.common.api.dto.dispatch.DispatchMessageReq;
import com.cheeseocean.im.common.api.dto.dispatch.DispatchMessageResp;
import com.cheeseocean.im.common.api.dto.dispatch.DispatchPayload;
import com.cheeseocean.im.common.api.dto.dispatch.DispatchResult;
import com.cheeseocean.im.common.api.enums.DispatchResultCode;
import com.cheeseocean.im.common.api.protocol.ServerEnvelope;
import com.cheeseocean.im.common.api.rpc.OnlineDispatcher;
import com.cheeseocean.im.postoffice.connection.ConnectionManager;
import com.cheeseocean.im.postoffice.connection.UserConnection;
import com.cheeseocean.im.postoffice.config.ServerProperties;
import com.cheeseocean.im.postoffice.dedup.DeliveryDedupStore;
import com.cheeseocean.im.postoffice.delivery.DeliveryWriteFinalizer;
import io.netty.channel.ChannelFuture;
import org.apache.dubbo.config.annotation.DubboService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@DubboService(interfaceClass = OnlineDispatcher.class)
public class OnlineDispatcherImpl implements OnlineDispatcher {

    private final ConnectionManager connectionManager;
    private final DeliveryWriteFinalizer deliveryWriteFinalizer;
    private final long writeTimeoutMs;

    public OnlineDispatcherImpl(ConnectionManager connectionManager,
                                DeliveryWriteFinalizer deliveryWriteFinalizer,
                                ServerProperties serverProperties) {
        this.connectionManager = connectionManager;
        this.deliveryWriteFinalizer = deliveryWriteFinalizer;
        this.writeTimeoutMs = Math.max(1L, serverProperties.getDelivery().getWriteTimeoutMs());
    }

    @Override
    public DispatchMessageResp dispatchMessage(DispatchMessageReq req) {
        DispatchMessageResp resp = new DispatchMessageResp();
        if (req == null || req.getUserId() == null || req.getPayload() == null
                || (req.getPayload().getMsg() == null && req.getPayload().getEnvelope() == null)) {
            return resp;
        }

        List<UserConnection> targets = resolveTargets(req);
        List<DispatchResult> results = new ArrayList<>();
        DispatchPayload payload = req.getPayload();
        long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(writeTimeoutMs);

        if (req.getConnectionIds() != null && !req.getConnectionIds().isEmpty()) {
            for (String connectionId : req.getConnectionIds()) {
                UserConnection connection = connectionManager.getConnection(connectionId);
                if (connection == null || !req.getUserId().equals(connection.getUserID())) {
                    results.add(result(connectionId, false, DispatchResultCode.CONNECTION_NOT_FOUND));
                    continue;
                }
                results.add(dispatch(connection, req.getUserId(), payload, deadlineNanos));
            }
            resp.setResults(results);
            return resp;
        }

        for (UserConnection connection : targets) {
            results.add(dispatch(connection, req.getUserId(), payload, deadlineNanos));
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

    private DispatchResult dispatch(UserConnection connection,
                                    String userId,
                                    DispatchPayload payload,
                                    long deadlineNanos) {
        String connectionId = connection.getConnectionID();
        String deliveryId = payload.getDeliveryId();
        if (deliveryId == null && payload.getMsg() != null) {
            deliveryId = payload.getMsg().getServerMsgId();
        }
        DeliveryDedupStore.Claim claim = connectionManager.claimDelivery(deliveryId, userId, connectionId);
        if (claim.status() == DeliveryDedupStore.ClaimStatus.DELIVERED) {
            return result(connectionId, true, DispatchResultCode.DUPLICATE);
        }
        if (claim.status() == DeliveryDedupStore.ClaimStatus.IN_PROGRESS) {
            return result(connectionId, false, DispatchResultCode.DELIVERY_IN_PROGRESS);
        }
        if (claim.status() != DeliveryDedupStore.ClaimStatus.ACQUIRED) {
            return result(connectionId, false, DispatchResultCode.DEDUP_UNAVAILABLE);
        }
        ServerEnvelope envelope = payload.getEnvelope() != null
                ? payload.getEnvelope()
                : ServerEnvelope.chatRecv(deliveryId, payload);
        ChannelFuture writeFuture = connectionManager.writeMessageToConnection(connection, envelope);
        if (writeFuture == null) {
            connectionManager.abortDelivery(claim);
            return result(connectionId, false, DispatchResultCode.SEND_FAILED);
        }
        long remainingNanos = deadlineNanos - System.nanoTime();
        if (connection.getChannel().eventLoop().inEventLoop()
                || (remainingNanos <= 0L && !writeFuture.isDone())
                || (!writeFuture.isDone()
                && !writeFuture.awaitUninterruptibly(remainingNanos, TimeUnit.NANOSECONDS))) {
            deliveryWriteFinalizer.finalizeWhenComplete(writeFuture, claim);
            return result(connectionId, false, DispatchResultCode.WRITE_PENDING);
        }
        if (writeFuture.isSuccess()) {
            if (connectionManager.commitDelivery(claim)) {
                return result(connectionId, true, DispatchResultCode.OK);
            }
            return result(connectionId, false, DispatchResultCode.DEDUP_COMMIT_FAILED);
        }
        connectionManager.abortDelivery(claim);
        return result(connectionId, false, DispatchResultCode.SEND_FAILED);
    }

    private DispatchResult result(String connectionId, boolean success, DispatchResultCode code) {
        return new DispatchResult(connectionId, success, code.getCode(), code.getDesc());
    }
}
