# PROJECT CHECKPOINT — siloverse messaging library

_Last updated: 2026-08-13. This document is the arbiter: if Claude asserts something about the code that isn't here or in a paste, call it — that's drift._

**Goal:** Learn EIP by building a messaging library. Modular monolith first, microservices later. Prior failure being avoided: a `message_delivery` table that reimplemented broker logic in PostgreSQL.

**Stack:** Java 21, Gradle (Kotlin DSL), version catalog `gradle/dep.versions.toml`, Spring Framework via BOM (NO Spring Boot), SLF4J api-only in core, JUnit 5 + AssertJ (AssertJ everywhere now, core included).

**Base package:** `io.github.siloverse.messaging`

**Git state:** `main` = squash-merged `8e27571 Add SynchronousBus` (whole sync chapter, one commit). Current branch: `add-aynschronous-bus` (fresh off main). Working tree clean.

## Locked decisions (do not reopen)

- Two unrelated markers: `Event` (0..many, pub-sub), `Command` (exactly 1, fire-forget). NO shared Message supertype. `Request<R>` designed then removed (YAGNI; blueprint in git history).
- Bus names: `SynchronousBus`, `AsynchronousBus`, `TransactionAwareAsynchronousBus`. No common supertype — guarantee visible at injection point.
- `SynchronousBus` = participant bus: same thread, same transaction, registration order, first failure rolls back everything. Zero event consumers = legal; zero command consumers = `NoHandlerException`. Participant→sync, observer→async.
- `@Consumer(id=...)` — id REQUIRED, never derived (queue name + consumer group + inbox dedup key).
- Fail-fast tiers: construction=NPE/IAE · registration/startup=`MessagingConfigurationException` · dispatch=`NoHandlerException`. Errors name culprit AND fix; one culprit per error (chained errors OK, e.g. lazy→then→parent-class).
- Registry lifecycle: OPEN → INITIALIZING (lookups throw) → FROZEN (register throws). Armed in BFPP phase; frozen registry = future broker topology source.
- `@Consumer` must be declared on the concrete class — parent-class declarations rejected at scan time (`rejectConsumersDeclaredOnParentClasses`). Single definition of "declares consumers" = `ConsumerMethodScanner.findDeclaredConsumerMethod(Class)` (class + superclass walk, returns `Optional<Method>`); Spring eligibility check delegates to it. NO validation-chain pattern (YAGNI — fixed policy, fail-fast, not user-extensible).
- Lazy/prototype bean WITH @Consumer (own OR inherited) = startup failure; without = skipped, laziness preserved.
- messaging-spring depends on core via `api(...)` (core types are part of its public surface).
- Plain `@Configuration` (`MessagingConfiguration`, main source set), static `@Bean`s for the BFPP chain.

## Built & green

- **messaging-core** (slf4j-api only): markers · 3 bus interfaces · `MessageKind` · `ConsumerDefinition(id, messageClass, bean, method, contextParameterIndex=-1)` · `ConsumerRegistry` (3-state, cardinality, dup-id, no-partial-state) · `ConsumerMethodScanner` (id non-blank, 1 param, one marker, void return, concrete-class-only + `findDeclaredConsumerMethod` helper) · `MessageDispatcher` (InvocationTargetException unwrap triage) · `DefaultSynchronousBus` · full AssertJ unit suites (48 tests green).
- **messaging-core async chapter (2026-08-13):** `naming/MessageNameRegistry` (builder, freeze, dup-class + dup-wire-name + blank rejected, culprit-and-fix messages) · `transport/Envelope` (record; nulls rejected at construction, `Map.copyOf` + `clone()` in/out defensive copies; equality-by-reference on payload accepted as YAGNI) · ports `transport/MessageTransport.send(Envelope)` + `transport/PayloadSerializer` (serialize + contentType) · `dispatch/DefaultAsynchronousBus` (mints UUID per dispatch, registry name, content-type header, shared private `dispatch(Object)`; unregistered class throws before transport). Tests use nested fakes in `DefaultAsynchronousBusTest` (FakePayloadSerializer = toString bytes, RecordingTransport) — promote to fixtures on second use.
- **messaging-spring**: `SpringConsumerScanner` (unwrap → delegate → selectInvocableMethod) · `ConsumerScanningInitializer` (BFPP arms once; SmartInitializingSingleton scans→freezes; eligibility inspects class metadata incl. superclasses, never instantiates) · `MessagingConfiguration` · integration tests green incl. @Transactional CGLIB end-to-end, @PostConstruct-publish startup failure, laziness preserved, lazy-inherited-consumer startup failure.
- **messaging-spring outbox (2026-08-14):** `outbox/JdbcOutboxWriter(JdbcTemplate, ObjectMapper)` — inserts the four bus-authored columns, `CAST(? AS jsonb)` for headers, `JsonProcessingException` wrapped in `MessagingException` (base type: not config, not dispatch) with messageId/type culprit. DDL shipped as resource `io/github/siloverse/messaging/outbox-postgres.sql` (table `messaging_outbox`; no UNIQUE on message_id — duplicates impossible by construction; contrast future inbox where UNIQUE IS the dedup mechanism). Testcontainers-Postgres integration tests apply the shipped DDL (file can't rot) and prove: commit keeps row with published_at NULL, ROLLBACK LEAVES NO ROW, no-tx append still writes. Decisions: real Postgres over H2 (jsonb/bytea in write path from day one — a lookalike DB is a mock in costume); DDL-as-resource, NO Flyway/Liquibase dependency (schema ownership belongs to the app; test-applies-shipped-file keeps it honest); Jackson as `api` dep (ObjectMapper in public constructor — same rule as core-via-api), ObjectMapper injected never owned (library inherits app config; safe because headers are a flat string map — fence in javadoc); jsonb lesson: Postgres stores parsed structure and re-renders text its own way — assert data, not formatting.

## Async chapter — design state (agreed, not yet coded)

**Outbox table (question a — answered):**

| column | type | job |
|---|---|---|
| `id` | bigserial | relay polling order (NOT created_at — ties) |
| `message_id` | uuid | wire identity; travels in headers; consumer inbox dedup key later |
| `message_type` | varchar | routing — picks the exchange (from `MessageNameRegistry`, never the class name) |
| `payload` | bytea | sealed bytes; relay NEVER deserializes |
| `headers` | jsonb | wire metadata: content-type, schema version, trace context |
| `created_at` | timestamptz | diagnostics only |
| `published_at` | timestamptz null | stamped by relay after broker ack; NULL = pending |

Relay mapping: `basicPublish(exchangeFor(message_type), message_type, propsFrom(headers, message_id), payload)` — every argument a column read.

**Forbidden columns (question b — answered):** `consumer_id`/recipient (broker bindings' job), per-consumer delivery status (broker acks), `retry_count`/`next_retry_at` (broker redelivery + DLQ), `processed`/`handled_at` (consumer's inbox table, consumer's DB), extracted business fields (payload is sealed). Litmus: outbox knowledge ends at broker ack; anything after = rebuilding `message_delivery`.

**Row fate (question c — decided):** relay ALWAYS stamps `published_at`. Hard delete / archive = future standalone retention job consuming the stamp (janitor cron), NOT a relay concern. NO strategy interface (litigated twice; the stamp is the extension point).

**RabbitMQ model (lesson locked):** publishers target EXCHANGES, never queues. Queue-per-consumer-id `<microservice>-<consumer-id>`, declared + bound by the CONSUMING side at startup from its frozen registry. Relay publishes once; broker fans out. Relay is kind-agnostic — event/command difference lives in topology declaration at boot, not in the publish path.

## ✅ Gate closed — crash story answered (2026-08-13)

Unstamped row after relay restart is ambiguous (never-published vs published-but-unstamped look identical; relay can't ask the broker). Relay REPUBLISHES — duplicate beats loss. Consumer sees byte-identical duplicates, same `message_id` = dedup key.

**`TransactionAwareAsynchronousBus` contract (javadoc must promise):** committed tx ⇒ delivered **at least once**; duplicates possible; consumers must be idempotent; rolled-back tx delivers nothing (outbox row rolls back with business data). Exactly-once delivery = myth without 2PC; real thing is at-least-once + idempotent consumption.

**`AsynchronousBus` rationale (decided):** direct-to-broker, NO outbox/relay/DB in path. Exists for (1) stateless publishers (no tx to join — outbox structurally unusable), (2) self-healing messages (cache hints, telemetry, heartbeats) not worth the outbox premium. Litmus: message asserts a DB-committed fact → tx-aware bus; otherwise → async bus. Contract: **at-most-once** wrt crashes (may await broker ack, throw on nack; crash before ack = silent loss). Ghost-message trap: publish inside a rolled-back tx announces a state that never happened — javadoc warns.

**Message naming — LOCKED, do not reopen (litigated 5×, 2026-08-13):** wire name comes from a `MessageNameRegistry` (core class: builder, `register(Class, String)`, `freeze()`, `nameOf(Class)`). Each service ships a static registry INSIDE its messages/contract jar (e.g. `OrderSiloMessages.names()`) so publisher and consumers read the same map — classes and names travel together. Name convention: `<service>.<message>` lowercase-dashed (e.g. `order-silo.order-created`) — a style rule for humans, never computed. REJECTED: `@MessageName` annotation (user preference; delete the typed file), class-name derivation (dash illegal in packages, rename = silent loss — failed twice in user's own examples), instance-level MessageProvider naming (no instance exists at startup binding time). Wire identity must be static per type, resolvable from `Class` alone. Failures: unregistered class → `MessagingConfigurationException` at first publish (publisher) / at startup binding (consumer); blank name or duplicate class → builder throws. Service module layout agreed: `silo` (app) / `messages` (contract jar) / `web` (DTOs); dashes legal in Maven coordinates, not in Java packages. "MessageProvider" = app-side factory building message objects — user's own code, no library involvement; bus signature stays `publish(Event)`/`send(Command)`.

**Ghost-trap enforcement (decided):** NO runtime tx detection — trap is semantic (message meaning), not mechanical (tx active), so a runtime guard false-positives on legit uses (e.g. metrics inside `@Transactional`) and would drag spring-tx into the publish path. Enforcement = javadoc + code review; static-analysis suggestion parked.

## Then / next

**Relay** (poll `WHERE published_at IS NULL ORDER BY id`, publish, stamp — the crash story becomes code) → RabbitMQ adapter (`MessageTransport` impl + topology from frozen registries at startup) → Spring wiring for the two new buses in `MessagingConfiguration`. Relay chapter opens with design questions: where does the relay live (module?), what drives its loop, and what does "broker ack" mean concretely.

**Parked:** after-commit dispatch · `MessageContext` (param index reserved) · `Request` revival · NATS adapter #3 · batch consumers · inbox/idempotency (now motivated: at-least-once ⇒ dedup by `message_id`) · static-analysis warning for `AsynchronousBus` inside `@Transactional` scope.

## Working style

User types in IDE (Claude applies changes only on explicit ask); why-before-what; deliberate red tests with predicted failure reasons stated BEFORE running; design questions before answers; Claude nags unanswered deliverables; one logical change per commit.
