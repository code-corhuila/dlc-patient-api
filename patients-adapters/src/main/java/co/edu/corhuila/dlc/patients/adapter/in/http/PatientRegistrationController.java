package co.edu.corhuila.dlc.patients.adapter.in.http;

import co.edu.corhuila.dlc.patients.application.model.AuthorizationFailure;
import co.edu.corhuila.dlc.patients.application.model.RegistrationConflictException;
import co.edu.corhuila.dlc.patients.application.model.RegistrationUnavailableException;
import co.edu.corhuila.dlc.patients.application.port.in.RegisterPatientUseCase;
import co.edu.corhuila.dlc.patients.application.port.out.RegistrationAuthorization;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URI;
import java.time.DateTimeException;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(prefix = "patients.registration", name = "enabled", havingValue = "true", matchIfMissing = true)
public final class PatientRegistrationController {
    private final RegisterPatientUseCase registration;
    private final RegistrationAuthorization authorization;
    private final ObjectMapper json;

    public PatientRegistrationController(RegisterPatientUseCase registration, RegistrationAuthorization authorization, ObjectMapper json) {
        this.registration = registration;
        this.authorization = authorization;
        this.json = json.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    @PostMapping(path = "/api/v1/patients", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> register(HttpServletRequest request) {
        UUID traceId = UUID.randomUUID();
        try {
            // Reject unauthorized callers before parsing; the use case independently rechecks before writing/replay.
            authorization.requireAuthorizedActor();
            var keys = Collections.list(request.getHeaders("Idempotency-Key"));
            if (keys.size() != 1) throw new IllegalArgumentException();
            var command = RegistrationRequest.command(json.readTree(request.getInputStream()), keys.getFirst(), traceId, UUID.randomUUID());
            var result = registration.register(command);
            return ResponseEntity.status(result.replayed() ? 200 : 201)
                    .location(URI.create("/api/v1/patients/" + result.patient().id())).cacheControl(CacheControl.noStore())
                    .body(RegistrationResponse.from(result));
        } catch (AuthorizationFailure failure) {
            int status = switch (failure.reason()) {
                case UNAUTHORIZED -> 401;
                case FORBIDDEN -> 403;
                case SERVICE_UNAVAILABLE -> 503;
            };
            return error(status, failure.reason().name(), "The operation is not currently authorized", traceId);
        } catch (RegistrationConflictException failure) {
            String message = failure.reason() == RegistrationConflictException.Reason.PATIENT_DOCUMENT_EXISTS
                    ? "A patient with this document is already registered."
                    : "The idempotency key was already used with a different request.";
            return error(409, failure.reason().name(), message, traceId);
        } catch (RegistrationUnavailableException failure) {
            return error(503, "SERVICE_UNAVAILABLE", "Registration storage is temporarily unavailable", traceId);
        } catch (IOException | IllegalArgumentException | DateTimeException failure) {
            return error(400, "VALIDATION_ERROR", "Invalid input data", traceId);
        } catch (RuntimeException failure) {
            return error(500, "INTERNAL_ERROR", "Internal server error", traceId);
        }
    }

    private static ResponseEntity<?> error(int status, String code, String message, UUID traceId) {
        var response = ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).cacheControl(CacheControl.noStore());
        if (status == 401) response.header("WWW-Authenticate", "Bearer realm=\"di-lucca\"");
        return response.body(Map.of("error", code, "message", message, "traceId", traceId.toString()));
    }
}
