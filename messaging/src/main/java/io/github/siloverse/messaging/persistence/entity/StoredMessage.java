package io.github.siloverse.messaging.persistence.entity;

import io.github.siloverse.messaging.api.MessageKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * The immutable logical message, stored once no matter how many consumers it has.
 *
 * <p>Per consumer state lives on {@link MessageDelivery}; nothing on this row ever changes after
 * the insert.
 */
@Entity
@Table(name = "messages")
public class StoredMessage {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "message_type", nullable = false, updatable = false, length = 500)
    private String messageType;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_kind", nullable = false, updatable = false, length = 20)
    private MessageKind messageKind;

    @Column(name = "payload", nullable = false, updatable = false, columnDefinition = "text")
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected StoredMessage() {
        // for JPA
    }

    private StoredMessage(UUID id, String messageType, MessageKind messageKind, String payload, Instant createdAt) {
        this.id = id;
        this.messageType = messageType;
        this.messageKind = messageKind;
        this.payload = payload;
        this.createdAt = createdAt;
    }

    /**
     * @param id          identifier of the new row
     * @param messageType type token from the serializer
     * @param messageKind command or event
     * @param payload     serialized message state
     * @param createdAt   publication instant
     * @return a new, unsaved message row
     */
    public static StoredMessage create(UUID id, String messageType, MessageKind messageKind,
                                       String payload, Instant createdAt) {
        return new StoredMessage(id, messageType, messageKind, payload, createdAt);
    }

    public UUID getId() {
        return id;
    }

    public String getMessageType() {
        return messageType;
    }

    public MessageKind getMessageKind() {
        return messageKind;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
