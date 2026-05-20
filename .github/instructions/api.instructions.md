---
description: "Use when modifying the OpenAPI specification, adding endpoints, or working with generated code. Covers API-first workflow, versioning, and regeneration."
applyTo: "**/open-api-specification.yml"
---

# API Design

- API-first: all changes begin in `open-api-specification.yml`. Never modify generated code in `record-controlled-work-api/generated/`.
- After updating the spec, regenerate with `./gradlew openApiGenerate`.
- Versioned APIs (e.g. `/v1/...`); follow RESTful conventions; use `ProblemDetail` (RFC 7807) for error responses.
