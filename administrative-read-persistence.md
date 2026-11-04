# Patient administrative read persistence

Implements the `PatientReadReader` port against the Patients-owned schema for
`GET /api/v1/patients/{id}`. The patient identifier and Dentist assignment scope are bound in SQL;
an empty Dentist assignment set never falls back to global data. Administrative access reads any
patient owned by Patients, while both the patient row and emergency contacts are loaded in one
read-only `REPEATABLE READ` transaction with five-second statement timeouts.

The adapter returns an empty result for a missing or out-of-scope patient and maps SQL, malformed
rows or connection failures to the sanitized `PatientReadUnavailableException`. It never reads IAM
or Appointments schemas and never decides identity or assignment authority. The shared JDBC row
mapper keeps search and identifier reads consistent.

`PatientsDataSourceConfiguration` exposes the adapter only with the existing database configuration;
no migration or schema changes are included. The PostgreSQL contract test is enabled by
`TEST_DATABASE_URL` and verifies administrative reads, contacts, Dentist scope and missing IDs.
