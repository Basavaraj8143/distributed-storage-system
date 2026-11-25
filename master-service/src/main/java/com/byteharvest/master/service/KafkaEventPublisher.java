package com.byteharvest.master.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes FILE_UPLOADED and FILE_DELETED to a Kafka topic. The message is just two strings:
 * the key is the fileId and the value is the event name.
 *
 * <p>The key decides the partition: every event about one file goes to the same partition, so a consumer
 * sees FILE_UPLOADED before FILE_DELETED for that file. Sending is asynchronous and a failure is only
 * logged.</p>
 */
@Component
@ConditionalOnProperty(name = "kafka.enabled", havingValue = "true")
public class KafkaEventPublisher implements EventPublisher {

    private static final Logger logger = LoggerFactory.getLogger(KafkaEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;

    public KafkaEventPublisher(KafkaTemplate<String, String> kafkaTemplate,
                               @Value("${kafka.topic.file-events:byteharvest.file-events}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    @Override
    public void publish(String type, String fileId) {
        try {
            kafkaTemplate.send(topic, fileId, type).whenComplete((result, error) -> {
                if (error != null) {
                    logger.warn("Could not publish {} to Kafka topic {}: {}", type, topic, error.toString());
                }
            });
        } catch (Exception e) {
            logger.warn("Could not publish {} to Kafka topic {}: {}", type, topic, e.toString());
        }
    }
}
