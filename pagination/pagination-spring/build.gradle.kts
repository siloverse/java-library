plugins {
    id("io.github.siloverse.jvm-library")
}

dependencies {
    api(project(":pagination:pagination-core"))

    // Pageable adapters are public API, so consumers see the Spring Data types.
    api(local.spring.data.commons)

    implementation(local.spring.context)
    implementation(local.spring.web)
    implementation(local.spring.webmvc)

    testImplementation(local.assertj.core)
    testImplementation(local.spring.test)
    testImplementation(local.jakarta.servlet.api)
    testImplementation(local.jackson.databind)
    testImplementation(local.json.path)
}
