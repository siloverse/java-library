package io.github.siloverse.messaging.integration;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Application repository, discovered by Spring Boot's own repository scanning.
 */
public interface TestOrderRepository extends JpaRepository<TestOrder, UUID> {
}
