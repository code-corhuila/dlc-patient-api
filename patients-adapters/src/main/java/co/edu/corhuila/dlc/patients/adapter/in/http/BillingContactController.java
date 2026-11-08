package co.edu.corhuila.dlc.patients.adapter.in.http;

import co.edu.corhuila.dlc.patients.application.model.AuthorizationFailure;
import co.edu.corhuila.dlc.patients.application.model.PatientNotFoundException;
import co.edu.corhuila.dlc.patients.application.model.PatientReadUnavailableException;
import co.edu.corhuila.dlc.patients.application.port.in.ReadBillingContactUseCase;
import co.edu.corhuila.dlc.patients.application.port.out.PatientServiceAuthorization;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Private minimum invoice recipient for Billing and dlc-worker (internal-coordination.yaml). */
@RestController
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = "patients.internal", name = "enabled", havingValue = "true")
public final class BillingContactController {
    private final PatientServiceAuthorization authorization;
    private final ReadBillingContactUseCase contacts;
    public BillingContactController(PatientServiceAuthorization authorization, ReadBillingContactUseCase contacts) {
        this.authorization = authorization;
        this.contacts = contacts;
    }
    @GetMapping("/internal/v1/patients/{id}/billing-contact")
    public ResponseEntity<?> read(@PathVariable("id") String rawId) {
        UUID trace = RequestCorrelation.current();
        try {
            authorization.requireBillingContactScope();
            UUID id = UUID.fromString(rawId);
            if (!id.toString().equalsIgnoreCase(rawId)) throw new IllegalArgumentException();
            var contact = contacts.read(id);
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("patientId", contact.patientId(),
                    "email", contact.email(), "displayName", contact.displayName()));
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
        return response.body(Map.of("error", code, "message", "Billing contact could not be resolved", "traceId", trace));
    }
}
