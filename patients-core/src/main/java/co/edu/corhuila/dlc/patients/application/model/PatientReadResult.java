package co.edu.corhuila.dlc.patients.application.model;

import co.edu.corhuila.dlc.patients.domain.model.Document;
import co.edu.corhuila.dlc.patients.domain.model.EmergencyContact;
import co.edu.corhuila.dlc.patients.domain.model.PatientProfile;
import co.edu.corhuila.dlc.patients.domain.model.PatientStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public sealed interface PatientReadResult permits PatientReadResult.Administrative, PatientReadResult.Care {
    UUID id();
    PatientStatus status();
    int version();

    record Administrative(UUID id, Document document, PatientProfile profile, PatientStatus status,
            Instant createdAt, Instant updatedAt, int version) implements PatientReadResult { }

    record Care(UUID id, String name, String phone, String email, PatientStatus status, int version)
            implements PatientReadResult { }
}
