package co.edu.corhuila.dlc.patients.application.model;

/** A storage dependency failure, distinct from an unexpected application error. */
public final class RegistrationUnavailableException extends IllegalStateException {
    public RegistrationUnavailableException(String message) {
        super(message);
    }
}
