package co.edu.corhuila.dlc.patients.application.port.out;

import co.edu.corhuila.dlc.patients.application.model.PatientSearchPage.Row;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@FunctionalInterface
public interface PatientReadReader {
    /** Read one administrative row inside the already-authorized scope. */
    Optional<Row> findById(UUID patientId, Scope scope);

    record Scope(PatientSearchAuthorization.Access access, Set<UUID> assignedPatientIds) {
        public Scope {
            Objects.requireNonNull(access);
            assignedPatientIds = Set.copyOf(assignedPatientIds);
        }
    }
}
