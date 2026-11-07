package co.edu.corhuila.dlc.patients.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class PatientRegistrationTest {
    private static final UUID ID = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-04T12:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate BIRTH_DATE = LocalDate.of(2000, 1, 1);
    private static final Document DOCUMENT = new Document(" cc ", " 001-23 ");

    @Test
    void registrationStartsActiveWithStableIdentityAndVersionOne() {
        var profile = profile(BIRTH_DATE, null, null);
        var patient = Patient.register(ID, DOCUMENT, profile, CLOCK);

        assertEquals(ID, patient.id());
        assertEquals(PatientStatus.ACTIVE, patient.status());
        assertEquals(1, patient.version());
        assertEquals(DOCUMENT, patient.document());
        assertEquals(" cc ", patient.document().type());
        assertEquals(" 001-23 ", patient.document().number());
        assertEquals(profile, patient.profile());
        assertNull(patient.profile().address());
        assertTrue(patient.profile().emergencyContacts().isEmpty());
    }

    @Test
    void contactsAreOptionalMultipleAndCannotBeChangedOutsideTheAggregate() {
        var contact = new EmergencyContact("Synthetic Contact", "Relative", "+57 3000000000");
        var supplied = new ArrayList<>(List.of(contact, contact));
        var patient = Patient.register(ID, DOCUMENT, profile(BIRTH_DATE, "Synthetic Address", supplied), CLOCK);
        supplied.clear();

        assertEquals(List.of(contact, contact), patient.profile().emergencyContacts());
        assertThrows(UnsupportedOperationException.class, patient.profile().emergencyContacts()::clear);
        assertEquals("Synthetic Address", patient.profile().address());
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-11-04", "2026-11-05"})
    void birthDateMustBeStrictlyBeforeTheTrustedRegistrationDate(String date) {
        var error = assertThrows(InvalidPatientDataException.class,
                () -> Patient.register(ID, DOCUMENT, profile(LocalDate.parse(date), null, null), CLOCK));
        assertEquals("birthDate", error.field());
    }

    @Test
    void yesterdayIsValidAndTheInjectedClockDeterminesToday() {
        var yesterday = LocalDate.of(2026, 11, 3);
        assertEquals(yesterday, Patient.register(ID, DOCUMENT, profile(yesterday, null, null), CLOCK)
                .profile().birthDate());
        var earlier = Clock.fixed(Instant.parse("2026-11-03T12:00:00Z"), ZoneOffset.UTC);
        assertThrows(InvalidPatientDataException.class,
                () -> Patient.register(ID, DOCUMENT, profile(yesterday, null, null), earlier));
    }

    @ParameterizedTest
    @CsvSource({"documentType,30", "documentNumber,50", "name,255", "phone,40", "email,255",
            "address,500", "emergencyContacts.name,255", "emergencyContacts.relationship,80",
            "emergencyContacts.phone,40"})
    void rejectsBlankAndOversizedFieldsWithoutExposingTheirValues(String field, int maximum) {
        for (var value : List.of("", " \t\n", "x".repeat(maximum + 1))) {
            var error = assertThrows(InvalidPatientDataException.class, () -> withField(field, value));
            assertEquals(field, error.field());
            assertFalse(error.getMessage().contains("x".repeat(maximum + 1)));
        }
        if (!field.equals("address")) {
            assertEquals(field, assertThrows(InvalidPatientDataException.class,
                    () -> withField(field, null)).field());
        }
    }

    @Test
    void acceptsContractLimitsUsingUnicodeCharactersRatherThanUtf16Units() {
        var name = "\uD83D\uDE00".repeat(255);
        var contact = new EmergencyContact("n".repeat(255), "r".repeat(80), "1".repeat(40));
        var profile = new PatientProfile(name, BIRTH_DATE, "1".repeat(40),
                "a".repeat(243) + "@example.org", "a".repeat(500), List.of(contact));
        var patient = Patient.register(ID, new Document("t".repeat(30), "0".repeat(50)), profile, CLOCK);
        assertEquals(name, patient.profile().name());
        assertEquals(50, patient.document().number().length());
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing-at.example.org", "a@localhost", "a@@example.org", "a b@example.org",
            "a@example.org\n", "a@\u2003example.org"})
    void rejectsMalformedEmail(String email) {
        assertEquals("email", assertThrows(InvalidPatientDataException.class,
                () -> withField("email", email)).field());
    }

    @Test
    void registrationRequiresIdentityDocumentProfileAndClock() {
        var profile = profile(BIRTH_DATE, null, null);
        assertEquals("id", assertThrows(InvalidPatientDataException.class,
                () -> Patient.register(null, DOCUMENT, profile, CLOCK)).field());
        assertEquals("document", assertThrows(InvalidPatientDataException.class,
                () -> Patient.register(ID, null, profile, CLOCK)).field());
        assertEquals("profile", assertThrows(InvalidPatientDataException.class,
                () -> Patient.register(ID, DOCUMENT, null, CLOCK)).field());
        assertEquals("clock", assertThrows(InvalidPatientDataException.class,
                () -> Patient.register(ID, DOCUMENT, profile, null)).field());
    }

    @Test
    void profileRequiresABirthDate() {
        assertEquals("birthDate", assertThrows(InvalidPatientDataException.class,
                () -> profile(null, null, null)).field());
    }

    @Test
    void contactCollectionsCannotContainNullElements() {
        var contacts = new ArrayList<EmergencyContact>();
        contacts.add(null);
        assertEquals("emergencyContacts", assertThrows(InvalidPatientDataException.class,
                () -> profile(BIRTH_DATE, null, contacts)).field());
    }

    private static PatientProfile profile(LocalDate birthDate, String address, List<EmergencyContact> contacts) {
        return new PatientProfile("Synthetic Patient", birthDate, "+57 3000000000",
                "patient@example.invalid", address, contacts);
    }

    private static Patient withField(String field, String value) {
        var document = new Document(field.equals("documentType") ? value : "CC",
                field.equals("documentNumber") ? value : "00123");
        var contact = new EmergencyContact(field.equals("emergencyContacts.name") ? value : "Contact",
                field.equals("emergencyContacts.relationship") ? value : "Relative",
                field.equals("emergencyContacts.phone") ? value : "3000000000");
        var profile = new PatientProfile(field.equals("name") ? value : "Patient", BIRTH_DATE,
                field.equals("phone") ? value : "3000000000",
                field.equals("email") ? value : "patient@example.invalid",
                field.equals("address") ? value : null, List.of(contact));
        return Patient.register(ID, document, profile, CLOCK);
    }
}
