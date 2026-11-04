package co.edu.corhuila.dlc.patients.application.usecase;

import static org.junit.jupiter.api.Assertions.*;
import co.edu.corhuila.dlc.patients.application.port.in.UpdatePatientUseCase;
import co.edu.corhuila.dlc.patients.application.port.out.PatientUpdateTransaction;
import co.edu.corhuila.dlc.patients.domain.model.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class UpdatePatientServiceTest {
    private final UUID patient = UUID.randomUUID(), actor = UUID.randomUUID();
    private final PatientProfile original = new PatientProfile("Ana", LocalDate.of(2000, 1, 1), "000", "a@example.invalid", null, List.of());

    @Test void sendsAuthorizedPatchAndServerAuditContextToTransaction() {
        var captured = new AtomicReference<PatientUpdateTransaction.Write>();
        var service = new UpdatePatientService(() -> actor, write -> { captured.set(write); return null; }, Clock.systemUTC(), UUID::randomUUID);
        var patch = new PatientProfilePatch(null, null, "New", null, null, null, null, null);
        assertThrows(NullPointerException.class, () -> service.update(new UpdatePatientUseCase.Command(patient, 2, patch)));
        assertEquals(actor, captured.get().actorId());
        assertEquals(2, captured.get().expectedVersion());
        assertNotNull(captured.get().auditId());
    }

    @Test void appliesOnlySuppliedFieldsAndRejectsNoChange() {
        var patch = new PatientProfilePatch(null, null, "New", null, null, null, null, null);
        var changed = patch.apply(new Document("CC", "123"), original, Clock.systemUTC());
        assertEquals("New", changed.profile().name());
        assertEquals("000", changed.profile().phone());
        assertThrows(InvalidPatientDataException.class, () -> new PatientProfilePatch(null, null, "Ana", null, null, null, null, null)
                .apply(new Document("CC", "123"), original, Clock.systemUTC()));
    }

    @Test void rejectsEmptyPatchAndFutureBirthDate() {
        assertThrows(InvalidPatientDataException.class, () -> new PatientProfilePatch(null, null, null, null, null, null, null, null));
        var patch = new PatientProfilePatch(null, null, null, LocalDate.of(2999, 1, 1), null, null, null, null);
        assertThrows(InvalidPatientDataException.class, () -> patch.apply(new Document("CC", "123"), original, Clock.systemUTC()));
    }
}
