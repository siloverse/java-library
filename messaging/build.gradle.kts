import org.gradle.api.publish.maven.tasks.PublishToMavenRepository

val libraryVersion = "0.1.0-SNAPSHOT"

subprojects {
    group = "io.github.siloverse"
    version = libraryVersion
}

val releaseGuard = tasks.register("releaseGuard") {
    description = "Refuses remote publication unless HEAD is a clean, tagged release commit on main."

    val gitStatus = providers.exec {
        commandLine("git", "status", "--porcelain")
    }.standardOutput.asText

    val tagsAtHead = providers.exec {
        commandLine("git", "tag", "--points-at", "HEAD")
    }.standardOutput.asText

    val onMain = providers.exec {
        commandLine("git", "merge-base", "--is-ancestor", "HEAD", "origin/main")
        isIgnoreExitValue = true
    }.result

    doLast {
        check(!libraryVersion.endsWith("-SNAPSHOT")) {
            "Refusing remote publish: version is $libraryVersion — snapshots go to mavenLocal only. " +
                    "Fix: release via a release PR (drop -SNAPSHOT), merge, tag, then publish from the tag checkout."
        }
        check(gitStatus.get().isBlank()) {
            "Refusing remote publish: working tree is dirty. " +
                    "Fix: commit or stash your changes — published artifacts must be reproducible from a commit."
        }
        check(tagsAtHead.get().lines().contains("messaging-v$libraryVersion")) {
            "Refusing remote publish: HEAD is not tagged messaging-v$libraryVersion. " +
                    "Fix: publish from the tag checkout — git checkout messaging-v$libraryVersion."
        }
        check(onMain.get().exitValue == 0) {
            "Refusing remote publish: HEAD is not on origin/main. " +
                    "Fix: the release commit must be merged before publishing — git fetch, then publish from the tag checkout."
        }
    }
}

subprojects {
    tasks.withType<PublishToMavenRepository>().configureEach {
        dependsOn(releaseGuard)
    }
}