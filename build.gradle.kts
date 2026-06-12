plugins {
    kotlin("jvm") version "2.3.0" apply false
    kotlin("plugin.serialization") version "2.3.0" apply false
}

group = "dev.antn"
version = "2.0.0-SNAPSHOT"

allprojects {
    group = rootProject.group
    version = rootProject.version

    repositories {
        maven(url = "https://central.sonatype.com/repository/maven-snapshots/") {
            content {
                includeModule("net.minestom", "minestom")
                includeModule("net.minestom", "testing")
            }
        }
        mavenCentral()
    }
}
