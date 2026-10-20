plugins {
    id("io.github.siloverse.platform")
}

// Constraints reference every module via project(...) so a module silently
// dropped from the build fails loudly here.
dependencies {
    constraints {
        api(project(":selector:selector-core"))
        api(project(":selector:selector-jdsl"))
        api(project(":selector:selector-spring"))
    }
}
