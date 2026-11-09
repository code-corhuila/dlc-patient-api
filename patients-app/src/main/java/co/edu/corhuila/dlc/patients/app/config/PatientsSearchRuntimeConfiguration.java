package co.edu.corhuila.dlc.patients.app.config;

import co.edu.corhuila.dlc.patients.adapter.out.appointments.AppointmentsAssignmentsClient;
import co.edu.corhuila.dlc.patients.adapter.out.iam.JwtPatientSearchAuthorization;
import co.edu.corhuila.dlc.patients.adapter.out.iam.IamSessionClient;
import co.edu.corhuila.dlc.patients.application.port.in.ReadPatientUseCase;
import co.edu.corhuila.dlc.patients.application.port.in.SearchPatientsUseCase;
import co.edu.corhuila.dlc.patients.application.port.out.CurrentPatientAssignments;
import co.edu.corhuila.dlc.patients.application.port.out.PatientReadReader;
import co.edu.corhuila.dlc.patients.application.port.out.PatientSearchAuthorization;
import co.edu.corhuila.dlc.patients.application.port.out.PatientSearchReader;
import co.edu.corhuila.dlc.patients.application.usecase.SearchPatientsService;
import co.edu.corhuila.dlc.patients.application.usecase.ReadPatientService;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({PatientsServiceClientSettings.class, PatientsAppointmentsSettings.class, PatientsIamSettings.class})
@ConditionalOnProperty(prefix = "patients.search", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnBean({PatientSearchReader.class, JwtDecoder.class})
public class PatientsSearchRuntimeConfiguration {
    @Bean(destroyMethod = "close")
    HttpClient patientsAppointmentsHttpClient(PatientsAppointmentsSettings settings, PatientsIamSettings iam) {
        appointmentsBase(settings, iam.environment());
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @Bean
    PatientSearchAuthorization patientsSearchAuthorization(JwtDecoder decoder, PatientsIamSettings settings,
            IamSessionClient session) {
        PatientsIamSettings.requireApprovedPermission(settings.searchPermission(), "patients:read");
        return new JwtPatientSearchAuthorization(decoder, settings.issuer(), settings.audience(), settings.searchPermission(),
                Clock.systemUTC(), PatientsAuthorizationConfiguration::requestToken, session);
    }

    @Bean
    CurrentPatientAssignments patientsCurrentAssignments(PatientsAppointmentsSettings settings, PatientsIamSettings iam,
            PatientsServiceClientSettings client,
            @org.springframework.beans.factory.annotation.Qualifier("patientsAppointmentsHttpClient") HttpClient patientsAppointmentsHttpClient) {
        URI base = appointmentsBase(settings, iam.environment());
        Supplier<String> token = client.issuesTokens()
                ? PatientsAuthorizationConfiguration.issuedToken(iam, client, "appointments-api", "assignments:read")
                : fileToken(settings.serviceTokenFile());
        return new AppointmentsAssignmentsClient(base, token, patientsAppointmentsHttpClient);
    }

    @Bean
    SearchPatientsUseCase patientsSearchUseCase(PatientSearchAuthorization authorization,
            CurrentPatientAssignments assignments, PatientSearchReader reader) {
        return new SearchPatientsService(authorization, assignments, reader);
    }

    @Bean
    ReadPatientUseCase patientsReadUseCase(PatientSearchAuthorization authorization,
            CurrentPatientAssignments assignments, PatientReadReader reader) {
        return new ReadPatientService(authorization, assignments, reader);
    }

    private static URI appointmentsBase(PatientsAppointmentsSettings settings, String environment) {
        try {
            URI uri = URI.create(settings.baseUrl());
            if (uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || !(uri.getRawPath().isEmpty() || "/".equals(uri.getRawPath()))
                    || !("https".equals(uri.getScheme()) || "dev".equals(environment) && "http".equals(uri.getScheme()))) throw new IllegalArgumentException();
            return uri;
        } catch (RuntimeException failure) { throw new IllegalStateException("Set a valid Appointments service URL"); }
    }
    private static Supplier<String> fileToken(String filename) {
        return () -> { try { String value = Files.readString(Path.of(filename)).replaceFirst("\\r?\\n\\z", ""); if (value.isBlank() || value.chars().anyMatch(Character::isWhitespace)) throw new IllegalArgumentException(); return value; } catch (Exception failure) { throw new IllegalStateException("Appointments credential unavailable"); } };
    }
}
