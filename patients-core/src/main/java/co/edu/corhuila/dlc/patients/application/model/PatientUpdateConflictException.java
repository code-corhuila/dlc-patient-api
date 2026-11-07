package co.edu.corhuila.dlc.patients.application.model;

public final class PatientUpdateConflictException extends RuntimeException {
    public enum Reason { VERSION_CONFLICT, PATIENT_DOCUMENT_EXISTS }
    private final Reason reason;
    public PatientUpdateConflictException(Reason reason) { super(reason.name()); this.reason = reason; }
    public Reason reason() { return reason; }
}
