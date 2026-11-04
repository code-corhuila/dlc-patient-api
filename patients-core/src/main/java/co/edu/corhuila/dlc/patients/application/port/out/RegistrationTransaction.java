package co.edu.corhuila.dlc.patients.application.port.out;

import co.edu.corhuila.dlc.patients.application.model.RegistrationResult;
import co.edu.corhuila.dlc.patients.domain.model.Patient;
import java.time.Instant;
import java.util.UUID;

public interface RegistrationTransaction {
    /**
     * One local transaction: claim/replay the actor + register + /patients + key scope,
     * enforce normalized document uniqueness across statuses, and persist patient/contacts,
     * REGISTERED audit, PatientRegistered v1 outbox and original response (retained >=168h).
     * Replay returns the stored result; hash/document conflicts raise RegistrationConflictException.
     * Map the external key deterministically to the DB UUID. Never publish to the broker here.
     */
    RegistrationResult register(Write write);

    record Write(Patient patient, UUID actorId, String idempotencyKey, String requestHash,
            UUID auditId, UUID eventId, Instant registeredAt, UUID correlationId, UUID causationId) {
    }
}
