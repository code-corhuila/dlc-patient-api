package co.edu.corhuila.dlc.patients.app;

import static org.junit.jupiter.api.Assertions.*;
import co.edu.corhuila.dlc.patients.application.port.in.ReadBillingContactUseCase;
import co.edu.corhuila.dlc.patients.application.port.in.ValidatePatientUseCase;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import java.nio.file.Path;

class PrivateValidationHttpTest {
    @TempDir Path temp;
    @TestConfiguration(proxyBeanMethods = false)
    static class Fixture {
        @Bean ValidatePatientUseCase validation() { return id -> new ValidatePatientUseCase.Result(id, true, 2); }
        @Bean ReadBillingContactUseCase billingContact() {
            return id -> new ReadBillingContactUseCase.Contact(id, "ana@example.test", "Ana García");
        }
    }
    @Test void onlyPrivateListenerAcceptsScopedSignedServiceJwt() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        var keys = generator.generateKeyPair();
        var keyFile = temp.resolve("service-public.pem");
        Files.writeString(keyFile, "-----BEGIN PUBLIC KEY-----\n" + Base64.getMimeEncoder(64, new byte[]{'\n'})
                .encodeToString(keys.getPublic().getEncoded()) + "\n-----END PUBLIC KEY-----\n");
        int internalPort;
        try (var socket = new ServerSocket(0)) { internalPort = socket.getLocalPort(); }
        try (var context = (ServletWebServerApplicationContext) new SpringApplicationBuilder(PatientsApplication.class, Fixture.class)
                .run("--server.port=0", "--patients.database.enabled=false", "--patients.iam.enabled=false",
                        "--patients.registration.enabled=false", "--patients.search.enabled=false",
                        "--patients.internal.enabled=true", "--patients.internal.port=" + internalPort,
                        "--patients.internal.public-key-file=" + keyFile, "--patients.internal.key-id=test-key",
                        "--patients.internal.issuer=service-issuer", "--patients.internal.audience=patients")) {
            int publicPort = context.getWebServer().getPort();
            UUID id = UUID.randomUUID();
            var claims = new JWTClaimsSet.Builder().issuer("service-issuer").subject("appointments").audience("patients")
                    .issueTime(Date.from(Instant.now().minusSeconds(5))).expirationTime(Date.from(Instant.now().plusSeconds(120)))
                    .claim("scope", "patients:validate").build();
            var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-key").build(), claims);
            jwt.sign(new RSASSASigner((RSAPrivateKey) keys.getPrivate()));
            try (var client = HttpClient.newHttpClient()) {
                String path = "/internal/v1/patients/" + id + "/validation";
                var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + internalPort + path))
                        .header("Authorization", "Bearer " + jwt.serialize()).GET().build();
                var result = client.send(request, HttpResponse.BodyHandlers.ofString());
                assertEquals(200, result.statusCode()); assertTrue(result.body().contains(id.toString()));
                assertEquals(404, client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + publicPort + path))
                        .header("Authorization", "Bearer " + jwt.serialize()).GET().build(), HttpResponse.BodyHandlers.ofString()).statusCode());
                assertEquals(401, client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + internalPort + path))
                        .GET().build(), HttpResponse.BodyHandlers.ofString()).statusCode());
                String contactPath = "http://127.0.0.1:" + internalPort + "/internal/v1/patients/" + id + "/billing-contact";
                assertEquals(403, client.send(HttpRequest.newBuilder(URI.create(contactPath))
                        .header("Authorization", "Bearer " + jwt.serialize()).GET().build(), HttpResponse.BodyHandlers.ofString()).statusCode());
                var contactClaims = new JWTClaimsSet.Builder(claims).subject("dlc-worker")
                        .claim("scope", "patients:billing-contact:read").build();
                var contactJwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-key").build(), contactClaims);
                contactJwt.sign(new RSASSASigner((RSAPrivateKey) keys.getPrivate()));
                var contact = client.send(HttpRequest.newBuilder(URI.create(contactPath))
                        .header("Authorization", "Bearer " + contactJwt.serialize()).GET().build(), HttpResponse.BodyHandlers.ofString());
                assertEquals(200, contact.statusCode()); assertTrue(contact.body().contains("ana@example.test"));
                assertEquals(404, client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + internalPort + "/health"))
                        .GET().build(), HttpResponse.BodyHandlers.ofString()).statusCode());
            }
        }
    }
}
