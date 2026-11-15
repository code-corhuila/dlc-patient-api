package co.edu.corhuila.dlc.patients.adapter.in.http;

import co.edu.corhuila.dlc.patients.application.model.AuthorizationFailure;
import co.edu.corhuila.dlc.patients.application.model.PatientNotFoundException;
import co.edu.corhuila.dlc.patients.application.model.PatientReadUnavailableException;
import co.edu.corhuila.dlc.patients.application.port.in.ValidatePatientUseCase;
import co.edu.corhuila.dlc.patients.application.port.out.PatientServiceAuthorization;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = "patients.internal", name = "enabled", havingValue = "true")
public final class PatientValidationController {
    private final PatientServiceAuthorization authorization;
    private final ValidatePatientUseCase validation;
    public PatientValidationController(PatientServiceAuthorization authorization, ValidatePatientUseCase validation) {
        this.authorization = authorization;
        this.validation = validation;
    }
    @GetMapping("/internal/v1/patients/{id}/validation")
    public ResponseEntity<?> validate(@PathVariable("id") String rawId) {
        UUID trace = RequestCorrelation.current();
        try {
            authorization.requireValidationScope();
            UUID id = UUID.fromString(rawId);
            if (!id.toString().equalsIgnoreCase(rawId)) throw new IllegalArgumentException();
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(validation.validate(id));
        } catch (AuthorizationFailure failure) {
            int status = switch (failure.reason()) { case UNAUTHORIZED -> 401; case FORBIDDEN -> 403; case SERVICE_UNAVAILABLE -> 503; };
            return error(status, failure.reason().name(), trace);
        } catch (PatientNotFoundException failure) {
            return error(404, "NOT_FOUND", trace);
        } catch (PatientReadUnavailableException failure) {
            return error(503, "SERVICE_UNAVAILABLE", trace);
        } catch (IllegalArgumentException failure) {
            return error(400, "VALIDATION_ERROR", trace);
        } catch (RuntimeException failure) {
            return error(500, "INTERNAL_ERROR", trace);
        }
    }
    private static ResponseEntity<?> error(int status, String code, UUID trace) {
        var response = ResponseEntity.status(status).cacheControl(CacheControl.noStore());
        if (status == 401) response.header("WWW-Authenticate", "Bearer");
        return response.body(Map.of("error", code, "message", "Patient validation could not be completed", "traceId", trace));
    }
}
