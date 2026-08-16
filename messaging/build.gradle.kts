import org.gradle.api.publish.maven.tasks.PublishToMavenRepository

plugins {
    alias(local.plugins.axion.release)
}

// Version is derived from git, never written in a file: HEAD exactly on a
// messaging-vX.Y.Z tag with a clean tree = bare X.Y.Z; anything else = next
// patch + -SNAPSHOT. Releasing = tagging main and pushing the tag.
scmVersion {
    tag {
        prefix.set("messaging-v")
        versionSeparator.set("")
    }
    // No branch-name decoration: snapshots read the same from any branch, and
    // CI's detached-HEAD tag checkout must derive the bare release version.
    versionCreator("simple")
}

val libraryVersion = scmVersion.version

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