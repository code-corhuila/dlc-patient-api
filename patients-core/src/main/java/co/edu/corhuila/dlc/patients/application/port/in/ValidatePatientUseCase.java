package co.edu.corhuila.dlc.patients.application.port.in;

import java.util.UUID;

public interface ValidatePatientUseCase {
    Result validate(UUID patientId);
    record Result(UUID patientId, boolean active, long version) {}
}
