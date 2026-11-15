package co.edu.corhuila.dlc.patients.domain.model;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

/** Registration aggregate; IDs and the clock are supplied by the trusted application boundary. */
public final class Patient {
    private final UUID id;
    private final Document document;
    private final PatientProfile profile;
    private final PatientStatus status;
    private final int version;

    private Patient(UUID id, Document document, PatientProfile profile) {
        this.id = id;
        this.document = document;
        this.profile = profile;
        this.status = PatientStatus.ACTIVE;
        this.version = 1;
    }

    public static Patient register(UUID id, Document document, PatientProfile profile, Clock clock) {
        PatientFields.required("id", id);
        PatientFields.required("document", document);
        PatientFields.required("profile", profile);
        PatientFields.required("clock", clock);
        if (!profile.birthDate().isBefore(LocalDate.now(clock))) {
            throw new InvalidPatientDataException("birthDate", "must be a past date");
        }
        return new Patient(id, document, profile);
    }

    /** Rehydrate a committed registration without reinterpreting its original date validation. */
    public static Patient restoreRegistration(UUID id, Document document, PatientProfile profile) {
        PatientFields.required("id", id);
        PatientFields.required("document", document);
        PatientFields.required("profile", profile);
        return new Patient(id, document, profile);
    }

    public UUID id() {
        return id;
    }

    public Document document() {
        return document;
    }

    public PatientProfile profile() {
        return profile;
    }

    public PatientStatus status() {
        return status;
    }

    public int version() {
        return version;
    }
}
