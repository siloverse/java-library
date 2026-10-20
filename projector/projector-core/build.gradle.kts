plugins {
    id("io.github.siloverse.jvm-library")
}

dependencies {
    // Reads @Projectable fields and rebuilds immutable DTOs through their primary constructor.
    implementation(kotlin("reflect"))

    testImplementation(local.assertj.core)
}
