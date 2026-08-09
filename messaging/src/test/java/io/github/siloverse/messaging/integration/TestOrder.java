package io.github.siloverse.messaging.integration;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Stand in for application business state.
 *
 * <p>It lives in the application's own package, so it is also proof that adding the library's
 * entities does not hide the application's entities from Hibernate.
 */
@Entity
@Table(name = "test_orders")
public class TestOrder {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    protected TestOrder() {
        // for JPA
    }

    public TestOrder(UUID id, String status) {
        this.id = id;
        this.status = status;
    }

    public UUID getId() {
        return id;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
