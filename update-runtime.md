# Patient administrative update runtime

The update runtime is opt-in: set `PATIENTS_UPDATE_ENABLED=true` and configure
`PATIENTS_IAM_UPDATE_PERMISSION` to the approved IAM write permission. A missing permission,
JWT decoder, IAM session client or update transaction prevents startup when enabled.
The authorization verifier checks current staff sessions, the configured permission and eligible
administrative roles. IAM must actually issue the approved permission; configuration alone does not
integrate or modify IAM. When enabled, `PATCH /api/v1/patients/{id}` accepts the documented
administrative patch and returns the updated administrative projection.
