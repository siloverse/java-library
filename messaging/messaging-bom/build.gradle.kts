plugins {
    `java-platform`
    `maven-publish`
}

dependencies {
    constraints { api(project(":messaging:messaging-core"))
        api(project(":messaging:messaging-spring"))
        api(project(":messaging:messaging-rabbitmq"))
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenPlatform") {
            from(components["javaPlatform"])
        }
    }
}