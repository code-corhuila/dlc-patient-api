package co.edu.corhuila.dlc.patients.app.config;

import static org.assertj.core.api.Assertions.assertThat;
import co.edu.corhuila.dlc.patients.adapter.out.iam.IamSessionClient;
import co.edu.corhuila.dlc.patients.application.port.in.UpdatePatientUseCase;
import co.edu.corhuila.dlc.patients.application.port.out.PatientUpdateAuthorization;
import co.edu.corhuila.dlc.patients.application.port.out.PatientUpdateTransaction;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import co.edu.corhuila.dlc.patients.application.model.AuthorizationFailure;
import co.edu.corhuila.dlc.patients.adapter.in.http.PatientUpdateController;
import com.fasterxml.jackson.databind.ObjectMapper;

class PatientsUpdateRuntimeConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PatientsUpdateRuntimeConfiguration.class)
            .withBean(JwtDecoder.class, () -> token -> null)
            .withBean(IamSessionClient.class, () -> mock(IamSessionClient.class))
            .withBean(PatientUpdateTransaction.class, () -> write -> null)
            .withPropertyValues("patients.update.enabled=true", "patients.iam.issuer=issuer",
                    "patients.iam.audience=audience", "patients.iam.update-permission=patients:write");

    @Test void createsUpdateBeansWhenConfigured() {
        runner.run(context -> assertThat(context).hasNotFailed().hasSingleBean(UpdatePatientUseCase.class)
                .hasSingleBean(PatientUpdateAuthorization.class));
    }
    @Test void enabledRuntimeCanConstructHttpController() {
        runner.withBean(ObjectMapper.class, ObjectMapper::new)
                .withUserConfiguration(PatientUpdateController.class)
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(PatientUpdateController.class));
    }
    @Test void missingPermissionFailsClosed() {
        runner.withPropertyValues("patients.iam.update-permission=").run(context -> assertThat(context).hasFailed());
    }
    @Test void disabledUpdateDoesNotCreateBeans() {
        runner.withPropertyValues("patients.update.enabled=false").run(context -> assertThat(context)
                .doesNotHaveBean(UpdatePatientUseCase.class).doesNotHaveBean(PatientUpdateAuthorization.class));
    }
    @Test void authorizationRequiresUpdatePermissionAndAdministrativeRole() {
        var session = mock(IamSessionClient.class); when(session.test("fixture")).thenReturn(true);
        var actor = UUID.randomUUID(); var now = Instant.now();
        var roles = new java.util.concurrent.atomic.AtomicReference<>(List.of("ADMINISTRATOR"));
        var permissions = new java.util.concurrent.atomic.AtomicReference<>(List.of("patients:write"));
        JwtDecoder decoder = token -> Jwt.withTokenValue(token).header("alg", "RS256").header("kid", "key")
                .subject(actor.toString()).claim("sid", UUID.randomUUID().toString()).claim("jti", "id")
                .claim("iss", "issuer").audience(List.of("audience")).issuedAt(now).expiresAt(now.plusSeconds(300))
                .claim("roles", roles.get()).claim("permissions", permissions.get()).build();
        var settings = new PatientsIamSettings(null, null, "issuer", "audience", null, null, "patients:write", null);
        var authorization = new PatientsUpdateRuntimeConfiguration().patientsUpdateAuthorization(decoder, settings, session);
        var request = new MockHttpServletRequest(); request.addHeader("Authorization", "Bearer fixture");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            assertThat(authorization.requireAuthorizedActor()).isEqualTo(actor);
            roles.set(List.of("DENTIST"));
            org.junit.jupiter.api.Assertions.assertEquals(AuthorizationFailure.Reason.FORBIDDEN,
                    org.junit.jupiter.api.Assertions.assertThrows(AuthorizationFailure.class, authorization::requireAuthorizedActor).reason());
            roles.set(List.of("SECRETARY_ASSISTANT")); permissions.set(List.of("patients:read"));
            org.junit.jupiter.api.Assertions.assertEquals(AuthorizationFailure.Reason.FORBIDDEN,
                    org.junit.jupiter.api.Assertions.assertThrows(AuthorizationFailure.class, authorization::requireAuthorizedActor).reason());
        } finally { RequestContextHolder.resetRequestAttributes(); }
    }
}
