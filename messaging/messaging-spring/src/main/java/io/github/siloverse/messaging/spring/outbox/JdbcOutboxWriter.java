package io.github.siloverse.messaging.spring.outbox;

import java.util.Objects;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.siloverse.messaging.core.error.MessagingException;
import io.github.siloverse.messaging.core.transport.Envelope;
import io.github.siloverse.messaging.core.transport.OutboxWriter;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Appends envelopes to the {@code messaging_outbox} table (DDL shipped as
 * {@code io/github/siloverse/messaging/outbox-postgres.sql} -- copy it into your migration tool).
 *
 * <p>Writes only the four bus-authored columns. The database assigns {@code id} and
 * {@code created_at}; {@code published_at} stays NULL until the relay stamps it after broker ack.
 *
 * <p>Transactions: this writer manages none. The insert joins whatever transaction the calling
 * thread is in -- commit keeps the row, rollback removes it. Without a transaction the insert
 * commits immediately, with no rollback safety.
 *
 * <p>Headers are serialized with the injected {@link ObjectMapper} as a flat string-to-string
 * map, so application mapper configuration cannot change the wire format. Do not serialize
 * arbitrary objects through it.
 */
public class JdbcOutboxWriter implements OutboxWriter {

    private static final String INSERT_SQL = """
            INSERT INTO messaging_outbox (
                message_id,
                message_type,
                headers,
                payload
            )
            VALUES (?, ?, CAST(? AS jsonb), ?)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcOutboxWriter(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public void append(Envelope envelope) {

        String headersJson = serializeHeaders(envelope);

        jdbcTemplate.update(INSERT_SQL, statement -> {
            statement.setObject(1, envelope.messageId());
            statement.setString(2, envelope.messageType());
            statement.setString(3, headersJson);
            statement.setBytes(4, envelope.payload());
        });
    }

    private String serializeHeaders(Envelope envelope) {
        try {

            return objectMapper.writeValueAsString(envelope.headers());
        } catch (JsonProcessingException exception) {
            throw new MessagingException(
                    String.format(
                            "Message header serialization failed for [message-id:%s], message-type:%s",
                            envelope.messageId(),
                            envelope.messageType()
                    ),
                    exception
            );
        }
    }
}
