package co.edu.corhuila.dlc.patients.adapter.in.http;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import co.edu.corhuila.dlc.patients.application.model.AuthorizationFailure;
import co.edu.corhuila.dlc.patients.application.port.in.ValidatePatientUseCase;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PatientValidationControllerTest {
    @Test void returnsOnlyStateAfterServiceAuthorization() throws Exception {
        var id = UUID.randomUUID();
        var controller = new PatientValidationController(() -> {}, patient -> new ValidatePatientUseCase.Result(patient, true, 2));
        MockMvcBuilders.standaloneSetup(controller).build().perform(get("/internal/v1/patients/" + id + "/validation"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.patientId").value(id.toString()))
                .andExpect(jsonPath("$.active").value(true)).andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.name").doesNotExist());
    }
    @Test void deniesBeforeParsingAndQuerying() throws Exception {
        var controller = new PatientValidationController(() -> { throw new AuthorizationFailure(AuthorizationFailure.Reason.UNAUTHORIZED); },
                patient -> { throw new AssertionError(); });
        MockMvcBuilders.standaloneSetup(controller).build().perform(get("/internal/v1/patients/bad/validation"))
                .andExpect(status().isUnauthorized());
    }
}
