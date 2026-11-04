package co.edu.corhuila.dlc.patients.application.usecase;

import co.edu.corhuila.dlc.patients.application.model.PatientReadResult;
import co.edu.corhuila.dlc.patients.application.port.in.UpdatePatientUseCase;
import co.edu.corhuila.dlc.patients.application.port.out.PatientUpdateAuthorization;
import co.edu.corhuila.dlc.patients.application.port.out.PatientUpdateTransaction;
import co.edu.corhuila.dlc.patients.domain.model.InvalidPatientDataException;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public final class UpdatePatientService implements UpdatePatientUseCase {
    private final PatientUpdateAuthorization authorization;
    private final PatientUpdateTransaction transaction;
    private final Clock clock;
    private final Supplier<UUID> ids;
    public UpdatePatientService(PatientUpdateAuthorization authorization, PatientUpdateTransaction transaction, Clock clock, Supplier<UUID> ids) {
        this.authorization = Objects.requireNonNull(authorization); this.transaction = Objects.requireNonNull(transaction);
        this.clock = Objects.requireNonNull(clock); this.ids = Objects.requireNonNull(ids);
    }
    @Override public PatientReadResult.Administrative update(Command command) {
        UUID actor = Objects.requireNonNull(authorization.requireAuthorizedActor());
        if (command == null || command.patientId() == null || command.patch() == null || command.expectedVersion() < 1)
            throw new InvalidPatientDataException("command", "patient id, patch and positive version required");
        return Objects.requireNonNull(transaction.update(new PatientUpdateTransaction.Write(command.patientId(), command.expectedVersion(),
                command.patch(), actor, Objects.requireNonNull(ids.get()), Objects.requireNonNull(ids.get()), clock.instant())));
    }
}
