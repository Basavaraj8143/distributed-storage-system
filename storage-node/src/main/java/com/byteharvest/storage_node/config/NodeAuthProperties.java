package com.byteharvest.storage_node.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The secret the master and the storage nodes share, bound from {@code app.node.token}.
 * The node rejects every request that does not carry it in the {@code X-Node-Token} header,
 * and sends it with its own heartbeats to the master.
 *
 * <p>There is no default: the node refuses to start without a token of at least
 * {@value #MIN_LENGTH} characters. It must be the same value the master uses.
 *
 * @param token the shared secret
 */
@ConfigurationProperties(prefix = "app.node")
public record NodeAuthProperties(String token) {

    public static final int MIN_LENGTH = 16;

    public NodeAuthProperties {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException(
                    "No node token configured. Set the NODE_TOKEN environment variable (or app.node.token). "
                            + "Use the same value as the master.");
        }
        if (token.length() < MIN_LENGTH) {
            throw new IllegalArgumentException(
                    "The node token is too short (" + token.length() + " characters). Use at least " + MIN_LENGTH + ".");
        }
    }
}
