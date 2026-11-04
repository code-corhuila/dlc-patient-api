package co.edu.corhuila.dlc.patients.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.edu.corhuila.dlc.patients.application.model.AuthorizationFailure;
import co.edu.corhuila.dlc.patients.application.model.PatientNotFoundException;
import co.edu.corhuila.dlc.patients.application.model.PatientReadResult.Administrative;
import co.edu.corhuila.dlc.patients.application.model.PatientReadResult.Care;
import co.edu.corhuila.dlc.patients.application.model.PatientReadUnavailableException;
import co.edu.corhuila.dlc.patients.application.model.PatientSearchPage.Row;
import co.edu.corhuila.dlc.patients.application.port.out.CurrentPatientAssignments;
import co.edu.corhuila.dlc.patients.application.port.out.PatientReadReader;
import co.edu.corhuila.dlc.patients.application.port.out.PatientSearchAuthorization;
import co.edu.corhuila.dlc.patients.application.port.out.PatientSearchAuthorization.Access;
import co.edu.corhuila.dlc.patients.application.port.out.PatientSearchAuthorization.View;
import co.edu.corhuila.dlc.patients.domain.model.Document;
import co.edu.corhuila.dlc.patients.domain.model.PatientProfile;
import co.edu.corhuila.dlc.patients.domain.model.PatientStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ReadPatientServiceTest {
    private static final UUID ACTOR = UUID.randomUUID();
    private static final UUID PATIENT = UUID.randomUUID();
    private static final Row ROW = new Row(PATIENT, new Document("CC", "123"),
            new PatientProfile("Ana", LocalDate.of(2000, 1, 2), "000", "ana@example.invalid", null, null),
            PatientStatus.ACTIVE, Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-02T00:00:00Z"), 2);

    @Test
    void returnsAdministrativeProjectionForAdministrativeAccess() {
        var result = service(View.ADMINISTRATIVE, Optional.of(ROW), ignored -> new CurrentPatientAssignments.Snapshot(ACTOR, 1, Set.of())).read(PATIENT);
        var view = (Administrative) result;
        assertEquals("CC", view.document().type());
        assertEquals("Ana", view.profile().name());
    }

    @Test
    void returnsRestrictedProjectionForAssignedDentist() {
        var result = service(View.CARE, Optional.of(ROW), ignored -> new CurrentPatientAssignments.Snapshot(ACTOR, 1, Set.of(PATIENT))).read(PATIENT);
        var view = (Care) result;
        assertEquals("Ana", view.name());
        assertEquals(2, view.version());
    }

    @Test
    void hidesMissingAndUnassignedPatientsAsNotFound() {
        var missing = service(View.ADMINISTRATIVE, Optional.empty(), ignored -> new CurrentPatientAssignments.Snapshot(ACTOR, 1, Set.of()));
        assertThrows(PatientNotFoundException.class, () -> missing.read(PATIENT));
        var unassigned = service(View.CARE, Optional.of(ROW), ignored -> new CurrentPatientAssignments.Snapshot(ACTOR, 1, Set.of()));
        assertThrows(PatientNotFoundException.class, () -> unassigned.read(PATIENT));
    }

    @Test
    void failsClosedWhenAuthorityChangesOrReaderFails() {
        var calls = new AtomicInteger();
        var changing = service(View.CARE, Optional.of(ROW), ignored -> new CurrentPatientAssignments.Snapshot(ACTOR,
                calls.incrementAndGet(), Set.of(PATIENT)));
        assertThrows(PatientReadUnavailableException.class, () -> changing.read(PATIENT));
        var failing = service(View.ADMINISTRATIVE, (id, scope) -> { throw new IllegalStateException("private storage"); });
        assertThrows(PatientReadUnavailableException.class, () -> failing.read(PATIENT));
    }

    @Test
    void propagatesAuthorizationFailures() {
        var authorization = (PatientSearchAuthorization) () -> { throw new AuthorizationFailure(AuthorizationFailure.Reason.FORBIDDEN); };
        var service = new ReadPatientService(authorization, ignored -> new CurrentPatientAssignments.Snapshot(ACTOR, 1, Set.of()),
                (id, scope) -> Optional.of(ROW));
        assertThrows(AuthorizationFailure.class, () -> service.read(PATIENT));
    }

    private static ReadPatientService service(View view, Optional<Row> result, CurrentPatientAssignments assignments) {
        return service(view, (id, scope) -> result, assignments);
    }

    private static ReadPatientService service(View view, PatientReadReader reader) {
        return new ReadPatientService(() -> new Access(ACTOR, view),
                ignored -> new CurrentPatientAssignments.Snapshot(ACTOR, 1, Set.of(PATIENT)), reader);
    }

    private static ReadPatientService service(View view, PatientReadReader reader, CurrentPatientAssignments assignments) {
        return new ReadPatientService(() -> new Access(ACTOR, view), assignments, reader);
    }
}
