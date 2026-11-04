package co.edu.corhuila.dlc.patients.application.port.in;

import co.edu.corhuila.dlc.patients.application.model.PatientReadResult;
import co.edu.corhuila.dlc.patients.domain.model.PatientProfilePatch;
import java.util.UUID;

public interface UpdatePatientUseCase {
    PatientReadResult.Administrative update(Command command);
    record Command(UUID patientId, int expectedVersion, PatientProfilePatch patch) { }
}
