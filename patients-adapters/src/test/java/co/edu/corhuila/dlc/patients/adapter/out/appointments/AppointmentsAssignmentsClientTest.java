package co.edu.corhuila.dlc.patients.adapter.out.appointments;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AppointmentsAssignmentsClientTest {
    private static final UUID DENTIST = UUID.randomUUID();
    private HttpServer server; private HttpClient http; private AtomicInteger calls;
    private volatile String overrideBody;
    @BeforeEach void start() throws Exception {
        calls = new AtomicInteger(); server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> { int page = calls.incrementAndGet(); exchange.getRequestBody().readAllBytes();
            var entries = new java.util.ArrayList<String>();
            for (int i = 0; i < (page == 1 ? 100 : 1); i++)
                entries.add("{\"patientId\":\"" + UUID.randomUUID() + "\",\"relationshipVersion\":1,\"sourceTypes\":[\"APPOINTMENT\"]}");
            String body = "{\"dentistId\":\"" + DENTIST + "\",\"assignmentsVersion\":3,\"data\":[" + String.join(",", entries)
                    + "],\"meta\":{\"page\":" + page + ",\"limit\":100,\"total\":101,\"totalPages\":2}}";
            if (overrideBody != null) body = overrideBody;
            byte[] bytes = body.getBytes(); exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close(); });
        server.start(); http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    }
    @AfterEach void stop() { http.close(); server.stop(0); }
    @Test void readsEveryPageAtOneVersionAndUsesServiceBearer() {
        var result = new AppointmentsAssignmentsClient(URI.create("http://127.0.0.1:" + server.getAddress().getPort()), () -> "service", http).currentFor(DENTIST);
        assertEquals(DENTIST, result.dentistId()); assertEquals(3, result.assignmentsVersion()); assertEquals(101, result.patientIds().size()); assertEquals(2, calls.get());
    }
    @Test void rejectsInconsistentPaginationMetadata() {
        overrideBody = "{\"dentistId\":\"" + DENTIST + "\",\"assignmentsVersion\":3,\"data\":[],\"meta\":{\"page\":1,\"limit\":100,\"total\":2,\"totalPages\":0}}";
        var client = new AppointmentsAssignmentsClient(URI.create("http://127.0.0.1:" + server.getAddress().getPort()), () -> "service", http);
        assertThrows(co.edu.corhuila.dlc.patients.application.model.PatientSearchUnavailableException.class, () -> client.currentFor(DENTIST));
    }

    @Test void unavailableEndpointFailsClosed() {
        var client = new AppointmentsAssignmentsClient(URI.create("http://127.0.0.1:1"), () -> "service", http);
        assertThrows(RuntimeException.class, () -> client.currentFor(DENTIST));
    }
}
