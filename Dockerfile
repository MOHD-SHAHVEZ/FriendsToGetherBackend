# ============================================
# STAGE 1: Build the application
# ============================================
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copy Maven wrapper and pom.xml first (for dependency caching)
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Make Maven wrapper executable
RUN chmod +x mvnw

# Download dependencies (cached layer — only re-runs when pom.xml changes)
RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY src/ src/

# Build the application (skip tests for faster builds)
RUN ./mvnw clean package -DskipTests -B \
    && mv target/*.jar target/app.jar

# ============================================
# STAGE 2: Production runtime (minimal image)
# ============================================
FROM eclipse-temurin:21-jre-alpine AS runtime

LABEL maintainer="mohds"
LABEL description="FriendsTogether Backend API"

# Security: run as non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Copy only the built JAR from builder stage
COPY --from=builder /app/target/app.jar app.jar

# Set ownership
RUN chown -R appuser:appgroup /app

# Switch to non-root user
USER appuser

# Expose the application port
EXPOSE 8080

# Health check for Render (hits Spring Actuator)
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# JVM tuning for containerized environments
# - Uses container-aware memory settings
# - Enables G1GC for low-latency
# - Exits on OOM instead of hanging
ENV JAVA_OPTS="-XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:+UseG1GC \
    -XX:+ExitOnOutOfMemoryError \
    -Djava.security.egd=file:/dev/./urandom"

# Run with production profile
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar --spring.profiles.active=prod"]