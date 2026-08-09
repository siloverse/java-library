package io.github.siloverse.messaging.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Delivery of one message to one consumer.
 *
 * <p>An event with three consumers produces one {@link StoredMessage} and three deliveries, each
 * claimed, retried and failed independently. A command produces exactly one delivery.
 */
@Entity
@Table(name = "message_deliveries")
public class MessageDelivery {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "message_id", nullable = false, updatable = false)
    private UUID messageId;

    @Column(name = "consumer_id", nullable = false, updatable = false, length = 500)
    private String consumerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DeliveryStatus status;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "available_at", nullable = false)
    private Instant availableAt;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected MessageDelivery() {
        // for JPA
    }

    private MessageDelivery(UUID id, UUID messageId, String consumerId, Instant availableAt, Instant createdAt) {
        this.id = id;
        this.messageId = messageId;
        this.consumerId = consumerId;
        this.status = DeliveryStatus.PENDING;
        this.attempts = 0;
        this.availableAt = availableAt;
        this.createdAt = createdAt;
    }

    /**
     * @param id         identifier of the new row
     * @param messageId  the message being delivered
     * @param consumerId the target consumer
     * @param now        creation instant, also the first instant the delivery is eligible
     * @return a new, unsaved delivery in {@code PENDING}
     */
    public static MessageDelivery pending(UUID id, UUID messageId, String consumerId, Instant now) {
        return new MessageDelivery(id, messageId, consumerId, now, now);
    }

    /**
     * Marks the delivery complete.
     *
     * @param now completion instant
     */
    public void markProcessed(Instant now) {
        this.status = DeliveryStatus.PROCESSED;
        this.processedAt = now;
        this.lockedAt = null;
        this.lastError = null;
    }

    /**
     * Returns the delivery to the queue for a later attempt.
     *
     * @param availableAt when it becomes eligible again
     * @param error       description of the failure
     */
    public void markForRetry(Instant availableAt, String error) {
        this.status = DeliveryStatus.PENDING;
        this.availableAt = availableAt;
        this.lockedAt = null;
        this.lastError = error;
    }

    /**
     * Gives up on the delivery. It is never claimed again.
     *
     * @param now   the instant of the final failure
     * @param error description of the failure
     */
    public void markFailed(Instant now, String error) {
        this.status = DeliveryStatus.FAILED;
        this.processedAt = now;
        this.lockedAt = null;
        this.lastError = error;
    }

    /**
     * Undoes a claim that could not be handed to a worker.
     */
    public void releaseClaim() {
        this.status = DeliveryStatus.PENDING;
        this.lockedAt = null;
        this.attempts = Math.max(0, this.attempts - 1);
    }

    public UUID getId() {
        return id;
    }

    public UUID getMessageId() {
        return messageId;
    }

    public String getConsumerId() {
        return consumerId;
    }

    public DeliveryStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getAvailableAt() {
        return availableAt;
    }

    public Instant getLockedAt() {
        return lockedAt;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
