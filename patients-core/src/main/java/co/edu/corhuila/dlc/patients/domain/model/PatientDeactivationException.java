package co.edu.corhuila.dlc.patients.domain.model;

public final class PatientDeactivationException extends RuntimeException {
    public enum Reason { STALE_VERSION, INVALID_STATUS_TRANSITION }
    private final Reason reason;
    public PatientDeactivationException(Reason reason) { super(reason.name()); this.reason = reason; }
    public Reason reason() { return reason; }
}
