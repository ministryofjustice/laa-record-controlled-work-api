# MoJ Repository – AI Coding Standards

This repository follows **Ministry of Justice (MoJ) Technical Guidance** and Justice Digital standards. Apply these when generating or modifying code.

## Project Overview

`laa-record-controlled-work-api` — Spring Boot 3 microservice (Ministry of Justice LAA). Multi-module Gradle:

- **`record-controlled-work-api`** — OpenAPI spec (`open-api-specification.yml`) → generates Spring interfaces and models via `openapi-generator`.
- **`record-controlled-work-service`** — Implements the generated API interfaces.

## Critical

- **Never** upload PII (personally identifiable information) or secrets to this repo.
- Use GitHub for version control; feature branches; commits: `type(scope): description`; mandatory review before merge.
- Use British English spelling throughout.

## Git Operations

- **Never commit or push automatically** — only when explicitly instructed by the user.
- Conventional commits: `type(scope): description`; feature branches; mandatory review before merge.

## Code quality

- Correct, clear, concise (in that order). Tests required for fixes and new features.
- Meaningful names; avoid globals; comment only when necessary (explain why, not how).
- Prefer composition over inheritance; small, single-responsibility units.

## Security

- Parameterised queries; validate inputs; secure auth (OAuth 2.0/JWT); encrypt sensitive data; keep dependencies updated.

## Licensing

- MoJ uses MIT; Crown Copyright (Ministry of Justice). Include LICENCE in repo.

## AI use

- Use only approved MoJ AI tools; review all AI-generated output; no sensitive data into AI unless approved.

Source: [MoJ Technical Guidance](https://technical-guidance.service.justice.gov.uk/).
