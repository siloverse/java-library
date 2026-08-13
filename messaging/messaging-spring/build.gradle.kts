plugins {
    alias { local.plugins.siloverse.jvm.library }
}

version = "1.0.0"

dependencies {
    api(project(":messaging:messaging-core"))

    api(local.slf4j.api)
    api(libs.jackson.databind)

    implementation(local.spring.aop)
    implementation(local.spring.beans)
    implementation(local.spring.context)
    implementation(local.spring.jdbc)

    testImplementation(local.assertj.core)
    testImplementation(local.jakarta.annotation.api)
    testImplementation(local.spring.tx)
    testImplementation(libs.testcontainers.postgresql)

    testRuntimeOnly(local.slf4j.simple)
    testRuntimeOnly(local.postgresql)
}
