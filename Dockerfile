# Build stage
FROM amazoncorretto:25.0.3-alpine@sha256:32d81edae73e1670244827c2f12e5bcf0d335f035b538455fe9d02eb0771d41b AS builder

RUN mkdir -p /build
WORKDIR /build

COPY gradlew settings.gradle build.gradle gradle.properties ./
COPY gradle/ gradle/
COPY record-controlled-work-api/ record-controlled-work-api/
COPY record-controlled-work-service/ record-controlled-work-service/

RUN --mount=type=secret,id=git_token \
    export GITHUB_TOKEN=$(cat /run/secrets/git_token) && \
    export GITHUB_ACTOR=x-token && \
    chmod +x gradlew && ./gradlew :record-controlled-work-service:bootJar --no-daemon

# Runtime stage
FROM amazoncorretto:25.0.3-alpine@sha256:32d81edae73e1670244827c2f12e5bcf0d335f035b538455fe9d02eb0771d41b

# Set up working directory in the container
RUN mkdir -p /opt/laa-record-controlled-work/
WORKDIR /opt/laa-record-controlled-work/

# Copy the JAR file from the build stage
COPY --from=builder /build/record-controlled-work-service/build/libs/record-controlled-work-service-1.0.0.jar app.jar

# Create a group and non-root user
RUN addgroup -S appgroup && adduser -u 1001 -S appuser -G appgroup

# Set the default user
USER 1001

# Expose the server.port and management.server.port for the application
EXPOSE 8081 8181

# Run the JAR file
CMD ["java", "-jar", "app.jar"]