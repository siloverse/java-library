package io.github.siloverse.messaging.integration;

import io.github.siloverse.messaging.exception.TransactionRequiredException;
import io.github.siloverse.messaging.fixture.OrderConfirmed;
import io.github.siloverse.messaging.persistence.DeliveryClaimStrategy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Two pollers competing for the same rows.
 *
 * <p>{@code FOR UPDATE SKIP LOCKED} is what stops a delivery from being handed to two workers at
 * once, whether those workers are in one JVM or in several application instances.
 */
class ConcurrentClaimTest extends AbstractDurableMessagingTest {

    private static final int EVENTS = 15;
    private static final int DELIVERIES_PER_EVENT = 2;

    @Autowired
    private DeliveryClaimStrategy claimStrategy;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void twoConcurrentClaimsNeverOverlap() throws Exception {
        for (int i = 0; i < EVENTS; i++) {
            orderService.publishInTransaction(new OrderConfirmed(UUID.randomUUID()));
        }
        int total = EVENTS * DELIVERIES_PER_EVENT;
        assertThat(deliveries.count()).isEqualTo(total);

        // Half the batch each: whichever poller runs second still finds enough unlocked rows, so
        // both claim their full batch and the two batches must not overlap.
        int batchSize = total / 2;
        CyclicBarrier startTogether = new CyclicBarrier(2);
        ExecutorService pollers = Executors.newFixedThreadPool(2);
        try {
            List<Future<List<UUID>>> results = new ArrayList<>();
            for (int poller = 0; poller < 2; poller++) {
                results.add(pollers.submit(() -> {
                    startTogether.await(10, TimeUnit.SECONDS);
                    return claimInOwnTransaction(batchSize);
                }));
            }

            List<UUID> first = results.get(0).get(20, TimeUnit.SECONDS);
            List<UUID> second = results.get(1).get(20, TimeUnit.SECONDS);

            assertThat(first).hasSize(batchSize);
            assertThat(second).hasSize(batchSize);
            assertThat(first).doesNotContainAnyElementsOf(second);

            List<UUID> claimed = new ArrayList<>(first);
            claimed.addAll(second);
            assertThat(claimed).doesNotHaveDuplicates().hasSize(total);
        }
        finally {
            pollers.shutdownNow();
        }
    }

    @Test
    void claimingOutsideATransactionIsRejected() {
        assertThatThrownBy(() -> claimStrategy.claim(10, Instant.now(), 3))
                .isInstanceOf(TransactionRequiredException.class)
                .hasMessageContaining("inside a transaction");
    }

    @Test
    void anAlreadyClaimedDeliveryIsNotClaimedAgain() {
        orderService.publishInTransaction(new OrderConfirmed(UUID.randomUUID()));

        List<UUID> first = claimInOwnTransaction(10);
        List<UUID> second = claimInOwnTransaction(10);

        assertThat(first).hasSize(DELIVERIES_PER_EVENT);
        assertThat(second).isEmpty();
    }

    private List<UUID> claimInOwnTransaction(int batchSize) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        List<UUID> claimed = template.execute(status -> claimStrategy.claim(batchSize, Instant.now(), 3));
        return claimed == null ? List.of() : claimed;
    }
}
