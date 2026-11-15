package co.edu.corhuila.dlc.patients.app.config;

import co.edu.corhuila.dlc.patients.adapter.out.persistence.JdbcRegistrationTransaction;
import co.edu.corhuila.dlc.patients.adapter.out.persistence.JdbcPatientReadReader;
import co.edu.corhuila.dlc.patients.adapter.out.persistence.JdbcPatientValidationReader;
import co.edu.corhuila.dlc.patients.application.port.out.PatientValidationReader;
import co.edu.corhuila.dlc.patients.application.port.in.ReadBillingContactUseCase;
import co.edu.corhuila.dlc.patients.application.port.in.ValidatePatientUseCase;
import co.edu.corhuila.dlc.patients.application.port.out.BillingContactReader;
import co.edu.corhuila.dlc.patients.application.usecase.BillingContactService;
import co.edu.corhuila.dlc.patients.adapter.out.persistence.JdbcBillingContactReader;
import co.edu.corhuila.dlc.patients.application.usecase.PatientValidationService;
import co.edu.corhuila.dlc.patients.adapter.out.persistence.JdbcDeactivationIntentStore;
import co.edu.corhuila.dlc.patients.application.port.out.DeactivationIntentStore;
import co.edu.corhuila.dlc.patients.adapter.out.persistence.JdbcPatientUpdateTransaction;
import co.edu.corhuila.dlc.patients.application.port.out.PatientUpdateTransaction;
import co.edu.corhuila.dlc.patients.adapter.out.persistence.JdbcPatientSearchReader;
import co.edu.corhuila.dlc.patients.application.port.out.PatientReadReader;
import co.edu.corhuila.dlc.patients.application.port.out.PatientSearchReader;
import co.edu.corhuila.dlc.patients.application.port.out.RegistrationTransaction;
import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Set;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PatientsDatabaseSettings.class)
@ConditionalOnProperty(prefix = "patients.database", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PatientsDataSourceConfiguration {
    @Bean(destroyMethod = "close")
    HikariDataSource patientsDataSource(PatientsDatabaseSettings settings) {
        String endpoint = endpoint(settings);
        if (settings.environment() == null || !Set.of("dev", "qa", "main", "prod").contains(settings.environment())
                || !("verify-full".equals(settings.sslMode()) || "disable".equals(settings.sslMode()))
                || (!"dev".equals(settings.environment()) && !"verify-full".equals(settings.sslMode()))) {
            throw new IllegalStateException("Invalid Patients database environment or TLS policy");
        }
        String password = password(settings.passwordFile());
        // The pool starts on the first connection request; /health remains liveness only.
        var pool = new HikariDataSource();
        pool.setPoolName("patients-runtime");
        pool.setJdbcUrl(endpoint);
        pool.setUsername("patients_app");
        pool.setPassword(password);
        pool.setSchema("patients");
        pool.setMaximumPoolSize(10);
        pool.setMinimumIdle(0);
        pool.setConnectionTimeout(5000);
        pool.setValidationTimeout(2500);
        pool.setInitializationFailTimeout(-1);
        pool.addDataSourceProperty("connectTimeout", "5");
        pool.addDataSourceProperty("socketTimeout", "10");
        pool.addDataSourceProperty("ApplicationName", "dlc-patient-api");
        pool.addDataSourceProperty("options", "-c statement_timeout=5000");
        pool.addDataSourceProperty("sslmode", settings.sslMode());
        if (!blank(settings.sslRootCert())) {
            pool.addDataSourceProperty("sslrootcert", settings.sslRootCert());
        }
        return pool;
    }

    @Bean
    RegistrationTransaction patientsRegistrationTransaction(DataSource source) {
        return new JdbcRegistrationTransaction(source);
    }

    @Bean
    PatientSearchReader patientsSearchReader(DataSource source) {
        return new JdbcPatientSearchReader(source);
    }

    @Bean
    PatientReadReader patientsReadReader(DataSource source) {
        return new JdbcPatientReadReader(source);
    }

    @Bean
    PatientValidationReader patientsValidationReader(DataSource source) {
        return new JdbcPatientValidationReader(source);
    }

    @Bean
    ValidatePatientUseCase patientsValidatePatientUseCase(PatientValidationReader reader) {
        return new PatientValidationService(reader);
    }

    @Bean
    BillingContactReader patientsBillingContactReader(DataSource source) {
        return new JdbcBillingContactReader(source);
    }

    @Bean
    ReadBillingContactUseCase patientsReadBillingContactUseCase(BillingContactReader reader) {
        return new BillingContactService(reader);
    }

    @Bean
    DeactivationIntentStore patientsDeactivationIntentStore(DataSource source) {
        return new JdbcDeactivationIntentStore(source);
    }

    @Bean
    PatientUpdateTransaction patientsUpdateTransaction(DataSource source) {
        return new JdbcPatientUpdateTransaction(source);
    }

    private static String endpoint(PatientsDatabaseSettings settings) {
        if (blank(settings.host()) || settings.port() < 1 || settings.port() > 65535
                || blank(settings.name()) || !settings.name().matches("[A-Za-z0-9_-]+")) {
            throw new IllegalStateException("Set a valid Patients database host, port and name");
        }
        try {
            var uri = new URI("postgresql", null, settings.host(), settings.port(), "/" + settings.name(), null, null);
            if (uri.getHost() == null || uri.getPort() != settings.port() || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null
                    || !("/" + settings.name()).equals(uri.getPath())) {
                throw new IllegalStateException("Invalid Patients database host");
            }
            return "jdbc:" + uri.toASCIIString();
        } catch (URISyntaxException exception) {
            throw new IllegalStateException("Invalid Patients database endpoint");
        }
    }

    private static String password(String filename) {
        if (blank(filename)) {
            throw new IllegalStateException("Set the Patients database password file");
        }
        try {
            String value = Files.readString(Path.of(filename)).replaceFirst("\\r?\\n\\z", "");
            if (value.isEmpty() || value.contains("\n") || value.contains("\r")) {
                throw new IllegalStateException("Patients database password file must contain one nonempty line");
            }
            return value;
        } catch (IOException | InvalidPathException exception) {
            throw new IllegalStateException("Patients database password file is unreadable");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
