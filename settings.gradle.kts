plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "nebula"

include("nebula-daemon")
include("nebula-sdk:core")
include("nebula-sdk:minestom")
include("services:lobby")
