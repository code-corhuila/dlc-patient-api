## User story

Refs: code-corhuila/dlc-docs#53 — HU-PAT-001
Replace the reference if another issue applies.

## What changes and why

## How it was tested

Record the RED output before the change and the GREEN result after it.

| Criterion | Command / CI job | Result |
|---|---|---|
| Replace with the affected criterion | `mvn -B verify` | Pass/fail/not executed |

## Promotion trail

For develop: N/A. For qa/main: list each re-applied commit with its `(cherry picked from commit <sha>)` line.

## Checklist

- [ ] Follows the Patients contracts in dlc-docs (`07-api/contracts/openapi/patients-service.yaml`).
- [ ] No schema or migration changes here; the schema lives only in dlc-patient-db.
- [ ] No secrets, tokens, `.env` files or real patient data.
- [ ] At most 400 changed lines, excluding tests and generated files.
- [ ] Commits follow Conventional Commits; CODEOWNERS is preserved.
