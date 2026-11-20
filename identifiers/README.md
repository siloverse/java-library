# siloverse identifiers

Identifiers a service makes itself, before it talks to the database.

**The rule, in one line:** an id that leaves the service is a UUID version 7: time first, so
new rows land at the end of the index like a sequence does; random after, so nobody can guess
the next one.

## 1. Dependencies

```kotlin
dependencies {
    implementation(platform("io.github.siloverse:identifiers-bom:0.1.0"))
    implementation("io.github.siloverse:identifiers-core")
}
```

## 2. Modules

| module | depends on | contents |
|---|---|---|
| `identifiers-core` | Kotlin stdlib | `UuidV7` |

## 3. Make an id

```kotlin
val id: UUID = UuidV7.next()
```

`next(now: Instant)` takes the time as a parameter for tests.

## 4. What an id is made of

| bits | content |
|---|---|
| 48 | milliseconds since 1970 |
| 4 | version, `7` |
| 12 | random |
| 2 | variant, `10` |
| 62 | random |

The 74 random bits come from `SecureRandom`. Two ids made in the same millisecond share the
time part and are in random order relative to each other: the id gives identity and a rough
order, not a strict sequence.

PostgreSQL 18 has `uuidv7()` built in; on older versions the application makes the id, which
also lets it know the id before the insert.
