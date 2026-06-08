# =============================================================================
# DigitalWallet backend — multi-stage JVM image
# Stage 1 builds the Quarkus fast-jar from source with the Maven wrapper.
# Stage 2 ships only the JRE + fast-jar layers (eclipse-temurin:21-jre).
# Architecture: docs/architecture/README.md §3, §8.
# =============================================================================

# ---- Build stage ------------------------------------------------------------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Resolve dependencies first so the layer caches across source-only changes.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline

# Build the fast-jar. Tests run in CI / `./mvnw verify`, not in the image build.
COPY src/ src/
RUN ./mvnw -B -q clean package -DskipTests

# ---- Runtime stage ----------------------------------------------------------
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

ENV LANG=C.UTF-8 \
    JAVA_OPTS="-Dquarkus.http.host=0.0.0.0"

# Run as an unprivileged user.
RUN groupadd --system --gid 1001 quarkus \
 && useradd  --system --uid 1001 --gid quarkus quarkus

# Copy the Quarkus fast-jar layout. Order matters for layer reuse:
# third-party libs change least often, the app classes most often.
COPY --from=build --chown=quarkus:quarkus /workspace/target/quarkus-app/lib/        ./lib/
COPY --from=build --chown=quarkus:quarkus /workspace/target/quarkus-app/*.jar       ./
COPY --from=build --chown=quarkus:quarkus /workspace/target/quarkus-app/app/        ./app/
COPY --from=build --chown=quarkus:quarkus /workspace/target/quarkus-app/quarkus/    ./quarkus/

USER quarkus
EXPOSE 8080

# Liveness against the built-in Quarkus health-ish root; compose owns the real
# healthcheck. Kept here so `docker run` alone still self-reports.
HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=5 \
    CMD ["sh", "-c", "exec 3<>/dev/tcp/127.0.0.1/8080"]

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/quarkus-run.jar"]
