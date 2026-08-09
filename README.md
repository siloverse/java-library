# java-library

Shared Java libraries for Siloverse services. One Gradle build, one module per library, each
published independently to GitHub Packages.

## Modules

| Module | Artifact | Description |
| --- | --- | --- |
| [`messaging`](messaging/README.md) | `io.github.siloverse.java-library:messaging` | In-process commands and events for Spring Boot, with a durable asynchronous transport that survives a JVM crash — no external broker. |

## Requirements

- **JDK 21** — the build targets a Java 21 toolchain. A newer JDK can run Gradle itself, but a
  Java 21 toolchain has to be resolvable.
- **Docker** — the `messaging` integration tests run against real PostgreSQL through Testcontainers.
- **GitHub Packages credentials** — the build plugins and the shared version catalog come from a
  private repository. Set `gpr.user` / `gpr.key` in `~/.gradle/gradle.properties`, or the
  `GITHUB_ACTOR` / `GITHUB_TOKEN` environment variables.

## Building

```bash
./gradlew build                    # compile, test and package everything
./gradlew test                     # tests only
./gradlew :messaging:test          # one module
./gradlew :messaging:build

./gradlew publishToMavenLocal      # install into ~/.m2 for local consumers
```

Configuration cache, build cache and parallel execution are on by default (see
[`gradle.properties`](gradle.properties)).

## Build conventions

Modules stay deliberately thin. A build file only declares its plugin, version and dependencies:

```kotlin
plugins {
    id("io.github.siloverse.jvm-library") version "1.2.0"
}

version = "1.0.0"

dependencies {
    implementation(libs.spring.boot.starter.data.jpa)
}
```

Everything else comes from shared build logic:

- **`io.github.siloverse.jvm-library`** applies the Java 21 toolchain, JUnit Platform with
  `junit-jupiter`, sources and javadoc jars, and Maven publishing. Setting `siloverse.kotlin=true`
  in `gradle.properties` (as this repo does) also enables Kotlin and the `src/main/kotlin` source
  set.
- **`libs`** is the shared version catalog `io.github.siloverse.gradle:version-catalog`, and the
  `io.github.siloverse.gradle:platform` BOM pins the versions, so dependencies are declared without
  version numbers. Current stack: Spring Boot 4.1, Jackson 2.22, JUnit 6.1, Testcontainers 2.0.
- **`local`** is the per-repository catalog in [`gradle/dep.versions.toml`](gradle/dep.versions.toml),
  for anything the shared catalog does not cover.

Prefer adding a dependency to the shared catalog over pinning a version in a module.

## Adding a module

1. Create `<module>/build.gradle.kts` following the shape above.
2. Add `include("<module>")` to [`settings.gradle.kts`](settings.gradle.kts).
3. Add a `<module>/README.md` and list the module in the table above.

## Publishing

Each module publishes a `mavenJava` publication with sources and javadoc jars to the
`GitHubPackages` repository. The target repository comes from the `siloverse.publish.repository`
property, falling back to `GITHUB_REPOSITORY` in CI.

```bash
./gradlew :messaging:publish
```

## Repository layout

```
.
├── gradle/
│   ├── dep.versions.toml       repository-local version catalog ("local")
│   └── wrapper/
├── messaging/                  in-process messaging library
│   ├── src/main/java
│   ├── src/test/java
│   └── README.md
├── gradle.properties           group, toolchain switches, Gradle features
└── settings.gradle.kts         module list, plugin and dependency repositories
```

## License

[Apache License 2.0](LICENSE).
