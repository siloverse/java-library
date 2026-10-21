plugins {
    id("io.github.siloverse.jvm-library")
}

dependencies {
    // A real consumer of the released query-chapter libraries, not of the sibling projects.
    api(local.siloverse.pagination.spring)
    api(local.siloverse.selector.spring)
    api(local.siloverse.projector.spring)
    api(local.springdoc.webmvc.api)

    implementation(local.spring.context)
    implementation(local.spring.web)

    // OpenApiSpec is a test helper: MockMvc comes from the consumer's test classpath.
    compileOnly(local.spring.test)
    compileOnly(local.jakarta.servlet.api)

    // The test boots a real Spring Boot app and reads the spec it produces.
    testImplementation(local.assertj.core)
    testImplementation(libs.spring.boot.starter.web)
    testImplementation(local.spring.boot.starter.test)
    testImplementation(local.spring.boot.webmvc.test) // Boot 4: @AutoConfigureMockMvc lives in its own module
    testImplementation(local.json.path)
}
