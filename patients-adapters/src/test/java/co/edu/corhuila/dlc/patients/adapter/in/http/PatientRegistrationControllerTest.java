package co.edu.corhuila.dlc.patients.adapter.in.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.edu.corhuila.dlc.patients.application.model.AuthorizationFailure;
import co.edu.corhuila.dlc.patients.application.model.RegistrationConflictException;
import co.edu.corhuila.dlc.patients.application.model.RegistrationResult;
import co.edu.corhuila.dlc.patients.application.model.RegistrationUnavailableException;
import co.edu.corhuila.dlc.patients.application.port.out.RegistrationAuthorization;
import co.edu.corhuila.dlc.patients.application.port.out.RegistrationTransaction;
import co.edu.corhuila.dlc.patients.application.usecase.RegisterPatientService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PatientRegistrationControllerTest {
    private static final UUID ACTOR = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-11-04T12:00:00Z");
    private static final String BODY = """
            {"documentType":"CC","documentNumber":"fixture-123","name":"Synthetic Patient",
             "birthDate":"2000-01-02","phone":"0000000","email":"fixture@example.invalid",
             "emergencyContacts":[{"name":"Synthetic Contact","relationship":"OTHER","phone":"0000"}]}
            """;
    private MockMvc http;
    private AuthorizationFailure.Reason denied;
    private RuntimeException storageFailure;
    private RegistrationResult saved;
    private RegistrationTransaction.Write written;
    private boolean replay;
    private int writes;

    @BeforeEach
    void boundaryWithRealUseCaseAndControlledPorts() {
        RegistrationAuthorization authorization = () -> {
            if (denied != null) throw new AuthorizationFailure(denied);
            return ACTOR;
        };
        RegistrationTransaction transaction = write -> {
            writes++;
            written = write;
            if (storageFailure != null) throw storageFailure;
            if (saved == null) saved = new RegistrationResult(write.patient(), write.registeredAt(), false);
            return replay ? new RegistrationResult(saved.patient(), saved.registeredAt(), true) : saved;
        };
        var service = new RegisterPatientService(authorization, transaction, Clock.fixed(NOW, ZoneOffset.UTC), UUID::randomUUID);
        http = MockMvcBuilders.standaloneSetup(new PatientRegistrationController(service, authorization, new ObjectMapper())).build();
    }

    @Test
    void createsFlatContractResponseAndReplaysOriginalRepresentation() throws Exception {
        String original = http.perform(request(BODY).header("X-User-Id", UUID.randomUUID()).header("Host", "untrusted.invalid"))
                .andExpect(status().isCreated()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.documentType").value("CC")).andExpect(jsonPath("$.documentNumber").value("fixture-123"))
                .andExpect(jsonPath("$.status").value("ACTIVE")).andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.birthDate").value("2000-01-02")).andExpect(jsonPath("$.createdAt").value(NOW.toString()))
                .andExpect(jsonPath("$.updatedAt").value(NOW.toString())).andExpect(jsonPath("$.address").doesNotExist())
                .andExpect(jsonPath("$.emergencyContacts[0].relationship").value("OTHER"))
                .andExpect(jsonPath("$.profile").doesNotExist()).andExpect(jsonPath("$.actorId").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String location = "/api/v1/patients/" + saved.patient().id();
        assertEquals(ACTOR, written.actorId());
        assertEquals("fixture-key-001", written.idempotencyKey());
        assertNotNull(written.correlationId());
        assertNotNull(written.causationId());
        replay = true;
        String repeated = http.perform(request(BODY)).andExpect(status().isOk())
                .andExpect(header().string("Location", location)).andReturn().getResponse().getContentAsString();
        assertEquals(original, repeated);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "[]", "null", "{", "{\"documentType\":\"CC\",\"documentType\":\"TI\"}",
            "trailing", "status", "actorId", "version", "clinicalHistory", "numeric", "future", "date", "contact-extra",
            "contact-null", "contact-type", "address-null", "contacts-null", "contact-missing", "email", "long-name"})
    void rejectsMalformedOrForbiddenFieldsBeforeWriting(String change) throws Exception {
        String body = switch (change) {
            case "trailing" -> BODY + " {}";
            case "status", "actorId", "version", "clinicalHistory" -> BODY.replaceFirst("\\{", "{\"" + change + "\":\"forbidden\",");
            case "numeric" -> BODY.replace("\"fixture-123\"", "123");
            case "future" -> BODY.replace("2000-01-02", "2999-01-02");
            case "date" -> BODY.replace("2000-01-02", "2000-02-30");
            case "contact-extra" -> BODY.replace("\"relationship\":", "\"id\":\"forbidden\",\"relationship\":");
            case "contact-null" -> BODY.replace("[{\"name\":\"Synthetic Contact\",\"relationship\":\"OTHER\",\"phone\":\"0000\"}]", "[null]");
            case "contact-type" -> BODY.substring(0, BODY.indexOf("\"emergencyContacts\"")) + "\"emergencyContacts\":1}";
            case "address-null" -> BODY.replaceFirst("\\{", "{\"address\":null,");
            case "contacts-null" -> BODY.substring(0, BODY.indexOf("\"emergencyContacts\"")) + "\"emergencyContacts\":null}";
            case "contact-missing" -> BODY.replace("\"relationship\":\"OTHER\",", "");
            case "email" -> BODY.replace("fixture@example.invalid", "invalid");
            case "long-name" -> BODY.replace("Synthetic Patient", "x".repeat(256));
            default -> change;
        };
        http.perform(request(body)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
        assertEquals(0, writes);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "short", "long", "duplicate"})
    void requiresOneContractSizedIdempotencyKey(String change) throws Exception {
        var request = post("/api/v1/patients").contentType("application/json").content(BODY);
        if (!change.equals("missing")) request.header("Idempotency-Key", change.equals("long") ? "x".repeat(129) : "short");
        if (change.equals("duplicate")) request.header("Idempotency-Key", "fixture-key-002");
        http.perform(request).andExpect(status().isBadRequest());
        assertEquals(0, writes);
    }

    @ParameterizedTest
    @ValueSource(strings = {"UNAUTHORIZED", "FORBIDDEN", "SERVICE_UNAVAILABLE"})
    void checksAuthorizationBeforeReadingInvalidPayload(String reason) throws Exception {
        denied = AuthorizationFailure.Reason.valueOf(reason);
        var response = http.perform(request("{")).andExpect(status().is(reason.equals("UNAUTHORIZED") ? 401
                : reason.equals("FORBIDDEN") ? 403 : 503)).andExpect(jsonPath("$.error").value(reason)).andReturn().getResponse();
        if (denied == AuthorizationFailure.Reason.UNAUTHORIZED) assertEquals("Bearer realm=\"di-lucca\"", response.getHeader("WWW-Authenticate"));
        assertEquals(0, writes);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PATIENT_DOCUMENT_EXISTS", "IDEMPOTENCY_CONFLICT"})
    void preservesDistinctBusinessConflictCodes(String reason) throws Exception {
        storageFailure = new RegistrationConflictException(RegistrationConflictException.Reason.valueOf(reason));
        http.perform(request(BODY)).andExpect(status().isConflict()).andExpect(jsonPath("$.error").value(reason));
    }

    @Test
    void unavailableStorageReturns503WithoutExposingItsCause() throws Exception {
        storageFailure = new RegistrationUnavailableException("private SQL fixture@example.invalid");
        http.perform(request(BODY)).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("SERVICE_UNAVAILABLE"));
    }

    @Test
    void unexpectedFailuresDoNotExposeImplementationOrSubmittedData() throws Exception {
        storageFailure = new IllegalStateException("private SQL fixture@example.invalid");
        String body = http.perform(request(BODY)).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("INTERNAL_ERROR")).andReturn().getResponse().getContentAsString();
        assertFalse(body.contains("SQL"));
        assertFalse(body.contains("fixture@example.invalid"));
    }

    private static MockHttpServletRequestBuilder request(String body) {
        return post("/api/v1/patients").contentType("application/json").header("Idempotency-Key", "fixture-key-001").content(body);
    }
}
