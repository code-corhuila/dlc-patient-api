package co.edu.corhuila.dlc.patients.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.edu.corhuila.dlc.patients.application.model.AuthorizationFailure;
import co.edu.corhuila.dlc.patients.application.model.PatientSearchPage;
import co.edu.corhuila.dlc.patients.application.model.PatientSearchPage.Row;
import co.edu.corhuila.dlc.patients.application.model.PatientSearchUnavailableException;
import co.edu.corhuila.dlc.patients.application.port.in.SearchPatientsUseCase.Query;
import co.edu.corhuila.dlc.patients.application.port.out.CurrentPatientAssignments;
import co.edu.corhuila.dlc.patients.application.port.out.PatientSearchAuthorization.Access;
import co.edu.corhuila.dlc.patients.application.port.out.PatientSearchAuthorization.View;
import co.edu.corhuila.dlc.patients.application.port.out.PatientSearchReader;
import co.edu.corhuila.dlc.patients.domain.model.Document;
import co.edu.corhuila.dlc.patients.domain.model.InvalidPatientDataException;
import co.edu.corhuila.dlc.patients.domain.model.PatientProfile;
import co.edu.corhuila.dlc.patients.domain.model.PatientStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SearchPatientsServiceTest {
    private static final UUID ACTOR = UUID.randomUUID();
    private static final Row ROW = row("ffffffff-ffff-ffff-ffff-ffffffffffff");
    private Access access = new Access(ACTOR, View.ADMINISTRATIVE);
    private PatientSearchReader.Result result = new PatientSearchReader.Result(List.of(ROW), 1);
    private CurrentPatientAssignments.Snapshot snapshot = new CurrentPatientAssignments.Snapshot(ACTOR, 1, Set.of(ROW.id()));
    private PatientSearchReader.Criteria criteria;
    private PatientSearchReader.Scope scope;
    private int authorizationCalls;
    private int assignmentCalls;
    private int reads;
    private boolean revoke;
    private boolean relationshipChanges;
    private RuntimeException authFailure;
    private RuntimeException assignmentFailure;
    private RuntimeException readFailure;
    private final SearchPatientsService service = new SearchPatientsService(() -> {
        authorizationCalls++;
        if (authFailure != null) throw authFailure;
        if (revoke && authorizationCalls > 1) throw new AuthorizationFailure(AuthorizationFailure.Reason.UNAUTHORIZED);
        return access;
    }, dentist -> {
        assertEquals(ACTOR, dentist);
        assignmentCalls++;
        if (assignmentFailure != null) throw assignmentFailure;
        return relationshipChanges && assignmentCalls > 1 ? new CurrentPatientAssignments.Snapshot(ACTOR, 2, Set.of()) : snapshot;
    }, (query, authorizedScope) -> {
        reads++;
        criteria = query;
        scope = authorizedScope;
        if (readFailure != null) throw readFailure;
        return result;
    });

    @Test
    void administrativeDefaultsPreserveFullProfileWithoutClinicalFields() {
        var page = service.search(new Query(null, null, null, null));
        assertEquals(List.of(ROW), page.data());
        assertEquals(new PatientSearchPage.Meta(1, 20, 1, 1), page.meta());
        assertEquals(View.ADMINISTRATIVE, scope.access().view());
        assertEquals(0, assignmentCalls);
        assertEquals(2, authorizationCalls);
        assertThrows(UnsupportedOperationException.class, () -> page.data().clear());
    }

    @Test
    void filtersAndLargeOffsetsReachReaderWithoutWildcardOrCaseReinterpretation() {
        result = new PatientSearchReader.Result(List.of(), 0);
        service.search(new Query(Integer.MAX_VALUE, 100, "MiX%_ñ", "INACTIVE"));
        assertEquals(214748364600L, criteria.offset());
        assertEquals("MiX%_ñ", criteria.search());
        assertEquals(PatientStatus.INACTIVE, criteria.status());
    }

    @ParameterizedTest
    @ValueSource(strings = {"page", "limit-zero", "limit-large", "search-empty", "search-long", "status"})
    void validatesInputsAfterAuthorizationBeforeAnyRead(String change) {
        var query = new Query(change.equals("page") ? 0 : 1, change.equals("limit-zero") ? 0 : change.equals("limit-large") ? 101 : 20,
                change.equals("search-empty") ? "" : change.equals("search-long") ? "x".repeat(256) : null,
                change.equals("status") ? "active" : null);
        assertThrows(InvalidPatientDataException.class, () -> service.search(query));
        assertEquals(1, authorizationCalls);
        assertEquals(0, reads + assignmentCalls);
    }

    @ParameterizedTest
    @ValueSource(strings = {"UNAUTHORIZED", "FORBIDDEN", "SERVICE_UNAVAILABLE"})
    void preservesAuthorizationFailuresBeforeEvenInvalidInput(String reason) {
        authFailure = new AuthorizationFailure(AuthorizationFailure.Reason.valueOf(reason));
        assertSame(authFailure, assertThrows(AuthorizationFailure.class, () -> service.search(null)));
        assertEquals(0, reads + assignmentCalls);
    }

    @Test
    void dentistScopePrecedesQueryAndOnlyMinimalCareProjectionLeavesUseCase() {
        access = new Access(ACTOR, View.CARE);
        var page = service.search(new Query(null, null, null, null));
        assertEquals(Set.of(ROW.id()), scope.assignedPatientIds());
        assertEquals(ACTOR, scope.access().actorId());
        assertEquals(new PatientSearchPage.Care(ROW.id(), "Synthetic", "000", "fixture@example.invalid", PatientStatus.INACTIVE, 2), page.data().getFirst());
        assertEquals(2, assignmentCalls);
    }

    @Test
    void dentistWithoutCurrentAssignmentsReturnsEmptyWithoutReadingGlobalData() {
        access = new Access(ACTOR, View.CARE);
        snapshot = new CurrentPatientAssignments.Snapshot(ACTOR, 1, Set.of());
        assertEquals(new PatientSearchPage.Meta(1, 20, 0, 0), service.search(new Query(null, null, null, null)).meta());
        assertEquals(0, reads);
    }

    @ParameterizedTest
    @ValueSource(strings = {"assignment-failure", "wrong-dentist", "unassigned-row", "global-count", "changed", "reader"})
    void unavailableOrInconsistentAuthorityNeverProducesAPartialOrUnrestrictedPage(String change) {
        access = new Access(ACTOR, View.CARE);
        if (change.equals("assignment-failure")) assignmentFailure = new IllegalStateException("private upstream details");
        if (change.equals("wrong-dentist")) snapshot = new CurrentPatientAssignments.Snapshot(UUID.randomUUID(), 1, Set.of(ROW.id()));
        if (change.equals("unassigned-row")) result = new PatientSearchReader.Result(List.of(row(UUID.randomUUID().toString())), 1);
        if (change.equals("global-count")) result = new PatientSearchReader.Result(List.of(ROW), 100);
        relationshipChanges = change.equals("changed");
        if (change.equals("reader")) readFailure = new IllegalStateException("private SQL");
        var failure = assertThrows(PatientSearchUnavailableException.class, () -> service.search(new Query(null, null, null, null)));
        assertNull(failure.getCause());
        assertFalse(failure.getMessage().contains("private"));
    }

    @Test
    void sessionRevokedDuringQueryCannotReceiveResults() {
        revoke = true;
        assertThrows(AuthorizationFailure.class, () -> service.search(new Query(null, null, null, null)));
    }

    @Test
    void beyondLastPageRetainsFilteredTotalAndCalculatesPagesWithoutOverflow() {
        result = new PatientSearchReader.Result(List.of(), 21);
        assertEquals(new PatientSearchPage.Meta(3, 20, 21, 2), service.search(new Query(3, 20, null, null)).meta());
        result = new PatientSearchReader.Result(List.of(ROW), Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, service.search(new Query(1, 1, "😀".repeat(255), null)).meta().totalPages());
    }

    @Test
    void nonFirstPagePreservesDatabaseUuidOrderingAndFilteredMetadata() {
        result = new PatientSearchReader.Result(List.of(ROW, row("00000000-0000-0000-0000-000000000001")), 22);
        var page = service.search(new Query(2, 20, null, null));
        assertEquals(result.rows(), page.data());
        assertEquals(new PatientSearchPage.Meta(2, 20, 22, 2), page.meta());
    }

    @Test
    void invalidPageShapesOrOrderingFailClosedInsteadOfSortingAfterPagination() {
        for (var invalid : List.of(new PatientSearchReader.Result(List.of(ROW), 0),
                new PatientSearchReader.Result(List.of(), 1), new PatientSearchReader.Result(List.of(ROW, ROW), 2),
                new PatientSearchReader.Result(List.of(row("00000000-0000-0000-0000-000000000001"), ROW), 2))) {
            result = invalid;
            assertThrows(PatientSearchUnavailableException.class, () -> service.search(new Query(null, null, null, null)));
        }
    }

    @Test
    void queryResultsAreDefensivelyCopied() {
        var mutable = new ArrayList<>(List.of(ROW));
        var copied = new PatientSearchReader.Result(mutable, 1);
        mutable.clear();
        assertEquals(List.of(ROW), copied.rows());
    }

    private static Row row(String id) {
        return new Row(UUID.fromString(id), new Document("CC", "fixture"),
                new PatientProfile("Synthetic", LocalDate.of(2000, 1, 1), "000", "fixture@example.invalid", "Private address", List.of()),
                PatientStatus.INACTIVE, Instant.EPOCH, Instant.EPOCH, 2);
    }
}
