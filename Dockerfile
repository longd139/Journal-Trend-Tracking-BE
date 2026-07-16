# ===============================
# SCITRACK — Backend Dockerfile
# Multi-stage build for Railway deployment
# ===============================

# ---- Build Stage ----
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Copy POM first for dependency caching
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source and build
COPY src ./src
RUN mvn clean package -DskipTests -B

# ---- Runtime Stage ----
# Use Ubuntu-based JRE for MSSQL JDBC driver native compatibility
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Create non-root user for security
RUN groupadd -r spring && useradd -r -g spring spring

# Copy the built JAR
COPY --from=build /app/target/journal-tracking-0.0.1-SNAPSHOT.jar app.jar

# Set ownership
RUN chown spring:spring app.jar

# Switch to non-root user
USER spring

# JVM tuning for containerized environment
# - XX:MaxRAMPercentage=50.0 → uses 50% of container RAM for heap
# - Use G1GC for better performance in memory-constrained containers
# - The PORT env var is set by Railway automatically
ENV JAVA_OPTS="-XX:MaxRAMPercentage=50.0 -XX:+UseG1GC -XX:+UseStringDeduplication"

# Health check using Spring Boot Actuator
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:${PORT:-8080}/actuator/health || exit 1

EXPOSE ${PORT:-8080}

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar --server.port=${PORT:-8080}"]
