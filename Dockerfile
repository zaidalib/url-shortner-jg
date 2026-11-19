# ── Stage 1: Build ────────────────────────────────────────────────────────────

# Use the official Maven image with Java 21 to compile and package the application.
FROM maven:3.9-eclipse-temurin-21 AS build

# Set the working directory inside the build container.
WORKDIR /app

# Copy the Maven project descriptor first so dependency resolution is cached
# as a separate Docker layer — rebuilds only re-download deps when pom.xml changes.
COPY pom.xml .

# Download all dependencies declared in pom.xml (offline-friendly layer cache).
RUN mvn dependency:go-offline -B

# Copy the full source tree into the build container.
COPY src ./src

# Compile, run tests, and package into an executable fat-jar; skip tests during
# Docker builds to keep image build times short (run tests in CI separately).
RUN mvn package -DskipTests -B

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────

# Use a minimal JRE-only Alpine image — significantly smaller than the full JDK.
FROM eclipse-temurin:21-jre-alpine

# Set the working directory for the application inside the runtime container.
WORKDIR /app

# Copy only the packaged jar from the build stage; the full Maven toolchain is left behind.
COPY --from=build /app/target/*.jar app.jar

# Tune the JVM heap for Render's free-tier (512 MB RAM); adjust if you upgrade the plan.
ENV JAVA_OPTS="-Xmx400m"

# Render dynamically assigns a port and injects it as $PORT; expose it for documentation.
EXPOSE 8080

# Start the application, passing JAVA_OPTS through the shell so the env variable expands.
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
