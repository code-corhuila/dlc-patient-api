# Changelog

All notable changes of `dlc-patient-api` are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the versions follow
[Semantic Versioning](https://semver.org/). A release is tagged `v<version>` once it is merged into `main`.

## [Unreleased]

## [3.0.0] - 2026-11-14

The changelog starts at this release.

### Added

- Add registration domain for HU-PAT-001.
- Add registration use case and ports.
- Add atomic PostgreSQL registration adapter.
- Configure runtime datasource and connection pool.
- Add registration authorization adapters.
- Wire registration authorization with IAM.
- Expose patient registration endpoint.
- Add authorized patient search use case.
- Add JDBC patient search persistence.
- Wire administrative patient search runtime.
- Expose administrative patient search endpoint.
- Add authorized patient read use case.
- Add JDBC patient read adapter.
- Wire patient read runtime.
- Expose patient read endpoint.
- Add patient administrative update domain.
- Persist patient profile updates.
- Wire patient update runtime.
- Expose patient update endpoint.
- Add patient deactivation domain.
- Persist deactivation intents.
- Persist finalization transaction.
- Add recovery decision policy.
- Add internal state validation foundation.
- Secure internal validation endpoint.
- Expose private billing contact for billing and worker.
- Obtain service tokens from IAM by client credentials (ADR-020).

### Fixed

- Align update and assignment contracts.
- Align HU-PAT-001 permissions, readiness and correlation.
- Require approved IAM permission names.
- Make the internal listener bind address configurable.
