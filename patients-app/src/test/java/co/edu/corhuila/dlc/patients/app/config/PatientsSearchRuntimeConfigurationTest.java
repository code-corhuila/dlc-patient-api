package co.edu.corhuila.dlc.patients.app.config;

import static org.assertj.core.api.Assertions.assertThat;

import co.edu.corhuila.dlc.patients.adapter.out.iam.IamSessionClient;
import co.edu.corhuila.dlc.patients.application.port.in.SearchPatientsUseCase;
import co.edu.corhuila.dlc.patients.application.port.out.PatientSearchAuthorization;
import co.edu.corhuila.dlc.patients.application.port.out.PatientSearchReader;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.jwt.JwtDecoder;

class PatientsSearchRuntimeConfigurationTest {
    private HttpClient client;
    private IamSessionClient sessions;
    private ApplicationContextRunner runner;

    @BeforeEach
    void setup() {
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
        sessions = new IamSessionClient(URI.create("http://127.0.0.1:1/internal/v1/sessions/validations"), () -> "token", client, true);
        runner = new ApplicationContextRunner().withUserConfiguration(PatientsSearchRuntimeConfiguration.class)
                .withBean(JwtDecoder.class, () -> value -> null).withBean(IamSessionClient.class, () -> sessions)
                .withBean(PatientSearchReader.class, () -> (criteria, scope) -> new PatientSearchReader.Result(java.util.List.of(), 0))
                .withPropertyValues("patients.iam.base-url=http://iam.test", "patients.iam.environment=dev",
                        "patients.iam.issuer=issuer", "patients.iam.audience=audience", "patients.iam.search-permission=patients:read",
                        "patients.appointments.base-url=http://appointments.test", "patients.appointments.service-token-file=C:/fixture-token");
    }

    @AfterEach
    void close() { client.close(); }

    @Test
    void composesSearchUseCaseAuthorizationAndAssignmentsAdapter() {
        runner.run(context -> assertThat(context).hasNotFailed().hasSingleBean(SearchPatientsUseCase.class)
                .hasSingleBean(PatientSearchAuthorization.class).hasSingleBean(co.edu.corhuila.dlc.patients.application.port.out.CurrentPatientAssignments.class));
    }

    @Test
    void missingSearchPermissionFailsClosedAtStartup() {
        runner.withPropertyValues("patients.iam.search-permission=").run(context -> assertThat(context).hasFailed());
    }

    @Test
    void productionAppointmentsRequiresHttps() {
        runner.withPropertyValues("patients.iam.environment=prod", "patients.appointments.base-url=http://appointments.test")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void disabledSearchDoesNotCreateSearchBeans() {
        runner.withPropertyValues("patients.search.enabled=false").run(context -> assertThat(context)
                .doesNotHaveBean(SearchPatientsUseCase.class).doesNotHaveBean(PatientSearchAuthorization.class));
    }
}
