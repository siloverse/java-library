# PROJECT CHECKPOINT — siloverse messaging library

_Last updated: 2026-08-13. This document is the arbiter: if Claude asserts something about the code that isn't here or in a paste, call it — that's drift._

**Goal:** Learn EIP by building a messaging library. Modular monolith first, microservices later. Prior failure being avoided: a `message_delivery` table that reimplemented broker logic in PostgreSQL.

**Stack:** Java 21, Gradle (Kotlin DSL), version catalog `gradle/dep.versions.toml`, Spring Framework via BOM (NO Spring Boot), SLF4J api-only in core, JUnit 5 + AssertJ (AssertJ everywhere now, core included).

**Base package:** `io.github.siloverse.messaging`

**Git state:** `main` = squash-merged `8e27571 Add SynchronousBus` (whole sync chapter, one commit). Current branch: `add-aynschronous-bus` (fresh off main). Working tree clean.

## Locked decisions (do not reopen)

- Two unrelated markers: `Event` (0..many, pub-sub), `Command` (exactly 1, fire-forget). NO shared Message supertype. `Request<R>` designed then removed (YAGNI; blueprint in git history).
- Bus names: `SynchronousBus`, `AsynchronousBus`, `TransactionAwareAsyncBus`. No common supertype — guarantee visible at injection point.
- `SynchronousBus` = participant bus: same thread, same transaction, registration order, first failure rolls back everything. Zero event consumers = legal; zero command consumers = `NoHandlerException`. Participant→sync, observer→async.
- `@Consumer(id=...)` — id REQUIRED, never derived (queue name + consumer group + inbox dedup key).
- Fail-fast tiers: construction=NPE/IAE · registration/startup=`MessagingConfigurationException` · dispatch=`NoHandlerException`. Errors name culprit AND fix; one culprit per error (chained errors OK, e.g. lazy→then→parent-class).
- Registry lifecycle: OPEN → INITIALIZING (lookups throw) → FROZEN (register throws). Armed in BFPP phase; frozen registry = future broker topology source.
- `@Consumer` must be declared on the concrete class — parent-class declarations rejected at scan time (`rejectConsumersDeclaredOnParentClasses`). Single definition of "declares consumers" = `ConsumerMethodScanner.findDeclaredConsumerMethod(Class)` (class + superclass walk, returns `Optional<Method>`); Spring eligibility check delegates to it. NO validation-chain pattern (YAGNI — fixed policy, fail-fast, not user-extensible).
- Lazy/prototype bean WITH @Consumer (own OR inherited) = startup failure; without = skipped, laziness preserved.
- messaging-spring depends on core via `api(...)` (core types are part of its public surface).
- Plain `@Configuration` (`MessagingConfiguration`, main source set), static `@Bean`s for the BFPP chain.

## Built & green

- **messaging-core** (slf4j-api only): markers · 3 bus interfaces · `MessageKind` · `ConsumerDefinition(id, messageClass, bean, method, contextParameterIndex=-1)` · `ConsumerRegistry` (3-state, cardinality, dup-id, no-partial-state) · `ConsumerMethodScanner` (id non-blank, 1 param, one marker, void return, concrete-class-only + `findDeclaredConsumerMethod` helper) · `MessageDispatcher` (InvocationTargetException unwrap triage) · `DefaultSynchronousBus` · full AssertJ unit suites.
- **messaging-spring**: `SpringConsumerScanner` (unwrap → delegate → selectInvocableMethod) · `ConsumerScanningInitializer` (BFPP arms once; SmartInitializingSingleton scans→freezes; eligibility inspects class metadata incl. superclasses, never instantiates) · `MessagingConfiguration` · integration tests green incl. @Transactional CGLIB end-to-end, @PostConstruct-publish startup failure, laziness preserved, lazy-inherited-consumer startup failure.

## Async chapter — design state (agreed, not yet coded)

**Outbox table (question a — answered):**

| column | type | job |
|---|---|---|
| `id` | bigserial | relay polling order (NOT created_at — ties) |
| `message_id` | uuid | wire identity; travels in headers; consumer inbox dedup key later |
| `message_type` | varchar | routing — picks the exchange (future `@MessageName` wire name, not class name) |
| `payload` | bytea | sealed bytes; relay NEVER deserializes |
| `headers` | jsonb | wire metadata: content-type, schema version, trace context |
| `created_at` | timestamptz | diagnostics only |
| `published_at` | timestamptz null | stamped by relay after broker ack; NULL = pending |

Relay mapping: `basicPublish(exchangeFor(message_type), message_type, propsFrom(headers, message_id), payload)` — every argument a column read.

**Forbidden columns (question b — answered):** `consumer_id`/recipient (broker bindings' job), per-consumer delivery status (broker acks), `retry_count`/`next_retry_at` (broker redelivery + DLQ), `processed`/`handled_at` (consumer's inbox table, consumer's DB), extracted business fields (payload is sealed). Litmus: outbox knowledge ends at broker ack; anything after = rebuilding `message_delivery`.

**Row fate (question c — decided):** relay ALWAYS stamps `published_at`. Hard delete / archive = future standalone retention job consuming the stamp (janitor cron), NOT a relay concern. NO strategy interface (litigated twice; the stamp is the extension point).

**RabbitMQ model (lesson locked):** publishers target EXCHANGES, never queues. Queue-per-consumer-id `<microservice>-<consumer-id>`, declared + bound by the CONSUMING side at startup from its frozen registry. Relay publishes once; broker fans out. Relay is kind-agnostic — event/command difference lives in topology declaration at boot, not in the publish path.

## ⛔ OPEN GATE — first item tomorrow

**The crash story (question c, part 2) — asked 4×, unanswered. No `TransactionAwareAsyncBus` code until answered** (the answer is the delivery contract the javadoc must promise). Scaffold:

1. Relay reads row, `basicPublish`, broker accepts.
2. Relay dies before `UPDATE ... SET published_at = now()` commits.
3. Relay restarts, runs `SELECT ... WHERE published_at IS NULL ORDER BY id`.
4. What does it see, and what does it do?
5. What does the consumer experience?
6. So: what delivery guarantee does the bus offer, and what must every consumer survive?

## Then / next

`TransactionAwareAsyncBus` impl (always-outbox, joins caller's tx) → relay → RabbitMQ adapter (topology from frozen registry at startup). Envelope/`@MessageName`/metadata designs exist in conversation history — not yet coded.

**Parked:** after-commit dispatch · `MessageContext` (param index reserved) · `Request` revival · NATS adapter #3 · batch consumers · inbox/idempotency (now motivated: at-least-once ⇒ dedup by `message_id`).

## Working style

User types in IDE (Claude applies changes only on explicit ask); why-before-what; deliberate red tests with predicted failure reasons stated BEFORE running; design questions before answers; Claude nags unanswered deliverables; one logical change per commit.
