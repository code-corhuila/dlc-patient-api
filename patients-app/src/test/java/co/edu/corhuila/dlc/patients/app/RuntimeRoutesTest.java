package co.edu.corhuila.dlc.patients.app;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.bind.annotation.RequestMethod;

class RuntimeRoutesTest {
    @TempDir Path directory;
    @Test void realCompositionExposesAllEnabledPublicPatientRoutes() throws Exception {
        Path secret = directory.resolve("synthetic-secret"); Files.writeString(secret, "synthetic-test-only");
        new WebApplicationContextRunner().withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(PatientsApplication.class).withPropertyValues(
                    "patients.database.host=localhost", "patients.database.name=synthetic",
                    "patients.database.password-file=" + secret,
                    "patients.iam.base-url=http://iam.test", "patients.iam.issuer=test-issuer",
                    "patients.iam.audience=test-audience", "patients.iam.service-token-file=" + secret,
                    "patients.appointments.base-url=http://appointments.test", "patients.update.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var routes = context.getBean(RequestMappingHandlerMapping.class).getHandlerMethods().keySet();
                    for (var method : new RequestMethod[]{RequestMethod.GET, RequestMethod.POST})
                        assertThat(routes).anyMatch(route -> route.getMethodsCondition().getMethods().contains(method)
                                && route.getPatternValues().contains("/api/v1/patients"));
                    for (var method : new RequestMethod[]{RequestMethod.GET, RequestMethod.PATCH})
                        assertThat(routes).anyMatch(route -> route.getMethodsCondition().getMethods().contains(method)
                                && route.getPatternValues().contains("/api/v1/patients/{id}"));
                });
    }
}
