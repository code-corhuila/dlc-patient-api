# Deactivation recovery policy

This increment defines only a conservative decision policy over durable local evidence.
FINALIZED plus matching inactive patient proof permits FINALIZE; ABORTED plus matching active
patient proof permits ABORT. Pending, missing, unknown or inconsistent evidence requires retry.
Timeout is never evidence of abort. Matching evidence must bind the operation and patient version,
and only an authoritative committed snapshot may be used.

The policy is not a worker, a guard coordinator or a database query and does not enable deactivation.
Appointments must support guarded idempotent acquisition, token rotation and resolution. A durable
way to discover operations whose local outcome is terminal but whose remote resolution is unknown,
plus concurrency control across recovery workers, remains required before this policy is connected.
No real Appointments behavior or crash recovery is validated by these unit tests.
