# Specify java runtime base image
FROM amazoncorretto:25-alpine

# Set up working directory in the container
RUN mkdir -p /opt/laa-record-controlled-work/
WORKDIR /opt/laa-record-controlled-work/

# Copy the JAR file into the container
COPY record-controlled-work-service/build/libs/record-controlled-work-service-1.0.0.jar app.jar

# Create a group and non-root user
RUN addgroup -S appgroup && adduser -u 1001 -S appuser -G appgroup

# Set the default user
USER 1001

# Expose the server.port and management.server.port for the application
EXPOSE 8081 8181

# Run the JAR file
CMD java -jar app.jar