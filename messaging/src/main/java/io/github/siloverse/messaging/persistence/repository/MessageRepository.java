package io.github.siloverse.messaging.persistence.repository;

import io.github.siloverse.messaging.persistence.entity.StoredMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Access to the immutable {@code messages} table.
 */
public interface MessageRepository extends JpaRepository<StoredMessage, UUID> {
}
