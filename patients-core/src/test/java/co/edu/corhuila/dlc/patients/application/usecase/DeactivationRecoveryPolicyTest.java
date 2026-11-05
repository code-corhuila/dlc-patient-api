package co.edu.corhuila.dlc.patients.application.usecase;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class DeactivationRecoveryPolicyTest {
    @Test void onlyDurableMatchingFactsPermitFinalization() {
        assertEquals(DeactivationRecoveryPolicy.Action.FINALIZE,
                DeactivationRecoveryPolicy.decide(DeactivationRecoveryPolicy.Outcome.FINALIZED, DeactivationRecoveryPolicy.PatientState.INACTIVE, DeactivationRecoveryPolicy.Proof.MATCHED));
        assertEquals(DeactivationRecoveryPolicy.Action.RETRY,
                DeactivationRecoveryPolicy.decide(DeactivationRecoveryPolicy.Outcome.FINALIZED, DeactivationRecoveryPolicy.PatientState.UNKNOWN, DeactivationRecoveryPolicy.Proof.UNKNOWN));
    }
    @Test void abortRequiresDurableAbortAndActivePatient() {
        assertEquals(DeactivationRecoveryPolicy.Action.ABORT,
                DeactivationRecoveryPolicy.decide(DeactivationRecoveryPolicy.Outcome.ABORTED, DeactivationRecoveryPolicy.PatientState.ACTIVE, DeactivationRecoveryPolicy.Proof.MATCHED));
        assertEquals(DeactivationRecoveryPolicy.Action.RETRY,
                DeactivationRecoveryPolicy.decide(DeactivationRecoveryPolicy.Outcome.PENDING, DeactivationRecoveryPolicy.PatientState.ACTIVE, DeactivationRecoveryPolicy.Proof.MATCHED));
    }
    @Test void inconsistentOrUnknownFactsNeverReleaseGuard() {
        for (var outcome : DeactivationRecoveryPolicy.Outcome.values())
            for (var state : DeactivationRecoveryPolicy.PatientState.values())
                assertEquals(DeactivationRecoveryPolicy.Action.RETRY, DeactivationRecoveryPolicy.decide(outcome,state,DeactivationRecoveryPolicy.Proof.UNKNOWN));
        assertEquals(DeactivationRecoveryPolicy.Action.RETRY,
                DeactivationRecoveryPolicy.decide(DeactivationRecoveryPolicy.Outcome.ABORTED, DeactivationRecoveryPolicy.PatientState.INACTIVE, DeactivationRecoveryPolicy.Proof.MATCHED));
    }
    @Test void nullEvidenceFailsClosed() {
        assertEquals(DeactivationRecoveryPolicy.Action.RETRY, DeactivationRecoveryPolicy.decide(null,null,null));
    }
}
