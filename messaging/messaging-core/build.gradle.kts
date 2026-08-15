plugins {
    alias { local.plugins.siloverse.jvm.library }
}

dependencies {
    api(local.slf4j.api)

    testImplementation(local.assertj.core)

    testRuntimeOnly(local.slf4j.simple)
}
