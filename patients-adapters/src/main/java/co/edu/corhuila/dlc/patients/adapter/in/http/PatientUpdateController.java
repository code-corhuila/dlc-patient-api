package co.edu.corhuila.dlc.patients.adapter.in.http;

import co.edu.corhuila.dlc.patients.application.model.*;
import co.edu.corhuila.dlc.patients.application.port.in.UpdatePatientUseCase;
import co.edu.corhuila.dlc.patients.application.port.out.PatientUpdateAuthorization;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.DateTimeException;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(prefix="patients.update", name="enabled", havingValue="true")
public final class PatientUpdateController {
    private final UpdatePatientUseCase update;
    private final PatientUpdateAuthorization authorization;
    private final ObjectMapper json;
    public PatientUpdateController(UpdatePatientUseCase update, PatientUpdateAuthorization authorization, ObjectMapper json) {
        this.update = update; this.authorization = authorization;
        this.json = json.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }
    @PatchMapping(path="/api/v1/patients/{id}", consumes=MediaType.APPLICATION_JSON_VALUE, produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> update(@PathVariable("id") String rawId, HttpServletRequest request) {
        UUID traceId = UUID.randomUUID();
        try {
            authorization.requireAuthorizedActor();
            UUID id = UUID.fromString(rawId);
            if (!id.toString().equalsIgnoreCase(rawId)) throw new IllegalArgumentException();
            var result = update.update(PatientUpdateRequest.command(json.readTree(request.getInputStream()), id, traceId, UUID.randomUUID()));
            var p = result.profile();
            var body = new PatientSearchResponse.View(result.id(), result.document().type(), result.document().number(), p.name(),
                    p.birthDate().toString(), p.phone(), p.email(), p.address(), p.emergencyContacts(), result.status(),
                    result.createdAt().toString(), result.updatedAt().toString(), result.version());
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
        } catch (AuthorizationFailure failure) {
            int status = switch (failure.reason()) { case UNAUTHORIZED -> 401; case FORBIDDEN -> 403; case SERVICE_UNAVAILABLE -> 503; };
            return error(status, failure.reason().name(), "The operation is not currently authorized", traceId);
        } catch (PatientNotFoundException failure) {
            return error(404, "NOT_FOUND", "Patient not found", traceId);
        } catch (PatientUpdateConflictException failure) {
            return error(409, failure.reason().name(), "Patient update conflict", traceId);
        } catch (PatientUpdateUnavailableException failure) {
            return error(503, "SERVICE_UNAVAILABLE", "Patient update is temporarily unavailable", traceId);
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
