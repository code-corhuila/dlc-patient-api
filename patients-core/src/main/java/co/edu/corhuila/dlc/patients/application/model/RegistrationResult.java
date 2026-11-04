package co.edu.corhuila.dlc.patients.application.model;

import co.edu.corhuila.dlc.patients.domain.model.Patient;
import java.time.Instant;
import java.util.Objects;

public record RegistrationResult(Patient patient, Instant registeredAt, boolean replayed) {
    public RegistrationResult {
        Objects.requireNonNull(patient, "patient");
        Objects.requireNonNull(registeredAt, "registeredAt");
    }
}
