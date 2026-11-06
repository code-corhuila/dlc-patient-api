package co.edu.corhuila.dlc.patients.app;

import java.sql.SQLException;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Private network probe; the Gateway exposes only owner API routes and its own /health. */
@RestController
final class DatabaseReadinessController {
    private final ObjectProvider<DataSource> sources;
    DatabaseReadinessController(ObjectProvider<DataSource> sources) { this.sources = sources; }
    @GetMapping("/ready")
    ResponseEntity<?> ready() {
        DataSource source = sources.getIfAvailable();
        if (source != null) {
            try (var connection = source.getConnection(); var statement = connection.createStatement()) {
                statement.setQueryTimeout(5);
                // Resolve the owned tables and SELECT grants without reading patient records.
                statement.execute("SELECT 1 FROM patients.patient, patients.emergency_contact, patients.patient_audit, "
                        + "patients.idempotency_record, patients.outbox_event, patients.deactivation_operation LIMIT 0");
                return result(200, "UP");
            } catch (SQLException failure) { /* Never return connection details or credentials. */ }
        }
        return result(503, "DOWN");
    }
    private static ResponseEntity<?> result(int status, String state) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore())
                .body(Map.of("status", state, "component", "patients-database"));
    }
}
