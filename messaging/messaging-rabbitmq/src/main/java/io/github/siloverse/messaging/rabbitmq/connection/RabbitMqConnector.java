package io.github.siloverse.messaging.rabbitmq.connection;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.AuthenticationFailureException;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import io.github.siloverse.messaging.core.error.MessagingConfigurationException;
import io.github.siloverse.messaging.core.error.MessagingException;

/**
 * Opens broker connections from {@link RabbitMqConnectionSettings}.
 *
 * <p>The caller owns the returned connection and must close it on shutdown. Automatic
 * connection and topology recovery are library policy: after a network failure the
 * connection heals itself; sends fail during the outage and work again after it.
 */
public final class RabbitMqConnector {

    private RabbitMqConnector() {
    }

    public static Connection connect(RabbitMqConnectionSettings settings) {
        Objects.requireNonNull(settings, "settings must not be null");

        var factory = new ConnectionFactory();
        factory.setHost(settings.host());
        factory.setPort(settings.port());
        factory.setUsername(settings.username());
        factory.setPassword(settings.password());
        factory.setAutomaticRecoveryEnabled(true);
        factory.setTopologyRecoveryEnabled(true);

        try {
            return factory.newConnection();
        } catch (AuthenticationFailureException e) {
            throw new MessagingConfigurationException(
                    "Broker at " + settings.host() + ":" + settings.port() + " rejected username '"
                            + settings.username() + "'. Correct the username/password in the"
                            + " connection settings.", e);
        } catch (IOException | TimeoutException e) {
            throw new MessagingException(
                    "Could not connect to broker at " + settings.host() + ":" + settings.port()
                            + ". Verify the broker is running and reachable from this host.", e);
        }
    }
}
