package co.edu.corhuila.dlc.patients.application.model;

public final class PatientSearchUnavailableException extends IllegalStateException {
    public PatientSearchUnavailableException() {
        super("Patient search is temporarily unavailable");
    }
}
