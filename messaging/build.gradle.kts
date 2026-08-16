plugins {
    alias(local.plugins.siloverse.library.release)
    alias(local.plugins.siloverse.jvm.library) apply false
    alias(local.plugins.siloverse.platform) apply false
}
group = "io.github.siloverse"
version = "1.0.8"
