---
applyTo: "**/*.java"
---

# Architecture

`laa-record-controlled-work-api` — Spring Boot 3 microservice. Multi-module Gradle:

- **`record-controlled-work-api`** — OpenAPI spec (`open-api-specification.yml`) → generates Spring interfaces and models via `openapi-generator`.
- **`record-controlled-work-service`** — Implements the generated API interfaces.

Layer pattern: **Controller → Service → Repository**, with MapStruct mappers for entity↔model conversion.

- **Controllers** implement generated `*Api` interfaces. Do NOT redeclare Spring MVC annotations — they come from the generated interface. Use `@Override` on all implemented methods.
- **Mappers** are MapStruct interfaces with `@Mapper(componentModel = "spring")`.
- **Exceptions** are custom unchecked exceptions handled by `@RestControllerAdvice` using RFC 7807 `ProblemDetail`.
- Never modify generated code in `record-controlled-work-api/generated/`. API changes start in `open-api-specification.yml`, then regenerate with `./gradlew openApiGenerate`.

# Code Style

Google Java Format enforced via Spotless and Checkstyle.

- 2-space indentation, 100-character line limit.
- Imports: static first, then third-party, alphabetical. No wildcards.
- Constructor injection via `@RequiredArgsConstructor`. Never use `@Autowired`.
- `@RequiredArgsConstructor` on every controller and service class.
- Javadoc on all public types and methods longer than 2 lines (`@param`, `@return`, `@throws`).
- Package: `uk.gov.justice.laa.rcw.{layer}` — `controller`, `service`, `repository`, `entity`, `mapper`, `exception`, `logging`.
