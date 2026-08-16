import org.gradle.api.publish.maven.tasks.PublishToMavenRepository

// Single source of the library version. Only the release task rewrites this
// line: a bare x.y.z exists exactly on the release commit it tags; every other
// commit carries the next -SNAPSHOT.
val libraryVersion = "1.0.3-SNAPSHOT"

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
                    "Fix: a bare version only derives from a release tag — tag the release commit on main " +
                    "(git tag messaging-vX.Y.Z) and push the tag; CI publishes."
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