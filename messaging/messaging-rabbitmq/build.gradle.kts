plugins {
    alias { local.plugins.siloverse.jvm.library }
}

version = "1.0.0"

dependencies {
    api(project(":messaging:messaging-core"))

    api(local.amqp.client)
    api(local.slf4j.api)

    testImplementation(local.assertj.core)
    testImplementation(libs.testcontainers.rabbitmq)

    testRuntimeOnly(local.slf4j.simple)
}
