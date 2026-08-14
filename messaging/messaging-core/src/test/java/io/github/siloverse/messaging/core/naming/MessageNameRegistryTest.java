package io.github.siloverse.messaging.core.naming;

import io.github.siloverse.messaging.core.error.MessagingConfigurationException;
import io.github.siloverse.messaging.core.fixtures.ConfirmOrder;
import io.github.siloverse.messaging.core.fixtures.OrderConfirmed;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class MessageNameRegistryTest {

    @Test
    void testRegisteredClassResolvesToItsWireName() {
        var registry = MessageNameRegistry.builder()
                .register(OrderConfirmed.class, "order-silo.order-confirmed")
                .register(ConfirmOrder.class, "order-silo.confirm-order")
                .freeze();

        assertThat(registry.nameOf(OrderConfirmed.class)).isEqualTo("order-silo.order-confirmed");
        assertThat(registry.nameOf(ConfirmOrder.class)).isEqualTo("order-silo.confirm-order");
    }

    @Test
    void testUnregisteredClassThrowsWithCulpritAndFix() {
        var registry = MessageNameRegistry.builder()
                .register(OrderConfirmed.class, "order-silo.order-confirmed")
                .freeze();

        assertThatThrownBy(() -> registry.nameOf(ConfirmOrder.class))
                .isInstanceOf(MessagingConfigurationException.class)
                .hasMessageContaining(ConfirmOrder.class.getName())   // culprit
                .hasMessageContaining("register(ConfirmOrder.class"); // fix
    }

    @Test
    void testBlankWireNameIsRejected() {
        assertThatThrownBy(() -> MessageNameRegistry.builder()
                .register(OrderConfirmed.class, "   "))
                .isInstanceOf(MessagingConfigurationException.class)
                .hasMessageContaining(OrderConfirmed.class.getName())
                .hasMessageContaining("blank");
    }

    @Test
    void testSameWireNameForTwoClassesIsRejected() {
        var builder = MessageNameRegistry.builder()
                .register(OrderConfirmed.class, "order-silo.order-confirmed");

        assertThatThrownBy(() -> builder.register(ConfirmOrder.class, "order-silo.order-confirmed"))
                .isInstanceOf(MessagingConfigurationException.class)
                .hasMessageContaining("order-silo.order-confirmed")   // the colliding name
                .hasMessageContaining(OrderConfirmed.class.getName()) // who owns it already
                .hasMessageContaining(ConfirmOrder.class.getName());  // who tried to take it
    }

    @Test
    void testSameClassRegisteredTwiceIsRejected() {
        var builder = MessageNameRegistry.builder()
                .register(OrderConfirmed.class, "order-silo.order-confirmed");

        assertThatThrownBy(() -> builder.register(OrderConfirmed.class, "order-silo.order-confirmed-v2"))
                .isInstanceOf(MessagingConfigurationException.class)
                .hasMessageContaining(OrderConfirmed.class.getName())
                .hasMessageContaining("already registered");
    }

    @Test
    void testComposeMergesContractJarRegistries() {
        var orderSilo = MessageNameRegistry.builder()
                .register(OrderConfirmed.class, "order-silo.order-confirmed")
                .freeze();
        var billingSilo = MessageNameRegistry.builder()
                .register(ConfirmOrder.class, "billing-silo.confirm-order")
                .freeze();

        var composed = MessageNameRegistry.compose(orderSilo, billingSilo);

        assertThat(composed.nameOf(OrderConfirmed.class)).isEqualTo("order-silo.order-confirmed");
        assertThat(composed.nameOf(ConfirmOrder.class)).isEqualTo("billing-silo.confirm-order");
        assertThat(composed.allNames())
                .containsExactlyInAnyOrder("order-silo.order-confirmed", "billing-silo.confirm-order");
    }

    @Test
    void testComposeRejectsDuplicateWireNameAcrossJars() {
        // two teams claiming the same wire name must die at startup, not cross-wire messages
        var one = MessageNameRegistry.builder()
                .register(OrderConfirmed.class, "order-silo.order-confirmed")
                .freeze();
        var two = MessageNameRegistry.builder()
                .register(ConfirmOrder.class, "order-silo.order-confirmed")
                .freeze();

        assertThatThrownBy(() -> MessageNameRegistry.compose(one, two))
                .isInstanceOf(MessagingConfigurationException.class)
                .hasMessageContaining("order-silo.order-confirmed")
                .hasMessageContaining(OrderConfirmed.class.getName())
                .hasMessageContaining(ConfirmOrder.class.getName());
    }

    @Test
    void testComposeRejectsTheSameClassArrivingTwice() {
        // the doubled-contract-jar scenario: one class must come from exactly one registry
        var one = MessageNameRegistry.builder()
                .register(OrderConfirmed.class, "order-silo.order-confirmed")
                .freeze();
        var again = MessageNameRegistry.builder()
                .register(OrderConfirmed.class, "order-silo.order-confirmed")
                .freeze();

        assertThatThrownBy(() -> MessageNameRegistry.compose(one, again))
                .isInstanceOf(MessagingConfigurationException.class)
                .hasMessageContaining(OrderConfirmed.class.getName());
    }

    @Test
    void testAllNamesEnumeratesEveryRegisteredWireName() {
        var registry = MessageNameRegistry.builder()
                .register(OrderConfirmed.class, "order-silo.order-confirmed")
                .register(ConfirmOrder.class, "order-silo.confirm-order")
                .freeze();

        // topology source: broker adapters declare one exchange per wire name
        assertThat(registry.allNames())
                .containsExactlyInAnyOrder("order-silo.order-confirmed", "order-silo.confirm-order");
    }
}
