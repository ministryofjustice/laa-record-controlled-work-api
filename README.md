# Record Controlled Work Api

### Install pre-hook commits

`scripts/setup-hooks.sh` to install pre-commit hooks this will run

- Spotless on the codebase
- checkStyle on main, test and integration test
- https://github.com/ministryofjustice/devsecops-hooks to scan for any secrets that may accidentally may have been commited.
- [gitlint](https://github.com/jorisroovers/gitlint) to ensure commit conventions are followed

{# TODO #}

## Overview

Template GitHub repository used for Spring Boot Java microservice projects.

The project uses the `laa-spring-boot-gradle-plugin` Gradle plugin which provides
sensible defaults for the following plugins:

- [Checkstyle](https://docs.gradle.org/current/userguide/checkstyle_plugin.html)
- [Dependency Management](https://plugins.gradle.org/plugin/io.spring.dependency-management)
- [Jacoco](https://docs.gradle.org/current/userguide/jacoco_plugin.html)
- [Java](https://docs.gradle.org/current/userguide/java_plugin.html)
- [Maven Publish](https://docs.gradle.org/current/userguide/publishing_maven.html)
- [Spring Boot](https://plugins.gradle.org/plugin/org.springframework.boot)
- [Test Logger](https://github.com/radarsh/gradle-test-logger-plugin)
- [Versions](https://github.com/ben-manes/gradle-versions-plugin)

The plugin is provided by - [laa-spring-boot-common](https://github.com/ministryofjustice/laa-spring-boot-common), where you can find
more information regarding setup and usage.

### Project Structure

Includes the following subprojects:

- `spring-boot-microservice-api` - example OpenAPI specification used for generating API stub interfaces and documentation.
- `spring-boot-microservice-service` - example REST API service with CRUD operations interfacing a JPA repository with an in-memory database.

## Setup Instructions

Once you've created your repository using this template, perform the following steps:

### Configure Dependabot

The template includes `.github/dependabot.yml` with weekly updates configured for Gradle and GitHub Actions.

After creating your repository from this template:

- Review the contents of `.github/dependabot.yml` and make the following changes if needed:
  - Change `uk.gov.laa.springboot.microservice.*` package references to `uk.gov.laa.{application-package-name}.*`.
  - Review schedule settings (`day`, `time`, `timezone`, and `cooldown`) and update if needed.
  - Update `labels` to match your repository conventions.
  - Uncomment the `registries` section and follow the inline instructions if you need updates from `laa-spring-boot-common`.
- Configure `CODEOWNERS` and enable required code owner review in repository branch protection/rulesets so Dependabot PRs route to the correct team.
- Add `REPO_TOKEN` as a repository secret if `registries` is enabled.
- See `Required GitHub repository settings after template creation` for repository-level security toggles.

### Required GitHub repository settings after template creation

- Enable Dependabot security updates (`Settings` -> `Security` -> `Code security and analysis`).
- (Optional) Enable auto-merge for low-risk dependency PRs (`Settings` -> `General` -> `Pull Requests` -> `Allow auto-merge`).

### Database scripts

The \*.sql scripts in `src/main/resources` have been included to provide an example database for demonstration purposes only and should be removed for your application.

## Build And Run Application

### Build application

`./gradlew clean build`

### Run integration tests

`./gradlew integrationTest`

### Run application

`./gradlew bootRun`

### Run application via Docker

`docker compose up`

### Debug application running via Docker

#### Configuration

- Go to Run > Edit Configurations
- Click + (Add New Configuration)
- Select Remote JVM Debug
- Configure:
- Name: Docker Debug
- Debugger mode: Attach to remote JVM
- Host: localhost
- Port: 5005
- Use module classpath: Select (laa-spring-boot-microservice-template)

#### Debugging

- run `docker compose up`
- run > Debug 'Docker Debug'

#### Local Development Logging

When running with the `local` profile, structured logging is disabled, for console output:

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

### Logging Configuration

This application uses **ECS (Elastic Common Schema) structured logging** for production environments and console logging for local development.

#### Structured Logging (Default/Production)

By default, the application outputs logs in ECS JSON format with distributed tracing support:

```json
{
  "@timestamp": "2026-03-06T16:25:18.992904Z",
  "ecs": {
    "version": "8.11"
  },
  "log": {
    "level": "INFO",
    "logger": "uk.gov.justice.laa.springboot.microservice.controller.ItemController"
  },
  "message": "Getting all items",
  "process": {
    "pid": 49402,
    "thread": {
      "name": "http-nio-8080-exec-2"
    }
  },
  "service": {
    "environment": "local",
    "name": "laa-spring-boot-microservice",
    "node": {
      "name": "unknown"
    },
    "version": "1.0.0"
  },
  "spanId": "fe4586c5fd5f7021",
  "traceId": "69aaffee8d19869cfe4586c5fd5f7021"
}
```

#### logback-spring.xml Conflicts

Adding `logback-spring.xml` will:

- Override the profile-based logging configuration in `application.yml`

## Application Endpoints

### API Documentation

#### Swagger UI

- http://localhost:8081/swagger-ui/index.html

#### API docs (JSON)

- http://localhost:8081/v3/api-docs

### Actuator Endpoints

The following actuator endpoints have been configured:

- http://localhost:8081/actuator
- http://localhost:8081/actuator/health

## Application Configuration

### Sentry

In order to integrate with Sentry, the following properties need to be configured in the `application.yml`:

```
sentry:
  dsn: <configure sentry dsn url here>
  environment: <configure environment name here>
```

## Libraries Used

- [Spring Boot Actuator](https://docs.spring.io/spring-boot/reference/actuator/index.html) - used to provide various endpoints to help monitor the application, such as view application health and information.
- [Spring Boot Web](https://docs.spring.io/spring-boot/reference/web/index.html) - used to provide features for building the REST API implementation.
- [Spring Data JPA](https://docs.spring.io/spring-data/jpa/reference/jpa.html) - used to simplify database access and interaction, by providing an abstraction over persistence technologies, to help reduce boilerplate code.
- [Springdoc OpenAPI](https://springdoc.org/) - used to generate OpenAPI documentation. It automatically generates Swagger UI, JSON documentation based on your Spring REST APIs.
- used to capture application exception events at runtime, which can be monitored via the Sentry UI.
- [Lombok](https://projectlombok.org/) - used to help to reduce boilerplate Java code by automatically generating common
  methods like getters, setters, constructors etc. at compile-time using annotations.
- [MapStruct](https://mapstruct.org/) - used for object mapping, specifically for converting between different Java object types, such as Data Transfer Objects (DTOs)
  and Entity objects. It generates mapping code at compile code.
- [H2](https://www.h2database.com/html/main.html) - used to provide an example database and should not be used in production.
- [Sentry for Java SDK](https://docs.sentry.io/platforms/java/) - used to capture application exception events at runtime, which can be monitored via the Sentry UI.

## ⚠️ Temporary Dependency Overrides

The following Gradle dependency overrides are **temporary** and should be removed once the dependency versions are
available in a future `laa-spring-boot-common` release.

| Dependency                                  | Overridden Version | Reason                                                                                                                                    | Date Added |
| ------------------------------------------- | ------------------ | ----------------------------------------------------------------------------------------------------------------------------------------- | ---------- |
| `com.fasterxml.jackson.core:jackson-core`   | `2.21.2`           | Fixes Snyk issue - [SNYK-JAVA-COMFASTERXMLJACKSONCORE-15907551](https://security.snyk.io/vuln/SNYK-JAVA-COMFASTERXMLJACKSONCORE-15907551) | 2026-04-30 |
| `org.apache.tomcat.embed:tomcat-embed-core` | `11.0.21`          | Fixes Snyk issues - [SNYK-JAVA-ORGAPACHETOMCATEMBED-15989820](https://security.snyk.io/vuln/SNYK-JAVA-ORGAPACHETOMCATEMBED-15989820)      | 2026-04-30 |
| `tools.jackson.core:jackson-core`           | `3.1.1`            | Fixes Snyk issue - [SNYK-JAVA-TOOLSJACKSONCORE-15907550](https://security.snyk.io/vuln/SNYK-JAVA-TOOLSJACKSONCORE-15907550)               | 2026-04-30 |
