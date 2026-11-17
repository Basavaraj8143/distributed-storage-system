package com.byteharvest.master.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Login for the user-facing API, bound from the {@code app.auth.*} properties.
 * There is deliberately no default password: the master refuses to start without one, so it can
 * never run with a password that is published in the source code.
 *
 * <pre>
 * app.auth.username=${APP_USERNAME:admin}
 * app.auth.password=${APP_PASSWORD:}
 * </pre>
 *
 * @param username login name, must not be blank
 * @param password login password, must not be blank
 */
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
        @DefaultValue("admin") String username,
        String password) {

    public AuthProperties {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("app.auth.username must not be blank.");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException(
                    "No password configured. Set the APP_PASSWORD environment variable (or app.auth.password).");
        }
    }
}
