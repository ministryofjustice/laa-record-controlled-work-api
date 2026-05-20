---
description: "Use when asking about build commands, running the service locally, Docker, ports, or the database setup."
---

# Build & Run

| Command            | Purpose                               |
| ------------------ | ------------------------------------- |
| `make build`       | Build the project                     |
| `make lint`        | Spotless formatting (`spotlessApply`) |
| `make integration` | Run integration tests                 |
| `make dev`         | Run locally with `local` profile      |
| `make docker-up`   | Run via Docker Compose                |

- Server port: **8081**, Management port: **8181**
- H2 in-memory database; schema via `schema.sql` and `data.sql` (JPA `ddl-auto: none`)
