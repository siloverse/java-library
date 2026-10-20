plugins {
    id("io.github.siloverse.jvm-library")
}

dependencies {
    api(project(":selector:selector-core"))

    implementation(local.spring.context)
    implementation(local.spring.web)
    implementation(local.spring.webmvc)

    testImplementation(local.assertj.core)
    testImplementation(local.spring.test)
    testImplementation(local.jakarta.servlet.api)
    testImplementation(local.jackson.databind)
    testImplementation(local.json.path)
}
