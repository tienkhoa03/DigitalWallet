# syntax=docker/dockerfile:1.7
#
# Backend image — Quarkus 3.15.6 LTS on Java 21 (JVM mode, fast-jar layout).
# Stack mandated by .claude/rules/upgrade-policy.md §1 and project-info.md §4.1.
# Multi-stage so the runtime image carries only the JRE and the Quarkus fast-jar —
# no Maven, no source tree, no test classes.

# ---------- Stage 1: build (JDK 21 + Maven wrapper) ----------
FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /workspace

# Maven wrapper first for cache friendliness — pom.xml drives dependency resolution.
COPY mvnw mvnw
COPY .mvn .mvn
COPY pom.xml pom.xml
RUN chmod +x mvnw \
 && ./mvnw -B -q -DskipTests dependency:go-offline || true

# Sources + Quarkus build. Tests run in CI (mvn verify); skip here for image build speed.
COPY src src
RUN ./mvnw -B -DskipTests package

# ---------- Stage 2: runtime (JRE 21, non-root) ----------
FROM eclipse-temurin:21-jre-jammy

# Non-root runtime user — security.md §1, defence-in-depth on the container surface.
RUN groupadd --system --gid 1001 quarkus \
 && useradd  --system --uid 1001 --gid quarkus quarkus

WORKDIR /app

# Quarkus fast-jar layout: lib/ + quarkus-run.jar + app/ + quarkus/.
COPY --from=build --chown=quarkus:quarkus /workspace/target/quarkus-app/lib/      ./lib/
COPY --from=build --chown=quarkus:quarkus /workspace/target/quarkus-app/*.jar     ./
COPY --from=build --chown=quarkus:quarkus /workspace/target/quarkus-app/app/      ./app/
COPY --from=build --chown=quarkus:quarkus /workspace/target/quarkus-app/quarkus/  ./quarkus/

USER quarkus

EXPOSE 8080

ENV LANG=C.UTF-8 \
    JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75 -Dfile.encoding=UTF-8" \
    QUARKUS_HTTP_HOST=0.0.0.0

ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS -jar quarkus-run.jar"]
