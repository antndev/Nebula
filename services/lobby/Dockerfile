# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:25-jdk AS build

WORKDIR /workspace

COPY gradle gradle
COPY gradlew gradlew
COPY gradlew.bat gradlew.bat
COPY settings.gradle.kts build.gradle.kts ./
COPY nebula-daemon/build.gradle.kts nebula-daemon/build.gradle.kts
COPY nebula-sdk/minestom/build.gradle.kts nebula-sdk/minestom/build.gradle.kts
COPY services/lobby/build.gradle.kts services/lobby/build.gradle.kts

RUN chmod +x gradlew

# Warm Gradle's dependency cache before copying sources so rebuilds stay fast.
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon :services:lobby:dependencies

COPY nebula-sdk/minestom/src nebula-sdk/minestom/src
COPY services/lobby/src services/lobby/src

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon :services:lobby:installDist

RUN jdeps \
    --ignore-missing-deps \
    --print-module-deps \
    --recursive \
    --multi-release 25 \
    services/lobby/build/install/lobby/lib/*.jar > /tmp/jre-modules.txt

RUN jlink \
    --add-modules "$(cat /tmp/jre-modules.txt)",jdk.unsupported \
    --compress=2 \
    --no-header-files \
    --no-man-pages \
    --strip-debug \
    --output /opt/jre

FROM gcr.io/distroless/base-debian12:nonroot

ENV JAVA_HOME=/opt/java \
    PATH="/opt/java/bin:${PATH}" \
    LOBBY_PORT=25566

WORKDIR /app

COPY --from=build /opt/jre /opt/java
COPY --from=build /workspace/services/lobby/build/install/lobby/lib /app/lib

EXPOSE 25566

ENTRYPOINT ["/opt/java/bin/java", "--sun-misc-unsafe-memory-access=allow", "-cp", "/app/lib/*", "nebula.lobby.MainKt"]
