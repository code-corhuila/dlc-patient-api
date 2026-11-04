package co.edu.corhuila.dlc.patients.adapter.in.http;

import co.edu.corhuila.dlc.patients.application.model.AuthorizationFailure;
import co.edu.corhuila.dlc.patients.application.model.PatientSearchUnavailableException;
import co.edu.corhuila.dlc.patients.application.port.in.SearchPatientsUseCase;
import co.edu.corhuila.dlc.patients.domain.model.InvalidPatientDataException;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(prefix = "patients.search", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnBean(SearchPatientsUseCase.class)
public final class PatientSearchController {
    private final SearchPatientsUseCase search;

    public PatientSearchController(SearchPatientsUseCase search) {
        this.search = search;
    }

    @GetMapping(path = "/api/v1/patients", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> list(@RequestParam(name = "page", required = false) String page,
            @RequestParam(name = "limit", required = false) String limit,
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "status", required = false) String status) {
        UUID traceId = UUID.randomUUID();
        try {
            var query = new SearchPatientsUseCase.Query(integer(page, "page"), integer(limit, "limit"), search, status);
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(PatientSearchResponse.from(this.search.search(query)));
        } catch (AuthorizationFailure failure) {
            int code = switch (failure.reason()) {
                case UNAUTHORIZED -> 401;
                case FORBIDDEN -> 403;
                case SERVICE_UNAVAILABLE -> 503;
            };
            return error(code, failure.reason().name(), "The operation is not currently authorized", traceId);
        } catch (InvalidPatientDataException failure) {
            return error(400, "VALIDATION_ERROR", "Invalid search parameters", traceId);
        } catch (PatientSearchUnavailableException failure) {
            return error(503, "SERVICE_UNAVAILABLE", "Patient search is temporarily unavailable", traceId);
        } catch (RuntimeException failure) {
            return error(500, "INTERNAL_ERROR", "Internal server error", traceId);
        }
    }

    private static Integer integer(String value, String field) {
        if (value == null) return null;
        try { return Integer.valueOf(value); }
        catch (NumberFormatException failure) { throw new InvalidPatientDataException(field, "must be an integer"); }
    }

    private static ResponseEntity<?> error(int status, String code, String message, UUID traceId) {
        var response = ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).cacheControl(CacheControl.noStore());
        if (status == 401) response.header("WWW-Authenticate", "Bearer realm=\"di-lucca\"");
        return response.body(Map.of("error", code, "message", message, "traceId", traceId.toString()));
    }
}
