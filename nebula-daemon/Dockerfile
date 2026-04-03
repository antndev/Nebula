FROM eclipse-temurin:25-jdk AS build

WORKDIR /workspace

COPY gradle gradle
COPY gradlew gradlew
COPY gradlew.bat gradlew.bat
COPY settings.gradle.kts build.gradle.kts ./
COPY nebula-daemon/build.gradle.kts nebula-daemon/build.gradle.kts
COPY nebula-daemon/src nebula-daemon/src

RUN chmod +x gradlew
RUN ./gradlew --no-daemon :nebula-daemon:installDist

FROM eclipse-temurin:25-jre

WORKDIR /app
COPY --from=build /workspace/nebula-daemon/build/install/nebula-daemon /app

RUN mkdir -p /var/lib/nebula

ENV NEBULA_DATA_DIR=/var/lib/nebula
ENTRYPOINT ["/app/bin/nebula-daemon"]
