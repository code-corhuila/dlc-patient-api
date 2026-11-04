package co.edu.corhuila.dlc.patients.app.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.edu.corhuila.dlc.patients.adapter.out.persistence.JdbcRegistrationTransaction;
import co.edu.corhuila.dlc.patients.application.port.out.RegistrationTransaction;
import co.edu.corhuila.dlc.patients.domain.model.Document;
import co.edu.corhuila.dlc.patients.domain.model.Patient;
import co.edu.corhuila.dlc.patients.domain.model.PatientProfile;
import com.zaxxer.hikari.HikariDataSource;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class PatientsDataSourceConfigurationTest {
    @TempDir
    Path directory;
    private Path passwordFile;

    @BeforeEach
    void externalSecret() throws Exception {
        passwordFile = directory.resolve("password");
        Files.writeString(passwordFile, "synthetic-runtime-secret");
    }

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner().withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(PatientsDataSourceConfiguration.class)
                .withPropertyValues("patients.database.enabled=true", "patients.database.environment=dev",
                        "patients.database.host=localhost", "patients.database.port=5432",
                        "patients.database.name=di_lucca", "patients.database.password-file=" + passwordFile);
    }

    @Test
    void bindsSafePoolDefaultsAndClosesTheManagedResource() {
        var reference = new AtomicReference<HikariDataSource>();
        runner().withPropertyValues("patients.database.username=postgres").run(context -> {
            assertNull(context.getStartupFailure());
            var pool = context.getBean(HikariDataSource.class);
            reference.set(pool);
            assertEquals("jdbc:postgresql://localhost:5432/di_lucca", pool.getJdbcUrl());
            assertEquals("patients_app", pool.getUsername());
            assertEquals(10, pool.getMaximumPoolSize());
            assertEquals(0, pool.getMinimumIdle());
            assertEquals(5000, pool.getConnectionTimeout());
            assertEquals("patients", pool.getSchema());
            assertEquals("verify-full", pool.getDataSourceProperties().getProperty("sslmode"));
            assertEquals("-c statement_timeout=5000", pool.getDataSourceProperties().getProperty("options"));
            assertNull(pool.getHikariPoolMXBean());
            assertInstanceOf(JdbcRegistrationTransaction.class, context.getBean(RegistrationTransaction.class));
        });
        assertTrue(reference.get().isClosed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"host=", "port=0", "port=65536", "name=di_lucca?user=postgres",
            "host=localhost/database?user=postgres", "host=postgres@localhost", "host=localhost#fragment",
            "environment=unknown", "ssl-mode=prefer", "password-file="})
    void refusesInvalidOrCredentialBearingEndpointConfiguration(String override) {
        runner().withPropertyValues("patients.database." + override).run(context ->
                assertNotNull(context.getStartupFailure()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"qa", "main", "prod"})
    void refusesUnverifiedTransportOutsideDevelopment(String environment) {
        runner().withPropertyValues("patients.database.environment=" + environment,
                "patients.database.ssl-mode=disable").run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Test
    void supportsExplicitLivenessOnlyModeWithoutReadingASecret() {
        runner().withPropertyValues("patients.database.enabled=false", "patients.database.password-file=")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertFalse(context.containsBean("patientsDataSource"));
                    assertTrue(context.getBeansOfType(RegistrationTransaction.class).isEmpty());
                });
    }

    @Test
    void rejectsMissingEmptyOrMultilineSecretWithoutEchoingItsContents() throws Exception {
        for (var secret : List.of("", "synthetic-private-value\nsecond")) {
            Files.writeString(passwordFile, secret);
            runner().run(context -> {
                assertNotNull(context.getStartupFailure());
                assertFalse(context.getStartupFailure().toString().contains("synthetic-private-value"));
            });
        }
        Files.delete(passwordFile);
        runner().run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Test
    void stripsOnlyTheFinalLineEndingAndPreservesPasswordSpaces() throws Exception {
        Files.writeString(passwordFile, " synthetic secret \r\n");
        runner().run(context -> assertEquals(" synthetic secret ", context.getBean(HikariDataSource.class).getPassword()));
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
    void pooledRuntimeConnectionIsReusedAndTheWiredAdapterCommits() throws Exception {
        var endpoint = URI.create(System.getenv("TEST_DATABASE_URL").substring(5));
        assertTrue(endpoint.getPath().matches("/patients_api_test_[a-f0-9]+"));
        assertEquals("patients_app", System.getenv("TEST_DATABASE_USER"));
        Files.writeString(passwordFile, System.getenv("TEST_DATABASE_PASSWORD"));
        var reference = new AtomicReference<HikariDataSource>();
        runner().withPropertyValues("patients.database.host=" + endpoint.getHost(),
                "patients.database.port=" + endpoint.getPort(), "patients.database.name=" + endpoint.getPath().substring(1),
                "patients.database.ssl-mode=disable").run(context -> {
            assertNull(context.getStartupFailure());
            var pool = context.getBean(HikariDataSource.class);
            reference.set(pool);
            int backend;
            try (var connection = pool.getConnection(); var statement = connection.createStatement();
                    var rows = statement.executeQuery("""
                            SELECT current_user,current_schema(),current_setting('statement_timeout'),pg_backend_pid()
                            """)) {
                assertTrue(rows.next());
                assertEquals("patients_app", rows.getString(1));
                assertEquals("patients", rows.getString(2));
                assertEquals("5s", rows.getString(3));
                backend = rows.getInt(4);
            }
            try (var connection = pool.getConnection(); var statement = connection.createStatement();
                    var rows = statement.executeQuery("SELECT pg_backend_pid()")) {
                assertTrue(rows.next());
                assertEquals(backend, rows.getInt(1));
            }
            var profile = new PatientProfile("Synthetic Pool", LocalDate.of(2000, 1, 1),
                    "3000000000", "synthetic@example.invalid", null, List.of());
            var patient = Patient.register(UUID.randomUUID(), new Document("CC", UUID.randomUUID().toString()),
                    profile, Clock.systemUTC());
            var write = new RegistrationTransaction.Write(patient, UUID.randomUUID(), UUID.randomUUID().toString(),
                    "a".repeat(64), UUID.randomUUID(), UUID.randomUUID(), Instant.now(), UUID.randomUUID(), UUID.randomUUID());
            assertFalse(context.getBean(RegistrationTransaction.class).register(write).replayed());
        });
        assertTrue(reference.get().isClosed());
        assertThrows(SQLException.class, () -> reference.get().getConnection());
    }
}
