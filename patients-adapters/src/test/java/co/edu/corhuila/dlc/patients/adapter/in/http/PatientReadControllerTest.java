package co.edu.corhuila.dlc.patients.adapter.in.http;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import co.edu.corhuila.dlc.patients.application.model.*;
import co.edu.corhuila.dlc.patients.domain.model.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PatientReadControllerTest {
    private final UUID id = UUID.randomUUID();

    @Test void careResponseContainsOnlyAllowedFields() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new PatientReadController(value ->
                new PatientReadResult.Care(value, "Test", "000", "test@example.invalid", PatientStatus.ACTIVE, 1))).build();
        mvc.perform(get("/api/v1/patients/" + id)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.id").value(id.toString())).andExpect(jsonPath("$.name").value("Test"))
                .andExpect(jsonPath("$.documentNumber").doesNotExist()).andExpect(jsonPath("$.birthDate").doesNotExist());
        mvc.perform(get("/api/v1/patients/not-a-uuid")).andExpect(status().isBadRequest());
    }

    @Test void failuresAreMappedWithoutDetails() throws Exception {
        for (var failure : new RuntimeException[]{new PatientNotFoundException(), new PatientReadUnavailableException(),
                new AuthorizationFailure(AuthorizationFailure.Reason.UNAUTHORIZED), new AuthorizationFailure(AuthorizationFailure.Reason.FORBIDDEN)}) {
            var mvc = MockMvcBuilders.standaloneSetup(new PatientReadController(value -> { throw failure; })).build();
            int expected = failure instanceof PatientNotFoundException ? 404 : failure instanceof PatientReadUnavailableException ? 503
                    : ((AuthorizationFailure) failure).reason() == AuthorizationFailure.Reason.UNAUTHORIZED ? 401 : 403;
            mvc.perform(get("/api/v1/patients/" + id)).andExpect(status().is(expected)).andExpect(jsonPath("$.traceId").exists());
        }
    }
}
