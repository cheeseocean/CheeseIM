package com.cheeseocean.im.common.api.rpc;

import com.cheeseocean.im.common.api.dto.receipt.ReceiptAckReq;

public interface ReceiptAckRpc {

    void apply(ReceiptAckReq req);
}
