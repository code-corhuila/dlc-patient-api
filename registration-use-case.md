# Registration application increment — HU-PAT-001

Basis: Docs `3035ceb`, Patients OpenAPI, authentication policy, owner events and
DB `32cf3b4` dictionary. API base: `b636112`. Domain rules remain in Patient.

The input port accepts document/profile, an 8–128-character retry key and tracing.
The authorization port must verify identity, current session and Administrator/Assistant
permission on every attempt, including replay; caller-supplied staff IDs are not accepted.
The application generates patient/audit/event UUIDs and captures one trusted clock instant.
It builds a versioned SHA-256 payload fingerprint using length-prefixed fields, explicit
nulls and ordered contacts. Keys, tracing, actor and generated metadata are excluded from
the payload hash; the transaction scopes retries separately by actor/register/resource/key.

One output-port operation must atomically claim/replay the key, enforce the existing
normalized document index, and save profile/contacts, REGISTERED audit, PatientRegistered
v1 outbox and original response. It must retain the response for at least 168 hours and
map the external key deterministically to the DB UUID. A replay returns the saved patient;
the result retains the original registration instant. Conflicts use the canonical codes. Failures propagate
without application retries. Broker publication belongs to a later outbox relay.

TDD RED: the focused test command failed before implementation because the application
types did not exist. GREEN: the first 10 cases passed; refactoring removed repeated key
length calculation and expanded fingerprint coverage. A second RED exposed the missing
original registration instant in the result before it was implemented. Reproduce with
`mvn -o -B -pl patients-core -Dtest=RegisterPatientServiceTest test` and `mvn -o -B verify`.

The recording transaction proves application orchestration only. Real authorization,
SQL transactions, concurrent replay/duplicates, response serialization and HTTP wiring
remain adapter increments; this branch does not complete HU-PAT-001 or modify migrations.

Final validation (2026-11-04): `mvn -o -B clean verify` PASS; 36 tests, zero failures.
The initial sandbox run blocked Java attach; the final run completed outside the sandbox.
