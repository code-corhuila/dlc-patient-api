package co.edu.corhuila.dlc.patients.adapter.out.persistence;

import co.edu.corhuila.dlc.patients.application.model.PatientReadUnavailableException;
import co.edu.corhuila.dlc.patients.application.port.out.PatientValidationReader;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

public final class JdbcPatientValidationReader implements PatientValidationReader {
    private final DataSource source;
    public JdbcPatientValidationReader(DataSource source) { this.source = Objects.requireNonNull(source); }
    @Override public Optional<State> find(UUID id) {
        Objects.requireNonNull(id);
        try (var connection = source.getConnection();
             var statement = connection.prepareStatement("SELECT status,version FROM patients.patient WHERE patient_id=?")) {
            statement.setQueryTimeout(5);
            statement.setObject(1, id);
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) return Optional.empty();
                String status = rows.getString(1);
                if (!"ACTIVE".equals(status) && !"INACTIVE".equals(status)) throw new IllegalStateException();
                return Optional.of(new State("ACTIVE".equals(status), rows.getLong(2)));
            }
        } catch (SQLException | RuntimeException failure) {
            throw new PatientReadUnavailableException();
        }
    }
}
