package co.edu.corhuila.dlc.patients.application.model;

public final class AuthorizationFailure extends RuntimeException {
    public enum Reason { UNAUTHORIZED, FORBIDDEN, SERVICE_UNAVAILABLE }
    private final Reason reason;

    public AuthorizationFailure(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
