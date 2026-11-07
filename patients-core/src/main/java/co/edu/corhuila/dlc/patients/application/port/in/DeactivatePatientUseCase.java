package co.edu.corhuila.dlc.patients.application.port.in;

import co.edu.corhuila.dlc.patients.domain.model.PatientDeactivation;
import co.edu.corhuila.dlc.patients.domain.model.InvalidPatientDataException;
import co.edu.corhuila.dlc.patients.application.model.PatientReadResult;
import java.util.UUID;

/** Implementations must authorize Administrator before starting or replaying an operation. */
public interface DeactivatePatientUseCase {
    PatientReadResult.Administrative deactivate(Command command);
    record Command(UUID patientId, PatientDeactivation.Request request, String idempotencyKey,
            UUID correlationId, UUID causationId) {
        public Command {
            int length = idempotencyKey == null ? 0 : idempotencyKey.codePointCount(0, idempotencyKey.length());
            if (patientId == null || request == null || correlationId == null || causationId == null)
                throw new InvalidPatientDataException("command", "required fields missing");
            if (length < 8 || length > 128 || idempotencyKey.isBlank())
                throw new InvalidPatientDataException("Idempotency-Key", "must have 8 to 128 characters");
        }
        @Override public String toString() { return "Command[patientId=" + patientId + ", request=" + request + ", idempotencyKey=[REDACTED]]"; }
    }
}
