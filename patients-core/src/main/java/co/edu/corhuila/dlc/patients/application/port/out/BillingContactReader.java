package co.edu.corhuila.dlc.patients.application.port.out;

import co.edu.corhuila.dlc.patients.application.port.in.ReadBillingContactUseCase;
import java.util.Optional;
import java.util.UUID;

/** Empty when the patient does not exist or has no registered email. */
public interface BillingContactReader {
    Optional<ReadBillingContactUseCase.Contact> find(UUID patientId);
}
