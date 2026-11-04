# Registration authorization adapter (HU-PAT-001)

Sources: dlc-docs `07-api/authentication.md`, `07-api/contracts/openapi/patients-service.yaml`
and `07-api/contracts/openapi/internal-coordination.yaml` (session validations).
The core port now has a JWT adapter: pinned RSA public key/kid, RS256, required claims,
issuer/audience, 30-second clock skew, one-hour maximum lifetime, role AND permission.
Every attempt rechecks IAM with a separate service bearer and the staff token in the JSON body.
Inactive sessions produce UNAUTHORIZED; dependency errors produce SERVICE_UNAVAILABLE;
eligible staff without the required role/permission produce FORBIDDEN. Exceptions omit credentials.
The permission is mandatory configuration: docs does not yet define its literal IAM catalog value.
TDD: focused Maven test initially failed for missing implementation; expanded RED had 36 tests,
3 failures (missing audience, trailing JSON, duplicate active). GREEN: 36 tests, zero failures/skips.
Full regression: `mvn -o -B clean verify` passed 96 tests, zero failures/errors/skips, with disposable migrated PostgreSQL.
IAM tests use an HTTP fixture and generated RSA keys, not a deployed IAM provider.
Runtime JWKS and use-case wiring are delivered in [runtime connection](authorization-runtime.md).
Remaining: service credential provisioning/renewal, approved permission mapping, HTTP registration
and deployed IAM verification.
