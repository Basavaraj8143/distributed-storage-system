package com.byteharvest.master.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Creates the events topic on startup when Kafka is enabled.
 *
 * <p>Three partitions so you can see how the fileId key spreads events; one replica because the local
 * broker is a single node.</p>
 */
@Configuration
@ConditionalOnProperty(name = "kafka.enabled", havingValue = "true")
public class KafkaTopicConfig {

    @Bean
    NewTopic fileEventsTopic(@Value("${kafka.topic.file-events:byteharvest.file-events}") String topic) {
        return TopicBuilder.name(topic).partitions(3).replicas(1).build();
    }
}
