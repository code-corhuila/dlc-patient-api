package co.edu.corhuila.dlc.patients.adapter.in.http;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import co.edu.corhuila.dlc.patients.application.model.AuthorizationFailure;
import co.edu.corhuila.dlc.patients.application.model.PatientNotFoundException;
import co.edu.corhuila.dlc.patients.application.model.PatientReadUnavailableException;
import co.edu.corhuila.dlc.patients.application.port.in.ReadBillingContactUseCase;
import co.edu.corhuila.dlc.patients.application.port.out.PatientServiceAuthorization;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class BillingContactControllerTest {
    private static final PatientServiceAuthorization GRANTED = new PatientServiceAuthorization() {
        @Override public void requireValidationScope() { throw new AssertionError(); }
        @Override public void requireBillingContactScope() { }
    };

    private static MockMvc mvc(PatientServiceAuthorization authorization, ReadBillingContactUseCase contacts) {
        return MockMvcBuilders.standaloneSetup(new BillingContactController(authorization, contacts)).build();
    }

    @Test void returnsOnlyTheContractFieldsWithoutCaching() throws Exception {
        var id = UUID.randomUUID();
        mvc(GRANTED, patient -> new ReadBillingContactUseCase.Contact(patient, "ana@example.test", "Ana García"))
                .perform(get("/internal/v1/patients/" + id + "/billing-contact"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.patientId").value(id.toString()))
                .andExpect(jsonPath("$.email").value("ana@example.test"))
                .andExpect(jsonPath("$.displayName").value("Ana García"))
                .andExpect(jsonPath("$.documentNumber").doesNotExist());
    }

    @Test void deniesBeforeParsingAndReading() throws Exception {
        PatientServiceAuthorization denied = new PatientServiceAuthorization() {
            @Override public void requireValidationScope() { }
            @Override public void requireBillingContactScope() {
                throw new AuthorizationFailure(AuthorizationFailure.Reason.FORBIDDEN);
            }
        };
        mvc(denied, patient -> { throw new AssertionError(); })
                .perform(get("/internal/v1/patients/bad/billing-contact")).andExpect(status().isForbidden());
    }

    @Test void mapsUnknownInvalidAndUnavailableReads() throws Exception {
        var id = UUID.randomUUID();
        mvc(GRANTED, patient -> { throw new PatientNotFoundException(); })
                .perform(get("/internal/v1/patients/" + id + "/billing-contact")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
        mvc(GRANTED, patient -> { throw new AssertionError(); })
                .perform(get("/internal/v1/patients/not-a-uuid/billing-contact")).andExpect(status().isBadRequest());
        mvc(GRANTED, patient -> { throw new PatientReadUnavailableException(); })
                .perform(get("/internal/v1/patients/" + id + "/billing-contact"))
                .andExpect(status().isServiceUnavailable());
    }
}
