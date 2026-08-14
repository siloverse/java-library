package io.github.siloverse.messaging.rabbitmq.connection;

import com.rabbitmq.client.Recoverable;
import io.github.siloverse.messaging.core.error.MessagingConfigurationException;
import io.github.siloverse.messaging.core.error.MessagingException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import static org.assertj.core.api.Assertions.*;

class RabbitMqConnectorTest {

    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:4.1-management-alpine");

    @BeforeAll
    static void startBroker() {
        rabbit.start();
    }

    @AfterAll
    static void stopBroker() {
        rabbit.stop();
    }

    @Test
    void connectsAndTheConnectionRecoversItself() throws Exception {
        var settings = new RabbitMqConnectionSettings(
                rabbit.getHost(), rabbit.getAmqpPort(), rabbit.getAdminUsername(), rabbit.getAdminPassword());

        try (var connection = RabbitMqConnector.connect(settings)) {
            assertThat(connection.isOpen()).isTrue();
            assertThat(connection)
                    .as("automatic recovery is library policy, not a hope -- the client returns a"
                            + " Recoverable connection exactly when recovery is enabled")
                    .isInstanceOf(Recoverable.class);
        }
    }

    @Test
    void wrongCredentialsAreAConfigurationError() {
        var settings = new RabbitMqConnectionSettings(
                rabbit.getHost(), rabbit.getAmqpPort(), rabbit.getAdminUsername(), "wrong-password");

        assertThatThrownBy(() -> RabbitMqConnector.connect(settings))
                .as("the broker rejected the credentials -- the fix is configuration, not operations")
                .isInstanceOf(MessagingConfigurationException.class)
                .hasMessageContaining(rabbit.getHost())
                .hasMessageContaining(rabbit.getAdminUsername())
                .hasMessageNotContaining("wrong-password");
    }

    @Test
    void unreachableBrokerIsAnEnvironmentalError() {
        // port 1 is reserved and nothing listens on it: connection refused, fast
        var settings = new RabbitMqConnectionSettings("localhost", 1, "app", "password");

        assertThatThrownBy(() -> RabbitMqConnector.connect(settings))
                .as("nobody is listening -- the fix is operational, so the base MessagingException,"
                        + " never the configuration tier")
                .isInstanceOf(MessagingException.class)
                .isNotInstanceOf(MessagingConfigurationException.class)
                .hasMessageContaining("localhost")
                .hasMessageContaining("1");
    }
}
