# ---------- stage 1: build ----------
# Dependencies are resolved in their own layer so that source-only changes
# don't invalidate the Maven cache (12-Factor II: explicit dependencies).
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests clean package && \
    java -Djarmode=layertools -jar target/library-api-1.0.0.jar extract --destination target/extracted

# ---------- stage 2: runtime ----------
FROM eclipse-temurin:17-jre-jammy AS runtime

# curl is here only for the container healthcheck.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# 12-Factor VI: the app runs as one stateless, unprivileged process.
RUN groupadd --system app && useradd --system --gid app --create-home app
WORKDIR /app

# Spring Boot layered jar: the layers that change least are copied first.
COPY --from=build --chown=app:app /build/target/extracted/dependencies/ ./
COPY --from=build --chown=app:app /build/target/extracted/spring-boot-loader/ ./
COPY --from=build --chown=app:app /build/target/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /build/target/extracted/application/ ./

USER app

# 12-Factor VII: the port is supplied by the environment.
ENV PORT=8080
EXPOSE 8080

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport"

HEALTHCHECK --interval=15s --timeout=3s --start-period=45s --retries=5 \
    CMD curl -fsS "http://127.0.0.1:${PORT}/actuator/health/readiness" || exit 1

ENTRYPOINT ["/bin/sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
