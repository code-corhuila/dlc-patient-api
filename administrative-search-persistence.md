# Patient search persistence (HU-PAT-001)

Implements the `PatientSearchReader` port against the existing Patients schema, following
`07-api/contracts/openapi/patients-service.yaml` and the application use-case contract.
Scope, status and literal case-insensitive name/document filters apply before count and pagination.
Dentist scope uses only the assigned patient IDs supplied by the application; an empty set never
falls back to global data. Results use createdAt DESC / UUID DESC, with contacts loaded locally.
Count, page and contacts share one read-only REPEATABLE READ transaction. SQL parameters are bound,
queries have timeouts, and failures become sanitized PatientSearchUnavailableException outcomes.
The adapter does not query IAM or Appointments schemas or establish identity/assignment authority.

TDD RED: focused test compilation failed before JdbcPatientSearchReader existed. The first database
run exposed a synthetic document exceeding the schema limit; its fixture was corrected to 44 characters.
GREEN: `mvn -o -B clean verify` passed 168 tests without failures/errors/skips against a disposable
PostgreSQL fixture with the 39 canonical dlc-patient-db migrations, using the patients_app role.
The JDBC test covers literal wildcard characters, name/document matching, status, scoped totals,
empty assignments, pagination and emergency contacts. No deployed IAM/Appointments provider was tested.
Pending: runtime composition, search authorization and Appointments adapters, and GET HTTP mapping.
