package io.github.siloverse.messaging.rabbitmq.transport;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import io.github.siloverse.messaging.core.transport.Envelope;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class RabbitMqMessageTransportTest {

    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:4.1-management-alpine");

    static Connection connection;

    @BeforeAll
    static void startBrokerAndConnect() throws Exception {
        rabbit.start();

        var factory = new ConnectionFactory();
        factory.setHost(rabbit.getHost());
        factory.setPort(rabbit.getAmqpPort());
        factory.setUsername(rabbit.getAdminUsername());
        factory.setPassword(rabbit.getAdminPassword());
        connection = factory.newConnection();
    }

    @AfterAll
    static void closeConnection() throws Exception {
        connection.close();
    }

    @Test
    void sentMessageLandsInBoundQueueWithIdentityIntact() throws Exception {
        var messageType = "order-silo.order-confirmed";
        var queue = "order-worker-on-order-confirmed";

        // play the consuming side: declare the topology the transport publishes into
        try (var setup = connection.createChannel()) {
            setup.exchangeDeclare(messageType, "fanout", true);
            setup.queueDeclare(queue, true, false, false, null);
            setup.queueBind(queue, messageType, messageType);
        }

        var messageId = UUID.randomUUID();
        var payload = "{\"orderId\":42}".getBytes(StandardCharsets.UTF_8);
        var envelope = new Envelope(messageId, messageType, Map.of("content-type", "application/json"), payload);

        var transport = new RabbitMqMessageTransport(connection);
        transport.send(envelope);

        // send() has returned, so the broker has acked -- the message must already be here
        try (var check = connection.createChannel()) {
            var delivered = check.basicGet(queue, true);

            assertThat(delivered)
                    .as("send() returned, so the broker acked -- the message must be in the bound queue")
                    .isNotNull();
            assertThat(delivered.getBody()).isEqualTo(payload);
            assertThat(delivered.getProps().getMessageId()).isEqualTo(messageId.toString());
            assertThat(delivered.getProps().getDeliveryMode())
                    .as("must be persistent (delivery mode 2) -- a confirm for a transient message is durability theater")
                    .isEqualTo(2);
            assertThat(delivered.getProps().getHeaders().get("content-type"))
                    .as("envelope headers must survive the wire")
                    .hasToString("application/json");
        }
    }
}
