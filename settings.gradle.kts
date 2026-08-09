pluginManagement {
    repositories {
        maven {
            url = uri("https://maven.pkg.github.com/siloverse/siloverse-build")
            credentials {
                username = providers.gradleProperty("gpr.user").orElse(System.getenv("GITHUB_ACTOR")).orNull
                password = providers.gradleProperty("gpr.key").orElse(System.getenv("GITHUB_TOKEN")).orNull
            }
        }
        gradlePluginPortal()
        mavenCentral()
        mavenLocal()
    }
}


dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven {
            url = uri("https://maven.pkg.github.com/siloverse/siloverse-build")
            credentials {
                username = providers.gradleProperty("gpr.user").orElse(System.getenv("GITHUB_ACTOR")).orNull
                password = providers.gradleProperty("gpr.key").orElse(System.getenv("GITHUB_TOKEN")).orNull
            }
        }
        mavenCentral()
        mavenLocal()
    }
    versionCatalogs {
        val siloverseBuildVersion =
            Regex("""(?m)^\s*siloverse-build\s*=\s*"([^"]+)"""")
                .find(file("gradle/dep.versions.toml").readText())
                ?.groupValues
                ?.get(1)
                ?: error("Missing 'siloverse-build' in gradle/dep.versions.toml")
        create("libs") {
            from("io.github.siloverse.gradle:version-catalog:$siloverseBuildVersion")
        }
        create("local") {
            from(files("gradle/dep.versions.toml"))
        }
    }
}

rootProject.name = "java-library"

include("messaging")