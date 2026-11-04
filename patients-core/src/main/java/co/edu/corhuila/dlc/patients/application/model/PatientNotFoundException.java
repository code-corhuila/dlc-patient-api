package co.edu.corhuila.dlc.patients.application.model;

public final class PatientNotFoundException extends RuntimeException {
    public PatientNotFoundException() {
        super("Patient not found");
    }
}
