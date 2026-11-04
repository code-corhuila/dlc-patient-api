package co.edu.corhuila.dlc.patients.application.port.in;

import co.edu.corhuila.dlc.patients.application.model.PatientReadResult;
import java.util.UUID;

@FunctionalInterface
public interface ReadPatientUseCase {
    PatientReadResult read(UUID patientId);
}
