package co.edu.corhuila.dlc.patients.adapter.out.persistence;

import co.edu.corhuila.dlc.patients.application.model.PatientSearchPage.Row;
import co.edu.corhuila.dlc.patients.application.model.PatientSearchUnavailableException;
import co.edu.corhuila.dlc.patients.application.port.out.PatientSearchAuthorization.View;
import co.edu.corhuila.dlc.patients.application.port.out.PatientSearchReader;
import co.edu.corhuila.dlc.patients.domain.model.Document;
import co.edu.corhuila.dlc.patients.domain.model.EmergencyContact;
import co.edu.corhuila.dlc.patients.domain.model.PatientProfile;
import co.edu.corhuila.dlc.patients.domain.model.PatientStatus;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;

public final class JdbcPatientSearchReader implements PatientSearchReader {
    private static final String FILTER = """
            FROM patients.patient WHERE (? = false OR patient_id = ANY (?))
            AND (?::text IS NULL OR status = ?)
            AND (?::text IS NULL OR strpos(lower(name), lower(?)) > 0 OR strpos(lower(document_number), lower(?)) > 0)
            """;
    private final DataSource source;

    public JdbcPatientSearchReader(DataSource source) { this.source = Objects.requireNonNull(source); }

    @Override
    public Result find(Criteria criteria, Scope scope) {
        try (var connection = source.getConnection()) {
            boolean autoCommit = connection.getAutoCommit(), readOnly = connection.isReadOnly();
            int isolation = connection.getTransactionIsolation();
            try {
                connection.setReadOnly(true);
                connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
                connection.setAutoCommit(false);
                long total;
                try (var statement = connection.prepareStatement("SELECT count(*) " + FILTER)) {
                    bind(statement, criteria, scope);
                    try (var rows = statement.executeQuery()) { rows.next(); total = rows.getLong(1); }
                }
                var patients = new ArrayList<Row>();
                try (var statement = connection.prepareStatement("SELECT patient_id,document_type,document_number,name,birth_date,phone,email,address,status,created_at,updated_at,version "
                        + FILTER + " ORDER BY created_at DESC, patient_id DESC LIMIT ? OFFSET ?")) {
                    bind(statement, criteria, scope);
                    statement.setInt(8, criteria.limit());
                    statement.setLong(9, criteria.offset());
                    try (var rows = statement.executeQuery()) {
                        while (rows.next()) patients.add(row(connection, rows));
                    }
                }
                connection.commit();
                return new Result(patients, total);
            } finally {
                if (!connection.getAutoCommit()) connection.rollback();
                connection.setAutoCommit(autoCommit);
                connection.setTransactionIsolation(isolation);
                connection.setReadOnly(readOnly);
            }
        } catch (SQLException | RuntimeException failure) {
            throw new PatientSearchUnavailableException();
        }
    }

    private static void bind(PreparedStatement statement, Criteria criteria, Scope scope) throws SQLException {
        statement.setQueryTimeout(5);
        statement.setBoolean(1, scope.access().view() == View.CARE);
        statement.setArray(2, statement.getConnection().createArrayOf("uuid", scope.assignedPatientIds().toArray()));
        String status = criteria.status() == null ? null : criteria.status().name();
        statement.setString(3, status); statement.setString(4, status);
        statement.setString(5, criteria.search()); statement.setString(6, criteria.search()); statement.setString(7, criteria.search());
    }

    private static Row row(Connection connection, ResultSet row) throws SQLException {
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
