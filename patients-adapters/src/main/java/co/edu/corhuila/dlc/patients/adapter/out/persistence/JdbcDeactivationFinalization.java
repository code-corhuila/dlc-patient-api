package co.edu.corhuila.dlc.patients.adapter.out.persistence;

import co.edu.corhuila.dlc.patients.application.model.PatientReadResult;
import co.edu.corhuila.dlc.patients.application.model.PatientSearchPage;
import co.edu.corhuila.dlc.patients.application.port.out.DeactivationFinalization;
import co.edu.corhuila.dlc.patients.domain.model.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;

public final class JdbcDeactivationFinalization implements DeactivationFinalization {
    private final DataSource source;
    private final JsonMapper json = JsonMapper.builder().addModule(new JavaTimeModule()).build();
    public JdbcDeactivationFinalization(DataSource source) { this.source=Objects.requireNonNull(source); }

    @Override public PatientReadResult.Administrative commit(Commit request) {
        if (request == null || request.operationId()==null || request.actorId()==null || request.guardId()==null || request.token()==null || request.token().isBlank()
                || request.operationVersion()<1 || request.auditId()==null || request.eventId()==null || request.correlationId()==null || request.causationId()==null || request.occurredAt()==null) throw new IllegalArgumentException("Invalid finalization request");
        try(var c=source.getConnection()) {
            c.setAutoCommit(false);
            try { var result=execute(c,request); c.commit(); return result; }
            catch(Exception failure) {
                try { c.rollback(); } catch(SQLException rollback) { throw new Unavailable(); }
                if(failure instanceof PatientDeactivationException conflict) throw conflict;
                throw new Unavailable();
            }
        } catch(SQLException failure) { throw new Unavailable(); }
    }
    private PatientReadResult.Administrative execute(Connection c, Commit request) throws Exception {
        UUID patient, actor, key; int expected; String reason, code, outcome, guardHash; UUID guard; long version;
        try(var s=c.prepareStatement("SELECT patient_id,actor_id,idempotency_key,expected_version,reason,reason_code,outcome,guard_id,guard_token_hash,operation_version FROM patients.deactivation_operation WHERE operation_id=? FOR UPDATE")) {
            s.setQueryTimeout(5); s.setObject(1,request.operationId()); try(var r=s.executeQuery()) {
                if(!r.next()) throw new Unavailable();
                patient=r.getObject(1,UUID.class); actor=r.getObject(2,UUID.class); key=r.getObject(3,UUID.class); expected=r.getInt(4);
                reason=r.getString(5); code=r.getString(6); outcome=r.getString(7); guard=r.getObject(8,UUID.class); guardHash=r.getString(9); version=r.getLong(10);
            }
        }
        if (!actor.equals(request.actorId())) throw new Unavailable();
        String target="/patients/"+patient+"/deactivations";
        if("FINALIZED".equals(outcome)) {
            try(var s=c.prepareStatement("SELECT response_body::text FROM patients.idempotency_record WHERE actor_kind='STAFF' AND actor_subject=? AND operation='deactivate' AND target_resource=? AND idempotency_key=?")) {
                s.setQueryTimeout(5); s.setString(1,actor.toString()); s.setString(2,target); s.setObject(3,key);
                try(var r=s.executeQuery()) { if(!r.next()) throw new Unavailable(); return json.readValue(r.getString(1),PatientReadResult.Administrative.class); }
            }
        }
        if(!"PENDING".equals(outcome) || version!=request.operationVersion() || !request.guardId().equals(guard)
                || !MessageDigest.isEqual(hash(request.token()).getBytes(StandardCharsets.US_ASCII),Objects.requireNonNullElse(guardHash,"").getBytes(StandardCharsets.US_ASCII))) throw new Unavailable();
        PatientSearchPage.Row row;
        try(var s=c.prepareStatement("SELECT patient_id,document_type,document_number,name,birth_date,phone,email,address,status,created_at,updated_at,version FROM patients.patient WHERE patient_id=? FOR UPDATE")) {
            s.setQueryTimeout(5); s.setObject(1,patient); try(var r=s.executeQuery()) { if(!r.next()) throw new Unavailable(); row=JdbcPatientRowMapper.read(c,r); }
        }
        PatientDeactivation.requireEligible(new PatientDeactivation.Request(PatientDeactivation.ReasonCode.valueOf(code),reason,expected),row.status(),row.version());
        var timestamp=request.occurredAt().atOffset(ZoneOffset.UTC);
        exec(c,"UPDATE patients.patient SET status='INACTIVE',version=version+1,updated_at=?,updated_by=? WHERE patient_id=?",timestamp,actor,patient);
        exec(c,"INSERT INTO patients.patient_audit(audit_id,patient_id,actor_id,action,patient_version,reason) VALUES (?,?,?,'DEACTIVATED',?,?)",request.auditId(),patient,actor,expected+1,reason);
        exec(c,"INSERT INTO patients.outbox_event(event_id,aggregate_id,event_type,occurred_at,payload,correlation_id,causation_id,actor_user_id) VALUES (?,?,'PatientDeactivated',?,jsonb_build_object('patientId',?::text,'reasonCode',?::text,'deactivatedAt',?::text,'deactivatedBy',?::text),?,?,?)",request.eventId(),patient,timestamp,patient,code,request.occurredAt().toString(),actor,request.correlationId(),request.causationId(),actor);
        exec(c,"UPDATE patients.deactivation_operation SET outcome='FINALIZED' WHERE operation_id=?",request.operationId());
        PatientSearchPage.Row after;
        try(var s=c.prepareStatement("SELECT patient_id,document_type,document_number,name,birth_date,phone,email,address,status,created_at,updated_at,version FROM patients.patient WHERE patient_id=?")) {
            s.setQueryTimeout(5); s.setObject(1,patient); try(var r=s.executeQuery()) { if(!r.next()) throw new Unavailable(); after=JdbcPatientRowMapper.read(c,r); }
        }
        var result=new PatientReadResult.Administrative(after.id(),after.document(),after.profile(),after.status(),after.createdAt(),after.updatedAt(),after.version());
        String requestHash=hash(json.writeValueAsString(java.util.List.of(code,reason,expected)));
        exec(c,"INSERT INTO patients.idempotency_record(actor_kind,actor_subject,operation,target_resource,idempotency_key,request_hash,response_status,response_body) VALUES ('STAFF',?,'deactivate',?,?,?,200,?::jsonb)",actor.toString(),target,key,requestHash,json.writeValueAsString(result));
        return result;
    }
    private static void exec(Connection c,String sql,Object... args) throws SQLException {
        try(var s=c.prepareStatement(sql)) { s.setQueryTimeout(5); for(int i=0;i<args.length;i++) s.setObject(i+1,args[i]); if(s.executeUpdate()!=1) throw new SQLException("Unexpected write count"); }
    }
    private static String hash(String value) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
}
