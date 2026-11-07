package co.edu.corhuila.dlc.patients.adapter.out.iam;

import co.edu.corhuila.dlc.patients.application.model.AuthorizationFailure;
import co.edu.corhuila.dlc.patients.application.port.out.PatientSearchAuthorization;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

public final class JwtPatientSearchAuthorization implements PatientSearchAuthorization {
    private static final Set<String> STAFF = Set.of("ADMINISTRATOR", "DENTIST", "SECRETARY_ASSISTANT");
    private final JwtDecoder decoder; private final String issuer, audience, permission; private final Clock clock;
    private final Supplier<String> token; private final Predicate<String> session;
    public JwtPatientSearchAuthorization(JwtDecoder decoder, String issuer, String audience, String permission,
            Clock clock, Supplier<String> token, Predicate<String> session) {
        this.decoder = decoder; this.issuer = issuer; this.audience = audience; this.permission = permission;
        this.clock = clock; this.token = token; this.session = session;
    }
    @Override public Access requireAuthorizedAccess() {
        try {
            String raw = token.get(); if (raw == null || raw.isBlank()) throw fail(AuthorizationFailure.Reason.UNAUTHORIZED);
            Jwt jwt = decoder.decode(raw); UUID actor = uuid(jwt.getSubject()); uuid(jwt.getClaimAsString("sid"));
            var roles = strings(jwt.getClaim("roles")); var permissions = strings(jwt.getClaim("permissions"));
            if (!"RS256".equals(jwt.getHeaders().get("alg")) || !issuer.equals(jwt.getClaimAsString("iss"))
                    || jwt.getAudience() == null || !jwt.getAudience().contains(audience) || jwt.getId() == null
                    || jwt.getIssuedAt() == null || jwt.getExpiresAt() == null || !jwt.getExpiresAt().isAfter(jwt.getIssuedAt())
                    || Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()).getSeconds() > 3600
                    || jwt.getIssuedAt().isAfter(clock.instant().plusSeconds(30)) || !STAFF.containsAll(roles)) throw fail(AuthorizationFailure.Reason.UNAUTHORIZED);
            try { if (!session.test(raw)) throw fail(AuthorizationFailure.Reason.UNAUTHORIZED); }
            catch (AuthorizationFailure failure) { throw failure; }
            catch (RuntimeException failure) { throw fail(AuthorizationFailure.Reason.SERVICE_UNAVAILABLE); }
            if (!permissions.contains(permission)) throw fail(AuthorizationFailure.Reason.FORBIDDEN);
            if (roles.contains("DENTIST")) return new Access(actor, View.CARE);
            if (roles.contains("ADMINISTRATOR") || roles.contains("SECRETARY_ASSISTANT")) return new Access(actor, View.ADMINISTRATIVE);
            throw fail(AuthorizationFailure.Reason.FORBIDDEN);
        } catch (AuthorizationFailure failure) { throw failure; }
        catch (BadJwtException failure) { throw fail(AuthorizationFailure.Reason.UNAUTHORIZED); }
        catch (IllegalArgumentException failure) { throw fail(AuthorizationFailure.Reason.UNAUTHORIZED); }
        catch (JwtException failure) { throw fail(AuthorizationFailure.Reason.SERVICE_UNAVAILABLE); }
    }
    private static AuthorizationFailure fail(AuthorizationFailure.Reason reason) { return new AuthorizationFailure(reason); }
    private static UUID uuid(String value) { UUID id = UUID.fromString(value == null ? "" : value); if (!id.toString().equalsIgnoreCase(value)) throw new IllegalArgumentException(); return id; }
    private static List<String> strings(Object value) { if (!(value instanceof List<?> list) || list.stream().anyMatch(item -> !(item instanceof String s) || s.isBlank())) throw new IllegalArgumentException(); return list.stream().map(String.class::cast).toList(); }
}
