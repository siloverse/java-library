plugins {
    id("io.github.siloverse.jvm-library")
}

dependencies {
    api(project(":selector:selector-core"))

    // The adapter's public types are JDSL's Jpql receiver and Predicatable result.
    api(local.kotlin.jdsl.dsl)
    api(local.kotlin.jdsl.query.model)


    testImplementation(local.assertj.core)

    // Proving ground: real Postgres through Spring Data JPA + the JDSL executor, no Boot.
    testImplementation(local.kotlin.jdsl.render)
    testImplementation(local.kotlin.jdsl.spring.data.jpa)
    testImplementation(local.spring.context)
    testImplementation(local.spring.jdbc)
    testImplementation(local.spring.orm)
    testImplementation(local.spring.data.jpa)
    testImplementation(local.hibernate.core)
    testImplementation(libs.testcontainers.postgresql)

    testRuntimeOnly(local.postgresql)
    testRuntimeOnly(local.slf4j.simple)
}
