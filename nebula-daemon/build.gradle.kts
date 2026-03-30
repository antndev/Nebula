plugins {
    kotlin("jvm") version "2.2.20"
    id("com.google.protobuf") version "0.9.4"
    application
}

group = "dev.antn"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("me.devnatan:docker-kotlin:0.14.4")
    implementation("org.slf4j:slf4j-api:2.0.13")
    implementation("ch.qos.logback:logback-classic:1.5.32")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}

application {
    mainClass.set("nebula.MainKt")
}

kotlin {
    jvmToolchain(21)
}