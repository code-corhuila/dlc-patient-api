package co.edu.corhuila.dlc.patients.adapter.out.iam;

import co.edu.corhuila.dlc.patients.application.model.AuthorizationFailure;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class IamSessionClient implements Predicate<String> {
    private final URI endpoint;
    private final Supplier<String> serviceCredential;
    private final HttpClient client;
    private final ObjectMapper json = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    public IamSessionClient(URI endpoint, Supplier<String> serviceCredential, HttpClient client, boolean localDevelopment) {
        if (endpoint.getHost() == null || endpoint.getUserInfo() != null || endpoint.getQuery() != null
                || endpoint.getFragment() != null || !"/internal/v1/sessions/validations".equals(endpoint.getPath())
                || !("https".equals(endpoint.getScheme()) || localDevelopment && "http".equals(endpoint.getScheme()))
                || client.followRedirects() != HttpClient.Redirect.NEVER
                || client.connectTimeout().orElse(Duration.ofDays(1)).compareTo(Duration.ofSeconds(5)) > 0) {
            throw new IllegalArgumentException("Invalid IAM client configuration");
        }
        this.endpoint = endpoint;
        this.serviceCredential = serviceCredential;
        this.client = client;
    }

    @Override
    public boolean test(String accessToken) {
        try {
            String credential = serviceCredential.get();
            if (credential == null || credential.isBlank()) throw unavailable();
            var request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(5))
                    .header("Authorization", "Bearer " + credential).header("Content-Type", "application/json")
                    .header("Accept", "application/json").POST(HttpRequest.BodyPublishers.ofString(
                            json.writeValueAsString(Map.of("accessToken", accessToken)))).build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw unavailable();
            var body = json.readTree(response.body());
            if (body == null || !body.isObject() || body.size() != 1 || !body.path("active").isBoolean()) throw unavailable();
            return body.get("active").booleanValue();
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (IOException | RuntimeException failure) {
            throw unavailable();
        }
    }

    private static AuthorizationFailure unavailable() {
        return new AuthorizationFailure(AuthorizationFailure.Reason.SERVICE_UNAVAILABLE);
    }
}
