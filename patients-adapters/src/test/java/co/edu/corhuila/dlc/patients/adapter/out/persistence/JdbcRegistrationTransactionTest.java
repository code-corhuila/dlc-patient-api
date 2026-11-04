package co.edu.corhuila.dlc.patients.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.edu.corhuila.dlc.patients.application.model.RegistrationConflictException;
import co.edu.corhuila.dlc.patients.application.model.RegistrationResult;
import co.edu.corhuila.dlc.patients.application.port.out.RegistrationTransaction.Write;
import co.edu.corhuila.dlc.patients.application.usecase.RegisterPatientService;
import co.edu.corhuila.dlc.patients.adapter.in.http.PatientRegistrationController;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import co.edu.corhuila.dlc.patients.domain.model.Document;
import co.edu.corhuila.dlc.patients.domain.model.EmergencyContact;
import co.edu.corhuila.dlc.patients.domain.model.Patient;
import co.edu.corhuila.dlc.patients.domain.model.PatientProfile;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.postgresql.ds.PGSimpleDataSource;

@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class JdbcRegistrationTransactionTest {
    private final UUID actor = UUID.randomUUID();
    private final PGSimpleDataSource source = new PGSimpleDataSource();
    private JdbcRegistrationTransaction transaction;

    @BeforeEach
    void connectOnlyToAnExplicitDisposableDatabase() throws Exception {
        source.setURL(System.getenv("TEST_DATABASE_URL"));
        source.setUser(System.getenv("TEST_DATABASE_USER"));
        source.setPassword(System.getenv("TEST_DATABASE_PASSWORD"));
        source.setConnectTimeout(5);
        source.setSocketTimeout(10);
        assertTrue(sql("SELECT current_database()").matches("patients_api_test_[a-f0-9]+"));
        assertEquals("patients_app", sql("SELECT current_user"));
        transaction = new JdbcRegistrationTransaction(source);
    }

    @Test
    void httpCreationAndReplayCommitOnePatientAuditAndEvent() throws Exception {
        var json = new ObjectMapper();
        var service = new RegisterPatientService(() -> actor, transaction, Clock.systemUTC(), UUID::randomUUID);
        var http = MockMvcBuilders.standaloneSetup(new PatientRegistrationController(service, () -> actor, json)).build();
        String body = "{\"documentType\":\"CC\",\"documentNumber\":\"" + UUID.randomUUID()
                + "\",\"name\":\"Synthetic\",\"birthDate\":\"2000-01-01\",\"phone\":\"000\",\"email\":\"fixture@example.invalid\"}";
        String key = UUID.randomUUID().toString();
        var first = http.perform(post("/api/v1/patients").contentType("application/json").header("Idempotency-Key", key).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse();
        var replay = http.perform(post("/api/v1/patients").contentType("application/json").header("Idempotency-Key", key).content(body))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertEquals(json.readTree(first.getContentAsString()), json.readTree(replay.getContentAsString()));
        assertEquals("/api/v1/patients/" + json.readTree(first.getContentAsString()).get("id").asText(), first.getHeader("Location"));
        for (var table : List.of("patient", "patient_audit", "outbox_event")) {
            String column = table.equals("patient") ? "created_by" : table.equals("patient_audit") ? "actor_id" : "actor_user_id";
            assertEquals("1", sql("SELECT count(*) FROM patients." + table + " WHERE " + column + "=?", actor));
        }
        assertEquals("1", sql("SELECT count(*) FROM patients.idempotency_record WHERE actor_subject=?", actor.toString()));
    }

    @Test
    void commitsProfileContactsAuditEventAndOriginalResponse() throws Exception {
        var write = write(UUID.randomUUID().toString(), UUID.randomUUID().toString());
        var result = transaction.register(write);
        assertFalse(result.replayed());
        assertEquals(write.patient().id(), result.patient().id());
        assertEquals("2", sql("SELECT count(*) FROM patients.emergency_contact WHERE patient_id=?", result.patient().id()));
        assertEquals(actor.toString(), sql("SELECT actor_id FROM patients.patient_audit WHERE audit_id=?", write.auditId()));
        assertEquals("PatientRegistered:ACTIVE", sql("SELECT event_type||':'||(payload->>'status') FROM patients.outbox_event WHERE event_id=?", write.eventId()));
        assertEquals("201:true", sql("SELECT response_status||':'||(expires_at>=created_at+interval '168 hours') FROM patients.idempotency_record WHERE actor_subject=?", actor.toString()));
        assertEquals("/api/v1/patients/" + result.patient().id(), sql("SELECT response_location FROM patients.idempotency_record WHERE actor_subject=?", actor.toString()));
        assertEquals("false:2000-01-01", sql("SELECT jsonb_exists(response_body,'address')::text||':'||(response_body->>'birthDate') FROM patients.idempotency_record WHERE actor_subject=?", actor.toString()));
    }

    @Test
    void replaysTheOriginalSnapshotAndRejectsAChangedRequestHash() throws Exception {
        var write = write(UUID.randomUUID().toString(), UUID.randomUUID().toString());
        transaction.register(write);
        sql("UPDATE patients.patient SET name='Changed',version=2 WHERE patient_id=?", write.patient().id());
        var retry = write(write.idempotencyKey(), write.patient().document().number());
        var replay = transaction.register(retry);
        assertTrue(replay.replayed());
        assertEquals(write.patient().id(), replay.patient().id());
        assertEquals(write.patient().profile(), replay.patient().profile());
        assertEquals(write.registeredAt(), replay.registeredAt());
        assertEquals(1, replay.patient().version());
        var conflict = new Write(retry.patient(), actor, retry.idempotencyKey(), "b".repeat(64),
                retry.auditId(), retry.eventId(), retry.registeredAt(), retry.correlationId(), retry.causationId());
        assertEquals(RegistrationConflictException.Reason.IDEMPOTENCY_CONFLICT,
                assertThrows(RegistrationConflictException.class, () -> transaction.register(conflict)).reason());
        assertNoWrites(retry);
    }

    @Test
    void rejectsNormalizedDuplicatesIncludingInactivePatients() throws Exception {
        for (var status : List.of("ACTIVE", "INACTIVE")) {
            String document = UUID.randomUUID().toString();
            sql("INSERT INTO patients.patient(patient_id,document_type,document_number,name,birth_date,phone,email,status,created_by,updated_by) VALUES (?,' cc ',?,'Synthetic',DATE '2000-01-01','3000000000','synthetic@example.invalid',?,?,?)",
                    UUID.randomUUID(), document, status, actor, actor);
            var write = write(UUID.randomUUID().toString(), " " + document.toUpperCase() + " ");
            assertEquals(RegistrationConflictException.Reason.PATIENT_DOCUMENT_EXISTS,
                    assertThrows(RegistrationConflictException.class, () -> transaction.register(write)).reason());
            assertNoWrites(write);
        }
    }

    @Test
    void outboxFailureRollsBackAllEffectsAndAllowsTheOriginalKeyToBeRetried() throws Exception {
        var existing = write(UUID.randomUUID().toString(), UUID.randomUUID().toString());
        transaction.register(existing);
        var next = write(UUID.randomUUID().toString(), UUID.randomUUID().toString());
        var collision = new Write(next.patient(), actor, next.idempotencyKey(), next.requestHash(),
                next.auditId(), existing.eventId(), next.registeredAt(), next.correlationId(), next.causationId());
        var failure = assertThrows(IllegalStateException.class, () -> transaction.register(collision));
        assertEquals("Registration persistence failed", failure.getMessage());
        assertNoWrites(next);
        assertFalse(transaction.register(next).replayed());
    }

    @Test
    void aRetryKeyCannotReplayAnotherActorsOriginalResponse() throws Exception {
        var original = write(UUID.randomUUID().toString(), UUID.randomUUID().toString());
        transaction.register(original);
        var candidate = write(original.idempotencyKey(), original.patient().document().number());
        var other = new Write(candidate.patient(), UUID.randomUUID(), candidate.idempotencyKey(),
                candidate.requestHash(), candidate.auditId(), candidate.eventId(), candidate.registeredAt(),
                candidate.correlationId(), candidate.causationId());
        assertEquals(RegistrationConflictException.Reason.PATIENT_DOCUMENT_EXISTS,
                assertThrows(RegistrationConflictException.class, () -> transaction.register(other)).reason());
        assertNoWrites(candidate);
    }

    @Test
    void concurrentSameKeyRequestsCommitOnceAndReturnTheSameSnapshot() throws Exception {
        String key = UUID.randomUUID().toString();
        String document = UUID.randomUUID().toString();
        var barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<RegistrationResult> request = () -> {
                barrier.await(10, TimeUnit.SECONDS);
                return transaction.register(write(key, document));
            };
            var first = executor.submit(request);
            var second = executor.submit(request);
            var a = first.get(20, TimeUnit.SECONDS);
            var b = second.get(20, TimeUnit.SECONDS);
            assertEquals(a.patient().id(), b.patient().id());
            assertEquals(a.registeredAt(), b.registeredAt());
            assertEquals(Set.of(false, true), Set.of(a.replayed(), b.replayed()));
            assertEquals("1", sql("SELECT count(*) FROM patients.idempotency_record WHERE actor_subject=?", actor.toString()));
        }
    }

    private Write write(String key, String document) {
        var contacts = List.of(new EmergencyContact("One", "Relative", "3000000000"),
                new EmergencyContact("Two", "Relative", "3000000001"));
        var profile = new PatientProfile("Synthetic O'Connor", LocalDate.of(2000, 1, 1),
                "3000000000", "synthetic@example.invalid", null, contacts);
        var patient = Patient.register(UUID.randomUUID(), new Document("CC", document), profile, Clock.systemUTC());
        return new Write(patient, actor, key, "a".repeat(64), UUID.randomUUID(), UUID.randomUUID(),
                Instant.now(), UUID.randomUUID(), UUID.randomUUID());
    }

    private void assertNoWrites(Write write) throws Exception {
        for (var table : List.of("patient", "emergency_contact", "patient_audit", "outbox_event")) {
            String column = table.equals("outbox_event") ? "aggregate_id" : "patient_id";
            assertEquals("0", sql("SELECT count(*) FROM patients." + table + " WHERE " + column + "=?", write.patient().id()));
        }
        assertEquals("0", sql("SELECT count(*) FROM patients.idempotency_record WHERE response_body->>'id'=?", write.patient().id().toString()));
    }

    private String sql(String text, Object... values) throws Exception {
        try (var connection = source.getConnection(); var statement = connection.prepareStatement(text)) {
            for (int i = 0; i < values.length; i++) {
                statement.setObject(i + 1, values[i]);
            }
            statement.execute();
            try (var rows = statement.getResultSet()) {
                return rows != null && rows.next() ? rows.getString(1) : "";
            }
        }
    }
}
