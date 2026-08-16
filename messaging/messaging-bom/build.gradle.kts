plugins {
    alias(local.plugins.siloverse.platform)
}

// Constraints reference every module via project(...) so a module silently
// dropped from the build fails loudly here.
dependencies {
    constraints {
        api(project(":messaging:messaging-core"))
        api(project(":messaging:messaging-spring"))
        api(project(":messaging:messaging-rabbitmq"))
    }
}
