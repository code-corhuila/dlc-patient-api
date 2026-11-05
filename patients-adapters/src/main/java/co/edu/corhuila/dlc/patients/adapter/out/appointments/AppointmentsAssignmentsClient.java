package co.edu.corhuila.dlc.patients.adapter.out.appointments;

import co.edu.corhuila.dlc.patients.application.model.PatientSearchUnavailableException;
import co.edu.corhuila.dlc.patients.application.port.out.CurrentPatientAssignments;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

public final class AppointmentsAssignmentsClient implements CurrentPatientAssignments {
    private static final Set<String> SOURCES = Set.of("APPOINTMENT", "ONGOING_ASSIGNMENT");
    private final URI base;
    private final Supplier<String> credential;
    private final HttpClient client;
    private final ObjectMapper json = new ObjectMapper();

    public AppointmentsAssignmentsClient(URI base, Supplier<String> credential, HttpClient client) {
        this.base = base; this.credential = credential; this.client = client;
    }

    @Override
    public Snapshot currentFor(UUID dentistId) {
        var ids = new HashSet<UUID>(); long version = -1; int page = 1; long expectedTotal = -1;
        try {
            while (true) {
                String token = credential.get();
                if (token == null || token.isBlank()) throw unavailable();
                var request = HttpRequest.newBuilder(base.resolve("/internal/v1/dentists/" + dentistId + "/assigned-patients?page=" + page + "&limit=100"))
                        .timeout(Duration.ofSeconds(5)).header("Authorization", "Bearer " + token)
                        .header("Accept", "application/json").GET().build();
                var response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) throw unavailable();
                JsonNode body = json.readTree(response.body());
                if (!body.isObject() || body.size() != 4 || !body.path("dentistId").isTextual()
                        || !dentistId.toString().equals(body.get("dentistId").asText()) || !body.path("assignmentsVersion").canConvertToLong()
                        || !body.path("data").isArray() || !body.path("meta").isObject()) throw unavailable();
                long current = body.get("assignmentsVersion").asLong();
                if (current < 1 || version != -1 && version != current) throw unavailable();
                version = current;
                for (JsonNode item : body.get("data")) {
                    if (!item.isObject() || item.size() != 3 || !item.path("patientId").isTextual()
                            || !item.path("relationshipVersion").canConvertToInt()
                            || item.path("relationshipVersion").asInt() < 1 || !item.path("sourceTypes").isArray()
                            || item.get("sourceTypes").isEmpty() || !validSources(item.get("sourceTypes"))) throw unavailable();
                    ids.add(UUID.fromString(item.get("patientId").asText()));
                }
                JsonNode meta = body.get("meta");
                if (!meta.isObject() || meta.size() != 4 || meta.path("page").asInt(-1) != page
                        || meta.path("limit").asInt(-1) != 100 || meta.path("total").asInt(-1) < 0
                        || meta.path("totalPages").asInt(-1) < 0) throw unavailable();
                long total = meta.path("total").asLong();
                int totalPages = meta.path("totalPages").asInt();
                if (totalPages != (total + 99) / 100 || expectedTotal != -1 && total != expectedTotal
                        || body.get("data").size() != Math.min(100, Math.max(0, total - (long) (page - 1) * 100))
                        || ids.size() != Math.min(total, (long) page * 100)) throw unavailable();
                expectedTotal = total;
                if (page >= totalPages) return new Snapshot(dentistId, version, ids);
                page++;
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); throw unavailable();
        } catch (Exception failure) { throw unavailable(); }
    }

    private static boolean validSources(JsonNode sources) {
        for (JsonNode source : sources) if (!source.isTextual() || !SOURCES.contains(source.asText())) return false;
        return true;
    }

    private static PatientSearchUnavailableException unavailable() { return new PatientSearchUnavailableException(); }
}
