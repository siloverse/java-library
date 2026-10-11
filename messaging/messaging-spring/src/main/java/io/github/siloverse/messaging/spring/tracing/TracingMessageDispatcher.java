package io.github.siloverse.messaging.spring.tracing;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import io.github.siloverse.messaging.core.consumer.ConsumerDefinition;
import io.github.siloverse.messaging.core.dispatch.DefaultMessageDispatcher;
import io.github.siloverse.messaging.core.dispatch.MessageDispatcher;
import io.github.siloverse.messaging.core.error.MessagingException;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

public class TracingMessageDispatcher implements MessageDispatcher {

    private final ObjectMapper objectMapper;
    private final MessageDispatcher messageDispatcher;
    private final Tracer tracer;
    private final Propagator propagator;

    public TracingMessageDispatcher(ObjectMapper objectMapper, Tracer tracer, Propagator propagator,
            MessageDispatcher messageDispatcher) {
        Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        Objects.requireNonNull(messageDispatcher, "messageDispatcher must not be null");
        Objects.requireNonNull(propagator, "propagator must not be null");
        Objects.requireNonNull(tracer, "tracer must not be null");
        this.objectMapper = objectMapper;
        this.messageDispatcher = messageDispatcher;
        this.tracer = tracer;
        this.propagator = propagator;
    }

    Logger logger = LoggerFactory.getLogger(TracingMessageDispatcher.class);

    public void dispatch(ConsumerDefinition def, Map<String, String> messageHeaders, Object message) {
        Span span = propagator.extract(messageHeaders, Map::get)
                .name(def.id() + " consume").start();
        try (Tracer.SpanInScope ws = tracer.withSpan(span)) {
            logMessage(messageHeaders, message);
            messageDispatcher.dispatch(def, messageHeaders, message);
        } catch (Throwable t) {
            span.error(t);
            throw t;
        } finally {
            span.end();
        }
    }

    private void logMessage(Map<String, String> messageHeaders, Object payload) {
        Map<String, Object> logMessage = new HashMap<>(messageHeaders);
        logMessage.put("payload", payload);
        logger.debug("Message Received={}", objectMapper.writeValueAsString(logMessage));
    }
}

