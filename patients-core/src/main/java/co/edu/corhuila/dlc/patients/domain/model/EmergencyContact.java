package co.edu.corhuila.dlc.patients.domain.model;

public record EmergencyContact(String name, String relationship, String phone) {
    public EmergencyContact {
        PatientFields.text("emergencyContacts.name", name, 255);
        PatientFields.text("emergencyContacts.relationship", relationship, 80);
        PatientFields.text("emergencyContacts.phone", phone, 40);
    }
}
