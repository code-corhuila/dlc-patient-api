package co.edu.corhuila.dlc.patients.adapter.in.http;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import co.edu.corhuila.dlc.patients.application.model.*;
import co.edu.corhuila.dlc.patients.application.port.in.UpdatePatientUseCase;
import co.edu.corhuila.dlc.patients.domain.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PatientUpdateControllerTest {
    private final UUID id = UUID.randomUUID();
    private final AtomicReference<UpdatePatientUseCase.Command> command = new AtomicReference<>();
    private RuntimeException failure;
    private final PatientUpdateController controller = new PatientUpdateController(c -> {
        command.set(c); if (failure != null) throw failure;
        return new PatientReadResult.Administrative(id, new Document("CC", "123"),
                new PatientProfile("Test", LocalDate.of(2000,1,1), "000", "test@example.invalid", null, List.of()),
                PatientStatus.ACTIVE, Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-02T00:00:00Z"), 2);
    }, () -> UUID.randomUUID(), new ObjectMapper());

    @Test void mapsPatchAndAdministrativeResponse() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(patch("/api/v1/patients/" + id).contentType("application/json").content("{\"expectedVersion\":1,\"name\":\"Test\"}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.documentType").value("CC")).andExpect(jsonPath("$.version").value(2));
        assertEquals(id,command.get().patientId()); assertEquals("Test",command.get().patch().name());
        assertNotNull(command.get().correlationId()); assertNotNull(command.get().causationId());
    }

    @Test void rejectsInvalidBodiesAndIdentifiers() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        for (String body : List.of("{}", "{\"expectedVersion\":1}", "{\"expectedVersion\":0,\"name\":\"x\"}",
                "{\"expectedVersion\":1,\"status\":\"ACTIVE\"}", "{\"expectedVersion\":1,\"name\":null}",
                "{\"expectedVersion\":1,\"name\":\"x\",\"name\":\"y\"}", "{\"expectedVersion\":1,\"birthDate\":\"bad\"}",
                "{\"expectedVersion\":1,\"emergencyContacts\":[{\"name\":\"x\"}]}", "{\"expectedVersion\":1,\"name\":\"x\"} {}"))
            mvc.perform(patch("/api/v1/patients/"+id).contentType("application/json").content(body)).andExpect(status().isBadRequest());
        mvc.perform(patch("/api/v1/patients/bad").contentType("application/json").content("{}")) .andExpect(status().isBadRequest());
        assertNull(command.get());
    }

    @Test void checksAuthorizationBeforeParsingAndProvidesBearerChallenge() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new PatientUpdateController(c -> { fail("must not write"); return null; },
                () -> { throw new AuthorizationFailure(AuthorizationFailure.Reason.UNAUTHORIZED); }, new ObjectMapper())).build();
        mvc.perform(patch("/api/v1/patients/"+id).contentType("application/json").content("{"))
                .andExpect(status().isUnauthorized()).andExpect(header().string("WWW-Authenticate", "Bearer realm=\"di-lucca\""));
    }

    @Test void staleVersionUsesDocumentedErrorCode() throws Exception {
        failure = new PatientUpdateConflictException(PatientUpdateConflictException.Reason.VERSION_CONFLICT);
        MockMvcBuilders.standaloneSetup(controller).build()
                .perform(patch("/api/v1/patients/" + id).contentType("application/json").content("{\"expectedVersion\":1,\"name\":\"Test\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("STALE_VERSION"));
    }

    @Test void mapsFailuresWithoutExposingDetails() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        for (var f : List.of(new PatientNotFoundException(), new PatientUpdateConflictException(PatientUpdateConflictException.Reason.VERSION_CONFLICT),
                new PatientUpdateUnavailableException(), new AuthorizationFailure(AuthorizationFailure.Reason.FORBIDDEN))) {
            failure = f;
            int status = f instanceof PatientNotFoundException ? 404 : f instanceof PatientUpdateConflictException ? 409 : f instanceof PatientUpdateUnavailableException ? 503 : 403;
            mvc.perform(patch("/api/v1/patients/"+id).contentType("application/json").content("{\"expectedVersion\":1,\"name\":\"Test\"}"))
                    .andExpect(status().is(status)).andExpect(jsonPath("$.traceId").exists());
        }
    }
}
