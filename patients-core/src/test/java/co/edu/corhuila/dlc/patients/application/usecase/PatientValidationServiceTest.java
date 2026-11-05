package co.edu.corhuila.dlc.patients.application.usecase;

import static org.junit.jupiter.api.Assertions.*;
import co.edu.corhuila.dlc.patients.application.model.PatientNotFoundException;
import co.edu.corhuila.dlc.patients.application.port.out.PatientValidationReader;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PatientValidationServiceTest {
    @Test void returnsOnlyTheAuthoritativeStateAndVersion() {
        var id = UUID.randomUUID();
        var service = new PatientValidationService(patientId -> Optional.of(new PatientValidationReader.State(false, 3)));
        var result = service.validate(id);
        assertEquals(id, result.patientId());
        assertFalse(result.active());
        assertEquals(3, result.version());
    }

    @Test void rejectsUnknownPatientsAndInvalidStates() {
        var service = new PatientValidationService(id -> Optional.empty());
        assertThrows(PatientNotFoundException.class, () -> service.validate(UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> new PatientValidationReader.State(true, 0));
    }
}
