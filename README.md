# dlc-patient-api

> Patients bounded context: service API.

Part of the **Di-lucca** distributed system — team `di-lucca`, Grupo 2.
Governance and documentation live in [`dlc-docs`](https://github.com/code-corhuila/dlc-docs);
the service is described in `09-microservices/services/03-patients-service/` and its
contract in `07-api/contracts/openapi/patients-service.yaml`.

## Purpose

Owns the unique patient identity, the administrative and contact profile, emergency
contacts and the active/inactive status (HU-PAT-001). Clinical history belongs to Clinical,
staff accounts to IAM and appointments to Appointments.

## Architecture

Java 21 and Spring Boot 3.5 (ADR-003), hexagonal with three Maven modules (Norma 5.3, Annex C):

| Module | Content | Depends on |
|---|---|---|
| `patients-core` | domain model, ports and use cases | nothing from the project; Spring, JPA, JDBC, AMQP and Jackson are banned by the enforcer |
| `patients-adapters` | HTTP, persistence, messaging and owner clients | `patients-core` |
| `patients-app` | composition root, configuration and limits | everything |

## Current increment

The first HU-PAT-001 domain increment lives in `patients-core`: an immutable
registration aggregate, administrative profile, document and emergency contacts,
with contract/storage field validation and an injected registration clock.
See [scope, contract references and TDD evidence](registration-domain.md).
HTTP registration and concrete authorization remain subsequent increments.

The [registration application increment](registration-use-case.md) adds the use case,
authorization and atomic transaction ports, with request fingerprinting and replay results.
Concrete authorization adapters and HTTP wiring remain pending.

The [PostgreSQL registration adapter](registration-persistence.md) now implements atomic
persistence and replay against the existing schema. Runtime DataSource, IAM and HTTP wiring remain pending.

## Dependencies

- PostgreSQL instance from [`dlc-infra-postgres`](https://github.com/code-corhuila/dlc-infra-postgres),
  with the `patients` schema migrated by [`dlc-patient-db`](https://github.com/code-corhuila/dlc-patient-db).
  This repository holds no migrations (Norma 5.2.1).
- Staff JWT and session validation from `dlc-iam-api`.
- Appointments owner contracts for current care relationships and the deactivation guard.

## Run

Build and test (Java 21 and Maven 3.9):

```bash
mvn -B verify
```

Without a local Maven, the same build runs in Docker:

```bash
docker run --rm -v "$PWD:/workspace" -w /workspace maven:3.9.11-eclipse-temurin-21 mvn -B verify
```

Start the service on the shared `platform` network:

```bash
cp .env.example .env
docker compose --env-file .env -f deploy/compose.yml up -d --build --wait
```

`GET /health` answers `200` without a token.

## Branching

Three permanent branches; none accepts a direct commit.

```
develop  <--PR--  feat/... fix/... chore/... docs/...
qa       <--PR--  promotion/qa-patient-api-N (cherry-pick -x of develop commits)
main     <--PR--  release/...  hotfix/...
```

Promotion happens by re-application (`git cherry-pick -x`), never by merging one permanent
branch into another. `main` requires the teacher's approval (`.github/CODEOWNERS`).
