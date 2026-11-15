package co.edu.corhuila.dlc.patients.adapter.in.http;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import co.edu.corhuila.dlc.patients.application.model.AuthorizationFailure;
import co.edu.corhuila.dlc.patients.application.port.in.RegisterPatientUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class RequestCorrelationTest {
    @AfterEach void clear() { RequestContextHolder.resetRequestAttributes(); }
    @Test void ownerErrorKeepsGatewayCorrelation() throws Exception {
        String id = UUID.randomUUID().toString();
        var controller = new PatientRegistrationController(mock(RegisterPatientUseCase.class),
                () -> { throw new AuthorizationFailure(AuthorizationFailure.Reason.FORBIDDEN); }, new ObjectMapper());
        MockMvcBuilders.standaloneSetup(controller).build().perform(post("/api/v1/patients")
                .contentType("application/json").content("{}").header("X-Correlation-Id", id))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.traceId").value(id));
    }
    @Test void malformedOrAmbiguousIdsAreReplacedWithCanonicalUuids() {
        for (String input : new String[]{"invalid", "1-1-1-1-1", "a".repeat(1000)}) {
            var request = new MockHttpServletRequest(); request.addHeader("X-Correlation-Id", input);
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
            assertNotEquals(input, RequestCorrelation.current().toString());
        }
        var request = new MockHttpServletRequest(); String first = UUID.randomUUID().toString();
        request.addHeader("X-Correlation-Id", first); request.addHeader("X-Correlation-Id", UUID.randomUUID().toString());
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        assertNotEquals(first, RequestCorrelation.current().toString());
    }
}
