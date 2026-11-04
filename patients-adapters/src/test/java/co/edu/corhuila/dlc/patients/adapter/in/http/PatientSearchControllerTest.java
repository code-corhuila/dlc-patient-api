package co.edu.corhuila.dlc.patients.adapter.in.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.corhuila.dlc.patients.application.model.AuthorizationFailure;
import co.edu.corhuila.dlc.patients.application.model.PatientSearchPage;
import co.edu.corhuila.dlc.patients.application.model.PatientSearchUnavailableException;
import co.edu.corhuila.dlc.patients.application.port.in.SearchPatientsUseCase;
import co.edu.corhuila.dlc.patients.domain.model.Document;
import co.edu.corhuila.dlc.patients.domain.model.PatientProfile;
import co.edu.corhuila.dlc.patients.domain.model.PatientStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PatientSearchControllerTest {
    private static final UUID PATIENT = UUID.randomUUID();
    private static final Instant CREATED = Instant.parse("2026-01-02T03:04:05Z");
    private MockMvc http;
    private SearchPatientsUseCase.Query query;
    private RuntimeException failure;

    @BeforeEach
    void boundaryWithControlledUseCase() {
        SearchPatientsUseCase useCase = requested -> {
            query = requested;
            if (failure != null) throw failure;
            return requested.page() == null ? page() : carePage();
        };
        http = MockMvcBuilders.standaloneSetup(new PatientSearchController(useCase)).build();
    }

    @Test
    void mapsAdministrativeRowsAndDefaultsToContract() throws Exception {
        http.perform(get("/api/v1/patients")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data[0].id").value(PATIENT.toString()))
                .andExpect(jsonPath("$.data[0].documentType").value("CC"))
                .andExpect(jsonPath("$.data[0].documentNumber").value("123"))
                .andExpect(jsonPath("$.data[0].birthDate").value("2000-01-02"))
                .andExpect(jsonPath("$.data[0].createdAt").value(CREATED.toString()))
                .andExpect(jsonPath("$.meta.page").value(1)).andExpect(jsonPath("$.meta.limit").value(20));
        assertEquals(new SearchPatientsUseCase.Query(null, null, null, null), query);
    }

    @Test
    void passesFiltersAndMapsCareProjectionWithoutAdministrativeFields() throws Exception {
        http.perform(get("/api/v1/patients?page=2&limit=10&search=Ana&status=ACTIVE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].name").value("Ana"))
                .andExpect(jsonPath("$.data[0].phone").value("000"))
                .andExpect(jsonPath("$.data[0].documentType").doesNotExist());
        assertEquals(new SearchPatientsUseCase.Query(2, 10, "Ana", "ACTIVE"), query);
    }

    @Test
    void mapsInvalidParametersAndAuthorizationFailures() throws Exception {
        http.perform(get("/api/v1/patients?page=abc")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
        failure = new AuthorizationFailure(AuthorizationFailure.Reason.UNAUTHORIZED);
        http.perform(get("/api/v1/patients")).andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer realm=\"di-lucca\""))
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    void mapsUnavailableAndUnexpectedFailuresWithoutCause() throws Exception {
        failure = new PatientSearchUnavailableException();
        http.perform(get("/api/v1/patients")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("SERVICE_UNAVAILABLE"));
        failure = new IllegalStateException("private database detail");
        http.perform(get("/api/v1/patients")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("INTERNAL_ERROR"));
    }

    private static PatientSearchPage page() {
        var profile = new PatientProfile("Ana", LocalDate.of(2000, 1, 2), "000", "ana@example.invalid", null, null);
        var row = new PatientSearchPage.Row(PATIENT, new Document("CC", "123"), profile, PatientStatus.ACTIVE,
                CREATED, CREATED, 2);
        return new PatientSearchPage(java.util.List.of(row), new PatientSearchPage.Meta(1, 20, 1, 1));
    }

    private static PatientSearchPage carePage() {
        return new PatientSearchPage(java.util.List.of(new PatientSearchPage.Care(PATIENT, "Ana", "000",
                "ana@example.invalid", PatientStatus.ACTIVE, 2)), new PatientSearchPage.Meta(2, 10, 1, 1));
    }
}
