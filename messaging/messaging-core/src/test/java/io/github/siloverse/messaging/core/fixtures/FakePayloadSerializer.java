package io.github.siloverse.messaging.core.fixtures;

import io.github.siloverse.messaging.core.transport.PayloadSerializer;

import java.nio.charset.StandardCharsets;

/** Test serializer: payload = toString() bytes, so assertions read as text, not byte noise. */
public class FakePayloadSerializer implements PayloadSerializer {

    @Override
    public byte[] serialize(Object message) {
        return message.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String contentType() {
        return "test/fake";
    }
}
