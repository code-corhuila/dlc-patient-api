package co.edu.corhuila.dlc.patients.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.*;
import co.edu.corhuila.dlc.patients.application.port.out.DeactivationIntentStore;
import co.edu.corhuila.dlc.patients.domain.model.PatientDeactivation;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.postgresql.ds.PGSimpleDataSource;

@EnabledIfEnvironmentVariable(named="TEST_DATABASE_URL", matches=".+")
class JdbcDeactivationIntentStoreTest {
    @Test void persistsIdempotentIntentAndRotatesGuardProof() throws Exception {
        var db = new PGSimpleDataSource(); db.setURL(System.getenv("TEST_DATABASE_URL"));
        db.setUser(System.getenv("TEST_DATABASE_USER")); db.setPassword(System.getenv("TEST_DATABASE_PASSWORD"));
        UUID patient=UUID.randomUUID(), actor=UUID.randomUUID(), operation=UUID.randomUUID(), guard=UUID.randomUUID();
        try(var c=db.getConnection()) {
            assertTrue(c.getCatalog().matches("patients_api_test_[a-f0-9]+")); assertEquals("patients_app",c.getMetaData().getUserName());
            try(var s=c.prepareStatement("INSERT INTO patients.patient(patient_id,document_type,document_number,name,birth_date,phone,email,created_by,updated_by) VALUES (?,'CC',?,'Fixture',DATE '2000-01-01','000','fixture@example.invalid',?,?)")) {
                s.setObject(1,patient); s.setString(2,patient.toString()); s.setObject(3,actor); s.setObject(4,actor); s.executeUpdate();
            }
        }
        var store=new JdbcDeactivationIntentStore(db);
        var intent=new DeactivationIntentStore.Intent(operation,patient,actor,"fixture-key",new PatientDeactivation.Request(PatientDeactivation.ReasonCode.OTHER,"reason",1));
        assertEquals(operation,store.begin(intent).operationId());
        var replay=new DeactivationIntentStore.Intent(UUID.randomUUID(),patient,actor,"fixture-key",intent.request());
        assertEquals(operation,store.begin(replay).operationId());
        var changed=new DeactivationIntentStore.Intent(UUID.randomUUID(),patient,actor,"fixture-key",new PatientDeactivation.Request(PatientDeactivation.ReasonCode.OTHER,"changed",1));
        assertThrows(DeactivationIntentStore.Conflict.class,()->store.begin(changed));
        var before=store.find(operation).orElseThrow(); assertEquals(1,before.operationVersion());
        store.recordGuard(operation,1,guard,"token-one"); store.recordGuard(operation,2,guard,"token-two");
        assertEquals(3,store.find(operation).orElseThrow().operationVersion());
        assertThrows(DeactivationIntentStore.GuardRace.class,()->store.recordGuard(operation,1,guard,"stale"));
        assertThrows(DeactivationIntentStore.GuardRace.class,()->store.recordGuard(operation,3,UUID.randomUUID(),"other"));
        assertThrows(DeactivationIntentStore.GuardRace.class,()->store.recordGuard(UUID.randomUUID(),1,guard,"other"));
        assertTrue(store.find(UUID.randomUUID()).isEmpty()); assertThrows(IllegalArgumentException.class,()->store.pending(0));
        try(var c=db.getConnection(); var s=c.prepareStatement("SELECT outcome,guard_token_hash FROM patients.deactivation_operation WHERE operation_id=?")) {
            s.setObject(1,operation); try(var r=s.executeQuery()){assertTrue(r.next()); assertEquals("PENDING",r.getString(1)); assertEquals(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest("token-two".getBytes(java.nio.charset.StandardCharsets.UTF_8))),r.getString(2));}
        }
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Boolean> rotate=()-> { try { store.recordGuard(operation,3,guard,UUID.randomUUID().toString()); return true; } catch(DeactivationIntentStore.GuardRace race) { return false; } };
            var a=pool.submit(rotate); var b=pool.submit(rotate); assertNotEquals(a.get(),b.get());
            assertEquals(4,store.find(operation).orElseThrow().operationVersion());
        } finally { pool.shutdownNow(); }
    }
    @Test void concurrentRetriesResolveToOneDurableOperation() throws Exception {
        var db=new PGSimpleDataSource(); db.setURL(System.getenv("TEST_DATABASE_URL")); db.setUser(System.getenv("TEST_DATABASE_USER")); db.setPassword(System.getenv("TEST_DATABASE_PASSWORD"));
        UUID patient=UUID.randomUUID(), actor=UUID.randomUUID();
        try(var c=db.getConnection()) {
            assertTrue(c.getCatalog().matches("patients_api_test_[a-f0-9]+")); assertEquals("patients_app",c.getMetaData().getUserName());
            try(var s=c.prepareStatement("INSERT INTO patients.patient(patient_id,document_type,document_number,name,birth_date,phone,email,created_by,updated_by) VALUES (?,'CC',?,'Fixture',DATE '2000-01-01','000','fixture@example.invalid',?,?)")) {
                s.setObject(1,patient); s.setString(2,patient.toString()); s.setObject(3,actor); s.setObject(4,actor); s.executeUpdate();
            }
        }
        var store=new JdbcDeactivationIntentStore(db);
        var request=new PatientDeactivation.Request(PatientDeactivation.ReasonCode.OTHER,"reason",1);
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<UUID> task=()->store.begin(new DeactivationIntentStore.Intent(UUID.randomUUID(),patient,actor,"same-key",request)).operationId();
            var first=pool.submit(task); var second=pool.submit(task); assertEquals(first.get(),second.get());
        } finally { pool.shutdownNow(); }
    }
}
