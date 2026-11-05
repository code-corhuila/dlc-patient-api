package co.edu.corhuila.dlc.patients.application.port.out;

import java.util.Optional;
import java.util.UUID;

public interface PatientValidationReader {
    Optional<State> find(UUID patientId);
    record State(boolean active, long version) {
        public State { if (version < 1) throw new IllegalArgumentException("Invalid patient version"); }
    }
}
