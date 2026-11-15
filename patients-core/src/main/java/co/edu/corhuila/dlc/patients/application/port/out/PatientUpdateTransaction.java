package co.edu.corhuila.dlc.patients.application.port.out;

import co.edu.corhuila.dlc.patients.application.model.PatientReadResult;
import co.edu.corhuila.dlc.patients.domain.model.PatientProfilePatch;
import java.time.Instant;
import java.util.UUID;

@FunctionalInterface
public interface PatientUpdateTransaction {
    /** Atomically validate version and actual change, enforce document uniqueness, update and persist audit and outbox. */
    PatientReadResult.Administrative update(Write write);
    record Write(UUID patientId, int expectedVersion, PatientProfilePatch patch, UUID actorId,
            UUID auditId, UUID eventId, Instant updatedAt, UUID correlationId, UUID causationId) { }
}
