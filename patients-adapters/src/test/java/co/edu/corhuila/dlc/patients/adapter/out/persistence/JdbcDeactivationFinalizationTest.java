package co.edu.corhuila.dlc.patients.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.*;
import co.edu.corhuila.dlc.patients.application.port.out.DeactivationIntentStore;
import co.edu.corhuila.dlc.patients.application.port.out.DeactivationFinalization;
import co.edu.corhuila.dlc.patients.domain.model.PatientDeactivation;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.postgresql.ds.PGSimpleDataSource;

@EnabledIfEnvironmentVariable(named="TEST_DATABASE_URL", matches=".+")
class JdbcDeactivationFinalizationTest {
    @Test void commitsAndReplaysOriginalResult() throws Exception {
        var db=new PGSimpleDataSource(); db.setURL(System.getenv("TEST_DATABASE_URL")); db.setUser(System.getenv("TEST_DATABASE_USER")); db.setPassword(System.getenv("TEST_DATABASE_PASSWORD"));
        UUID patient=UUID.randomUUID(), actor=UUID.randomUUID(), operation=UUID.randomUUID(), guard=UUID.randomUUID();
        try(var c=db.getConnection()) {
            assertTrue(c.getCatalog().matches("patients_api_test_[a-f0-9]+")); assertEquals("patients_app",c.getMetaData().getUserName());
            try(var s=c.prepareStatement("INSERT INTO patients.patient(patient_id,document_type,document_number,name,birth_date,phone,email,created_by,updated_by) VALUES (?,'CC',?,'Fixture',DATE '2000-01-01','000','fixture@example.invalid',?,?)")) {
                s.setObject(1,patient); s.setString(2,patient.toString()); s.setObject(3,actor); s.setObject(4,actor); s.executeUpdate();
            }
        }
        var store=new JdbcDeactivationIntentStore(db);
        store.begin(new DeactivationIntentStore.Intent(operation,patient,actor,"fixture-key",new PatientDeactivation.Request(PatientDeactivation.ReasonCode.OTHER,"private reason",1)));
        store.recordGuard(operation,1,guard,"fixture-token");
        var finalizer=new JdbcDeactivationFinalization(db);
        var request=new DeactivationFinalization.Commit(operation,actor,guard,"fixture-token",2,UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),Instant.now());
        var invalid=new DeactivationFinalization.Commit(operation,actor,guard,"wrong-token",2,UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),Instant.now());
        assertThrows(DeactivationFinalization.Unavailable.class,()->finalizer.commit(invalid));
        UUID existingAudit=UUID.randomUUID();
        try(var c=db.getConnection(); var s=c.prepareStatement("INSERT INTO patients.patient_audit(audit_id,patient_id,actor_id,action,patient_version) VALUES (?,?,?,'REGISTERED',1)")) {
            s.setObject(1,existingAudit); s.setObject(2,patient); s.setObject(3,actor); s.executeUpdate();
        }
        var rollback=new DeactivationFinalization.Commit(operation,actor,guard,"fixture-token",2,existingAudit,UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),Instant.now());
        assertThrows(DeactivationFinalization.Unavailable.class,()->finalizer.commit(rollback));
        try(var c=db.getConnection(); var s=c.prepareStatement("SELECT status,version FROM patients.patient WHERE patient_id=?")) { s.setObject(1,patient); try(var r=s.executeQuery()){assertTrue(r.next()); assertEquals("ACTIVE",r.getString(1)); assertEquals(1,r.getInt(2));} }
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        co.edu.corhuila.dlc.patients.application.model.PatientReadResult.Administrative first;
        try {
            var start=new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<co.edu.corhuila.dlc.patients.application.model.PatientReadResult.Administrative> task=()-> { start.await(); return finalizer.commit(request); };
            var a=pool.submit(task); var b=pool.submit(task); start.countDown(); first=a.get(); assertEquals(first,b.get());
        } finally { pool.shutdownNow(); }
        assertEquals("INACTIVE",first.status().name()); assertEquals(2,first.version());
        assertEquals(first,finalizer.commit(request));
        try(var c=db.getConnection(); var s=c.prepareStatement("SELECT outcome,(SELECT count(*) FROM patients.patient_audit WHERE audit_id=? AND action='DEACTIVATED'),(SELECT payload::text FROM patients.outbox_event WHERE event_id=?) FROM patients.deactivation_operation WHERE operation_id=?")) {
            s.setObject(1,request.auditId()); s.setObject(2,request.eventId()); s.setObject(3,operation);
            try(var r=s.executeQuery()) { assertTrue(r.next()); assertEquals("FINALIZED",r.getString(1)); assertEquals(1,r.getInt(2)); assertFalse(r.getString(3).contains("private reason")); }
        }
        var differentActor=new DeactivationFinalization.Commit(operation,UUID.randomUUID(),guard,"fixture-token",2,UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),Instant.now());
        assertThrows(DeactivationFinalization.Unavailable.class,()->finalizer.commit(differentActor));
    }
}
