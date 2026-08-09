plugins {
    alias { local.plugins.siloverse.jvm.library }
}

version = "1.0.0"

dependencies {
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)

    compileOnly(local.spring.boot.jackson)

    annotationProcessor(local.spring.boot.configuration.processor)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.bundles.testcontainers)
    testRuntimeOnly("org.postgresql:postgresql")
}
