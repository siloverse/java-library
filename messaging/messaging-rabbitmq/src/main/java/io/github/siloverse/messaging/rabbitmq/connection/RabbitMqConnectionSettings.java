package io.github.siloverse.messaging.rabbitmq.connection;

import java.util.Objects;

public record RabbitMqConnectionSettings(
        String host,
        int port,
        String username,
        String password
) {
    public RabbitMqConnectionSettings {
        Objects.requireNonNull(host, "host must not be null");
        Objects.requireNonNull(username, "username must not be null");
        Objects.requireNonNull(password, "password must not be null");

        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException(
                    "port must be between 1 and 65535"
            );
        }
        if (host.isBlank()) {
            throw new IllegalArgumentException(
                    "host must not be blank"
            );
        }

        if (username.isBlank()) {
            throw new IllegalArgumentException(
                    "username must not be blank"
            );
        }

        if (password.isBlank()) {
            throw new IllegalArgumentException(
                    "password must not be blank"
            );
        }
    }
}
