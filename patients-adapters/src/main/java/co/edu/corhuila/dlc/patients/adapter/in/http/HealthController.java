package co.edu.corhuila.dlc.patients.adapter.in.http;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Liveness only; it exposes no dependency detail and requires no token (Annex C). */
@RestController
public class HealthController {

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "service", "dlc-patient-api");
    }
}
