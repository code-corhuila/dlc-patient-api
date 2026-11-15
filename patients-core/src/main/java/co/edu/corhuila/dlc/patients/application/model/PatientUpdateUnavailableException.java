package co.edu.corhuila.dlc.patients.application.model;

public final class PatientUpdateUnavailableException extends IllegalStateException {
    public PatientUpdateUnavailableException() { super("Patient update is temporarily unavailable"); }
}
