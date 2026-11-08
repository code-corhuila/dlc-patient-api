package co.edu.corhuila.dlc.patients.application.usecase;

import static org.junit.jupiter.api.Assertions.*;
import co.edu.corhuila.dlc.patients.application.model.PatientNotFoundException;
import co.edu.corhuila.dlc.patients.application.port.in.ReadBillingContactUseCase;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BillingContactServiceTest {
    @Test void returnsOnlyTheMinimumContact() {
        var id = UUID.randomUUID();
        var contact = new ReadBillingContactUseCase.Contact(id, "ana@example.test", "Ana García");
        assertEquals(contact, new BillingContactService(patient -> Optional.of(contact)).read(id));
    }

    @Test void treatsAPatientWithoutDeliverableEmailAsNotFound() {
        var service = new BillingContactService(patient -> Optional.empty());
        assertThrows(PatientNotFoundException.class, () -> service.read(UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class,
                () -> new ReadBillingContactUseCase.Contact(UUID.randomUUID(), " ", "Ana"));
        assertThrows(IllegalArgumentException.class,
                () -> new ReadBillingContactUseCase.Contact(UUID.randomUUID(), "ana@example.test", ""));
    }
}
