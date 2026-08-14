package io.github.siloverse.messaging.spring.outbox;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.siloverse.messaging.core.error.MessagingException;
import io.github.siloverse.messaging.core.transport.Envelope;
import io.github.siloverse.messaging.core.transport.MessageTransport;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Moves pending rows from {@code messaging_outbox} to the broker: polls {@code published_at IS NULL} in id order,
 * publishes each via {@link MessageTransport}, and stamps {@code published_at} per row immediately after the send
 * returns.
 *
 * <p>Stops at the first send failure -- a failure is usually broker-wide, and the
 * unstamped rows are the retry mechanism: the next poll resumes exactly at the failed row. Because the transport may
 * have accepted a message whose stamp was never written (crash between send and stamp), delivery is at least once and
 * duplicates carry the same {@code message_id}.
 *
 * <p>Payload bytes pass through sealed -- this class never deserializes them.
 */
public class OutboxRelay {

    private static final String SELECT_PENDING_SQL = """
            SELECT id, message_id, message_type, headers, payload
            FROM messaging_outbox
            WHERE published_at IS NULL
            ORDER BY id
            """;

    private static final String STAMP_SQL = """
            UPDATE messaging_outbox
            SET published_at = now()
            WHERE id = ?
            """;

    private static final TypeReference<Map<String, String>> HEADERS_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final MessageTransport messageTransport;
    private final ObjectMapper objectMapper;

    public OutboxRelay(JdbcTemplate jdbcTemplate, MessageTransport messageTransport, ObjectMapper objectMapper) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.messageTransport = Objects.requireNonNull(messageTransport, "messageTransport must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    public void pollOnce() {
        List<PendingRow> pending = jdbcTemplate.query(SELECT_PENDING_SQL, this::mapRow);

        for (PendingRow row : pending) {
            messageTransport.send(row.envelope());
            jdbcTemplate.update(STAMP_SQL, row.id());
        }
    }

    private PendingRow mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
        UUID messageId = resultSet.getObject("message_id", UUID.class);
        return new PendingRow(
                resultSet.getLong("id"),
                new Envelope(
                        messageId,
                        resultSet.getString("message_type"),
                        parseHeaders(resultSet.getString("headers"), messageId),
                        resultSet.getBytes("payload")
                )
        );
    }

    private Map<String, String> parseHeaders(String headersJson, UUID messageId) {
        try {
            return objectMapper.readValue(headersJson, HEADERS_TYPE);
        } catch (JsonProcessingException exception) {
            throw new MessagingException(
                    "Message header deserialization failed for [message-id:" + messageId + "]",
                    exception
            );
        }
    }

    private record PendingRow(long id, Envelope envelope) { }
}
