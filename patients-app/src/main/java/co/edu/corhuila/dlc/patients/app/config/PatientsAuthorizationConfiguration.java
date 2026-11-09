package co.edu.corhuila.dlc.patients.app.config;

import co.edu.corhuila.dlc.patients.adapter.out.iam.IamServiceTokenSupplier;
import co.edu.corhuila.dlc.patients.adapter.out.iam.IamSessionClient;
import co.edu.corhuila.dlc.patients.adapter.out.iam.JwtRegistrationAuthorization;
import co.edu.corhuila.dlc.patients.application.model.AuthorizationFailure;
import co.edu.corhuila.dlc.patients.application.port.in.RegisterPatientUseCase;
import co.edu.corhuila.dlc.patients.application.port.out.RegistrationAuthorization;
import co.edu.corhuila.dlc.patients.application.port.out.RegistrationTransaction;
import co.edu.corhuila.dlc.patients.application.usecase.RegisterPatientService;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({PatientsIamSettings.class, PatientsServiceClientSettings.class})
@ConditionalOnProperty(prefix = "patients.iam", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PatientsAuthorizationConfiguration {
    @Bean(destroyMethod = "close")
    HttpClient patientsIamHttpClient(PatientsIamSettings settings, PatientsServiceClientSettings client) {
        origin(settings);
        PatientsIamSettings.requireApprovedPermission(settings.registrationPermission(), "patients:register");
        new JwtRegistrationAuthorization.Policy(settings.issuer(), settings.audience(), settings.registrationPermission());
        if (!client.issuesTokens()) try { serviceToken(settings.serviceTokenFile()); }
        catch (AuthorizationFailure failure) { throw new IllegalStateException("Set a readable IAM service token file"); }
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @Bean
    JwtDecoder patientsStaffJwtDecoder(PatientsIamSettings settings, @org.springframework.beans.factory.annotation.Qualifier("patientsIamHttpClient") HttpClient patientsIamHttpClient) {
        URI origin = origin(settings);
        var transport = new JdkClientHttpRequestFactory(patientsIamHttpClient);
        transport.setReadTimeout(Duration.ofSeconds(5));
        var keys = new RestTemplate(transport);
        keys.getInterceptors().add((request, body, execution) -> {
            var response = execution.execute(request, body);
            if (response.getStatusCode().value() != 200) {
                response.close();
                throw new IOException("IAM key retrieval unavailable");
            }
            return response;
        });
        var decoder = NimbusJwtDecoder.withJwkSetUri(origin.resolve("/api/v1/auth/jwks").toString())
                .jwsAlgorithm(SignatureAlgorithm.RS256).restOperations(keys).build();
        Clock clock = Clock.systemUTC();
        var timestamps = new JwtTimestampValidator(Duration.ofSeconds(30));
        timestamps.setClock(clock);
        decoder.setJwtValidator(timestamps);
        return decoder;
    }

    @Bean
    IamSessionClient patientsIamSessionClient(PatientsIamSettings settings, PatientsServiceClientSettings client,
            @org.springframework.beans.factory.annotation.Qualifier("patientsIamHttpClient") HttpClient patientsIamHttpClient) {
        URI origin = origin(settings);
        Supplier<String> token = () -> serviceToken(settings.serviceTokenFile());
        if (client.issuesTokens()) {
            var issued = issuedToken(settings, client, "auth-service", "identity:validate");
            token = () -> {
                try { return issued.get(); }
                catch (IllegalStateException failure) { throw new AuthorizationFailure(AuthorizationFailure.Reason.SERVICE_UNAVAILABLE); }
            };
        }
        return new IamSessionClient(origin.resolve("/internal/v1/sessions/validations"),
                token, patientsIamHttpClient, "dev".equals(settings.environment()));
    }

    /** ADR-020: a token issued by IAM (the configured IAM origin) for one audience, by client credentials. */
    static Supplier<String> issuedToken(PatientsIamSettings settings, PatientsServiceClientSettings client,
            String audience, String scope) {
        return new IamServiceTokenSupplier(origin(settings), client.id(), Path.of(client.secretFile()), audience, scope,
                Duration.ofSeconds(5), Clock.systemUTC());
    }

    @Bean
    RegistrationAuthorization patientsRegistrationAuthorization(PatientsIamSettings settings, JwtDecoder patientsStaffJwtDecoder,
            IamSessionClient sessions) {
        Clock clock = Clock.systemUTC();
        return new JwtRegistrationAuthorization(patientsStaffJwtDecoder, new JwtRegistrationAuthorization.Policy(
                settings.issuer(), settings.audience(), settings.registrationPermission()), clock,
                PatientsAuthorizationConfiguration::requestToken, sessions);
    }

    @Bean
    RegisterPatientUseCase patientsRegisterPatientUseCase(RegistrationAuthorization authorization, RegistrationTransaction transaction) {
        return new RegisterPatientService(authorization, transaction, Clock.systemUTC(), UUID::randomUUID);
    }

    private static URI origin(PatientsIamSettings settings) {
        try {
            URI uri = URI.create(settings.baseUrl());
            if (!Set.of("dev", "qa", "main", "prod").contains(settings.environment()) || uri.getHost() == null
                    || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || !(uri.getRawPath().isEmpty() || "/".equals(uri.getRawPath()))
                    || uri.getPort() == 0 || uri.getPort() > 65535
                    || !("https".equals(uri.getScheme()) || "dev".equals(settings.environment()) && "http".equals(uri.getScheme()))) {
                throw new IllegalArgumentException();
            }
            return uri;
        } catch (RuntimeException failure) {
            throw new IllegalStateException("Set a valid IAM origin and environment; HTTPS is required outside dev");
        }
    }

    private static String serviceToken(String filename) {
        try {
            String token = Files.readString(Path.of(filename)).replaceFirst("\\r?\\n\\z", "");
            if (token.isBlank() || token.chars().anyMatch(Character::isWhitespace)) throw new IllegalArgumentException();
            return token;
        } catch (Exception failure) {
            throw new AuthorizationFailure(AuthorizationFailure.Reason.SERVICE_UNAVAILABLE);
        }
    }

    static String requestToken() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) return null;
        var headers = Collections.list(attributes.getRequest().getHeaders("Authorization"));
        if (headers.size() != 1 || !headers.getFirst().matches("(?i)Bearer [A-Za-z0-9_.-]+")) return null;
        return headers.getFirst().substring(7);
    }
}
