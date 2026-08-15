plugins {
    `java-platform`
    `maven-publish`
}

dependencies {
    constraints {
        api(project(":messaging:messaging-core"))
        api(project(":messaging:messaging-spring"))
        api(project(":messaging:messaging-rabbitmq"))
    }
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri(
                "https://maven.pkg.github.com/" +
                        providers.gradleProperty("siloverse.publish.repository")
                            .orElse(providers.environmentVariable("GITHUB_REPOSITORY"))
                            .getOrElse("siloverse/java-library")
            )
            credentials {
                username = providers.gradleProperty("gpr.user")
                    .orElse(providers.environmentVariable("GITHUB_ACTOR")).orNull
                password = providers.gradleProperty("gpr.key")
                    .orElse(providers.environmentVariable("GITHUB_TOKEN")).orNull
            }
        }
    }
    publications {
        create<MavenPublication>("mavenPlatform") {
            from(components["javaPlatform"])
        }
    }
}