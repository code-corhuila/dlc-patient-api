package co.edu.corhuila.dlc.patients.adapter.out.iam;

import co.edu.corhuila.dlc.patients.application.model.AuthorizationFailure;
import co.edu.corhuila.dlc.patients.application.port.out.PatientServiceAuthorization;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

public final class JwtServiceAuthorization implements PatientServiceAuthorization {
    private final JwtDecoder decoder;
    private final Supplier<String> token;
    private final String issuer, audience;
    private final Clock clock;
    public JwtServiceAuthorization(JwtDecoder decoder, Supplier<String> token, String issuer, String audience, Clock clock) {
        this.decoder = Objects.requireNonNull(decoder); this.token = Objects.requireNonNull(token);
        this.clock = Objects.requireNonNull(clock);
        if (issuer == null || issuer.isBlank() || audience == null || audience.isBlank()) throw new IllegalArgumentException();
        this.issuer = issuer; this.audience = audience;
    }
    @Override public void requireValidationScope() { require("patients:validate"); }
    @Override public void requireBillingContactScope() { require("patients:billing-contact:read"); }
    private void require(String required) {
        try {
            String value = token.get();
            if (value == null || value.isBlank()) throw fail(AuthorizationFailure.Reason.UNAUTHORIZED);
            var jwt = decoder.decode(value);
            var issued = jwt.getIssuedAt(); var expiry = jwt.getExpiresAt();
            if (!issuer.equals(jwt.getClaimAsString("iss")) || jwt.getAudience() == null
                    || !jwt.getAudience().contains(audience) || jwt.getSubject() == null || jwt.getSubject().isBlank()
                    || issued == null || expiry == null || !expiry.isAfter(clock.instant())
                    || issued.isAfter(clock.instant().plusSeconds(30)) || !expiry.isAfter(issued)
                    || Duration.between(issued, expiry).compareTo(Duration.ofHours(1)) > 0
                    || jwt.hasClaim("sid") || jwt.hasClaim("roles")) throw fail(AuthorizationFailure.Reason.UNAUTHORIZED);
            Object scope = jwt.getClaim("scope"), scp = jwt.getClaim("scp");
            boolean allowed = scope instanceof String s && Arrays.asList(s.split("\\s+")).contains(required)
                    || scp instanceof java.util.List<?> list && list.contains(required);
            if (!allowed) throw fail(AuthorizationFailure.Reason.FORBIDDEN);
        } catch (BadJwtException | IllegalArgumentException failure) {
            throw fail(AuthorizationFailure.Reason.UNAUTHORIZED);
        } catch (JwtException failure) {
            throw fail(AuthorizationFailure.Reason.SERVICE_UNAVAILABLE);
        }
    }
    private static AuthorizationFailure fail(AuthorizationFailure.Reason reason) { return new AuthorizationFailure(reason); }
}
