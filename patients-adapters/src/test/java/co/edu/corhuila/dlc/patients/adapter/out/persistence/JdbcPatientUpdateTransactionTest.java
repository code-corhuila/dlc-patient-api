package co.edu.corhuila.dlc.patients.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.*;
import co.edu.corhuila.dlc.patients.application.model.PatientUpdateConflictException;
import co.edu.corhuila.dlc.patients.application.model.PatientUpdateUnavailableException;
import co.edu.corhuila.dlc.patients.application.port.out.PatientUpdateTransaction.Write;
import co.edu.corhuila.dlc.patients.domain.model.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.postgresql.ds.PGSimpleDataSource;

@EnabledIfEnvironmentVariable(named="TEST_DATABASE_URL", matches=".+")
class JdbcPatientUpdateTransactionTest {
    @Test void successConflictRollbackAndConcurrency() throws Exception {
        var db = new PGSimpleDataSource(); db.setURL(System.getenv("TEST_DATABASE_URL"));
        db.setUser(System.getenv("TEST_DATABASE_USER")); db.setPassword(System.getenv("TEST_DATABASE_PASSWORD"));
        UUID patient=UUID.randomUUID(), other=UUID.randomUUID(), actor=UUID.randomUUID(), correlation=UUID.randomUUID(), causation=UUID.randomUUID();
        try (var c=db.getConnection()) {
            assertTrue(c.getCatalog().matches("patients_api_test_[a-f0-9]+")); assertEquals("patients_app",c.getMetaData().getUserName());
            try (var s=c.prepareStatement("INSERT INTO patients.patient(patient_id,document_type,document_number,name,birth_date,phone,email,created_by,updated_by) VALUES (?,'CC',?,'Fixture',DATE '2000-01-01','000','test@example.invalid',?,?)")) {
                for(var id:List.of(patient,other)) { s.setObject(1,id); s.setString(2,id.toString()); s.setObject(3,actor); s.setObject(4,actor); s.executeUpdate(); }
            }
        }
        var tx=new JdbcPatientUpdateTransaction(db);
        var patch=new PatientProfilePatch(null,null,"Updated",null,null,null,null,List.of(new EmergencyContact("Test","Relative","111")));
        var w=new Write(patient,1,patch,actor,UUID.randomUUID(),UUID.randomUUID(),Instant.now(),correlation,causation);
        assertEquals(2,tx.update(w).version());
        try(var c=db.getConnection(); var s=c.prepareStatement("SELECT correlation_id,causation_id,(SELECT count(*) FROM patients.emergency_contact WHERE patient_id=?) FROM patients.outbox_event WHERE event_id=?")) {
            s.setObject(1,patient); s.setObject(2,w.eventId()); try(var rs=s.executeQuery()) { assertTrue(rs.next()); assertEquals(correlation,rs.getObject(1,UUID.class)); assertEquals(causation,rs.getObject(2,UUID.class)); assertEquals(1,rs.getInt(3)); }
        }
        var duplicate=new PatientProfilePatch(null,other.toString(),null,null,null,null,null,null);
        var conflict=assertThrows(PatientUpdateConflictException.class,()->tx.update(new Write(patient,2,duplicate,actor,UUID.randomUUID(),UUID.randomUUID(),Instant.now(),correlation,causation)));
        assertEquals(PatientUpdateConflictException.Reason.PATIENT_DOCUMENT_EXISTS,conflict.reason());
        var rollback=new Write(patient,2,new PatientProfilePatch(null,null,"Rollback",null,null,null,null,null),actor,UUID.randomUUID(),w.eventId(),Instant.now(),correlation,causation);
        assertThrows(PatientUpdateUnavailableException.class,()->tx.update(rollback));
        try(var c=db.getConnection(); var s=c.prepareStatement("SELECT name,version,(SELECT count(*) FROM patients.patient_audit WHERE patient_id=?) FROM patients.patient WHERE patient_id=?")) {
            s.setObject(1,patient); s.setObject(2,patient); try(var rs=s.executeQuery()) { assertTrue(rs.next()); assertEquals("Updated",rs.getString(1)); assertEquals(2,rs.getInt(2)); assertEquals  (1,rs.getInt(3)); }
        }
        var pool=Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> update=()-> { try { tx.update(new Write(patient,2,new PatientProfilePatch(null,null,UUID.randomUUID().toString(),null,null,null,null,null),actor,UUID.randomUUID(),UUID.randomUUID(),Instant.now(),correlation,causation)); return true; } catch(PatientUpdateConflictException e) { return false; } };
            var a=pool.submit(update); var b=pool.submit(update); assertNotEquals(a.get(),b.get());
        } finally { pool.shutdownNow(); }
    }
}
