package co.edu.corhuila.dlc.patients.application.usecase;

import co.edu.corhuila.dlc.patients.application.model.PatientNotFoundException;
import co.edu.corhuila.dlc.patients.application.port.in.ReadBillingContactUseCase;
import co.edu.corhuila.dlc.patients.application.port.out.BillingContactReader;
import java.util.Objects;
import java.util.UUID;

public final class BillingContactService implements ReadBillingContactUseCase {
    private final BillingContactReader reader;
    public BillingContactService(BillingContactReader reader) { this.reader = Objects.requireNonNull(reader); }
    @Override public Contact read(UUID id) {
        Objects.requireNonNull(id);
        return reader.find(id).orElseThrow(PatientNotFoundException::new);
    }
}
