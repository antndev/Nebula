import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val targetJava = 25

plugins {
    kotlin("jvm")
    `java-library`
}

dependencies {
    api(project(":nebula-sdk:core"))
    api("net.minestom:minestom:26_1-SNAPSHOT")
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
