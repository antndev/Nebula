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
    // gRPC Kotlin
    implementation("io.grpc:grpc-kotlin-stub:1.5.0")
    implementation("io.grpc:grpc-protobuf:1.75.0")
    implementation("io.grpc:grpc-stub:1.75.0")
    implementation("io.grpc:grpc-netty-shaded:1.75.0")

    // Protobuf
    implementation("com.google.protobuf:protobuf-kotlin:4.32.0")
    implementation("javax.annotation:javax.annotation-api:1.3.2")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.13")
    implementation("ch.qos.logback:logback-classic:1.5.13")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:4.32.0"
    }
    plugins {
        create("grpc") { artifact = "io.grpc:protoc-gen-grpc-java:1.75.0" }
        create("grpckt") { artifact = "io.grpc:protoc-gen-grpc-kotlin:1.5.0:jdk8@jar" }
    }
    generateProtoTasks {
        all().forEach {
            it.plugins {
                create("grpc")
                create("grpckt")
            }
            it.builtins {
                create("kotlin")
            }
        }
    }
}

application {
    mainClass.set("nebula.MainKt")
}

kotlin {
    jvmToolchain(21)
}