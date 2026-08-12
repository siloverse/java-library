package io.github.siloverse.messaging.core.consumer;

import io.github.siloverse.messaging.core.error.MessagingConfigurationException;
import io.github.siloverse.messaging.core.error.NoHandlerException;
import io.github.siloverse.messaging.core.fixtures.ConfirmOrder;
import io.github.siloverse.messaging.core.fixtures.OrderConfirmed;
import io.github.siloverse.messaging.core.fixtures.WeirdMessage;
import io.github.siloverse.messaging.core.helper.ConsumerDefinitionHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ConsumerRegistryTest {

    ConsumerRegistry consumerRegistry;

    @BeforeEach
    void setup() {
        consumerRegistry = new ConsumerRegistry();
    }

    @Test
    void testTwoEventConsumers() {
        var definition1 = ConsumerDefinitionHelper.createConsumerDef("order-confirmed-1", OrderConfirmed.class);
        var definition2 = ConsumerDefinitionHelper.createConsumerDef("order-confirmed-2", OrderConfirmed.class);

        consumerRegistry.register(
                definition1
        );
        consumerRegistry.register(
                definition2
        );
        var consumers = consumerRegistry.eventConsumersFor(OrderConfirmed.class);

        assertThat(consumers).hasSize(2);
        assertThat(consumers.getFirst().id()).isEqualTo("order-confirmed-1");
        assertThat(consumers.getLast().id()).isEqualTo("order-confirmed-2");

    }


    @Test
    void testOneCommandConsumers() {
        var definition1 = ConsumerDefinitionHelper.createConsumerDef("confirm-order-1", ConfirmOrder.class);

        consumerRegistry.register(
                definition1
        );

        var consumer = consumerRegistry.commandConsumerFor(ConfirmOrder.class);

        assertThat(consumer).isNotNull();
        assertThat(consumer.id()).isEqualTo("confirm-order-1");
    }

    @Test
    void testTwoCommandConsumersThrowError() {
        assertThatThrownBy(() -> {
            var definition1 = ConsumerDefinitionHelper.createConsumerDef("confirm-order-1", ConfirmOrder.class);
            var definition2 = ConsumerDefinitionHelper.createConsumerDef("confirm-order-2", ConfirmOrder.class);

            consumerRegistry.register(
                    definition1
            );
            consumerRegistry.register(
                    definition2
            );
        })
                .isInstanceOf(MessagingConfigurationException.class)
                .hasMessage("Multiple consumers registered for command: %s", ConfirmOrder.class.getName());
    }


    @Test
    void testTwoConsumersWithSameIdThrowError() {
        assertThatThrownBy(() -> {

            var definition1 = ConsumerDefinitionHelper.createConsumerDef("order-consumer", OrderConfirmed.class);
            var definition2 = ConsumerDefinitionHelper.createConsumerDef("order-consumer", ConfirmOrder.class);

            consumerRegistry.register(
                    definition1
            );
            consumerRegistry.register(
                    definition2
            );

        })
                .isInstanceOf(MessagingConfigurationException.class)
                .hasMessageContaining("Consumer id is already registered");
    }

    @Test
    void testCommandConsumerForWithNothingRegistered() {
        assertThatThrownBy(() -> consumerRegistry.commandConsumerFor(ConfirmOrder.class))
                .isInstanceOf(NoHandlerException.class)
                .hasMessageContaining("No consumer registered for command");
    }

    @Test
    void testEventConsumersForWithNothingRegistered() {
        // zero subscribers is a legitimate pub-sub state: empty list, no exception
        var consumers = consumerRegistry.eventConsumersFor(OrderConfirmed.class);

        assertThat(consumers).isEmpty();
    }

    @Test
    void testFailedRegistrationLeavesNoPartialState() {
        var definition1 = ConsumerDefinitionHelper.createConsumerDef("order-consumer", OrderConfirmed.class);
        var definition2 = ConsumerDefinitionHelper.createConsumerDef("order-consumer", ConfirmOrder.class);

        consumerRegistry.register(definition1);

        // duplicate id -> rejected
        assertThatThrownBy(() -> consumerRegistry.register(definition2))
                .isInstanceOf(MessagingConfigurationException.class);

        // the FAILED registration must leave no trace:
        // 1) ConfirmOrder must NOT be in the command map
        assertThatThrownBy(() -> consumerRegistry.commandConsumerFor(ConfirmOrder.class))
                .isInstanceOf(NoHandlerException.class);

        // 2) registering the same consumer under a fresh id must now succeed
        var retry = ConsumerDefinitionHelper.createConsumerDef("confirm-order-1", ConfirmOrder.class);
        consumerRegistry.register(retry);
        assertThat(consumerRegistry.commandConsumerFor(ConfirmOrder.class).id())
                .isEqualTo("confirm-order-1");
    }

    @Test
    void testMessageWithTwoMarkersIsRejected() {
        var definition = ConsumerDefinitionHelper.createConsumerDef("weird-consumer", WeirdMessage.class);

        assertThatThrownBy(() -> consumerRegistry.register(definition))
                .isInstanceOf(MessagingConfigurationException.class)
                .hasMessageContaining("more than one");
    }

    @Test
    void testFailedCommandRegistrationDoesNotBurnTheId() {
        consumerRegistry.register(
                ConsumerDefinitionHelper.createConsumerDef("confirm-order-1", ConfirmOrder.class));

        assertThatThrownBy(() -> consumerRegistry.register(
                ConsumerDefinitionHelper.createConsumerDef("confirm-order-2", ConfirmOrder.class)))
                .isInstanceOf(MessagingConfigurationException.class);

        // the rejected consumer's id must still be usable for a different message
        consumerRegistry.register(
                ConsumerDefinitionHelper.createConsumerDef("confirm-order-2", OrderConfirmed.class));
    }
}
