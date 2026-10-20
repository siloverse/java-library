# java-library

Container repository for the siloverse Java/Kotlin libraries. Each top-level directory
is one library with its own version, release cadence and tags (`<library>-vX.Y.Z`).
Libraries share one repository until one of them needs its own ownership or cadence.

| Library | What it is | Released | Consumers |
| --- | --- | --- | --- |
| `messaging` | Event/command messaging for silo services: transactional outbox + relay, RabbitMQ transport with publisher confirms, typed consumers, opt-in inbox dedup, trace context carried through the broker, constant-delay retry tiers (`@RabbitPolicy`). | `1.0.12` on GitHub Packages. | user-silo (publisher), notification-silo (consumer) |
| `pagination` | Kotlin. Offset, slice and keyset pagination as one core model (stdlib only) plus a Spring adapter: query-param resolver, policy with a 400 mapping, flat response models, `Pageable` and keyset-scroll adapters. Keyset behaviour proven against Postgres 17. | `0.1.0` on GitHub Packages. | booking-silo listings (planned) |

## Working with the repo

- Build everything: `./gradlew build` (integration tests use Testcontainers, Docker required).
- Snapshot-test a library: `cd messaging && ../gradlew publishToMavenLocal`. A consumer
  then adds `mavenLocal()` and pins the `-SNAPSHOT`; both are temporary scaffolding that
  never ships on a consumer's main branch.
- Release a library from a PR branch:
  `./gradlew :<library>:release -PreleaseVersion=x.y.z` (for example `:messaging:release` or `:pagination:release`). The task validates, rewrites the
  version line, builds, commits and tags, publishes to GitHub Packages, commits the next
  `-SNAPSHOT` and pushes branch and tag atomically. Merge the PR **with a merge commit**;
  a rebase-merge rewrites SHAs and orphans the pushed tag. Publishing needs a `gpr.key`
  PAT with `write:packages`.
- Build conventions and the release machinery come from
  [siloverse-build](https://github.com/siloverse/siloverse-build); the version is pinned
  once in `settings.gradle.kts` via `io.github.siloverse.parent`.

## Documents

- `messaging/README.md`: the consumer guide (contract jars, schema, wiring, consumers).
- `pagination/README.md`: the three kinds of page, keyset rules, the Spring wiring, what was proven on Postgres.
- `CHECKPOINT.md`: the engineering journal and the arbiter of what is decided and built.
