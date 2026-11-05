package co.edu.corhuila.dlc.patients.app.config;

import co.edu.corhuila.dlc.patients.adapter.out.iam.IamSessionClient;
import co.edu.corhuila.dlc.patients.adapter.out.iam.JwtRegistrationAuthorization;
import co.edu.corhuila.dlc.patients.application.port.in.UpdatePatientUseCase;
import co.edu.corhuila.dlc.patients.application.port.out.PatientUpdateAuthorization;
import co.edu.corhuila.dlc.patients.application.port.out.PatientUpdateTransaction;
import co.edu.corhuila.dlc.patients.application.usecase.UpdatePatientService;
import java.time.Clock;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PatientsIamSettings.class)
@ConditionalOnProperty(prefix = "patients.update", name = "enabled", havingValue = "true")
public class PatientsUpdateRuntimeConfiguration {
    @Bean
    PatientUpdateAuthorization patientsUpdateAuthorization(JwtDecoder decoder, PatientsIamSettings settings, IamSessionClient session) {
        var policy = new JwtRegistrationAuthorization.Policy(settings.issuer(), settings.audience(), settings.updatePermission());
        var verifier = new JwtRegistrationAuthorization(decoder, policy, Clock.systemUTC(),
                PatientsAuthorizationConfiguration::requestToken, session);
        return verifier::requireAuthorizedActor;
    }
    @Bean
    UpdatePatientUseCase patientsUpdateUseCase(PatientUpdateAuthorization authorization, PatientUpdateTransaction transaction) {
        return new UpdatePatientService(authorization, transaction, Clock.systemUTC(), UUID::randomUUID);
    }
}
