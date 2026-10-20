plugins {
    id("io.github.siloverse.platform")
}

// Constraints reference every module via project(...) so a module silently
// dropped from the build fails loudly here.
dependencies {
    constraints {
        api(project(":pagination:pagination-core"))
        api(project(":pagination:pagination-spring"))
        api(project(":pagination:pagination-jdsl"))
    }
}
