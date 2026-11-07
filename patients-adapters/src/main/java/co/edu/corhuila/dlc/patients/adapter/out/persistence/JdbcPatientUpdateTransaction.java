package co.edu.corhuila.dlc.patients.adapter.out.persistence;

import co.edu.corhuila.dlc.patients.application.model.*;
import co.edu.corhuila.dlc.patients.application.port.out.PatientUpdateTransaction;
import co.edu.corhuila.dlc.patients.domain.model.InvalidPatientDataException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;

public final class JdbcPatientUpdateTransaction implements PatientUpdateTransaction {
    private final DataSource source;
    public JdbcPatientUpdateTransaction(DataSource source) { this.source = Objects.requireNonNull(source); }

    @Override public PatientReadResult.Administrative update(Write write) {
        try (var connection = source.getConnection()) {
            connection.setAutoCommit(false);
            try {
                var result = perform(connection, write);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException failure) {
                try { connection.rollback(); } catch (SQLException rollback) { throw new PatientUpdateUnavailableException(); }
                if (failure instanceof PatientNotFoundException missing) throw missing;
                if (failure instanceof PatientUpdateConflictException conflict) throw conflict;
                if (failure instanceof InvalidPatientDataException invalid) throw invalid;
                if (failure instanceof org.postgresql.util.PSQLException sql && "23505".equals(sql.getSQLState())
                        && sql.getServerErrorMessage() != null && "uk_patient_document".equals(sql.getServerErrorMessage().getConstraint()))
                    throw new PatientUpdateConflictException(PatientUpdateConflictException.Reason.PATIENT_DOCUMENT_EXISTS);
                throw new PatientUpdateUnavailableException();
            }
        } catch (SQLException failure) { throw new PatientUpdateUnavailableException(); }
    }

    private PatientReadResult.Administrative perform(Connection connection, Write write) throws SQLException {
        PatientSearchPage.Row row;
        try (var statement = connection.prepareStatement("SELECT patient_id,document_type,document_number,name,birth_date,phone,email,address,status,created_at,updated_at,version FROM patients.patient WHERE patient_id=? FOR UPDATE")) {
            statement.setQueryTimeout(5); statement.setObject(1, write.patientId());
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) throw new PatientNotFoundException();
                row = JdbcPatientRowMapper.read(connection, rows);
            }
        }
        if (row.version() != write.expectedVersion()) throw new PatientUpdateConflictException(PatientUpdateConflictException.Reason.VERSION_CONFLICT);
        var updated = write.patch().apply(row.document(), row.profile(), Clock.fixed(write.updatedAt(), ZoneOffset.UTC));
        var doc = updated.document(); var profile = updated.profile(); var old = row.profile();
        var fields = new ArrayList<String>();
        if (!doc.type().equals(row.document().type())) fields.add("documentType");
        if (!doc.number().equals(row.document().number())) fields.add("documentNumber");
        if (!profile.name().equals(old.name())) fields.add("name");
        if (!profile.birthDate().equals(old.birthDate())) fields.add("birthDate");
        if (!profile.phone().equals(old.phone())) fields.add("phone");
        if (!profile.email().equals(old.email())) fields.add("email");
        if (!Objects.equals(profile.address(), old.address())) fields.add("address");
        if (!profile.emergencyContacts().equals(old.emergencyContacts())) fields.add("emergencyContacts");
        var timestamp = write.updatedAt().atOffset(ZoneOffset.UTC);
        execute(connection, "UPDATE patients.patient SET document_type=?,document_number=?,name=?,birth_date=?,phone=?,email=?,address=?,version=version+1,updated_at=?,updated_by=? WHERE patient_id=?", doc.type(), doc.number(), profile.name(), profile.birthDate(), profile.phone(), profile.email(), profile.address(), timestamp, write.actorId(), write.patientId());
        if (fields.contains("emergencyContacts")) {
            execute(connection, "DELETE FROM patients.emergency_contact WHERE patient_id=?", write.patientId());
            for (var contact : profile.emergencyContacts()) execute(connection, "INSERT INTO patients.emergency_contact(emergency_contact_id,patient_id,name,relationship,phone) VALUES (?,?,?,?,?)", UUID.randomUUID(), write.patientId(), contact.name(), contact.relationship(), contact.phone());
        }
        var array = connection.createArrayOf("text", fields.toArray());
        execute(connection, "INSERT INTO patients.patient_audit(audit_id,patient_id,actor_id,action,patient_version,changed_fields) VALUES (?,?,?,'PROFILE_UPDATED',?,?)", write.auditId(), write.patientId(), write.actorId(), row.version()+1, array);
        execute(connection, "INSERT INTO patients.outbox_event(event_id,aggregate_id,event_type,occurred_at,payload,correlation_id,causation_id,actor_user_id) VALUES (?,?,'PatientProfileUpdated',?,jsonb_build_object('patientId',?::text,'changedFields',to_jsonb(?::text[]),'updatedAt',?::text,'updatedBy',?::text),?,?,?)", write.eventId(), write.patientId(), timestamp, write.patientId(), array, write.updatedAt().toString(), write.actorId(), write.correlationId(), write.causationId(), write.actorId());
        return new PatientReadResult.Administrative(row.id(), doc, profile, row.status(), row.createdAt(), write.updatedAt(), row.version()+1);
    }

    private static void execute(Connection connection, String sql, Object... args) throws SQLException {
        try (var statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(5);
            for (int i=0; i<args.length; i++) statement.setObject(i+1, args[i]);
            statement.executeUpdate();
        }
    }
}
