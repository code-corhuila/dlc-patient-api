package co.edu.corhuila.dlc.patients.adapter.out.iam;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import co.edu.corhuila.dlc.patients.application.model.AuthorizationFailure;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JwtRegistrationAuthorizationTest {
    private static final UUID ACTOR = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final Instant NOW = Instant.parse("2026-11-04T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static RSAKey key;
    private static RSAKey wrongKey;
    private final AtomicInteger calls = new AtomicInteger();
    private HttpServer server;
    private HttpClient http;
    private JwtRegistrationAuthorization authorization;
    private String token;
    private String response = "{\"active\":true}";
    private int status = 200;
    private String receivedBody;
    private String receivedCredential;

    @BeforeAll
    static void generatedFixtureKeys() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        key = new RSAKey.Builder((RSAPublicKey) pair.getPublic()).privateKey(pair.getPrivate()).build();
        pair = generator.generateKeyPair();
        wrongKey = new RSAKey.Builder((RSAPublicKey) pair.getPublic()).privateKey(pair.getPrivate()).build();
    }

    @BeforeEach
    void privateIamContractFixture() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            calls.incrementAndGet();
            receivedBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            receivedCredential = exchange.getRequestHeaders().getFirst("Authorization");
            exchange.getResponseHeaders().set("Location", "/unexpected");
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        var endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/internal/v1/sessions/validations");
        var sessions = new IamSessionClient(endpoint, () -> "fixture-service-credential", http, true);
        var policy = new JwtRegistrationAuthorization.Policy("https://iam.example.invalid", "patients-service", "fixture:register");
        authorization = new JwtRegistrationAuthorization(JwtRegistrationAuthorization.rsaDecoder(
                key.toRSAPublicKey(), "fixture-key", CLOCK), policy, CLOCK, () -> token, sessions);
    }

    @AfterEach
    void closeFixture() {
        http.close();
        server.stop(0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMINISTRATOR", "SECRETARY_ASSISTANT"})
    void eligibleStaffUseThePrivateSessionContract(String role) throws Exception {
        token = issue(role);
        assertEquals(ACTOR, authorization.requireAuthorizedActor());
        assertEquals("Bearer fixture-service-credential", receivedCredential);
        assertEquals("{\"accessToken\":\"" + token + "\"}", receivedBody);
        assertEquals(1, calls.get());
    }

    @ParameterizedTest
    @ValueSource(strings = {"issuer", "audience", "subject", "sid", "jti", "iat", "exp", "kid",
            "expired", "future", "long-life", "roles", "permissions", "unknown-role", "HS256", "none", "wrong-key",
            "missing-audience", "wrong-kid"})
    void rejectsInvalidJwtBeforeCallingIam(String change) throws Exception {
        token = issue(change);
        assertEquals(AuthorizationFailure.Reason.UNAUTHORIZED,
                assertThrows(AuthorizationFailure.class, authorization::requireAuthorizedActor).reason());
        assertEquals(0, calls.get());
    }

    @Test
    void missingTokenAndForbiddenRoleOrPermissionNeverGrantAccess() throws Exception {
        assertEquals(AuthorizationFailure.Reason.UNAUTHORIZED,
                assertThrows(AuthorizationFailure.class, authorization::requireAuthorizedActor).reason());
        for (var change : List.of("DENTIST", "no-permission")) {
            token = issue(change);
            assertEquals(AuthorizationFailure.Reason.FORBIDDEN,
                    assertThrows(AuthorizationFailure.class, authorization::requireAuthorizedActor).reason());
        }
    }

    @Test
    void everyAttemptRechecksCurrentSessionEligibility() throws Exception {
        token = issue("ADMINISTRATOR");
        assertEquals(ACTOR, authorization.requireAuthorizedActor());
        response = "{\"active\":false}";
        assertEquals(AuthorizationFailure.Reason.UNAUTHORIZED,
                assertThrows(AuthorizationFailure.class, authorization::requireAuthorizedActor).reason());
        assertEquals(2, calls.get());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "[]", "{\"active\":\"true\"}", "{\"active\":true,\"extra\":1}", "invalid",
            "{\"active\":true} {}", "{\"active\":false,\"active\":true}"})
    void malformedAuthorityResponsesFailClosedWithoutExposingCredentials(String body) throws Exception {
        token = issue("ADMINISTRATOR");
        response = body;
        unavailable();
    }

    @ParameterizedTest
    @ValueSource(ints = {302, 401, 403, 404, 429, 500})
    void failedAuthorityCallsAndRedirectsFailClosed(int code) throws Exception {
        token = issue("ADMINISTRATOR");
        status = code;
        unavailable();
        assertEquals(1, calls.get());
    }

    private void unavailable() {
        var failure = assertThrows(AuthorizationFailure.class, authorization::requireAuthorizedActor);
        assertEquals(AuthorizationFailure.Reason.SERVICE_UNAVAILABLE, failure.reason());
        assertFalse(failure.toString().contains(token));
        assertFalse(failure.toString().contains("fixture-service-credential"));
    }

    private String issue(String change) throws Exception {
        var claims = new JWTClaimsSet.Builder().subject(ACTOR.toString()).issuer("https://iam.example.invalid")
                .audience("patients-service").issueTime(Date.from(NOW.minusSeconds(10)))
                .expirationTime(Date.from(NOW.plusSeconds(600))).jwtID("fixture-jti")
                .claim("sid", ACTOR.toString()).claim("roles", List.of("ADMINISTRATOR"))
                .claim("permissions", List.of("fixture:register"));
        switch (change) {
            case "issuer" -> claims.issuer("https://other.example.invalid");
            case "audience" -> claims.audience("another-service");
            case "missing-audience" -> claims.claim("aud", null);
            case "subject" -> claims.subject("not-a-uuid");
            case "sid" -> claims.claim("sid", "not-a-uuid");
            case "jti", "iat", "exp" -> claims.claim(change, null);
            case "expired" -> claims.expirationTime(Date.from(NOW.minusSeconds(31)));
            case "future" -> claims.issueTime(Date.from(NOW.plusSeconds(31)));
            case "long-life" -> claims.expirationTime(Date.from(NOW.plusSeconds(3601)));
            case "roles", "permissions" -> claims.claim(change, "not-an-array");
            case "unknown-role" -> claims.claim("roles", List.of("ADMINISTRATOR", "PATIENT"));
            case "DENTIST", "SECRETARY_ASSISTANT" -> claims.claim("roles", List.of(change));
            case "no-permission" -> claims.claim("permissions", List.of());
        }
        if (change.equals("none")) {
            return new PlainJWT(claims.build()).serialize();
        }
        var algorithm = change.equals("HS256") ? JWSAlgorithm.HS256 : JWSAlgorithm.RS256;
        var jwt = new SignedJWT(new JWSHeader.Builder(algorithm)
                .keyID(change.equals("kid") ? null : change.equals("wrong-kid") ? "other-key" : "fixture-key").build(), claims.build());
        jwt.sign(change.equals("HS256") ? new MACSigner(new byte[32])
                : new RSASSASigner(change.equals("wrong-key") ? wrongKey : key));
        return jwt.serialize();
    }
}
