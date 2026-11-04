# PostgreSQL registration adapter — HU-PAT-001

Basis: API `ba5f6be`, Docs `3035ceb`, DB `32cf3b4`. No migrations are copied or edited.
JdbcRegistrationTransaction receives a runtime DataSource and writes only patients-owned tables.
READ COMMITTED plus a transaction-scoped advisory lock serializes actor/register/resource/key
retries. External UTF-8 keys use UUID.nameUUIDFromBytes; preserve this mapping once deployed.
The existing normalized document index also covers inactive profiles. Patient/contacts,
REGISTERED audit, PatientRegistered v1 outbox and the original 201 response commit together.
Responses retain their original ID/profile/time after later profile updates; absent addresses
are omitted and dates use ISO strings. A dedicated domain factory rehydrates committed
registrations without applying today's date rules again. No broker publication occurs here.
Statements use bound parameters and five-second timeouts; connection state is restored.
Conflicts use canonical codes; other failures are sanitized without exposing SQL values.

Run migrations using dlc-patient-db/environment-bootstrap.md in a disposable database named
patients_api_test_<hex>, then set TEST_DATABASE_URL, TEST_DATABASE_USER=patients_app and
TEST_DATABASE_PASSWORD externally. Run `mvn -B clean verify`; absent URL skips integration tests.
RED: tests failed because the adapter did not exist. Real-engine checks cover atomic writes,
snapshot replay/hash conflicts, actor isolation, active/inactive uniqueness, outbox-failure
rollback and concurrent same-key replay. Runtime DataSource/pool, IAM and HTTP wiring remain
pending; this persistence increment does not complete HU-PAT-001 or configure remote CI.

Validated 2026-11-04: `mvn -o -B clean verify` PASS, 42 tests, zero failures/skips.
Six integration tests used PostgreSQL 17.11, the canonical 39-change schema and patients_app.
