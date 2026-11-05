package co.edu.corhuila.dlc.patients.domain.model;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import co.edu.corhuila.dlc.patients.application.port.in.DeactivatePatientUseCase;
import co.edu.corhuila.dlc.patients.application.port.out.DeactivationCoordinator;
import java.util.UUID;

class PatientDeactivationTest {
    @Test void activeWithMatchingVersionIsEligibleForGuardAcquisition() {
        var request = new PatientDeactivation.Request(PatientDeactivation.ReasonCode.PATIENT_REQUEST, "Requested", 4);
        assertDoesNotThrow(() -> PatientDeactivation.requireEligible(request, PatientStatus.ACTIVE, 4));
    }
    @Test void refusesInactiveAndStaleVersions() {
        var request = new PatientDeactivation.Request(PatientDeactivation.ReasonCode.OTHER, "Reason", 3);
        assertEquals(PatientDeactivationException.Reason.INVALID_STATUS_TRANSITION,
                assertThrows(PatientDeactivationException.class, () -> PatientDeactivation.requireEligible(request, PatientStatus.INACTIVE, 3)).reason());
        assertEquals(PatientDeactivationException.Reason.STALE_VERSION,
                assertThrows(PatientDeactivationException.class, () -> PatientDeactivation.requireEligible(request, PatientStatus.ACTIVE, 4)).reason());
    }
    @Test void requiresClosedCodeReasonAndPositiveVersion() {
        assertThrows(InvalidPatientDataException.class, () -> new PatientDeactivation.Request(null, "Reason", 1));
        assertThrows(InvalidPatientDataException.class, () -> new PatientDeactivation.Request(PatientDeactivation.ReasonCode.OTHER, "  ", 1));
        assertThrows(InvalidPatientDataException.class, () -> new PatientDeactivation.Request(PatientDeactivation.ReasonCode.OTHER, "x".repeat(1001), 1));
        assertThrows(InvalidPatientDataException.class, () -> new PatientDeactivation.Request(PatientDeactivation.ReasonCode.OTHER, "Reason", 0));
    }
    @Test void reasonLimitCountsUnicodeCodePoints() {
        assertDoesNotThrow(() -> new PatientDeactivation.Request(PatientDeactivation.ReasonCode.ADMINISTRATIVE, "😀".repeat(1000), 1));
        assertThrows(InvalidPatientDataException.class, () -> new PatientDeactivation.Request(PatientDeactivation.ReasonCode.OTHER, "😀".repeat(1001), 1));
    }
    @Test void commandRequiresScopedRetryKeyAndRedactsPrivateValues() {
        var request = new PatientDeactivation.Request(PatientDeactivation.ReasonCode.OTHER, "private reason", 1);
        assertThrows(InvalidPatientDataException.class, () -> new DeactivatePatientUseCase.Command(UUID.randomUUID(), request, "short", UUID.randomUUID(), UUID.randomUUID()));
        var command = new DeactivatePatientUseCase.Command(UUID.randomUUID(), request, "private-key", UUID.randomUUID(), UUID.randomUUID());
        assertFalse(command.toString().contains("private reason")); assertFalse(command.toString().contains("private-key"));
        var guard = new DeactivationCoordinator.Guard(UUID.randomUUID(), "private-token");
        assertFalse(guard.toString().contains("private-token"));
    }
}
