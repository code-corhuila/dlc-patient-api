package co.edu.corhuila.dlc.patients.application.usecase;

/** Conservative decision from authoritative durable local evidence, not a guard acquisition or resolution. */
public final class DeactivationRecoveryPolicy {
    public enum Outcome { PENDING, FINALIZED, ABORTED, UNKNOWN }
    public enum PatientState { ACTIVE, INACTIVE, UNKNOWN }
    /** MATCHED means the patient state and durable operation refer to the same committed version. */
    public enum Proof { MATCHED, UNKNOWN }
    public enum Action { FINALIZE, ABORT, RETRY }
    private DeactivationRecoveryPolicy() { }

    public static Action decide(Outcome outcome, PatientState patientState, Proof proof) {
        if (proof != Proof.MATCHED) return Action.RETRY;
        if (outcome == Outcome.FINALIZED && patientState == PatientState.INACTIVE) return Action.FINALIZE;
        if (outcome == Outcome.ABORTED && patientState == PatientState.ACTIVE) return Action.ABORT;
        return Action.RETRY;
    }
}
