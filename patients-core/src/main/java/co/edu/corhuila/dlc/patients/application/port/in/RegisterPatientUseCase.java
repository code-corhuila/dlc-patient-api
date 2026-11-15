package co.edu.corhuila.dlc.patients.application.port.in;

import co.edu.corhuila.dlc.patients.application.model.RegistrationResult;
import co.edu.corhuila.dlc.patients.domain.model.Document;
import co.edu.corhuila.dlc.patients.domain.model.PatientProfile;
import java.util.UUID;

public interface RegisterPatientUseCase {
    RegistrationResult register(Command command);

    /** Tracing comes from the request boundary; staff identity is never supplied in the payload. */
    record Command(Document document, PatientProfile profile, String idempotencyKey,
            UUID correlationId, UUID causationId) {
    }
}
