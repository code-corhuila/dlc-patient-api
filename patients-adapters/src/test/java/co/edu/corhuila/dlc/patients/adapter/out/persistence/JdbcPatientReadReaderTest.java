package co.edu.corhuila.dlc.patients.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.edu.corhuila.dlc.patients.application.port.out.PatientReadReader.Scope;
import co.edu.corhuila.dlc.patients.application.port.out.PatientSearchAuthorization.Access;
import co.edu.corhuila.dlc.patients.application.port.out.PatientSearchAuthorization.View;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.postgresql.ds.PGSimpleDataSource;

@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class JdbcPatientReadReaderTest {
    @Test
    void readsContactsAndEnforcesAdministrativeAndCareScopes() throws Exception {
        var source = new PGSimpleDataSource();
        source.setURL(System.getenv("TEST_DATABASE_URL"));
        source.setUser(System.getenv("TEST_DATABASE_USER"));
        source.setPassword(System.getenv("TEST_DATABASE_PASSWORD"));
        UUID actor = UUID.randomUUID(), patient = UUID.randomUUID(), outside = UUID.randomUUID();
        try (var connection = source.getConnection()) {
            assertTrue(connection.getCatalog().matches("patients_api_test_[a-f0-9]+"));
            assertEquals("patients_app", connection.getMetaData().getUserName());
            try (var insert = connection.prepareStatement("INSERT INTO patients.patient(patient_id,document_type,document_number,name,birth_date,phone,email,status,created_by,updated_by) VALUES (?,'CC',?, ?,DATE '2000-01-01','000','fixture@example.invalid','ACTIVE',?,?)")) {
                for (var id : new UUID[]{patient, outside}) {
                    insert.setObject(1, id); insert.setString(2, id.toString()); insert.setString(3, "Read Fixture");
                    insert.setObject(4, actor); insert.setObject(5, actor); insert.executeUpdate();
                }
            }
            try (var insert = connection.prepareStatement("INSERT INTO patients.emergency_contact(emergency_contact_id,patient_id,name,relationship,phone) VALUES (?,?, 'Fixture','Relative','000')")) {
                insert.setObject(1, UUID.randomUUID()); insert.setObject(2, patient); insert.executeUpdate();
            }
        }
        var reader = new JdbcPatientReadReader(source);
        var admin = new Scope(new Access(actor, View.ADMINISTRATIVE), Set.of());
        var care = new Scope(new Access(actor, View.CARE), Set.of(patient));
        var row = reader.findById(patient, admin);
        assertTrue(row.isPresent());
        assertEquals(1, row.get().profile().emergencyContacts().size());
        assertTrue(reader.findById(patient, care).isPresent());
        assertFalse(reader.findById(outside, care).isPresent());
        assertFalse(reader.findById(UUID.randomUUID(), admin).isPresent());
    }
}
