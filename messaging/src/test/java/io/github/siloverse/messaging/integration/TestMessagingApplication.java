package io.github.siloverse.messaging.integration;

import io.github.siloverse.messaging.fixture.RecordingConsumers;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * Minimal application used by the integration tests.
 *
 * <p>Deliberately not a {@code @SpringBootApplication}: component scanning would pull every fixture
 * of the test source set into every context. {@code @EnableAutoConfiguration} still registers the
 * auto-configuration package, which is what the library uses to keep the application's own entities
 * and repositories discoverable.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@Import({ RecordingConsumers.class, TestOrderService.class })
public class TestMessagingApplication {
}
