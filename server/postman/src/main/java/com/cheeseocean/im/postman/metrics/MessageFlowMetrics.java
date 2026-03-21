package com.cheeseocean.im.postman.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class MessageFlowMetrics {

    private final MeterRegistry meterRegistry;

    public MessageFlowMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordAcceptedIngress() {
        meterRegistry.counter("im.message.ingress.accepted").increment();
    }
}
