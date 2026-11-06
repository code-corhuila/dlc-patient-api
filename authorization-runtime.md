# Patients to IAM runtime connection (HU-PAT-001)

Only Patients is changed. IAM API and DB were inspected read-only against dlc-docs
`07-api/authentication.md` and `07-api/contracts/openapi/internal-coordination.yaml`.
Inspected source revisions: IAM API `4630ec2`, IAM DB `05b72fe`.
Patients obtains RS256 public keys from `/api/v1/auth/jwks`, validates staff tokens locally,
then calls `/internal/v1/sessions/validations` with a separate service bearer.
The registration use case and authorization port are now wired; no patient HTTP route is added.
Identity comes from the current request bearer, never caller-supplied identity headers.
Both transports have five-second timeouts and reject redirects; HTTPS is mandatory outside dev.
Nimbus caches public keys (default five minutes) and refetches for an unknown signing key ID.
Session eligibility is checked on every attempt, including replay; it is never cached here.

Set every PATIENTS_IAM_* variable listed in `.env.example`. Issuer/audience must match
IAM_JWT_ISSUER/IAM_JWT_AUDIENCE exactly; IAM currently emits a single configured staff audience.
Mount PATIENTS_IAM_SECRETS_DIR read-only with a one-line `service-token` file. A credential provider
must provision/renew a signed, short-lived service JWT accepted by IAM_INTERNAL_SERVICE_JWKS_URI,
IAM_INTERNAL_SERVICE_ISSUER and IAM_INTERNAL_SERVICE_AUDIENCE, with `identity:validate` scope.
Patients rereads the file per IAM call; replace it atomically within the mounted directory.
Patients holds no IAM signing private key and never reads the IAM schema directly.
IAM configuration is required by default; disabling it creates no registration use-case bean.

Auth [PR #83](https://github.com/code-corhuila/dlc-iam-api/pull/83) issues the approved
`patients:register`, `patients:read` and `patients:write` claims from current roles.
Until that PR is merged and deployed, Auth `develop` may still issue `permissions: []` and
these operations remain forbidden. Patients accepts only the exact approved permission
names and still checks the current IAM session and owner-side role/assignment rules.
Service credential issuance and renewal remain deployment responsibilities.
IAM has no Compose service here; do not assume a deployed hostname or working shared network.

TDD RED: focused Maven test failed compilation because PatientsAuthorizationConfiguration was absent.
Expanded RED: 13 runtime tests, one failure because a JWKS HTTP 302 body was accepted; only HTTP 200 is now accepted.
GREEN: `mvn -o -B clean verify` passed 109 tests, zero failures/errors/skips, against disposable migrated PostgreSQL.
Tests use generated RSA keys and HTTP fixtures matching IAM wire formats, not a running IAM deployment.
Run: `mvn -B -pl patients-app -am -Dtest=PatientsAuthorizationConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test`.
Patient HTTP registration/error mapping is delivered in [registration HTTP](registration-http.md).
Deployed IAM/TLS/provider verification remains pending.
