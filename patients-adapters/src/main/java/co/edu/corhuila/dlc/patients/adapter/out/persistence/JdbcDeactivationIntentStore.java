package co.edu.corhuila.dlc.patients.adapter.out.persistence;

import co.edu.corhuila.dlc.patients.application.port.out.DeactivationIntentStore;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import java.util.Optional;
import java.util.List;
import java.util.ArrayList;
import java.sql.ResultSet;
import javax.sql.DataSource;

public final class JdbcDeactivationIntentStore implements DeactivationIntentStore {
    private final DataSource source;
    public JdbcDeactivationIntentStore(DataSource source) { this.source = Objects.requireNonNull(source); }

    @Override public Operation begin(Intent intent) {
        UUID key = UUID.nameUUIDFromBytes(intent.idempotencyKey().getBytes(StandardCharsets.UTF_8));
        try (var connection = source.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (var insert = connection.prepareStatement("INSERT INTO patients.deactivation_operation(operation_id,patient_id,actor_id,idempotency_key,expected_version,reason,reason_code) VALUES (?,?,?,?,?,?,?) ON CONFLICT (actor_id,patient_id,idempotency_key) DO NOTHING")) {
                    insert.setQueryTimeout(5); insert.setObject(1,intent.operationId()); insert.setObject(2,intent.patientId());
                    insert.setObject(3,intent.actorId()); insert.setObject(4,key); insert.setInt(5,intent.request().expectedVersion());
                    insert.setString(6,intent.request().reason()); insert.setString(7,intent.request().reasonCode().name()); insert.executeUpdate();
                }
                Operation result;
                try (var query = connection.prepareStatement("SELECT operation_id,outcome,expected_version,reason,reason_code FROM patients.deactivation_operation WHERE actor_id=? AND patient_id=? AND idempotency_key=?")) {
                    query.setQueryTimeout(5); query.setObject(1,intent.actorId()); query.setObject(2,intent.patientId()); query.setObject(3,key);
                    try (var rows = query.executeQuery()) {
                        if (!rows.next()) throw new Unavailable();
                        if (rows.getInt(3) != intent.request().expectedVersion() || !intent.request().reason().equals(rows.getString(4)) || !intent.request().reasonCode().name().equals(rows.getString(5))) throw new Conflict();
                        result = new Operation(rows.getObject(1,UUID.class),rows.getString(2));
                    }
                }
                connection.commit(); return result;
            } catch (SQLException | RuntimeException failure) {
                try { connection.rollback(); } catch (SQLException rollback) { throw new Unavailable(); }
                if (failure instanceof Conflict conflict) throw conflict;
                throw new Unavailable();
            }
        } catch (SQLException failure) { throw new Unavailable(); }
    }

    @Override public void recordGuard(UUID operationId, long expectedVersion, UUID guardId, String token) {
        if (operationId == null || expectedVersion < 1 || guardId == null || token == null || token.isBlank()) throw new IllegalArgumentException("Invalid guard proof");
        try (var connection = source.getConnection(); var statement = connection.prepareStatement("UPDATE patients.deactivation_operation SET guard_id=?,guard_token_hash=? WHERE operation_id=? AND outcome='PENDING' AND (guard_id IS NULL OR guard_id=?) AND operation_version=?")) {
            statement.setQueryTimeout(5); statement.setObject(1,guardId); statement.setString(2,hash(token)); statement.setObject(3,operationId); statement.setObject(4,guardId); statement.setLong(5,expectedVersion);
            if (statement.executeUpdate() != 1) throw new GuardRace();
        } catch (SQLException failure) { throw new Unavailable(); }
    }
    @Override public Optional<Recovery> find(UUID operationId) {
        Objects.requireNonNull(operationId);
        try (var connection=source.getConnection(); var statement=connection.prepareStatement("SELECT operation_id,patient_id,actor_id,guard_id,outcome,operation_version FROM patients.deactivation_operation WHERE operation_id=?")) {
            statement.setQueryTimeout(5); statement.setObject(1,operationId);
            try(var rows=statement.executeQuery()) { return rows.next() ? Optional.of(recovery(rows)) : Optional.empty(); }
        } catch (SQLException failure) { throw new Unavailable(); }
    }
    @Override public List<Recovery> pending(int limit) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("Invalid recovery limit");
        try (var connection=source.getConnection(); var statement=connection.prepareStatement("SELECT operation_id,patient_id,actor_id,guard_id,outcome,operation_version FROM patients.deactivation_operation WHERE outcome='PENDING' ORDER BY created_at,operation_id LIMIT ?")) {
            statement.setQueryTimeout(5); statement.setInt(1,limit); var result=new ArrayList<Recovery>();
            try(var rows=statement.executeQuery()) { while(rows.next()) result.add(recovery(rows)); }
            return List.copyOf(result);
        } catch (SQLException failure) { throw new Unavailable(); }
    }
    private static Recovery recovery(ResultSet row) throws SQLException {
        return new Recovery(row.getObject(1,UUID.class),row.getObject(2,UUID.class),row.getObject(3,UUID.class),row.getObject(4,UUID.class),row.getString(5),row.getLong(6));
    }
    private static String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new Unavailable(); }
    }
}
