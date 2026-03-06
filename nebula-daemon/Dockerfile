FROM gradle:9.0.0-jdk21 AS build

WORKDIR /workspace

COPY gradle gradle
COPY gradlew gradlew
COPY gradlew.bat gradlew.bat
COPY build.gradle.kts settings.gradle.kts ./
COPY src src

RUN ./gradlew --no-daemon installDist

FROM eclipse-temurin:21-jre

WORKDIR /app
COPY --from=build /workspace/build/install/nebula-daemon /app

RUN mkdir -p /var/lib/nebula

ENV NEBULA_DATA_DIR=/var/lib/nebula
ENTRYPOINT ["/app/bin/nebula-daemon"]
