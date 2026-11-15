# Patient search application increment (HU-PAT-001)

Sources: dlc-docs `07-api/contracts/openapi/patients-service.yaml` (GET /patients),
`07-api/authentication.md`, Patients owner decisions and the Appointments assigned-patients snapshot contract.
Only the framework-free application core is added; no GET route, JDBC reader or runtime bean is introduced.
Search authorization has its own port: current identity/session, eligible role AND an explicit operation permission.
Administrator/Assistant use the administrative view; Dentist receives only id/name/phone/email/status/version.
Inputs default to page 1/limit 20, enforce page >= 1, limit 1–100, search length 1–255 Unicode code points,
and exact ACTIVE/INACTIVE status. Offset/metadata arithmetic handles large values without integer overflow.
The reader must apply scope/status/literal case-insensitive name-or-document search before count/pagination,
using one consistent rows/count snapshot ordered by createdAt DESC then PostgreSQL UUID DESC.
Full administrative data is an internal typed read model, not the future HTTP serialization shape.
Dentist scope requires a fresh complete Appointments snapshot before the query, including all owner pages
at one assignmentsVersion. A changed snapshot before return, unknown authority or out-of-scope rows fails closed.
Empty assignments return zero results without a global reader call; metadata is restricted to the same scope.
Current identity/session/operation access is rechecked before returning any page. No authorization cache is added.
Storage/assignment failures use a sanitized PatientSearchUnavailableException; IAM authorization failures retain their reason.
TDD RED: focused compilation failed for the absent search use case and ports.
GREEN: `mvn -o -B clean verify` passed 167 tests (24 search cases), zero failures/errors/skips, with disposable PostgreSQL.
Run: `mvn -B -pl patients-core -Dtest=SearchPatientsServiceTest test`.
Tests use controlled ports, not deployed IAM/Appointments or a real search SQL adapter.
Pending: concrete search permission mapping, IAM adapter, approved Appointments consumer access/service credentials,
complete version-consistent assignment traversal, JDBC scoped filtering/count/order, composition and GET HTTP mapping.
