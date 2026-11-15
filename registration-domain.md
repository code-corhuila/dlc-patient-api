# Patient registration domain — HU-PAT-001

This increment implements the registration model in the framework-free core.
It does not complete HU-PAT-001 or expose a registration endpoint.

## Contract basis

Reviewed snapshots: Docs `3035ceb`, DB `32cf3b4`; API base `6a0bf04`.

- `dlc-docs/02-domain/entities-and-rules.md`: patient identity and administrative boundaries.
- `dlc-docs/07-api/contracts/openapi/patients-service.yaml`: CreatePatient fields and limits.
- `dlc-docs/09-microservices/services/03-patients-service/data-model.md`: ACTIVE registration and positive version.
- `dlc-patient-db/data-dictionary.md`: nullable address, optional contacts and storage constraints.

## Behavior and boundaries

`Patient.register` receives a trusted UUID and Clock, rejects a birth date on or after
the clock's current date, and creates an immutable ACTIVE profile at version 1.
Display document values, including leading zeroes and spaces, are retained. Document
record equality is display equality, not a uniqueness check. The persistence adapter
must rely on PostgreSQL's `upper(btrim(...))` unique index, including inactive patients;
Java casing is deliberately not substituted for database collation behavior.

Lengths count Unicode code points, matching OpenAPI lengths and PostgreSQL char_length.
Required text is nonblank; email follows the basic storage format. Phone checks cover
the documented required/length constraints without inventing a country-specific format.
Contacts may be omitted or repeated; their list is copied and exposed as immutable.
Validation errors identify the field/rule without including supplied profile values.

The application must later derive the authorized actor, generate identifiers and use
the trusted clock. Authorization, normalized uniqueness under concurrency, persistence,
audit metadata, HTTP idempotency and transactional lifecycle events remain separate work.

## TDD evidence

Run with Java 21 and Maven 3.9: `mvn -B -pl patients-core test`.
RED was executed before implementation: exit 1, test compilation failed because the
domain types did not exist. GREEN: 22 domain cases passed. Refactoring separates
required-profile/contact checks and simplifies shared validation. Final `mvn -B verify`
passed on 2026-11-04: 24 domain cases and the existing HTTP health case, zero failures.
The core dependency enforcer also passed. Local Surefire reports are under each module's target.
