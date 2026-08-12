plugins {
    alias { local.plugins.siloverse.jvm.library }
}

version = "1.0.0"

dependencies {
    api(local.slf4j.api)

    testImplementation(local.assertj.core)

    testRuntimeOnly(local.slf4j.simple)
}
