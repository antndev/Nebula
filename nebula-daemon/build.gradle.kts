@file:Suppress("VulnerableLibrariesLocal")

import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val targetJava = 25

plugins {
    kotlin("jvm") version "2.3.0"
    application
}

group = "dev.antn"
version = "2.0.0-SNAPSHOT"

repositories {
    maven(url = "https://central.sonatype.com/repository/maven-snapshots/") {
        content {
            includeModule("net.minestom", "minestom")
            includeModule("net.minestom", "testing")
        }
    }
    mavenCentral()
}

dependencies {
    implementation("me.devnatan:docker-kotlin:0.14.4")
    implementation("org.slf4j:slf4j-api:2.0.13")
    implementation("ch.qos.logback:logback-classic:1.5.32")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("net.minestom:minestom:26_1-SNAPSHOT")
}

application {
    mainClass = "nebula.MainKt"
    applicationDefaultJvmArgs = listOf("--sun-misc-unsafe-memory-access=allow")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(targetJava))
    }
}

kotlin {
    jvmToolchain(targetJava)
    compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget(targetJava.toString()))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(targetJava)
}

tasks.withType<JavaExec>().configureEach {
    javaLauncher.set(
        javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(targetJava))
        }
    )
}