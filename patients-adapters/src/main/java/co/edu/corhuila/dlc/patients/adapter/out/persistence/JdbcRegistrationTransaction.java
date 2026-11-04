package co.edu.corhuila.dlc.patients.adapter.out.persistence;

import co.edu.corhuila.dlc.patients.application.model.RegistrationConflictException;
import co.edu.corhuila.dlc.patients.application.model.RegistrationConflictException.Reason;
import co.edu.corhuila.dlc.patients.application.model.RegistrationResult;
import co.edu.corhuila.dlc.patients.application.port.out.RegistrationTransaction;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;

public final class JdbcRegistrationTransaction implements RegistrationTransaction {
    private final DataSource source;

    public JdbcRegistrationTransaction(DataSource source) {
        this.source = Objects.requireNonNull(source);
    }

    @Override
    public RegistrationResult register(Write write) {
        UUID key = UUID.nameUUIDFromBytes(write.idempotencyKey().getBytes(StandardCharsets.UTF_8));
        UUID scope = UUID.nameUUIDFromBytes(("patients/register/" + write.actorId() + "/" + key)
                .getBytes(StandardCharsets.UTF_8));
        try (var connection = source.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            int isolation = connection.getTransactionIsolation();
            try {
                connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
                connection.setAutoCommit(false);
                execute(connection, "SELECT pg_advisory_xact_lock(?)", scope.getMostSignificantBits());
                var result = replay(connection, write, key);
                if (result == null) {
                    persist(connection, write, key);
                    result = new RegistrationResult(write.patient(), write.registeredAt(), false);
                }
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException failure) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    throw new IllegalStateException("Registration rollback could not be confirmed");
                }
                if (failure instanceof RegistrationConflictException conflict) {
                    throw conflict;
                }
                throw new IllegalStateException("Registration persistence failed");
            } finally {
                connection.setAutoCommit(autoCommit);
                connection.setTransactionIsolation(isolation);
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("Registration connection failed");
        }
    }

    private RegistrationResult replay(Connection connection, Write write, UUID key) throws SQLException {
        try (var statement = connection.prepareStatement("""
                SELECT request_hash,response_body::text FROM patients.idempotency_record
                WHERE actor_kind='STAFF' AND actor_subject=? AND operation='register'
                  AND target_resource='/patients' AND idempotency_key=?
                """)) {
            statement.setQueryTimeout(5);
            statement.setString(1, write.actorId().toString());
            statement.setObject(2, key);
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return null;
                }
                if (!write.requestHash().equals(rows.getString(1))) {
                    throw new RegistrationConflictException(Reason.IDEMPOTENCY_CONFLICT);
                }
                return RegistrationSnapshot.replay(rows.getString(2));
            }
        }
    }

    private void persist(Connection connection, Write write, UUID key) throws SQLException {
        var patient = write.patient();
        var profile = patient.profile();
        var time = write.registeredAt().atOffset(ZoneOffset.UTC);
        int inserted = execute(connection, """
                INSERT INTO patients.patient(patient_id,document_type,document_number,name,birth_date,phone,email,
                    address,created_at,updated_at,created_by,updated_by) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT (upper(btrim(document_type)),upper(btrim(document_number))) DO NOTHING
                """, patient.id(), patient.document().type(), patient.document().number(), profile.name(),
                profile.birthDate(), profile.phone(), profile.email(), profile.address(), time, time, write.actorId(), write.actorId());
        if (inserted == 0) {
            throw new RegistrationConflictException(Reason.PATIENT_DOCUMENT_EXISTS);
        }
        for (var contact : profile.emergencyContacts()) {
            execute(connection, """
                    INSERT INTO patients.emergency_contact(emergency_contact_id,patient_id,name,relationship,phone)
                    VALUES (?,?,?,?,?)
                    """, UUID.randomUUID(), patient.id(), contact.name(), contact.relationship(), contact.phone());
        }
        execute(connection, """
                INSERT INTO patients.patient_audit(audit_id,patient_id,actor_id,action,patient_version)
                VALUES (?,?,?,'REGISTERED',1)
                """, write.auditId(), patient.id(), write.actorId());
        execute(connection, """
                INSERT INTO patients.outbox_event(event_id,aggregate_id,event_type,occurred_at,payload,
                    correlation_id,causation_id,actor_user_id) VALUES (?,?,'PatientRegistered',?,
                    jsonb_build_object('patientId',?::text,'status','ACTIVE','registeredAt',?::text,
                    'registeredBy',?::text),?,?,?)
                """, write.eventId(), patient.id(), time, patient.id(), write.registeredAt().toString(),
                write.actorId(), write.correlationId(), write.causationId(), write.actorId());
        execute(connection, """
                INSERT INTO patients.idempotency_record(actor_kind,actor_subject,operation,target_resource,
                    idempotency_key,request_hash,response_status,response_body,response_location)
                VALUES ('STAFF',?,'register','/patients',?,?,201,?::jsonb,?)
                """, write.actorId().toString(), key, write.requestHash(), RegistrationSnapshot.encode(write),
                "/api/v1/patients/" + patient.id());
    }

    private static int execute(Connection connection, String text, Object... values) throws SQLException {
        try (var statement = connection.prepareStatement(text)) {
            statement.setQueryTimeout(5);
            for (int i = 0; i < values.length; i++) {
                statement.setObject(i + 1, values[i]);
            }
            statement.execute();
            return statement.getUpdateCount();
        }
    }
}
