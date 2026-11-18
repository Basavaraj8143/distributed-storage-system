package com.byteharvest.master.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The secret the master and the storage nodes share, bound from {@code app.node.token}.
 * Every call between them carries it in the {@code X-Node-Token} header, so a program that does not
 * know it cannot pose as a node or as the master.
 *
 * <p>There is no default: the master refuses to start without a token of at least
 * {@value #MIN_LENGTH} characters. Generate one with {@code openssl rand -hex 32}.
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
                            + "Generate one with: openssl rand -hex 32");
        }
        if (token.length() < MIN_LENGTH) {
            throw new IllegalArgumentException(
                    "The node token is too short (" + token.length() + " characters). Use at least " + MIN_LENGTH
                            + ". Generate one with: openssl rand -hex 32");
        }
    }
}
