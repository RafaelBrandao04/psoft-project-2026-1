# ==============================================================================
# Multi-Stage Production-Grade Dockerfile for Library Management System (psoft-g1)
#
# Stage 1 (Builder): Compiles code & extracts Spring Boot layertools layers
# Stage 2 (Runtime): Minimal, hardened JRE 17 with unprivileged non-root user
# ==============================================================================

# ------------------------------------------------------------------------------
# Stage 1: Build & Layer Extraction
# ------------------------------------------------------------------------------
FROM eclipse-temurin:17-jdk-jammy AS builder
WORKDIR /workspace

# Copy build descriptors and wrapper first to leverage Docker layer caching
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw

# Copy source code and package application fat JAR
COPY src/ src/
RUN ./mvnw clean package -DskipTests -B

# Extract Spring Boot layers using layertools
WORKDIR /workspace/extracted
RUN java -Djarmode=layertools -jar /workspace/target/*.jar extract

# ------------------------------------------------------------------------------
# Stage 2: Hardened, Unprivileged Production Runtime
# ------------------------------------------------------------------------------
FROM eclipse-temurin:17-jre-jammy AS runtime

# Metadata labels (OCI Image Specification)
LABEL maintainer="psoft-g1 team" \
      org.opencontainers.image.title="psoft-g1" \
      org.opencontainers.image.description="Library Management System REST Backend" \
      org.opencontainers.image.licenses="MIT"

# Install curl for Actuator healthcheck probes and ca-certificates
RUN apt-get update && apt-get install -y --no-install-recommends curl ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Security Principle of Least Privilege: Create unprivileged system group & user (UID 10001)
RUN groupadd -g 10001 psoft && \
    useradd -u 10001 -g psoft -s /usr/sbin/nologin -m psoft

WORKDIR /app

# Prepare upload directory with strict ownership assigned to psoft user
RUN mkdir -p /app/uploads-psoft-g1 && \
    chown -R psoft:psoft /app/uploads-psoft-g1

# Copy application layers in order of frequency of change (caching optimization)
COPY --from=builder --chown=psoft:psoft /workspace/extracted/dependencies/ ./
COPY --from=builder --chown=psoft:psoft /workspace/extracted/spring-boot-loader/ ./
COPY --from=builder --chown=psoft:psoft /workspace/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=psoft:psoft /workspace/extracted/application/ ./

# Switch execution context to non-root user
USER psoft:psoft

# Expose default application port
EXPOSE 8080

# Environment variables with sensible defaults
ENV PORT=8080 \
    SPRING_PROFILES_ACTIVE=dev,bootstrap \
    DEMO_ENVIRONMENT=docker \
    FILE_UPLOAD_DIR=/app/uploads-psoft-g1 \
    JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8 -Djava.awt.headless=true -XX:+UseG1GC -XX:MaxRAMPercentage=75.0"

# Container Healthcheck via Spring Boot Actuator readiness probe
HEALTHCHECK --interval=15s --timeout=3s --start-period=30s --retries=3 \
    CMD curl -f http://localhost:${PORT}/actuator/health/readiness || exit 1

# Launch container via Spring Boot JarLauncher
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]

