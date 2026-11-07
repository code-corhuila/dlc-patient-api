package co.edu.corhuila.dlc.patients.application.usecase;

import co.edu.corhuila.dlc.patients.application.model.RegistrationResult;
import co.edu.corhuila.dlc.patients.application.port.in.RegisterPatientUseCase;
import co.edu.corhuila.dlc.patients.application.port.out.RegistrationAuthorization;
import co.edu.corhuila.dlc.patients.application.port.out.RegistrationTransaction;
import co.edu.corhuila.dlc.patients.domain.model.InvalidPatientDataException;
import co.edu.corhuila.dlc.patients.domain.model.Patient;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public final class RegisterPatientService implements RegisterPatientUseCase {
    private final RegistrationAuthorization authorization;
    private final RegistrationTransaction transaction;
    private final Clock clock;
    private final Supplier<UUID> identifiers;

    public RegisterPatientService(RegistrationAuthorization authorization, RegistrationTransaction transaction,
            Clock clock, Supplier<UUID> identifiers) {
        this.authorization = Objects.requireNonNull(authorization);
        this.transaction = Objects.requireNonNull(transaction);
        this.clock = Objects.requireNonNull(clock);
        this.identifiers = Objects.requireNonNull(identifiers);
    }

    @Override
    public RegistrationResult register(Command command) {
        var actor = Objects.requireNonNull(authorization.requireAuthorizedActor(), "authorized actor");
        Objects.requireNonNull(command, "command");
        var key = command.idempotencyKey();
        int keyLength = key == null ? 0 : key.codePointCount(0, key.length());
        if (keyLength < 8 || keyLength > 128) {
            throw new InvalidPatientDataException("Idempotency-Key", "must have 8 to 128 characters");
        }
        Objects.requireNonNull(command.correlationId(), "correlationId");
        Objects.requireNonNull(command.causationId(), "causationId");
        var instant = clock.instant();
        var patient = Patient.register(identifiers.get(), command.document(), command.profile(),
                Clock.fixed(instant, clock.getZone()));
        var write = new RegistrationTransaction.Write(patient, actor, key, RegistrationFingerprint.hash(command),
                Objects.requireNonNull(identifiers.get()), Objects.requireNonNull(identifiers.get()),
                instant, command.correlationId(), command.causationId());
        return Objects.requireNonNull(transaction.register(write), "registration result");
    }
}
