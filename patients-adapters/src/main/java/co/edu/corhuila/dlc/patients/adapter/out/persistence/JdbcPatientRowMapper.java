package co.edu.corhuila.dlc.patients.adapter.out.persistence;

import co.edu.corhuila.dlc.patients.application.model.PatientSearchPage.Row;
import co.edu.corhuila.dlc.patients.domain.model.Document;
import co.edu.corhuila.dlc.patients.domain.model.EmergencyContact;
import co.edu.corhuila.dlc.patients.domain.model.PatientProfile;
import co.edu.corhuila.dlc.patients.domain.model.PatientStatus;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.UUID;

final class JdbcPatientRowMapper {
    private JdbcPatientRowMapper() { }

    static Row read(Connection connection, ResultSet row) throws SQLException {
        UUID id = row.getObject("patient_id", UUID.class);
        var contacts = new ArrayList<EmergencyContact>();
        try (var statement = connection.prepareStatement("SELECT name,relationship,phone FROM patients.emergency_contact WHERE patient_id=? ORDER BY emergency_contact_id")) {
            statement.setQueryTimeout(5); statement.setObject(1, id);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) contacts.add(new EmergencyContact(rows.getString(1), rows.getString(2), rows.getString(3)));
            }
        }
        return new Row(id, new Document(row.getString("document_type"), row.getString("document_number")),
                new PatientProfile(row.getString("name"), row.getObject("birth_date", java.time.LocalDate.class),
                        row.getString("phone"), row.getString("email"), row.getString("address"), contacts),
                PatientStatus.valueOf(row.getString("status")), row.getTimestamp("created_at").toInstant(),
                row.getTimestamp("updated_at").toInstant(), row.getInt("version"));
    }
}
