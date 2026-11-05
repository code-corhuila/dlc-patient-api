package co.edu.corhuila.dlc.patients.application.port.out;

import java.util.UUID;

/** Appointments owns durable guards; timeout never proves abort and guards do not expire automatically. */
public interface DeactivationCoordinator {
    /** Acquire idempotently for the persisted operation ID; retries may rotate the secret token. */
    Guard acquire(UUID patientId, UUID operationId);
    /** Finalize only after a durable INACTIVE commit. */
    void finalizeGuard(UUID patientId, UUID operationId, Guard guard);
    /** Abort only after authoritative proof of a durable non-commit. */
    void abortGuard(UUID patientId, UUID operationId, Guard guard);

    record Guard(UUID guardId, String token) {
        public Guard {
            if (guardId == null || token == null || token.isBlank()) throw new IllegalArgumentException("Invalid guard proof");
        }
        @Override public String toString() { return "Guard[guardId=" + guardId + ", token=[REDACTED]]"; }
    }
}
