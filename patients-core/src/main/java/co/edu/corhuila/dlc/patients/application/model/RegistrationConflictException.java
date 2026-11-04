package co.edu.corhuila.dlc.patients.application.model;

public final class RegistrationConflictException extends RuntimeException {
    public enum Reason {
        PATIENT_DOCUMENT_EXISTS,
        IDEMPOTENCY_CONFLICT
    }

    private final Reason reason;

    public RegistrationConflictException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
