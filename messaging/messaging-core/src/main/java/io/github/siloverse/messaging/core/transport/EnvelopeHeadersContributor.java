package io.github.siloverse.messaging.core.transport;

import java.util.Map;

public interface EnvelopeHeadersContributor {
    void contribute(Map<String, String> headers);
}