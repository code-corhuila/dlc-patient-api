package co.edu.corhuila.dlc.patients.adapter.out.persistence;

import co.edu.corhuila.dlc.patients.application.model.PatientReadUnavailableException;
import co.edu.corhuila.dlc.patients.application.port.in.ReadBillingContactUseCase;
import co.edu.corhuila.dlc.patients.application.port.out.BillingContactReader;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

/** Reads only the name and email; a patient without email has no billing contact. */
public final class JdbcBillingContactReader implements BillingContactReader {
    private final DataSource source;
    public JdbcBillingContactReader(DataSource source) { this.source = Objects.requireNonNull(source); }
    @Override public Optional<ReadBillingContactUseCase.Contact> find(UUID id) {
        Objects.requireNonNull(id);
        try (var connection = source.getConnection();
             var statement = connection.prepareStatement("SELECT name,email FROM patients.patient WHERE patient_id=?")) {
            statement.setQueryTimeout(5);
            statement.setObject(1, id);
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) return Optional.empty();
                String name = rows.getString(1), email = rows.getString(2);
                if (email == null || email.isBlank()) return Optional.empty();
                return Optional.of(new ReadBillingContactUseCase.Contact(id, email, name));
            }
        } catch (SQLException | RuntimeException failure) {
            throw new PatientReadUnavailableException();
        }
    }
}
