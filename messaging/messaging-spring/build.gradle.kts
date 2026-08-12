plugins {
    alias { local.plugins.siloverse.jvm.library }
}

version = "1.0.0"

dependencies {
    api(project(":messaging:messaging-core"))

    api(local.slf4j.api)

    implementation(local.spring.aop)
    implementation(local.spring.beans)
    implementation(local.spring.context)

    testImplementation(local.assertj.core)
    testImplementation(local.jakarta.annotation.api)
    testImplementation(local.spring.tx)
    testRuntimeOnly(local.slf4j.simple)
}
