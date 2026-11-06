package co.edu.corhuila.dlc.patients.adapter.in.http;

import java.util.Collections;
import java.util.UUID;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

final class RequestCorrelation {
    private RequestCorrelation() { }
    static UUID current() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            var values = Collections.list(attributes.getRequest().getHeaders("X-Correlation-Id"));
            if (values.size() == 1) {
                try {
                    UUID id = UUID.fromString(values.getFirst());
                    if (id.toString().equalsIgnoreCase(values.getFirst())) return id;
                } catch (IllegalArgumentException ignored) { }
            }
        }
        return UUID.randomUUID();
    }
}
