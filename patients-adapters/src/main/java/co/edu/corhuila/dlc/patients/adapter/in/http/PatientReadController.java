package co.edu.corhuila.dlc.patients.adapter.in.http;

import co.edu.corhuila.dlc.patients.application.model.AuthorizationFailure;
import co.edu.corhuila.dlc.patients.application.model.PatientNotFoundException;
import co.edu.corhuila.dlc.patients.application.model.PatientReadResult;
import co.edu.corhuila.dlc.patients.application.model.PatientReadUnavailableException;
import co.edu.corhuila.dlc.patients.application.port.in.ReadPatientUseCase;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(prefix = "patients.search", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnBean(ReadPatientUseCase.class)
public final class PatientReadController {
    private final ReadPatientUseCase read;
    public PatientReadController(ReadPatientUseCase read) { this.read = read; }

    @GetMapping(path = "/api/v1/patients/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> read(@PathVariable("id") String rawId) {
        UUID traceId = UUID.randomUUID();
        try {
            UUID id = UUID.fromString(rawId);
            if (!id.toString().equalsIgnoreCase(rawId)) throw new IllegalArgumentException();
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(view(read.read(id)));
        } catch (AuthorizationFailure failure) {
            int status = switch (failure.reason()) { case UNAUTHORIZED -> 401; case FORBIDDEN -> 403; case SERVICE_UNAVAILABLE -> 503; };
            return error(status, failure.reason().name(), "The operation is not currently authorized", traceId);
        } catch (PatientNotFoundException failure) {
            return error(404, "NOT_FOUND", "Patient not found", traceId);
        } catch (PatientReadUnavailableException failure) {
            return error(503, "SERVICE_UNAVAILABLE", "Patient read is temporarily unavailable", traceId);
        } catch (IllegalArgumentException failure) {
            return error(400, "VALIDATION_ERROR", "Invalid patient id", traceId);
        } catch (RuntimeException failure) {
            return error(500, "INTERNAL_ERROR", "Internal server error", traceId);
        }
    }

    private static PatientSearchResponse.View view(PatientReadResult result) {
        if (result instanceof PatientReadResult.Care care) return new PatientSearchResponse.View(care.id(), null, null,
                care.name(), null, care.phone(), care.email(), null, null, care.status(), null, null, care.version());
        var row = (PatientReadResult.Administrative) result;
        var profile = row.profile();
        return new PatientSearchResponse.View(row.id(), row.document().type(), row.document().number(), profile.name(),
                profile.birthDate().toString(), profile.phone(), profile.email(), profile.address(), profile.emergencyContacts(),
                row.status(), row.createdAt().toString(), row.updatedAt().toString(), row.version());
    }

    private static ResponseEntity<?> error(int status, String code, String message, UUID traceId) {
        var response = ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).cacheControl(CacheControl.noStore());
        if (status == 401) response.header("WWW-Authenticate", "Bearer realm=\"di-lucca\"");
        return response.body(Map.of("error", code, "message", message, "traceId", traceId.toString()));
    }
}
