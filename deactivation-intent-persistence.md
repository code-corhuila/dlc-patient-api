# Deactivation intent persistence

The JDBC adapter stores an idempotent PENDING intent before guard acquisition, scoped by actor,
patient and key. Identical retries return the original operation ID; a changed request conflicts.
Guard proof storage records only a SHA-256 token hash and permits token rotation for the same guard.
Writes use an expected operation version and stale updates fail with `GuardRace`.
This compare-and-set alone does not order token issuance at Appointments: a failed race must trigger
reconciliation and a fresh acquisition, and callers must coordinate concurrent acquisitions.
Recovery can find an operation by ID or list bounded pending operations; raw tokens are not stored.

This increment does not deactivate a patient, finalize or abort a guard, or recover an uncertain
operation. Those actions require a separate durable transaction and recovery protocol, including
atomic patient state, audit, outbox and operation outcome, plus authoritative Appointments checks.
Never release a guard solely because a request timed out. No cross-service database writes occur.
