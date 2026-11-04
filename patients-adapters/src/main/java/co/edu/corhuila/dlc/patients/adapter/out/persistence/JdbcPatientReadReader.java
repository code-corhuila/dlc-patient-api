package co.edu.corhuila.dlc.patients.adapter.out.persistence;

import co.edu.corhuila.dlc.patients.application.model.PatientReadUnavailableException;
import co.edu.corhuila.dlc.patients.application.model.PatientSearchPage.Row;
import co.edu.corhuila.dlc.patients.application.port.out.PatientReadReader;
import co.edu.corhuila.dlc.patients.application.port.out.PatientSearchAuthorization.View;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

public final class JdbcPatientReadReader implements PatientReadReader {
    private static final String SELECT = "SELECT patient_id,document_type,document_number,name,birth_date,phone,email,address,status,created_at,updated_at,version "
            + "FROM patients.patient WHERE patient_id=? AND (?=false OR patient_id=ANY(?))";
    private final DataSource source;

    public JdbcPatientReadReader(DataSource source) { this.source = Objects.requireNonNull(source); }

    @Override
    public Optional<Row> findById(UUID patientId, Scope scope) {
        Objects.requireNonNull(patientId); Objects.requireNonNull(scope);
        try (var connection = source.getConnection()) {
            boolean autoCommit = connection.getAutoCommit(), readOnly = connection.isReadOnly();
            int isolation = connection.getTransactionIsolation();
            try {
                connection.setReadOnly(true);
                connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
                connection.setAutoCommit(false);
                Optional<Row> result;
                try (var statement = connection.prepareStatement(SELECT)) {
                    statement.setQueryTimeout(5);
                    statement.setObject(1, patientId);
                    statement.setBoolean(2, scope.access().view() == View.CARE);
                    statement.setArray(3, connection.createArrayOf("uuid", scope.assignedPatientIds().toArray()));
                    try (var rows = statement.executeQuery()) {
                        result = rows.next() ? Optional.of(JdbcPatientRowMapper.read(connection, rows)) : Optional.empty();
                    }
                }
                connection.commit();
                return result;
            } finally {
                if (!connection.getAutoCommit()) connection.rollback();
                connection.setAutoCommit(autoCommit);
                connection.setTransactionIsolation(isolation);
                connection.setReadOnly(readOnly);
            }
        } catch (SQLException | RuntimeException failure) {
            throw new PatientReadUnavailableException();
        }
    }
}
