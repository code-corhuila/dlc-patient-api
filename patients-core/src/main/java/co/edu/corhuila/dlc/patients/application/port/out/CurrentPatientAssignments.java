package co.edu.corhuila.dlc.patients.application.port.out;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@FunctionalInterface
public interface CurrentPatientAssignments {
    /** Complete authoritative Appointments snapshot, all pages at one version; no historical/cache fallback. */
    Snapshot currentFor(UUID dentistId);
    record Snapshot(UUID dentistId, long assignmentsVersion, Set<UUID> patientIds) {
        public Snapshot {
            Objects.requireNonNull(dentistId);
            if (assignmentsVersion < 1) throw new IllegalArgumentException("Invalid assignment version");
            patientIds = Set.copyOf(patientIds);
        }
    }
}
