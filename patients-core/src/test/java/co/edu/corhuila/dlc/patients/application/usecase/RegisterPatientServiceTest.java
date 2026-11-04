package co.edu.corhuila.dlc.patients.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.edu.corhuila.dlc.patients.application.model.RegistrationConflictException;
import co.edu.corhuila.dlc.patients.application.model.RegistrationResult;
import co.edu.corhuila.dlc.patients.application.port.in.RegisterPatientUseCase.Command;
import co.edu.corhuila.dlc.patients.application.port.out.RegistrationTransaction;
import co.edu.corhuila.dlc.patients.domain.model.Document;
import co.edu.corhuila.dlc.patients.domain.model.EmergencyContact;
import co.edu.corhuila.dlc.patients.domain.model.InvalidPatientDataException;
import co.edu.corhuila.dlc.patients.domain.model.Patient;
import co.edu.corhuila.dlc.patients.domain.model.PatientProfile;
import co.edu.corhuila.dlc.patients.domain.model.PatientStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class RegisterPatientServiceTest {
    private static final UUID ACTOR = new UUID(0, 10);
    private static final UUID CORRELATION = new UUID(0, 20);
    private static final UUID CAUSATION = new UUID(0, 30);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-04T12:00:00Z"), ZoneOffset.UTC);
    private final RecordingTransaction transaction = new RecordingTransaction();
    private final AtomicLong ids = new AtomicLong(100);
    private int authorizations;
    private RuntimeException authorizationFailure;
    private final RegisterPatientService service = new RegisterPatientService(() -> {
        authorizations++;
        if (authorizationFailure != null) {
            throw authorizationFailure;
        }
        return ACTOR;
    }, transaction, CLOCK, () -> new UUID(0, ids.incrementAndGet()));

    @Test
    void preparesOneAtomicRegistrationWithTrustedActorTimeAndTracing() {
        var result = service.register(command("Synthetic Patient", null, "request-001"));
        var write = transaction.last;
        assertEquals(1, transaction.calls);
        assertEquals(1, authorizations);
        assertSame(write.patient(), result.patient());
        assertFalse(result.replayed());
        assertEquals(PatientStatus.ACTIVE, write.patient().status());
        assertEquals(1, write.patient().version());
        assertEquals(ACTOR, write.actorId());
        assertEquals(CLOCK.instant(), write.registeredAt());
        assertEquals(write.registeredAt(), result.registeredAt());
        assertEquals(CORRELATION, write.correlationId());
        assertEquals(CAUSATION, write.causationId());
        assertEquals("request-001", write.idempotencyKey());
        assertNotEquals(write.patient().id(), write.eventId());
        assertNotEquals(write.auditId(), write.eventId());
        assertTrue(write.requestHash().matches("[0-9a-f]{64}"));
    }

    @Test
    void returnsTheStoredReplayAndReauthorizesBeforeEveryAttempt() {
        var original = Patient.register(new UUID(0, 999), new Document("CC", "00123"),
                profile("Original", null), CLOCK);
        transaction.result = new RegistrationResult(original, CLOCK.instant().minusSeconds(86400), true);
        assertSame(transaction.result, service.register(command("Patient", null, "request-001")));
        authorizationFailure = new SecurityException("Permission revoked");
        assertSame(authorizationFailure, assertThrows(SecurityException.class,
                () -> service.register(command("Patient", null, "request-001"))));
        assertEquals(2, authorizations);
        assertEquals(1, transaction.calls);
    }

    @Test
    void unavailableAuthorityFailsClosedBeforeGeneratingIdentifiersOrWriting() {
        authorizationFailure = new IllegalStateException("Authority unavailable");
        assertSame(authorizationFailure, assertThrows(IllegalStateException.class,
                () -> service.register(command("Patient", null, "request-001"))));
        assertEquals(100, ids.get());
        assertEquals(0, transaction.calls);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"short"})
    void rejectsMissingAndShortIdempotencyKeys(String key) {
        assertEquals("Idempotency-Key", assertThrows(InvalidPatientDataException.class,
                () -> service.register(command("Patient", null, key))).field());
        assertEquals(0, transaction.calls);
    }

    @Test
    void enforcesBothKeyLengthBoundaries() {
        for (int length : List.of(8, 128)) {
            service.register(command("Patient", null, "k".repeat(length)));
            assertEquals("k".repeat(length), transaction.last.idempotencyKey());
        }
        assertThrows(InvalidPatientDataException.class,
                () -> service.register(command("Patient", null, "k".repeat(129))));
        assertEquals(2, transaction.calls);
    }

    @Test
    void fingerprintsOnlyThePayloadWithoutAmbiguousFieldSeparators() {
        var original = command(new Document("a|b", "c"), profile("Patient", null), "request-001");
        String first = hash(original);
        assertEquals(first, hash(new Command(original.document(), original.profile(),
                "request-002", CAUSATION, CORRELATION)));
        assertNotEquals(first, hash(command(new Document("a", "b|c"), original.profile(), "request-001")));
        assertNotEquals(hash(command("Patient", null, "request-001")),
                hash(command("Patient", "null", "request-001")));
    }

    @Test
    void changedProfileFieldsOrOrderedContactsChangeTheRequestFingerprint() {
        var profile = profile("Patient", null);
        var one = new EmergencyContact("One", "Relative", "3000000000");
        var two = new EmergencyContact("Two", "Relative", "3000000001");
        String original = hash(command("Patient", null, "request-001"));
        for (var contacts : List.of(List.of(one), List.of(one, two), List.of(two, one))) {
            var changed = new PatientProfile(profile.name(), profile.birthDate(), profile.phone(),
                    profile.email(), null, contacts);
            String updated = hash(command(new Document("CC", "00123"), changed, "request-001"));
            assertNotEquals(original, updated);
            original = updated;
        }
        assertNotEquals(hash(command("Patient", null, "request-001")),
                hash(command("Changed name", null, "request-001")));
    }

    @Test
    void invalidRegistrationNeverReachesTheTransaction() {
        var future = new PatientProfile("Patient", LocalDate.of(2026, 11, 5), "3000000000",
                "patient@example.invalid", null, List.of());
        assertThrows(InvalidPatientDataException.class, () -> service.register(new Command(
                new Document("CC", "00123"), future, "request-001", CORRELATION, CAUSATION)));
        assertEquals(0, transaction.calls);
    }

    @Test
    void transactionConflictsAndFailuresArePropagatedWithoutRetrying() {
        for (var reason : RegistrationConflictException.Reason.values()) {
            transaction.failure = new RegistrationConflictException(reason);
            assertSame(transaction.failure, assertThrows(RegistrationConflictException.class,
                    () -> service.register(command("Patient", null, "request-001"))));
        }
        transaction.failure = new IllegalStateException("Transaction rolled back");
        assertSame(transaction.failure, assertThrows(IllegalStateException.class,
                () -> service.register(command("Patient", null, "request-001"))));
        assertEquals(3, transaction.calls);
    }

    private String hash(Command command) {
        service.register(command);
        return transaction.last.requestHash();
    }

    private static Command command(String name, String address, String key) {
        return command(new Document("CC", "00123"), profile(name, address), key);
    }

    private static Command command(Document document, PatientProfile profile, String key) {
        return new Command(document, profile, key, CORRELATION, CAUSATION);
    }

    private static PatientProfile profile(String name, String address) {
        return new PatientProfile(name, LocalDate.of(2000, 1, 1), "3000000000",
                "patient@example.invalid", address, List.of());
    }

    private static final class RecordingTransaction implements RegistrationTransaction {
        private Write last;
        private int calls;
        private RegistrationResult result;
        private RuntimeException failure;

        public RegistrationResult register(Write write) {
            last = write;
            calls++;
            if (failure != null) {
                throw failure;
            }
            return result == null ? new RegistrationResult(write.patient(), write.registeredAt(), false) : result;
        }
    }
}
