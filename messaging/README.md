# siloverse messaging

Event/command messaging for silo services: transactional publishing (outbox + relay),
RabbitMQ transport with publisher confirms, typed consumption with a bounded failure
policy, and opt-in deduplication.

**The delivery contract, in one line:** at-least-once delivery, exactly-once *committed
effects* for consumers that opt into `dedup = true` — everything outside the consumer's
transaction is your responsibility.

## Modules

| module | depends on | contents |
|---|---|---|
| `messaging-core` | slf4j-api | markers (`Event`, `Command`), bus interfaces, `MessageNameRegistry`, `ConsumerRegistry`, ports (`MessageTransport`, `PayloadSerializer`, `PayloadDeserializer`, `OutboxWriter`, `Inbox`) |
| `messaging-spring` | core, Spring (no Boot), Jackson | consumer scanning, `MessagingConfiguration` + `AsyncMessagingConfiguration`, outbox writer + relay, `JdbcInbox`, Jackson serializers, lifecycle |
| `messaging-rabbitmq` | core, amqp-client | `RabbitMqConnector`, `RabbitMqMessageTransport` (publisher confirms), `RabbitMqTopologyDeclarer`, `RabbitMqMessageListener` |

The library uses Spring Framework directly and works fine inside a Spring Boot service —
Boot's auto-configured `DataSource`, `JdbcTemplate`, `TransactionTemplate` and
`ObjectMapper` are exactly the beans it consumes.

## 1. Dependencies

```kotlin
implementation("io.github.siloverse:messaging-spring:<version>")
implementation("io.github.siloverse:messaging-rabbitmq:<version>")
// messaging-core arrives transitively via api(...)
```

## 2. The contract jar: message classes + wire names travel together

Each silo ships its messages in a separate `messages` module that both the publisher and
every consumer depend on. Wire names are registered there — never derived from class names
(a rename must never change what is on the wire):

```java
// order-silo-messages jar
public record OrderConfirmed(UUID orderId, int amount) implements Event {}

public final class OrderSiloMessages {
    public static MessageNameRegistry names() {
        return MessageNameRegistry.builder()
                .register(OrderConfirmed.class, "order-silo.order-confirmed")
                .freeze();
    }
}
```

Convention: `<service>.<message>`, lowercase-dashed.

## 3. Database schema

Ship-your-own-schema: copy the DDL resources from the `messaging-spring` jar into your
migration tool (Flyway/Liquibase — the library deliberately depends on neither):

- `io/github/siloverse/messaging/outbox-postgres.sql` — required for the
  transaction-aware bus (the outbox).
- `io/github/siloverse/messaging/inbox-postgres.sql` — required only if any consumer
  declares `dedup = true`.

## 4. Wiring (one `@Configuration` in your Boot service)

Register both library configurations and provide the app side of the contract:

```java
@Configuration
@Import({MessagingConfiguration.class, AsyncMessagingConfiguration.class})
class MessagingWiring {

    // -- broker connection: config in, library handles the rest --------------

    @Bean
    RabbitMqConnectionSettings rabbitSettings(
            @Value("${rabbit.host}") String host, @Value("${rabbit.port}") int port,
            @Value("${rabbit.username}") String user, @Value("${rabbit.password}") String password) {
        return new RabbitMqConnectionSettings(host, port, user, password);
    }

    @Bean(destroyMethod = "close")
    Connection rabbitConnection(RabbitMqConnectionSettings settings) {
        return RabbitMqConnector.connect(settings);   // auto-recovering, library policy
    }

    @Bean
    MessageTransport messageTransport(Connection connection) {
        return new RabbitMqMessageTransport(connection);   // publisher confirms: return = durably accepted
    }

    // -- serialization + names ----------------------------------------------

    @Bean
    PayloadSerializer payloadSerializer(ObjectMapper mapper) {
        return new JacksonPayloadSerializer(mapper);
    }

    @Bean
    MessageNameRegistry messageNames() {
        // your own names + every silo you consume from, merged with cross-jar checks
        return MessageNameRegistry.compose(
                BillingSiloMessages.names(),
                OrderSiloMessages.names());
    }

    // -- topology: declared at startup, after the consumer registry freezes --

    @Bean
    TopologyDeclaration rabbitTopology(Connection connection, ConsumerRegistry consumers,
            MessageNameRegistry names) {
        var declarer = new RabbitMqTopologyDeclarer(connection);
        return () -> {
            declarer.declarePublisherTopology(names);
            declarer.declareConsumerTopology("billing-silo", consumers, names);
        };
    }

    // -- consuming: own connection, owned by the assembly --------------------

    @Bean
    MessageListener rabbitListener(RabbitMqConnectionSettings settings, ConsumerRegistry consumers,
            MessageNameRegistry names, ObjectMapper mapper, MessageDispatcher dispatcher,
            JdbcTemplate jdbcTemplate, TransactionTemplate transactionTemplate) {
        return new MessageListener() {
            private Connection consumeConnection;
            private RabbitMqMessageListener listener;

            @Override public void start() {
                // separate connection: broker flow control throttles publishers and
                // must not starve consumers
                consumeConnection = RabbitMqConnector.connect(settings);
                listener = new RabbitMqMessageListener(consumeConnection, "billing-silo",
                        consumers, names, new JacksonPayloadDeserializer(mapper), dispatcher,
                        new JdbcInbox(jdbcTemplate, transactionTemplate));  // omit if no dedup consumers
                listener.start();
            }

            @Override public void stop() {
                if (listener != null) listener.stop();
                try { if (consumeConnection != null) consumeConnection.close(); } catch (Exception ignored) {}
            }
        };
    }
}
```

Startup and shutdown ordering (scan → freeze → declare topology → start listeners →
start relay; reverse on shutdown) is handled by the library's `MessagingLifecycle`.
Optional: an `OutboxRelaySettings` bean to change the relay poll interval (default 1s).

## 5. Publishing

Pick the bus by asking: **does this message assert a database-committed fact?**

```java
@Service
class OrderService {
    private final TransactionAwareAsynchronousBus bus;   // inject by the SPECIFIC type

    @Transactional
    void confirmOrder(UUID orderId) {
        // ... business writes ...
        bus.publish(new OrderConfirmed(orderId, 42));
        // outbox row commits WITH the business writes; rollback -> nothing is sent.
        // Committed => delivered at least once (duplicates possible).
    }
}
```

- `TransactionAwareAsynchronousBus` — for messages asserting committed facts (the default
  choice). Requires a transaction to join.
- `AsynchronousBus` — direct to broker, at-most-once on crash. Only for stateless
  publishers and self-healing messages (heartbeats, cache hints). Never publish a
  committed-fact message with it, and never call it inside a transaction you might roll
  back (the ghost-message trap).
- `SynchronousBus` — same-thread, same-transaction, in-process participants.

## 6. Consuming

```java
@Component
class OrderWorker {

    @Consumer(id = "order-worker")                    // id is REQUIRED: queue name + inbox key
    void on(OrderConfirmed event) {
        // at-least-once: this method MUST be idempotent.
        // Ask: would running twice with the same message change the outcome twice?
    }

    @Consumer(id = "invoice-biller", dedup = true)    // handler that cannot be idempotent
    @Transactional                                    // optional: REQUIRED joins the inbox tx
    void bill(OrderConfirmed event) {
        // exactly-once COMMITTED effects: everything synchronous, on this thread,
        // against this service's DataSource commits atomically with the dedup ledger --
        // including TransactionAwareAsynchronousBus publishes (exactly-once chaining).
        // OUTSIDE the guarantee: other threads, other DataSources, REQUIRES_NEW,
        // emails/HTTP/files.
    }
}
```

Failure policy (fixed, per delivery): first failure → one immediate retry; second →
the message parks in `<service>-<consumer-id>.dlq` with an ERROR log naming the message
id and cause. The DLQ pages a human: fix the cause, then replay. Never a loss, never a
loop.

## Guarantees cheat-sheet

| you do | you get |
|---|---|
| publish via tx-aware bus, commit | delivered at least once |
| publish via tx-aware bus, rollback | nothing delivered |
| consume (default) | at-least-once invocation — be idempotent |
| consume with `dedup = true` | exactly-once committed effects in your DB tx |
| side effects outside the tx | your responsibility (idempotency keys: use the message id) |
