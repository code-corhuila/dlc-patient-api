package co.edu.corhuila.dlc.patients.application.model;

public final class PatientReadUnavailableException extends IllegalStateException {
    public PatientReadUnavailableException() {
        super("Patient read is temporarily unavailable");
    }
}
