package co.edu.corhuila.dlc.patients.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.*;

import co.edu.corhuila.dlc.patients.application.port.out.PatientSearchAuthorization.Access;
import co.edu.corhuila.dlc.patients.application.port.out.PatientSearchAuthorization.View;
import co.edu.corhuila.dlc.patients.application.port.out.PatientSearchReader.Criteria;
import co.edu.corhuila.dlc.patients.application.port.out.PatientSearchReader.Scope;
import co.edu.corhuila.dlc.patients.domain.model.PatientStatus;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.postgresql.ds.PGSimpleDataSource;

@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class JdbcPatientSearchReaderTest {
    @Test
    void filtersBeforeCountingAndPaginationWithLiteralSearchAndContacts() throws Exception {
        var source = new PGSimpleDataSource();
        source.setURL(System.getenv("TEST_DATABASE_URL"));
        source.setUser(System.getenv("TEST_DATABASE_USER"));
        source.setPassword(System.getenv("TEST_DATABASE_PASSWORD"));
        UUID actor = UUID.randomUUID(), a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
        String marker = UUID.randomUUID().toString();
        try (var connection = source.getConnection()) {
            assertTrue(connection.getCatalog().matches("patients_api_test_[a-f0-9]+"));
            assertEquals("patients_app", connection.getMetaData().getUserName());
            try (var insert = connection.prepareStatement("INSERT INTO patients.patient(patient_id,document_type,document_number,name,birth_date,phone,email,status,created_by,updated_by) VALUES (?,'CC',?, ?,DATE '2000-01-01','000','fixture@example.invalid',?,?,?)")) {
                for (var id : new UUID[]{a, b, c}) {
                    insert.setObject(1, id); insert.setString(2, marker.substring(0, 8) + id); insert.setString(3, marker + (id.equals(a) ? "%_Literal" : "Other"));
                    insert.setString(4, id.equals(c) ? "INACTIVE" : "ACTIVE"); insert.setObject(5, actor); insert.setObject(6, actor); insert.executeUpdate();
                }
            }
            try (var insert = connection.prepareStatement("INSERT INTO patients.emergency_contact(emergency_contact_id,patient_id,name,relationship,phone) VALUES (?,?, 'Fixture','Relative','000')")) {
                insert.setObject(1, UUID.randomUUID()); insert.setObject(2, a); insert.executeUpdate();
            }
        }
        var reader = new JdbcPatientSearchReader(source);
        var admin = new Scope(new Access(actor, View.ADMINISTRATIVE), Set.of());
        var care = new Scope(new Access(actor, View.CARE), Set.of(a, c));
        var literal = reader.find(new Criteria(1, 20, marker.toUpperCase() + "%_lItErAl", null), admin);
        assertEquals(1, literal.total());
        assertEquals(a, literal.rows().getFirst().id());
        assertEquals(1, literal.rows().getFirst().profile().emergencyContacts().size());
        assertEquals(a, reader.find(new Criteria(1, 20, (marker.substring(0, 8) + a).toUpperCase(), null), admin).rows().getFirst().id());
        var first = reader.find(new Criteria(1, 1, marker, PatientStatus.ACTIVE), admin);
        var second = reader.find(new Criteria(2, 1, marker, PatientStatus.ACTIVE), admin);
        assertEquals(2, first.total());
        assertNotEquals(first.rows().getFirst().id(), second.rows().getFirst().id());
        var scoped = reader.find(new Criteria(1, 1, marker, PatientStatus.ACTIVE), care);
        assertEquals(1, scoped.total());
        assertEquals(a, scoped.rows().getFirst().id());
        assertEquals(0, reader.find(new Criteria(2, 1, marker, PatientStatus.ACTIVE), care).rows().size());
        assertEquals(0, reader.find(new Criteria(1, 20, marker, null), new Scope(new Access(actor, View.CARE), Set.of())).total());
    }
}
