package co.edu.corhuila.dlc.patients.adapter.out.iam;

import static co.edu.corhuila.dlc.patients.application.model.AuthorizationFailure.Reason.UNAUTHORIZED;
import static co.edu.corhuila.dlc.patients.application.model.AuthorizationFailure.Reason.FORBIDDEN;
import static co.edu.corhuila.dlc.patients.application.model.AuthorizationFailure.Reason.SERVICE_UNAVAILABLE;

import co.edu.corhuila.dlc.patients.application.model.AuthorizationFailure;
import co.edu.corhuila.dlc.patients.application.port.out.RegistrationAuthorization;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

public final class JwtRegistrationAuthorization implements RegistrationAuthorization {
    private static final Set<String> STAFF = Set.of("ADMINISTRATOR", "SECRETARY_ASSISTANT", "DENTIST");
    public record Policy(String issuer, String audience, String permission) {
        public Policy {
            if (issuer == null || issuer.isBlank() || audience == null || audience.isBlank()
                    || permission == null || permission.isBlank()) throw new IllegalArgumentException("Authorization policy required");
        }
    }
    private final JwtDecoder decoder;
    private final Policy policy;
    private final Clock clock;
    private final Supplier<String> accessToken;
    private final Predicate<String> sessionActive;

    public JwtRegistrationAuthorization(JwtDecoder decoder, Policy policy, Clock clock,
            Supplier<String> accessToken, Predicate<String> sessionActive) {
        this.decoder = decoder;
        this.policy = policy;
        this.clock = clock;
        this.accessToken = accessToken;
        this.sessionActive = sessionActive;
    }

    public static JwtDecoder rsaDecoder(RSAPublicKey key, String keyId, Clock clock) {
        if (keyId == null || keyId.isBlank()) throw new IllegalArgumentException("Key ID required");
        var decoder = NimbusJwtDecoder.withPublicKey(key).signatureAlgorithm(SignatureAlgorithm.RS256).build();
        var timestamp = new JwtTimestampValidator(Duration.ofSeconds(30));
        timestamp.setClock(clock);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamp, jwt ->
                keyId.equals(jwt.getHeaders().get("kid")) ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"))));
        return decoder;
    }

    @Override
    public UUID requireAuthorizedActor() {
        try {
            String token = accessToken.get();
            if (token == null || token.isBlank()) throw new AuthorizationFailure(UNAUTHORIZED);
            Jwt jwt = decoder.decode(token);
            UUID actor = uuid(jwt.getSubject());
            uuid(jwt.getClaimAsString("sid"));
            var roles = strings(jwt.getClaim("roles"));
            var permissions = strings(jwt.getClaim("permissions"));
            if (!"RS256".equals(jwt.getHeaders().get("alg")) || !text(jwt.getHeaders().get("kid"))
                    || !policy.issuer().equals(jwt.getClaimAsString("iss"))
                    || jwt.getAudience() == null || !jwt.getAudience().contains(policy.audience())
                    || !text(jwt.getId()) || jwt.getIssuedAt() == null || jwt.getExpiresAt() == null
                    || !jwt.getExpiresAt().isAfter(jwt.getIssuedAt())
                    || Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()).getSeconds() > 3600
                    || jwt.getIssuedAt().isAfter(clock.instant().plusSeconds(30)) || !STAFF.containsAll(roles)) {
                throw new AuthorizationFailure(UNAUTHORIZED);
            }
            boolean active;
            try { active = sessionActive.test(token); }
            catch (RuntimeException failure) { throw new AuthorizationFailure(SERVICE_UNAVAILABLE); }
            if (!active) throw new AuthorizationFailure(UNAUTHORIZED);
            if (roles.stream().noneMatch(role -> role.equals("ADMINISTRATOR") || role.equals("SECRETARY_ASSISTANT"))
                    || !permissions.contains(policy.permission())) throw new AuthorizationFailure(FORBIDDEN);
            return actor;
        } catch (BadJwtException | IllegalArgumentException failure) {
            throw new AuthorizationFailure(UNAUTHORIZED);
        } catch (JwtException failure) {
            throw new AuthorizationFailure(SERVICE_UNAVAILABLE);
        }
    }

    private static boolean text(Object value) { return value instanceof String string && !string.isBlank(); }

    private static UUID uuid(String value) {
        UUID id = UUID.fromString(value == null ? "" : value);
        if (!id.toString().equalsIgnoreCase(value)) throw new IllegalArgumentException();
        return id;
    }

    private static List<String> strings(Object claim) {
        if (!(claim instanceof List<?> list) || list.stream().anyMatch(value -> !text(value))) {
            throw new IllegalArgumentException();
        }
        return list.stream().map(String.class::cast).toList();
    }
}
