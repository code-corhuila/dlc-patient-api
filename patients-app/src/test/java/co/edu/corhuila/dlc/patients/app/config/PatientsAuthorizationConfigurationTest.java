package co.edu.corhuila.dlc.patients.app.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.dlc.patients.application.model.AuthorizationFailure;
import co.edu.corhuila.dlc.patients.application.port.in.RegisterPatientUseCase;
import co.edu.corhuila.dlc.patients.application.port.out.RegistrationAuthorization;
import co.edu.corhuila.dlc.patients.application.port.out.RegistrationTransaction;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class PatientsAuthorizationConfigurationTest {
    private static final UUID ACTOR = UUID.randomUUID();
    private static RSAKey first;
    private static RSAKey second;
    @TempDir Path directory;
    private HttpServer server;
    private Path credential;
    private String origin;
    private volatile RSAKey published;
    private volatile int jwksStatus = 200;
    private volatile int sessionStatus = 200;
    private volatile boolean active = true;
    private volatile String receivedCredential;
    private final AtomicInteger keyReads = new AtomicInteger();
    private final AtomicInteger sessionReads = new AtomicInteger();
    private final AtomicInteger redirectedReads = new AtomicInteger();

    @BeforeAll
    static void keys() throws Exception {
        first = new RSAKeyGenerator(2048).keyID("first").generate();
        second = new RSAKeyGenerator(2048).keyID("second").generate();
    }

    @BeforeEach
    void iamContractFixture() throws Exception {
        credential = directory.resolve("service-token");
        Files.writeString(credential, "fixture-service-token\n");
        published = first;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/auth/jwks", exchange -> {
            keyReads.incrementAndGet();
            byte[] body = new JWKSet(published.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Location", "/unexpected");
            exchange.sendResponseHeaders(jwksStatus, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/internal/v1/sessions/validations", exchange -> {
            sessionReads.incrementAndGet();
            receivedCredential = exchange.getRequestHeaders().getFirst("Authorization");
            exchange.getRequestBody().readAllBytes();
            byte[] body = ("{\"active\":" + active + "}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(sessionStatus, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/unexpected", exchange -> {
            redirectedReads.incrementAndGet();
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        server.start();
        origin = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void cleanup() {
        RequestContextHolder.resetRequestAttributes();
        server.stop(0);
    }

    @Test
    void wiresUseCaseAndRefreshesUnknownSigningKeyAndServiceCredential() throws Exception {
        String initial = token(first, List.of("fixture:register"));
        String rotated = token(second, List.of("fixture:register"));
        context().run(app -> {
            assertThat(app).hasNotFailed().hasSingleBean(RegisterPatientUseCase.class);
            var authorization = app.getBean(RegistrationAuthorization.class);
            request(initial);
            assertEquals(ACTOR, authorization.requireAuthorizedActor());
            published = second;
            try { Files.writeString(credential, "fixture-renewed-token\n"); }
            catch (Exception failure) { throw new AssertionError(failure); }
            request(rotated);
            assertEquals(ACTOR, authorization.requireAuthorizedActor());
            assertEquals("Bearer fixture-renewed-token", receivedCredential);
            active = false;
            failure(authorization, AuthorizationFailure.Reason.UNAUTHORIZED);
            assertEquals(3, sessionReads.get());
            assertTrue(keyReads.get() >= 2);
        });
    }

    @Test
    void currentIamEmptyPermissionsCannotRegister() throws Exception {
        request(token(first, List.of()));
        context().run(app -> assertEquals(AuthorizationFailure.Reason.FORBIDDEN, assertThrows(AuthorizationFailure.class,
                () -> app.getBean(RegisterPatientUseCase.class).register(null)).reason()));
    }

    @Test
    void missingRequestIdentityFailsBeforeIam() {
        context().run(app -> failure(app.getBean(RegistrationAuthorization.class), AuthorizationFailure.Reason.UNAUTHORIZED));
        assertEquals(0, keyReads.get() + sessionReads.get());
    }

    @Test
    void duplicateBearerHeadersCannotSelectAnIdentity() throws Exception {
        request(token(first, List.of("fixture:register")));
        var attributes = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        ((MockHttpServletRequest) attributes.getRequest()).addHeader("Authorization", "Bearer another-token");
        context().run(app -> failure(app.getBean(RegistrationAuthorization.class), AuthorizationFailure.Reason.UNAUTHORIZED));
        assertEquals(0, keyReads.get() + sessionReads.get());
    }

    @Test
    void lostServiceCredentialFailsClosedAfterStartup() throws Exception {
        request(token(first, List.of("fixture:register")));
        context().run(app -> {
            try { Files.delete(credential); }
            catch (Exception failure) { throw new AssertionError(failure); }
            failure(app.getBean(RegistrationAuthorization.class), AuthorizationFailure.Reason.SERVICE_UNAVAILABLE);
            assertEquals(0, sessionReads.get());
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"environment=prod", "registration-permission=", "base-url=http://user@localhost",
            "base-url=http://localhost/unexpected", "service-token-file=missing"})
    void invalidConfigurationFailsStartup(String setting) {
        context().withPropertyValues("patients.iam." + setting).run(app -> assertThat(app).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"jwks", "jwks-redirect", "sessions"})
    void dependencyFailuresAreSanitized(String dependency) throws Exception {
        request(token(first, List.of("fixture:register")));
        if (dependency.equals("jwks")) jwksStatus = 503;
        else if (dependency.equals("jwks-redirect")) jwksStatus = 302;
        else sessionStatus = 401;
        context().run(app -> failure(app.getBean(RegistrationAuthorization.class), AuthorizationFailure.Reason.SERVICE_UNAVAILABLE));
        assertEquals(0, redirectedReads.get());
    }

    private ApplicationContextRunner context() {
        return new ApplicationContextRunner().withUserConfiguration(PatientsAuthorizationConfiguration.class)
                .withBean(RegistrationTransaction.class, () -> write -> null)
                .withPropertyValues("patients.iam.base-url=" + origin, "patients.iam.environment=dev",
                        "patients.iam.issuer=fixture-iam", "patients.iam.audience=fixture-audience",
                        "patients.iam.registration-permission=fixture:register", "patients.iam.service-token-file=" + credential);
    }

    private static void request(String token) {
        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        request.addHeader("X-User-Id", UUID.randomUUID().toString());
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private static void failure(RegistrationAuthorization authorization, AuthorizationFailure.Reason reason) {
        var failure = assertThrows(AuthorizationFailure.class, authorization::requireAuthorizedActor);
        assertEquals(reason, failure.reason());
        assertEquals(reason.name(), failure.getMessage());
        assertEquals(null, failure.getCause());
    }

    private static String token(RSAKey key, List<String> permissions) throws Exception {
        var now = Instant.now();
        var claims = new JWTClaimsSet.Builder().subject(ACTOR.toString()).issuer("fixture-iam")
                .claim("aud", "fixture-audience").issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(3600)))
                .jwtID(UUID.randomUUID().toString()).claim("sid", UUID.randomUUID().toString())
                .claim("roles", List.of("ADMINISTRATOR")).claim("permissions", permissions).build();
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }
}
