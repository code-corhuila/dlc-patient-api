package co.edu.corhuila.dlc.patients.application.usecase;

import co.edu.corhuila.dlc.patients.application.model.PatientNotFoundException;
import co.edu.corhuila.dlc.patients.application.port.in.ValidatePatientUseCase;
import co.edu.corhuila.dlc.patients.application.port.out.PatientValidationReader;
import java.util.Objects;
import java.util.UUID;

public final class PatientValidationService implements ValidatePatientUseCase {
    private final PatientValidationReader reader;
    public PatientValidationService(PatientValidationReader reader) { this.reader = Objects.requireNonNull(reader); }
    @Override public Result validate(UUID id) {
        Objects.requireNonNull(id);
        var state = reader.find(id).orElseThrow(PatientNotFoundException::new);
        return new Result(id, state.active(), state.version());
    }
}
