package com.cheeseocean.im.postmaster.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "cheeseim.message-flow")
public class MessageFlowProperties {

    private boolean asyncHistoryEnabled;
    private boolean asyncDeliveryEnabled;
    private boolean asyncReceiptEnabled;

    public boolean isAsyncHistoryEnabled() {
        return asyncHistoryEnabled;
    }

    public void setAsyncHistoryEnabled(boolean asyncHistoryEnabled) {
        this.asyncHistoryEnabled = asyncHistoryEnabled;
    }

    public boolean isAsyncDeliveryEnabled() {
        return asyncDeliveryEnabled;
    }

    public void setAsyncDeliveryEnabled(boolean asyncDeliveryEnabled) {
        this.asyncDeliveryEnabled = asyncDeliveryEnabled;
    }

    public boolean isAsyncReceiptEnabled() {
        return asyncReceiptEnabled;
    }

    public void setAsyncReceiptEnabled(boolean asyncReceiptEnabled) {
        this.asyncReceiptEnabled = asyncReceiptEnabled;
    }
}
