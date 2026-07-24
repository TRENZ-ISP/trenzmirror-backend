# --- Build stage ---
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /app

# Copy Gradle wrapper and build files first so dependency resolution can be cached
COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts gradle.properties* ./
RUN chmod +x gradlew

# Copy the rest of the source
COPY src src

# Build a single runnable "fat" jar (Ktor Gradle plugin task, bundles all dependencies)
RUN ./gradlew buildFatJar --no-daemon

# --- Runtime stage ---
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Copy whichever fat jar got produced (name includes the Ktor plugin's own versioning)
COPY --from=build /app/build/libs/*-all.jar app.jar

# The SQLite database file lives here. Mount a persistent volume at /data in your
# hosting platform's settings, and set DB_PATH=jdbc:sqlite:/data/trenz_mirror.db
# as an environment variable so data survives restarts/redeploys.
RUN mkdir -p /data

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
