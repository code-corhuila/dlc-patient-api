# Patient search runtime connection

The runtime composition now wires the search use case, the JDBC reader, current authorization
and the Appointments assigned-patients contract. This follows the Patients search contract and
the private `GET /internal/v1/dentists/{id}/assigned-patients` contract in dlc-docs.
Appointments is queried with a separate service credential, HTTPS outside dev, no redirects,
five-second timeouts and complete pagination at one `assignmentsVersion`. Patients never reads
Appointments or IAM tables directly. A changed assignment snapshot, malformed response, unavailable
authority or missing credential fails closed.

The search JWT requires the configured search permission and maps Administrator/Assistant to the
administrative view and Dentist to the restricted care projection. Sessions are revalidated per request.
The public `GET /api/v1/patients` controller maps the documented query parameters, pagination metadata,
administrative projection and Dentist care projection without exposing clinical fields.
The same runtime now wires `ReadPatientUseCase` when the JDBC `PatientReadReader` is available,
reusing the validated authorization and current Appointments assignment snapshot for identifier reads.
Set `PATIENTS_IAM_SEARCH_PERMISSION`, `PATIENTS_APPOINTMENTS_BASE_URL`, and the read-only
`PATIENTS_APPOINTMENTS_SECRETS_DIR` mount before enabling runtime search. The approved permission and
deployed Appointments service credential remain deployment requirements.

TDD GREEN: runtime composition and HTTP tests cover bean wiring, disabled search, missing permission, HTTPS policy,
query mapping, projections and sanitized error responses.
The full regression remains the release check with disposable PostgreSQL.
