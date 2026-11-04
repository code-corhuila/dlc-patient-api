# Patient registration HTTP boundary (HU-PAT-001)

Implements POST `/api/v1/patients` from dlc-docs `07-api/contracts/openapi/patients-service.yaml`,
the shared ErrorResponse/WWW-Authenticate contract, API guidelines and authentication rules.
Accepts only the flat CreatePatient administrative fields, with strict types, required fields,
closed nested contacts, valid past ISO dates and one Idempotency-Key of 8–128 characters.
Unknown, duplicate, trailing JSON and explicit null optional fields are rejected with 400.
Returns the flat Patient representation: 201 for creation, 200 for an authorized replay,
the original timestamps/version and a Gateway-relative Location independent of caller Host headers.
The boundary authorizes before parsing; the use case independently rechecks before persistence/replay.
This intentionally performs two IAM session checks for a valid request, with no cached authorization.
Actor identity comes from the authorization port; correlation/causation UUIDs are generated server-side.
Errors retain error/message/traceId, use a Bearer challenge on 401 and omit sensitive exception details.
Duplicate document/key conflicts remain distinct 409 codes; storage outages return 503, unexpected errors 500.
Responses use no-store. No IAM account, schema/migration or IAM repository changes are introduced.
Registration is enabled by default; disabling IAM alone cannot start an exposed registration endpoint.
The dependency-free health test explicitly disables registration, IAM and DB.

TDD RED: focused Maven compilation failed because PatientRegistrationController did not exist.
GREEN: `mvn -o -B clean verify` passed 143 tests, zero failures/errors/skips, with disposable migrated PostgreSQL.
Run: `mvn -B -pl patients-adapters -am -Dtest=PatientRegistrationControllerTest -Dsurefire.failIfNoSpecifiedTests=false test`.
HTTP tests use the real use case and controlled authorization/storage ports; an additional HTTP-to-PostgreSQL
test verifies one committed patient, audit, outbox event and journal entry across creation/replay.
Pending: deployed IAM/TLS verification, approved IAM permissions/service credentials, other patient routes,
private coordination endpoints and outbox publication. Current IAM empty permissions still forbid registration.
