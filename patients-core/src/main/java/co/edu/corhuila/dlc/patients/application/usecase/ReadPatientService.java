package co.edu.corhuila.dlc.patients.application.usecase;

import co.edu.corhuila.dlc.patients.application.model.AuthorizationFailure;
import co.edu.corhuila.dlc.patients.application.model.PatientNotFoundException;
import co.edu.corhuila.dlc.patients.application.model.PatientReadResult;
import co.edu.corhuila.dlc.patients.application.model.PatientReadResult.Administrative;
import co.edu.corhuila.dlc.patients.application.model.PatientReadResult.Care;
import co.edu.corhuila.dlc.patients.application.model.PatientReadUnavailableException;
import co.edu.corhuila.dlc.patients.application.port.in.ReadPatientUseCase;
import co.edu.corhuila.dlc.patients.application.port.out.CurrentPatientAssignments;
import co.edu.corhuila.dlc.patients.application.port.out.PatientReadReader;
import co.edu.corhuila.dlc.patients.application.port.out.PatientSearchAuthorization;
import co.edu.corhuila.dlc.patients.application.port.out.PatientSearchAuthorization.Access;
import co.edu.corhuila.dlc.patients.application.port.out.PatientSearchAuthorization.View;
import co.edu.corhuila.dlc.patients.domain.model.InvalidPatientDataException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class ReadPatientService implements ReadPatientUseCase {
    private final PatientSearchAuthorization authorization;
    private final CurrentPatientAssignments assignments;
    private final PatientReadReader reader;

    public ReadPatientService(PatientSearchAuthorization authorization, CurrentPatientAssignments assignments,
            PatientReadReader reader) {
        this.authorization = Objects.requireNonNull(authorization);
        this.assignments = Objects.requireNonNull(assignments);
        this.reader = Objects.requireNonNull(reader);
    }

    @Override
    public PatientReadResult read(UUID patientId) {
        if (patientId == null) throw new InvalidPatientDataException("id", "is required");
        Access access = authorizedAccess();
        var before = access.view() == View.CARE ? currentAssignments(access) : null;
        if (before != null && !before.patientIds().contains(patientId)) throw new PatientNotFoundException();
        var scope = new PatientReadReader.Scope(access, before == null ? Set.of() : before.patientIds());
        Optional<co.edu.corhuila.dlc.patients.application.model.PatientSearchPage.Row> found;
        try {
            found = reader.findById(patientId, scope);
            if (found == null) throw unavailable();
            if (before != null && !before.equals(currentAssignments(access))) throw unavailable();
        } catch (PatientNotFoundException failure) {
            throw failure;
        } catch (PatientReadUnavailableException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw unavailable();
        }
        if (found.isEmpty()) throw new PatientNotFoundException();
        var row = found.get();
        if (!patientId.equals(row.id()) || row.document() == null || row.profile() == null || row.status() == null
                || row.createdAt() == null || row.updatedAt() == null || row.version() < 1
                || before != null && !before.patientIds().contains(row.id())) throw unavailable();
        Access rechecked = authorizedAccess();
        if (!access.equals(rechecked)) throw new AuthorizationFailure(AuthorizationFailure.Reason.FORBIDDEN);
        return access.view() == View.CARE
                ? new Care(row.id(), row.profile().name(), row.profile().phone(), row.profile().email(), row.status(), row.version())
                : new Administrative(row.id(), row.document(), row.profile(), row.status(), row.createdAt(), row.updatedAt(), row.version());
    }

    private Access authorizedAccess() {
        try { return Objects.requireNonNull(authorization.requireAuthorizedAccess()); }
        catch (AuthorizationFailure failure) { throw failure; }
        catch (RuntimeException failure) { throw new AuthorizationFailure(AuthorizationFailure.Reason.SERVICE_UNAVAILABLE); }
    }

    private CurrentPatientAssignments.Snapshot currentAssignments(Access access) {
        try {
            var snapshot = assignments.currentFor(access.actorId());
            if (!snapshot.dentistId().equals(access.actorId())) throw unavailable();
            return snapshot;
        } catch (PatientReadUnavailableException failure) { throw failure; }
        catch (RuntimeException failure) { throw unavailable(); }
    }

    private static PatientReadUnavailableException unavailable() {
        return new PatientReadUnavailableException();
    }
}
