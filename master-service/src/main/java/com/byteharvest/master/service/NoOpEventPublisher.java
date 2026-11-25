package com.byteharvest.master.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Default: Kafka is off, so file activity is not published anywhere. */
@Component
@ConditionalOnProperty(name = "kafka.enabled", havingValue = "false", matchIfMissing = true)
public class NoOpEventPublisher implements EventPublisher {

    @Override
    public void publish(String type, String fileId) {
        // intentionally nothing
    }
}
