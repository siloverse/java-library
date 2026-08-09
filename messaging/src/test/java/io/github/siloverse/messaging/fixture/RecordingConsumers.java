package io.github.siloverse.messaging.fixture;

import io.github.siloverse.messaging.annotation.Consumer;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * One command consumer and two event consumers, each recording what it received.
 *
 * <p>Consumers can be told to fail for a given order id, which is how the retry and consumer
 * isolation tests provoke failures.
 */
@Component
public class RecordingConsumers {

    private final List<ConfirmOrder> commands = new CopyOnWriteArrayList<>();
    private final List<OrderConfirmed> emails = new CopyOnWriteArrayList<>();
    private final List<OrderConfirmed> analytics = new CopyOnWriteArrayList<>();
    private final List<String> invocations = new CopyOnWriteArrayList<>();

    private volatile UUID failingCommandOrderId;
    private volatile UUID failingEmailOrderId;
    private volatile UUID failingAnalyticsOrderId;

    @Consumer
    public void confirm(ConfirmOrder command) {
        commands.add(command);
        invocations.add("confirm");
        failIfRequested(command.orderId(), failingCommandOrderId, "confirm");
    }

    @Consumer(order = 1)
    public void sendEmail(OrderConfirmed event) {
        emails.add(event);
        invocations.add("sendEmail");
        failIfRequested(event.orderId(), failingEmailOrderId, "sendEmail");
    }

    @Consumer(order = 2)
    public void updateAnalytics(OrderConfirmed event) {
        analytics.add(event);
        invocations.add("updateAnalytics");
        failIfRequested(event.orderId(), failingAnalyticsOrderId, "updateAnalytics");
    }

    private void failIfRequested(UUID received, UUID failing, String consumer) {
        if (failing != null && failing.equals(received)) {
            throw new IllegalStateException(consumer + " was told to fail for order " + received);
        }
    }

    public List<ConfirmOrder> commands() {
        return commands;
    }

    public List<OrderConfirmed> emails() {
        return emails;
    }

    public List<OrderConfirmed> analytics() {
        return analytics;
    }

    /**
     * @return the names of the consumer methods in the order they ran
     */
    public List<String> invocations() {
        return invocations;
    }

    public void failCommandFor(UUID orderId) {
        this.failingCommandOrderId = orderId;
    }

    public void failEmailFor(UUID orderId) {
        this.failingEmailOrderId = orderId;
    }

    public void failAnalyticsFor(UUID orderId) {
        this.failingAnalyticsOrderId = orderId;
    }

    public void reset() {
        commands.clear();
        emails.clear();
        analytics.clear();
        invocations.clear();
        failingCommandOrderId = null;
        failingEmailOrderId = null;
        failingAnalyticsOrderId = null;
    }
}
