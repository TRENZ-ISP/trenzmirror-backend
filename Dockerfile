# --- Build stage ---
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app

# Copy Gradle wrapper and build files first so dependency resolution is cached
# across builds when only source files change.
COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
RUN chmod +x gradlew

# Copy the rest of the source
COPY src src

# Build a single runnable "fat" jar (Ktor Gradle plugin task; bundles all dependencies)
RUN ./gradlew buildFatJar --no-daemon

# --- Runtime stage ---
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Copy whichever fat jar got produced (name includes the Ktor plugin's own versioning)
COPY --from=build /app/build/libs/*-all.jar app.jar

# SQLite database lives here by default. Mount a persistent volume at /data on
# your hosting platform and set DB_PATH=jdbc:sqlite:/data/trenz_mirror.db so
# data survives restarts and redeploys.
RUN mkdir -p /data

# Railway injects PORT at runtime; the app reads it via application.conf's
# ${?PORT} substitution. This default just lets the image run standalone too.
ENV PORT=8080
EXPOSE 8080

# Curl against the port; a 404 still means the server answered and is healthy -
# only a connection failure (server down) fails this check.
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
    CMD curl -s "http://localhost:${PORT}/" -o /dev/null || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
