# Record Controlled Work Api

{# TODO - update renovate App to also check local private repo laa-spring-boot-common
Go to https://developer.mend.io
Find your org/repo
Go to Secrets settings
Add a secret for host maven.pkg.github.com with your PAT (needs read:packages, SSO-authorized for MoJ)
The hostRules in the config tells Renovate which host needs credentials — the portal is where the actual token lives securely.
#}

## Overview

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

- `record-controlled-work-api` - OpenAPI specification used for generating API stub interfaces and documentation.
- `record-controlled-work-service` - REST API service with CRUD operations interfacing a JPA repository with an in-memory database.

## Setup Instructions

### Install pre-hook commits

`scripts/setup-hooks.sh` to install pre-commit hooks this will run

- Spotless on the codebase
- checkStyle on main, test and integration test
- https://github.com/ministryofjustice/devsecops-hooks to scan for any secrets that may accidentally may have been commited.
- [gitlint](https://github.com/jorisroovers/gitlint) to ensure commit conventions are followed

### Add GitHub Token

Generate a Github PAT (Personal Access Token) to access the required plugin, via https://github.com/settings/tokens

Specify the Note field, e.g. “Token to allow access to LAA Gradle plugin”

\*Moj has a requirement of max 366 days on expiration date on PAT tokens

If you don't already have one, create a `gradle.properties` file in your home directory at `~/.gradle/gradle.properties`.

Add the following properties to `~/.gradle/gradle.properties` and replace the placeholder values as follows:

```
project.ext.gitPackageUser = YOUR_GITHUB_USERNAME
project.ext.gitPackageKey = PAT_CREATED_ABOVE
```

Alternatively, you can skip creating that file entirely: the plugin checks the `GITHUB_ACTOR` and `GITHUB_TOKEN` environment variables first, before falling back to `gradle.properties`. Set `GITHUB_ACTOR`/`GITHUB_TOKEN` in [.env](.env) (see the 1Password-backed `GITHUB_TOKEN` entry already there) and run Gradle via `op run --env-file=.env -- ./gradlew <task>`.

> **Note:** The `info-and-advice-datastore-client` dependency in [record-controlled-work-service/build.gradle](record-controlled-work-service/build.gradle) is currently commented out (it needs an `OAuth2AuthorizedClientManager` bean that isn't wired up yet), so a GitHub token isn't required for local builds/tests right now. Once it's re-enabled, since it's a fixed (non-`SNAPSHOT`) version, Gradle caches it locally after the first authenticated resolution and won't need credentials again on later builds - run `make warm-deps` once (or after clearing your Gradle cache/bumping that dependency's version) to authenticate and populate the cache, then `./gradlew test` works without `op run` or `gradle.properties`.

Go back to Github to authorize MOJ for SSO

### Bruno Collection

Bruno is a Git-friendly, offline-first API client built for developers which will enable local api testing.
A collection is checked into the repo and can be opened through [Bruno](https://www.usebruno.com/) by opening
the `bruno-collection` folder.

See [docs/bruno.md](docs/bruno.md) for full setup instructions, including how to select an environment,
fetch an OAuth2 access token for `local`/`uat`, the Entra redirect URI requirement for `uat`, and how to
inspect a cached token's decoded claims.

## Build And Run Application

### Prerequisites

On first setup, add `host.docker.internal` to your `/etc/hosts` (Docker Desktop on Mac does not add this automatically):

```bash
echo '127.0.0.1 host.docker.internal' | sudo tee -a /etc/hosts
```

### Build application

`make build`

### Run integration tests

`make integration`

### Run application

`make dev`

### Run application via Docker

`make docker-up`

This also starts the [laa-info-and-advice-datastore](https://github.com/ministryofjustice/laa-info-and-advice-datastore)
API and its Postgres instance. The datastore is built from a sibling checkout at `../laa-info-and-advice-datastore`.

To get an access token and call the API locally:

```bash
TOKEN=$(curl -s -X POST http://host.docker.internal:9090/default/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials&client_id=test&client_secret=test" \
  | jq -r .access_token)

curl -H "Authorization: Bearer $TOKEN" http://localhost:8081/api/v1/applications
```

The mock server automatically issues tokens with the `Applications.Read` role for `client_credentials` grants.

You can also test this with the Bruno collection's `local` environment - see [docs/bruno.md](docs/bruno.md).

#### Switching between the mock IdP and real Entra ID

By default, both `rcw-api` and `datastore-api` validate/exchange tokens against the `mock-oauth2-server`
container. To instead run the stack against real Entra ID (e.g. to test with tokens obtained via Bruno's
`local-entra` environment), copy [.env.example](.env.example) to `.env` and uncomment the Entra block, then
run `make docker-up` as normal - `docker-compose.yml` picks these up via environment variable substitution,
falling back to the mock server defaults when unset.

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

- run `make docker-up`
- run > Debug 'Docker Debug'

#### Local Development Logging

When running with the `local` profile, structured logging is disabled, for console output:

```bash
make dev
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
    "logger": "uk.gov.justice.laa.springboot.microservice.controller.record-controlled-work.ItemController"
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

- http://localhost:8181/actuator
- http://localhost:8181/actuator/health

## Application Configuration

### Sentry

In order to integrate with Sentry, the following properties need to be configured in the `application.yml`:

```
sentry:
  dsn: <configure sentry dsn url here>
  environment: <configure environment name here>
```

### Patterns and practices
See [patterns and practices](docs/patterns-and-practices.md) for more information.
