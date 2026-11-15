package co.edu.corhuila.dlc.patients.application.port.out;

import co.edu.corhuila.dlc.patients.application.model.PatientReadResult;
import java.time.Instant;
import java.util.UUID;

/** Local commit only. The caller must authenticate retries and serialize acquisition, token rotation,
 * local commit and resolution across workers. A matching local hash does not prove current validity at Appointments. */
public interface DeactivationFinalization {
    PatientReadResult.Administrative commit(Commit request);
    record Commit(UUID operationId, UUID actorId, UUID guardId, String token, long operationVersion, UUID auditId,
            UUID eventId, UUID correlationId, UUID causationId, Instant occurredAt) {
        @Override public String toString() { return "Commit[operationId=" + operationId + ", token=[REDACTED]]"; }
    }
    final class Unavailable extends RuntimeException { public Unavailable() { super("Deactivation finalization unavailable"); } }
}
