package com.byteharvest.master.service;

/**
 * Announces two file activities to the outside world: FILE_UPLOADED and FILE_DELETED.
 *
 * <p>Services depend on this interface only, so they do not know whether Kafka exists.
 * Spring injects {@link NoOpEventPublisher} by default and {@link KafkaEventPublisher} when
 * {@code kafka.enabled=true}. Implementations must never throw: publishing is best effort and must not
 * break an upload or a delete.</p>
 */
public interface EventPublisher {

    String FILE_UPLOADED = "FILE_UPLOADED";
    String FILE_DELETED = "FILE_DELETED";

    /**
     * @param type   one of the constants above
     * @param fileId the file the activity is about
     */
    void publish(String type, String fileId);
}
