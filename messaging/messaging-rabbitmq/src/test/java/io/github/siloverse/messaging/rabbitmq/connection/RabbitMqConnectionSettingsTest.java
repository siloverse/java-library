package io.github.siloverse.messaging.rabbitmq.connection;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class RabbitMqConnectionSettingsTest {

    @Test
    void validSettingsConstruct() {
        var settings = new RabbitMqConnectionSettings("rabbit.internal", 5672, "app", "password");

        assertThat(settings.host()).isEqualTo("rabbit.internal");
        assertThat(settings.port()).isEqualTo(5672);
    }

    @Test
    void nullsAreRejectedNamingTheField() {
        assertThatNullPointerException()
                .isThrownBy(() -> new RabbitMqConnectionSettings(null, 5672, "app", "password"))
                .withMessageContaining("host");
        assertThatNullPointerException()
                .isThrownBy(() -> new RabbitMqConnectionSettings("rabbit.internal", 5672, null, "password"))
                .withMessageContaining("username");
        assertThatNullPointerException()
                .isThrownBy(() -> new RabbitMqConnectionSettings("rabbit.internal", 5672, "app", null))
                .withMessageContaining("password");
    }

    @Test
    void blankHostAndUsernameAreRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RabbitMqConnectionSettings("  ", 5672, "app", "password"))
                .withMessageContaining("host");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RabbitMqConnectionSettings("rabbit.internal", 5672, "  ", "password"))
                .withMessageContaining("username");
    }

    @Test
    void portOutsideValidRangeIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RabbitMqConnectionSettings("rabbit.internal", 0, "app", "password"))
                .withMessageContaining("port");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RabbitMqConnectionSettings("rabbit.internal", 65536, "app", "password"))
                .withMessageContaining("port");
    }
}
