package co.edu.corhuila.dlc.patients.app.config;

import co.edu.corhuila.dlc.patients.adapter.out.iam.JwtRegistrationAuthorization;
import co.edu.corhuila.dlc.patients.adapter.out.iam.JwtServiceAuthorization;
import co.edu.corhuila.dlc.patients.application.port.out.PatientServiceAuthorization;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.util.Base64;
import org.apache.catalina.connector.Connector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class PatientsInternalConfiguration {
    @Bean
    InternalListenerFilter patientsInternalListenerFilter(@Value("${patients.internal.enabled:false}") boolean enabled,
            @Value("${patients.internal.port:9091}") int port) {
        return new InternalListenerFilter(enabled ? port : 0);
    }

    @Bean
    @ConditionalOnProperty(prefix = "patients.internal", name = "enabled", havingValue = "true")
    WebServerFactoryCustomizer<TomcatServletWebServerFactory> privateConnector(
            @Value("${patients.internal.port:9091}") int port, @Value("${server.port:8080}") int publicPort) {
        if (port < 1 || port > 65535 || port == publicPort) throw new IllegalStateException("Invalid internal port");
        return factory -> {
            var connector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
            connector.setPort(port);
            if (!connector.setProperty("address", "127.0.0.1")) throw new IllegalStateException("Cannot bind internal listener to loopback");
            factory.addAdditionalTomcatConnectors(connector);
        };
    }

    @Bean
    @ConditionalOnProperty(prefix = "patients.internal", name = "enabled", havingValue = "true")
    PatientServiceAuthorization patientsServiceAuthorization(@Value("${patients.internal.public-key-file:}") String path,
            @Value("${patients.internal.key-id:}") String kid, @Value("${patients.internal.issuer:}") String issuer,
            @Value("${patients.internal.audience:}") String audience) {
        try {
            String pem = Files.readString(Path.of(path)).replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "").replaceAll("\\s", "");
            var key = (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(pem)));
            if (key.getModulus().bitLength() < 2048) throw new IllegalArgumentException();
            var clock = Clock.systemUTC();
            return new JwtServiceAuthorization(JwtRegistrationAuthorization.rsaDecoder(key, kid, clock),
                    PatientsAuthorizationConfiguration::requestToken, issuer, audience, clock);
        } catch (Exception failure) {
            throw new IllegalStateException("Invalid internal service key or authorization configuration", failure);
        }
    }
}
