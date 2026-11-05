package co.edu.corhuila.dlc.patients.adapter.out.iam;

import static org.junit.jupiter.api.Assertions.*;
import co.edu.corhuila.dlc.patients.application.model.AuthorizationFailure;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.Test;

class JwtServiceAuthorizationTest {
    private final Clock clock = Clock.systemUTC();
    private String token(RSAPrivateKey key, String audience, String scope, boolean staff) throws Exception {
        var now = Instant.now();
        var builder = new JWTClaimsSet.Builder().issuer("service-issuer").subject("appointments")
                .audience(audience).issueTime(Date.from(now.minusSeconds(5)))
                .expirationTime(Date.from(now.plusSeconds(120))).claim("scope", scope);
        if (staff) builder.claim("sid", "staff-session");
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("service-key").build(), builder.build());
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }
    @Test void validatesSignatureAudienceScopeAndServiceIdentity() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        var keys = generator.generateKeyPair(); var other = generator.generateKeyPair();
        var decoder = JwtRegistrationAuthorization.rsaDecoder((RSAPublicKey) keys.getPublic(), "service-key", clock);
        var valid = token((RSAPrivateKey) keys.getPrivate(), "patients", "patients:validate", false);
        assertDoesNotThrow(() -> new JwtServiceAuthorization(decoder, () -> valid, "service-issuer", "patients", clock).requireValidationScope());
        for (var invalid : new String[]{token((RSAPrivateKey) other.getPrivate(), "patients", "patients:validate", false),
                token((RSAPrivateKey) keys.getPrivate(), "wrong", "patients:validate", false),
                token((RSAPrivateKey) keys.getPrivate(), "patients", "wrong", false),
                token((RSAPrivateKey) keys.getPrivate(), "patients", "patients:validate", true)}) {
            assertThrows(AuthorizationFailure.class, () -> new JwtServiceAuthorization(decoder, () -> invalid,
                    "service-issuer", "patients", clock).requireValidationScope());
        }
    }
}
