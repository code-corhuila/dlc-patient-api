package co.edu.corhuila.dlc.patients.domain.model;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Null means omitted; provided values are validated against the complete resulting profile. */
public record PatientProfilePatch(String documentType, String documentNumber, String name, LocalDate birthDate,
        String phone, String email, String address, List<EmergencyContact> emergencyContacts) {
    public PatientProfilePatch {
        if (documentType == null && documentNumber == null && name == null && birthDate == null && phone == null
                && email == null && address == null && emergencyContacts == null) throw new InvalidPatientDataException("patch", "must include a field");
        if (emergencyContacts != null) emergencyContacts = List.copyOf(emergencyContacts);
    }

    public Updated apply(Document document, PatientProfile profile, Clock clock) {
        Objects.requireNonNull(document); Objects.requireNonNull(profile); Objects.requireNonNull(clock);
        var nextDocument = new Document(documentType == null ? document.type() : documentType,
                documentNumber == null ? document.number() : documentNumber);
        var nextProfile = new PatientProfile(name == null ? profile.name() : name,
                birthDate == null ? profile.birthDate() : birthDate, phone == null ? profile.phone() : phone,
                email == null ? profile.email() : email, address == null ? profile.address() : address,
                emergencyContacts == null ? profile.emergencyContacts() : emergencyContacts);
        if (birthDate != null && !birthDate.isBefore(LocalDate.now(clock))) throw new InvalidPatientDataException("birthDate", "must be a past date");
        if (nextDocument.equals(document) && nextProfile.equals(profile)) throw new InvalidPatientDataException("patch", "must change a field");
        return new Updated(nextDocument, nextProfile);
    }

    public record Updated(Document document, PatientProfile profile) { }
}
