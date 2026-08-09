# messaging

An in-process messaging library for Java and Spring Boot: commands, events, `@Consumer` methods,
and a durable asynchronous transport that survives a JVM crash — without an external broker.

The asynchronous transport is in-process, but the **database is the queue**. Messages are written in
the same transaction as the business change that caused them, and a poller feeds them to a worker
pool afterwards. Nothing lives only in memory.

## Getting started

```kotlin
dependencies {
    implementation("io.github.siloverse.java-library:messaging:1.0.0")
}
```

Define messages, publish them, consume them:

```java
public record ConfirmOrder(UUID orderId) implements Command {}
public record OrderConfirmed(UUID orderId) implements Event {}

@Service
public class OrderService {

    private final EventBus eventBus;

    public OrderService(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    @Transactional
    public void confirm(UUID orderId) {
        // change business state
        eventBus.publish(MessageProvider.of(new OrderConfirmed(orderId)));
    }
}

@Component
public class OrderConsumers {

    @Consumer
    public void sendEmail(OrderConfirmed event) { }

    @Consumer
    public void updateAnalytics(OrderConfirmed event) { }
}
```

Everything else is auto-configured. Apply
[`schema-postgresql.sql`](src/main/resources/io/github/siloverse/messaging/schema-postgresql.sql)
and switch the buses to a durable mode to make delivery asynchronous:

```properties
messaging.event.mode=transactional_async
messaging.command.mode=transactional_async
```

## Architecture

```
send() / publish()
        │
        ▼
  MessageProvider.provide()          ← always on the caller's thread
        │
        ├── SYNC ──────────────► Command/EventDispatcher ──► @Consumer   (same thread, same transaction)
        │
        └── (TRANSACTIONAL_)ASYNC
                │
                ▼
        DurableMessageStore              INSERT INTO messages
                │                        INSERT INTO message_deliveries  (one per consumer)
                ▼
             COMMIT                      ← together with the caller's business changes
                                ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─
                ▼
          MessagePoller                  claim with FOR UPDATE SKIP LOCKED
                │
                ▼
           TaskExecutor                  execution mechanism, never the queue
                │
                ▼
         MessageProcessor                own transaction per delivery
                │
                ▼
             @Consumer
```

The pieces are deliberately small and independent:

| Concern | Type |
| --- | --- |
| Public API | `api.Message`, `Command`, `Event`, `MessageProvider`, `CommandBus`, `EventBus` |
| Consumer discovery | `consumer.ConsumerScanner` → `ConsumerRegistry` → `ConsumerInvoker` |
| Consumer identity | `consumer.ConsumerIdStrategy` (default: bean + class + method + parameter type) |
| Dispatch | `dispatch.CommandDispatcher`, `dispatch.EventDispatcher` |
| Durable write | `persistence.DurableMessageStore` (JPA implementation) |
| Claiming | `persistence.DeliveryClaimStrategy` (`SKIP LOCKED` implementation) |
| Background delivery | `async.MessagePoller` → `async.MessageProcessor` |
| Serialization | `serialization.MessageSerializer` (Jackson implementation) |

### Package structure

```
io.github.siloverse.messaging
├── api            Message, Command, Event, MessageProvider, CommandBus, EventBus, MessageKind
├── annotation     @Consumer
├── consumer       ConsumerDescriptor, ConsumerIdStrategy, DefaultConsumerIdStrategy,
│                  ConsumerScanner, ConsumerRegistry, ConsumerInvoker
├── dispatch       MessageDispatcher, CommandDispatcher, EventDispatcher
├── sync           SynchronousCommandBus, SynchronousEventBus
├── async          AsynchronousCommandBus, AsynchronousEventBus, MessagePoller, MessageProcessor
├── persistence    DurableMessageStore, JpaDurableMessageStore,
│   │              DeliveryClaimStrategy, SkipLockedDeliveryClaimStrategy
│   ├── entity     StoredMessage, MessageDelivery, DeliveryStatus
│   └── repository MessageRepository, MessageDeliveryRepository
├── serialization  MessageSerializer, JacksonMessageSerializer, SerializedMessage
├── transaction    TransactionAwareAsynchronousCommandBus, TransactionAwareAsynchronousEventBus
├── config         MessagingProperties, MessagingAutoConfiguration, MessagingEntityRegistrar
└── exception      MessagingException and friends
```

## Bus modes

`CommandBus` and `EventBus` are configured independently. Exactly one implementation of each is
registered, so injection is never ambiguous.

| Mode | Behaviour | Caller transaction |
| --- | --- | --- |
| `sync` (default) | Dispatch inline, no database involved | Joins whatever the caller has |
| `async` | Store durably, deliver later | Joins one, or opens one (`REQUIRED`) |
| `transactional_async` | Store durably, deliver later | **Required** (`MANDATORY`) |

```properties
messaging.command.mode=sync                 # sync | async | transactional_async
messaging.event.mode=sync

messaging.async.enabled=true                # durable half of the library
messaging.async.poller-enabled=true         # set false to drive MessagePoller yourself
messaging.async.poll-interval=250ms
messaging.async.initial-delay=1s
messaging.async.batch-size=100
messaging.async.max-attempts=5
messaging.async.retry-delay=5s
messaging.async.lock-timeout=5m
messaging.async.worker-count=4
messaging.async.queue-capacity=1000
messaging.async.shutdown-timeout=30s

messaging.schema.initialize=false           # true creates the tables on startup
messaging.schema.location=classpath:io/github/siloverse/messaging/schema-postgresql.sql
```

## Database schema

```sql
CREATE TABLE messages
(
    id           UUID         NOT NULL PRIMARY KEY,
    message_type VARCHAR(500) NOT NULL,   -- fully qualified class name
    message_kind VARCHAR(20)  NOT NULL,   -- COMMAND | EVENT
    payload      TEXT         NOT NULL,   -- JSON
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE message_deliveries
(
    id           UUID         NOT NULL PRIMARY KEY,
    message_id   UUID         NOT NULL REFERENCES messages (id),
    consumer_id  VARCHAR(500) NOT NULL,   -- stable consumer identity
    status       VARCHAR(20)  NOT NULL,   -- PENDING | PROCESSING | PROCESSED | FAILED
    attempts     INTEGER      NOT NULL DEFAULT 0,
    available_at TIMESTAMP WITH TIME ZONE NOT NULL,
    locked_at    TIMESTAMP WITH TIME ZONE,
    processed_at TIMESTAMP WITH TIME ZONE,
    last_error   TEXT,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_message_deliveries_claim
    ON message_deliveries (available_at, created_at) WHERE status = 'PENDING';
CREATE INDEX idx_message_deliveries_locked
    ON message_deliveries (locked_at) WHERE status = 'PROCESSING';
CREATE INDEX idx_message_deliveries_message
    ON message_deliveries (message_id);
```

The message row is immutable. All mutable per consumer state lives on the delivery rows, so an
event with three consumers is **one** message and **three** independently retried deliveries; a
command is one message and one delivery.

Both indexes are partial because they only ever serve one status. The claim index matches the claim
query exactly (`status = 'PENDING' AND available_at <= now`, ordered by `available_at, created_at`),
and the lock index matches stale lock recovery (`status = 'PROCESSING' AND locked_at < cutoff`).

The project has no migration tool, so the schema ships as a SQL script. Apply it through Flyway or
Liquibase in production; `messaging.schema.initialize=true` runs it on startup for tests and local
development. The script is idempotent.

## Transaction semantics

The transaction aware buses run with `@Transactional(propagation = MANDATORY)`. The message insert
is part of the caller's transaction, not an `afterCommit` callback:

```
BEGIN
  UPDATE orders ...
  INSERT INTO messages ...
  INSERT INTO message_deliveries ...
COMMIT          -- both, or neither
```

There is deliberately no `REQUIRES_NEW` and no `afterCommit` publishing. Both would open a window in
which the business change is committed but the message is not — exactly the failure this design
exists to prevent. Publishing without a transaction fails loudly with
`IllegalTransactionStateException` instead of silently degrading; `JpaDurableMessageStore` asserts
the same invariant a second time, so a hand-wired store cannot bypass it either.

Downstream of the commit, each stage owns its transaction:

- **Claiming** runs in its own transaction. `SELECT ... FOR UPDATE SKIP LOCKED` picks eligible rows,
  an `UPDATE` flips them to `PROCESSING` and increments `attempts`, and the commit publishes the
  claim. Because claimed rows no longer match the eligibility predicate, they stay invisible to other
  instances after the row locks are released.
- **Processing** runs in its own transaction per delivery, so whatever the consumer writes commits
  together with the delivery being marked `PROCESSED`. If the consumer throws, that transaction is
  rolled back — undoing the consumer's partial writes — and the failure is recorded in a second,
  independent transaction so the attempt count and error survive the rollback.

## Delivery guarantee: at-least-once

**This library provides at-least-once delivery. It is not exactly-once, and does not pretend to be.**
Consumers must tolerate running twice for the same message. There is no generic idempotency support
in this version.

A consumer can run more than once when:

- the consumer succeeds but the processing transaction fails to commit — the delivery stays
  `PROCESSING`, its lock expires, and it is retried;
- the JVM dies after the consumer has done its work but before the commit;
- a delivery takes longer than `messaging.async.lock-timeout`, is treated as abandoned, and is
  reclaimed while the original worker is still running.

Messages are never lost, because the durable write commits with the business transaction and no
state that matters lives only in memory.

### Crash recovery

| When the crash happens | Outcome |
| --- | --- |
| Before the business transaction commits | Nothing persisted, business change rolled back. Correct. |
| After the message is committed, before processing | The `PENDING` delivery is still there and is processed after restart. |
| While processing a delivery | The delivery is stuck in `PROCESSING`. Once `locked_at` is older than `lock-timeout`, the poller reschedules it — or fails it if it has no attempts left. |

### Retries

A failing consumer increments `attempts`, stores the stack trace in `last_error` and moves
`available_at` forward by `retry-delay`. After `max-attempts` the delivery becomes `FAILED` and is
never claimed again. Failures that retrying cannot fix — an unreadable payload, a consumer that no
longer exists — skip the retries and fail immediately. There is no external dead letter queue: failed
deliveries stay in the table with their error, queryable by the application.

## Consumers

```java
@Consumer                 // order() controls sequence among the consumers of one event
public void sendEmail(OrderConfirmed event) { }
```

Validated at startup, failing the context when broken: exactly one argument, the argument implements
`Command` or `Event` (exactly one of the two, and a concrete type), `void` return, non-static, and
declared on a Spring bean. A command type with two consumers fails startup as well.

Consumers are matched on the **concrete** message type; a consumer declared for a supertype does not
receive subtypes. Events may have zero consumers.

Consumer identity is `beanName#DeclaringClass#method(ParameterType)`, computed by
`ConsumerIdStrategy` so the scheme can evolve. It is stored on every delivery, which is how a
delivery written yesterday finds its consumer after a restart. Renaming a consumer method orphans
its pending deliveries: they fail permanently with a clear error rather than being retried forever.

## Design decisions

- **The database is the queue, `TaskExecutor` is only the execution mechanism.** Submitting to an
  executor would lose queued work on a crash, so nothing is handed to a worker before it is committed.
- **Two tables, not one.** A single `processed` flag on the message cannot express three consumers
  at three different retry counts.
- **`SKIP LOCKED` for claiming, isolated in one class.** It is the only dialect specific SQL in the
  library and requires PostgreSQL 9.5+, MySQL 8+, MariaDB 10.6+ or Oracle. Replace the
  `DeliveryClaimStrategy` bean for other databases.
- **`attempts` is incremented at claim time, not at failure time.** A message that kills the JVM
  would otherwise never exhaust its attempts and would be retried forever.
- **An event with no consumers is stored with zero deliveries.** Publishing is not an error, and the
  message table is append-only anyway, so a consumerless event is no more permanent than a processed
  one. It also keeps a record that the event happened, and starts being delivered as soon as a
  consumer is added in a later release.
- **Exactly one `CommandBus` and one `EventBus` bean.** Registering every implementation would make
  injection ambiguous; the mode properties pick one and applications can declare their own bean to
  override it.
- **The worker pool is a non-default bean candidate.** Otherwise Spring Boot's
  `@ConditionalOnMissingBean(Executor.class)` would see it and silently stop creating the
  application's own `applicationTaskExecutor`.
- **Entity registration is additive.** Registering entity scan packages replaces Boot's default, so
  the library re-adds the application's auto-configuration packages; the auto-configuration is
  ordered after `DataJpaRepositoriesAutoConfiguration` so the application's own repositories are
  still scanned.

## Testing

75 tests, of which the durable ones run against real PostgreSQL through Testcontainers (Docker
required). No mocks stand in for persistence or concurrency.

```bash
./gradlew :messaging:test          # whole suite
./gradlew :messaging:build         # plus javadoc and packaging
```

| Area | Tests |
| --- | --- |
| `MessageProviderTest` | provider returns the message, lambdas work, evaluated once per call at publish time |
| `ConsumerScannerTest` | discovery, ordering, stable ids, and rejection of every invalid shape |
| `SynchronousBusTest` | one consumer per command, all consumers per event, ordering, zero consumers, failure propagation |
| `JacksonMessageSerializerTest` | round trip to the concrete type, unknown/non-message types, unreadable payloads |
| `MessageDeliveryTest` | delivery state transitions |
| `MessagingAutoConfigurationTest` | defaults, missing database, bean overriding, startup failure on duplicate command consumers |
| `DurableCommandTest` | one message and one delivery, not dispatched inline, processed once |
| `DurableEventTest` | one message and two deliveries, both consumers run, zero-consumer events |
| `RetryTest` | attempts increase, `available_at` moves, `FAILED` after the limit, recovery on a later attempt |
| `ConsumerIsolationTest` | one consumer succeeds and stays `PROCESSED` while only the failing one is retried |
| `TransactionAwarePublishingTest` | commit persists both, rollback persists neither, publishing outside a transaction is rejected |
| `StaleLockRecoveryTest` | expired locks become claimable, fresh locks are untouched, exhausted ones fail |
| `ConcurrentClaimTest` | two concurrent pollers claim disjoint sets, claimed rows are not reclaimed |
| `BackgroundPollerTest` | end to end delivery through the real scheduled poller |
| `DurableAutoConfigurationTest` | mode selection, `applicationTaskExecutor` survives, application entities still work |

## Limitations

- At-least-once only. No idempotency support.
- `SKIP LOCKED` is required; the default claim strategy targets PostgreSQL.
- `messages` and `message_deliveries` are append-only. Purging processed rows is left to the
  application — a scheduled `DELETE` on `processed_at` is the usual answer.
- Retries use a fixed delay, not exponential backoff.
- Consumers match the concrete message type only; no supertype or interface matching.
- No broker, no metrics, no tracing, no schema registry, no dead letter queue. Failed deliveries stay
  in the table.
- Message payloads are stored as JSON keyed by class name, so renaming or moving a message class
  orphans messages that are still pending.
