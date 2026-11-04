package co.edu.corhuila.dlc.patients.application.usecase;

import co.edu.corhuila.dlc.patients.application.model.AuthorizationFailure;
import co.edu.corhuila.dlc.patients.application.model.PatientSearchPage;
import co.edu.corhuila.dlc.patients.application.model.PatientSearchPage.Item;
import co.edu.corhuila.dlc.patients.application.model.PatientSearchPage.Row;
import co.edu.corhuila.dlc.patients.application.model.PatientSearchUnavailableException;
import co.edu.corhuila.dlc.patients.application.port.in.SearchPatientsUseCase;
import co.edu.corhuila.dlc.patients.application.port.out.CurrentPatientAssignments;
import co.edu.corhuila.dlc.patients.application.port.out.PatientSearchAuthorization;
import co.edu.corhuila.dlc.patients.application.port.out.PatientSearchAuthorization.Access;
import co.edu.corhuila.dlc.patients.application.port.out.PatientSearchAuthorization.View;
import co.edu.corhuila.dlc.patients.application.port.out.PatientSearchReader;
import co.edu.corhuila.dlc.patients.domain.model.InvalidPatientDataException;
import co.edu.corhuila.dlc.patients.domain.model.PatientStatus;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class SearchPatientsService implements SearchPatientsUseCase {
    private static final Comparator<Row> ORDER = Comparator.comparing(Row::createdAt).thenComparing(row -> row.id().toString()).reversed();
    private final PatientSearchAuthorization authorization;
    private final CurrentPatientAssignments assignments;
    private final PatientSearchReader reader;

    public SearchPatientsService(PatientSearchAuthorization authorization, CurrentPatientAssignments assignments, PatientSearchReader reader) {
        this.authorization = Objects.requireNonNull(authorization);
        this.assignments = Objects.requireNonNull(assignments);
        this.reader = Objects.requireNonNull(reader);
    }

    @Override
    public PatientSearchPage search(Query query) {
        Access access = authorizedAccess();
        var criteria = criteria(query);
        PatientSearchPage page;
        try {
            var before = access.view() == View.CARE ? currentAssignments(access) : null;
            var scope = new PatientSearchReader.Scope(access, before == null ? Set.of() : before.patientIds());
            var result = before != null && before.patientIds().isEmpty()
                    ? new PatientSearchReader.Result(List.of(), 0) : reader.find(criteria, scope);
            validate(result, criteria, scope);
            var items = result.rows().stream().<Item>map(row -> access.view() == View.CARE
                    ? new PatientSearchPage.Care(row.id(), row.profile().name(), row.profile().phone(), row.profile().email(), row.status(), row.version())
                    : row).toList();
            long total = result.total();
            page = new PatientSearchPage(items, new PatientSearchPage.Meta(criteria.page(), criteria.limit(), total,
                    total / criteria.limit() + (total % criteria.limit() == 0 ? 0 : 1)));
            if (before != null && !before.equals(currentAssignments(access))) throw new PatientSearchUnavailableException();
        } catch (RuntimeException failure) {
            throw new PatientSearchUnavailableException();
        }
        if (!access.equals(authorizedAccess())) throw new AuthorizationFailure(AuthorizationFailure.Reason.FORBIDDEN);
        return page;
    }

    private Access authorizedAccess() {
        try { return Objects.requireNonNull(authorization.requireAuthorizedAccess()); }
        catch (AuthorizationFailure failure) { throw failure; }
        catch (RuntimeException failure) { throw new AuthorizationFailure(AuthorizationFailure.Reason.SERVICE_UNAVAILABLE); }
    }

    private CurrentPatientAssignments.Snapshot currentAssignments(Access access) {
        var snapshot = assignments.currentFor(access.actorId());
        if (!snapshot.dentistId().equals(access.actorId())) throw new PatientSearchUnavailableException();
        return snapshot;
    }

    private static PatientSearchReader.Criteria criteria(Query query) {
        if (query == null) throw invalid("query");
        int page = query.page() == null ? 1 : query.page();
        int limit = query.limit() == null ? 20 : query.limit();
        if (page < 1) throw invalid("page");
        if (limit < 1 || limit > 100) throw invalid("limit");
        if (query.search() != null && (query.search().isEmpty() || query.search().codePointCount(0, query.search().length()) > 255)) throw invalid("search");
        PatientStatus status;
        try { status = query.status() == null ? null : PatientStatus.valueOf(query.status()); }
        catch (IllegalArgumentException failure) { throw invalid("status"); }
        return new PatientSearchReader.Criteria(page, limit, query.search(), status);
    }

    private static void validate(PatientSearchReader.Result result, PatientSearchReader.Criteria criteria, PatientSearchReader.Scope scope) {
        if (result.total() < 0 || result.rows().size() != Math.min(criteria.limit(), Math.max(0, result.total() - criteria.offset()))
                || result.rows().stream().map(Row::id).distinct().count() != result.rows().size()
                || scope.access().view() == View.CARE && result.total() > scope.assignedPatientIds().size()) throw new PatientSearchUnavailableException();
        for (int i = 0; i < result.rows().size(); i++) {
            Row row = result.rows().get(i);
            if (row.id() == null || row.document() == null || row.profile() == null || row.status() == null
                    || row.createdAt() == null || row.updatedAt() == null || row.version() < 1
                    || scope.access().view() == View.CARE && !scope.assignedPatientIds().contains(row.id())
                    || i > 0 && ORDER.compare(result.rows().get(i - 1), row) > 0) throw new PatientSearchUnavailableException();
        }
    }

    private static InvalidPatientDataException invalid(String field) {
        return new InvalidPatientDataException(field, "Invalid search parameters");
    }
}
