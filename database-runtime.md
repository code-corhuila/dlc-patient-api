# Patients runtime DataSource — HU-PAT-001

Basis: API `7a4e916`, Docs service configuration/security rules and Annex C limits;
DB `32cf3b4` supplies schema and runtime grants. This increment owns no migrations.

Database configuration is enabled by default and requires host, port, database name and
a readable UTF-8 password file. The user is fixed to patients_app; JDBC URL credentials,
query parameters and malformed endpoint components are rejected. Secrets are not URL or
environment values: only their paths are configured. One final LF/CRLF is removed while
password spaces are preserved; empty, multiline or unreadable secrets fail without echoing contents.

HikariCP starts on first borrow, with max 10/min idle 0, acquisition/connect limits of 5s,
socket timeout 10s and server statement_timeout 5s. Connections default to schema patients.
Spring wires RegistrationTransaction to the JDBC adapter and closes the pool at shutdown.
Web shutdown allows 20s; Compose allows 30s before termination. /health remains liveness;
lazy pool creation does not prove DB readiness. PATIENTS_DB_ENABLED=false explicitly
supports liveness-only tests and supplies neither DataSource nor registration transaction.

Copy .env.example to the ignored .env. Provision the database using the DB repository,
then set PATIENTS_DB_SECRETS_DIR to a dedicated external directory containing password and,
when required, root.crt. The directory must already exist and be readable by the container's
patients user; Compose mounts it read-only. For direct Java execution, set
PATIENTS_DB_PASSWORD_FILE to the actual local file instead of the container path.
The example is dev-only. Set environment, host, project and secrets separately for qa/main.
TLS defaults to verify-full; disable is accepted only in dev. QA/main/prod require the
approved CA and hostname verification. Actual production TLS/secret-manager bindings remain operator work.

TDD RED: missing configuration type, then regression cases for host userinfo/query/fragment.
Tests cover settings, secret handling, TLS policy, managed closure and the explicitly disabled mode.
With the existing TEST_DATABASE_* fixture, the pool integration test verifies the actual
patients_app role, schema, timeout, connection reuse and a commit through the wired adapter.
Run `mvn -B clean verify`; absent TEST_DATABASE_URL skips real-engine tests.
IAM authorization, use-case/HTTP wiring, readiness and deployment acceptance remain pending.

Validated 2026-11-04: `mvn -o -B clean verify` PASS, 60 tests, zero failures/skips.
Seven integration cases used PostgreSQL 17.11 and patients_app; the disposable fixture was removed.
Compose validation passed for the read-only existing secret directory and 30s shutdown grace.
TLS policy was tested as configuration; a real TLS handshake was not exercised.
