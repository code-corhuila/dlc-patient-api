package co.edu.corhuila.dlc.patients.domain.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public record PatientProfile(String name, LocalDate birthDate, String phone, String email,
        String address, List<EmergencyContact> emergencyContacts) {
    private static final Pattern EMAIL = Pattern.compile("[^\\s@]+@[^\\s@]+\\.[^\\s@]+",
            Pattern.UNICODE_CHARACTER_CLASS);

    public PatientProfile {
        PatientFields.text("name", name, 255);
        PatientFields.required("birthDate", birthDate);
        PatientFields.text("phone", phone, 40);
        PatientFields.text("email", email, 255);
        if (!EMAIL.matcher(email).matches()) {
            throw new InvalidPatientDataException("email", "must have local@domain.suffix format");
        }
        if (address != null) {
            PatientFields.text("address", address, 500);
        }
        if (emergencyContacts == null) {
            emergencyContacts = List.of();
        }
        if (emergencyContacts.stream().anyMatch(Objects::isNull)) {
            throw new InvalidPatientDataException("emergencyContacts", "must not contain null elements");
        }
        emergencyContacts = List.copyOf(emergencyContacts);
    }
}
