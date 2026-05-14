# Copilot Instructions

# MoJ Repository – AI Coding Standards

This repository follows **Ministry of Justice (MoJ) Technical Guidance** and Justice Digital standards. Apply these when generating or modifying code.

## Critical

- **Never** upload PII (personally identifiable information) or secrets to this repo.
- Use GitHub for version control; feature branches; commits: `type(scope): description`; mandatory review before merge.

## Language and Style

- Use British English spelling throughout
- Follow conventional commit message format

## Git Operations

- **Never commit or push automatically** — only when explicitly instructed by the user
- Always provide clear, descriptive commit messages following conventional commits

## Code quality

- Correct, clear, concise (in that order). Tests required for fixes and new features.
- Meaningful names; avoid globals; comment only when necessary (explain why, not how).
- Prefer composition over inheritance; small, single-responsibility units.

## Design & APIs

- SOLID principles; versioned APIs (e.g. /v1/...); OpenAPI/Swagger; RESTful.

## Security

- Parameterised queries; validate inputs; secure auth (OAuth 2.0/JWT); encrypt sensitive data; keep dependencies updated.

## Licensing

- MoJ uses MIT; Crown Copyright (Ministry of Justice). Include LICENCE in repo.

Source: [MoJ Technical Guidance](https://technical-guidance.service.justice.gov.uk/).

## Project Overview

`laa-record-controlled-work-api` — Spring Boot 3 microservice (Ministry of Justice LAA). Multi-module Gradle:

- **`record-controlled-work-api`** — OpenAPI spec (`open-api-specification.yml`) → generates Spring interfaces and models via `openapi-generator`.
- **`record-controlled-work-service`** — Implements the generated API interfaces.

## Architecture

API-first: **Controller → Service → Repository**, with MapStruct mappers for entity↔model conversion.

- **Controllers** implement generated `*Api` interfaces. Do NOT redeclare Spring MVC annotations — they come from the generated interface. Use `@Override` on all implemented methods.
- **Mappers** are MapStruct interfaces with `@Mapper(componentModel = "spring")`.
- **Entities** use Lombok `@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`. Suffixed with `Entity`.
- **Exceptions** are custom unchecked exceptions handled by `@RestControllerAdvice` using RFC 7807 `ProblemDetail`.
- Never modify generated code in `record-controlled-work-api/generated/`. API changes start in `open-api-specification.yml`, then regenerate with `./gradlew openApiGenerate`.

## Code Style

Google Java Format enforced via Spotless and Checkstyle.

- 2-space indentation, 100-character line limit.
- Imports: static first, then third-party, alphabetical. No wildcards.
- Constructor injection via `@RequiredArgsConstructor`. Never use `@Autowired`.
- `@Slf4j` logging with `{}` placeholders. No string concatenation.
- `@Slf4j` and `@RequiredArgsConstructor` on every controller and service class.
- Javadoc on all public types and methods longer than 2 lines (`@param`, `@return`, `@throws`).
- Package: `uk.gov.justice.laa.rcw.{layer}` — `controller`, `service`, `repository`, `entity`, `mapper`, `exception`.

## Testing

JUnit 5 + Mockito + AssertJ. All test classes and methods are **package-private**.

- **Controller tests**: `@WebMvcTest` with `MockMvc` and `@MockitoBean`. Assert with result matchers and Hamcrest.
- **Service tests**: `@ExtendWith(MockitoExtension.class)` with `@Mock`/`@InjectMocks`. AssertJ assertions, `verify()` for interactions.
- **Mapper tests**: instantiate mapper impl directly. `private static final` constants for test data.
- **Exception handler tests**: plain instantiation, no Spring context.
- **Integration tests** (`src/integrationTest/`): `@SpringBootTest` + `@AutoConfigureMockMvc` + `@Transactional` with H2.

### Test naming

- Services/mappers: `should{Action}` (e.g. `shouldGetAllItems`)
- Controllers: `{method}_{expectedResult}` (e.g. `getItems_returnsOkStatusAndAllItems`)
- Negative: `shouldNot{Action}_when{Condition}ThenThrowsException`
- Mock variables prefixed with `mock` (e.g. `mockItemService`)

## Build & Run

| Command            | Purpose                               |
| ------------------ | ------------------------------------- |
| `make build`       | Build the project                     |
| `make lint`        | Spotless formatting (`spotlessApply`) |
| `make integration` | Run integration tests                 |
| `make dev`         | Run locally with `local` profile      |
| `make docker-up`   | Run via Docker Compose                |

- Server port: **8081**, Management port: **8181**
- H2 in-memory database, schema via `schema.sql` and `data.sql` (JPA `ddl-auto: none`)
