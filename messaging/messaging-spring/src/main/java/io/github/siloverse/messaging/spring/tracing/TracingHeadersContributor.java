package io.github.siloverse.messaging.spring.tracing;

import java.util.Map;
import java.util.Objects;

import io.github.siloverse.messaging.core.transport.EnvelopeHeadersContributor;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;

public class TracingHeadersContributor implements EnvelopeHeadersContributor {

    private final Tracer tracer;
    private final Propagator propagator;

    public TracingHeadersContributor(
            Tracer tracer,
            Propagator propagator
    ) {
        Objects.requireNonNull(tracer, "tracer must not be null");
        Objects.requireNonNull(propagator, "propagator must not be null");
        this.tracer = tracer;
        this.propagator = propagator;
    }

    @Override
    public void contribute(Map<String, String> headers) {
        var context = tracer.currentTraceContext().context();
        if (context == null) {
            return;
        }
        propagator.inject(context, headers, (contextPropagationMap, key, value) -> {
            Objects.requireNonNull(contextPropagationMap, "contextPropagationMap must not be null");
            contextPropagationMap.put(key, value);
        });
    }
}
