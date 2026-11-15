package co.edu.corhuila.dlc.patients.application.port.out;

import co.edu.corhuila.dlc.patients.application.model.PatientSearchPage.Row;
import co.edu.corhuila.dlc.patients.domain.model.PatientStatus;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@FunctionalInterface
public interface PatientSearchReader {
    /** Filter scope/status and literal case-insensitive name/document substring BEFORE count/pagination.
     * Return a consistent rows/count snapshot ordered by createdAt DESC, UUID DESC; no cross-owner schema joins. */
    Result find(Criteria criteria, Scope scope);
    record Criteria(int page, int limit, String search, PatientStatus status) {
        public long offset() { return ((long) page - 1) * limit; }
    }
    record Scope(PatientSearchAuthorization.Access access, Set<UUID> assignedPatientIds) {
        public Scope { assignedPatientIds = Set.copyOf(assignedPatientIds); }
    }
    record Result(List<Row> rows, long total) {
        public Result { rows = List.copyOf(rows); }
    }
}
