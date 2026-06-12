import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val targetJava = 21

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    `java-library`
}

dependencies {
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
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
