package co.edu.corhuila.dlc.patients.application.port.out;

import co.edu.corhuila.dlc.patients.domain.model.PatientDeactivation;
import java.util.Objects;
import java.util.UUID;
import java.util.Optional;
import java.util.List;

/** Durable local intent; it must commit before any request to Appointments. */
public interface DeactivationIntentStore {
    Operation begin(Intent intent);
    void recordGuard(UUID operationId, long expectedOperationVersion, UUID guardId, String token);
    Optional<Recovery> find(UUID operationId);
    List<Recovery> pending(int limit);
    record Intent(UUID operationId, UUID patientId, UUID actorId, String idempotencyKey, PatientDeactivation.Request request) {
        public Intent {
            Objects.requireNonNull(operationId); Objects.requireNonNull(patientId); Objects.requireNonNull(actorId); Objects.requireNonNull(request);
            if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.codePointCount(0,idempotencyKey.length()) < 8 || idempotencyKey.codePointCount(0,idempotencyKey.length()) > 128) throw new IllegalArgumentException("Invalid idempotency key");
        }
        @Override public String toString() { return "Intent[operationId=" + operationId + ", patientId=" + patientId + ", key=[REDACTED], request=" + request + "]"; }
    }
    record Operation(UUID operationId, String outcome) { }
    record Recovery(UUID operationId, UUID patientId, UUID actorId, UUID guardId, String outcome, long operationVersion) { }
    final class Conflict extends RuntimeException { public Conflict() { super("IDEMPOTENCY_CONFLICT"); } }
    final class GuardRace extends RuntimeException { public GuardRace() { super("Guard state changed; reconcile with Appointments"); } }
    final class Unavailable extends RuntimeException { public Unavailable() { super("Deactivation storage unavailable"); } }
}
