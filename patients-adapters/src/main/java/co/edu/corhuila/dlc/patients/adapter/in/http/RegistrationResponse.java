package co.edu.corhuila.dlc.patients.adapter.in.http;

import co.edu.corhuila.dlc.patients.application.model.RegistrationResult;
import co.edu.corhuila.dlc.patients.domain.model.EmergencyContact;
import co.edu.corhuila.dlc.patients.domain.model.PatientStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
record RegistrationResponse(UUID id, String documentType, String documentNumber, String name, String birthDate,
        String phone, String email, String address, List<EmergencyContact> emergencyContacts, PatientStatus status,
        String createdAt, String updatedAt, int version) {
    static RegistrationResponse from(RegistrationResult result) {
        var patient = result.patient();
        var profile = patient.profile();
        return new RegistrationResponse(patient.id(), patient.document().type(), patient.document().number(),
                profile.name(), profile.birthDate().toString(), profile.phone(), profile.email(), profile.address(),
                profile.emergencyContacts(), patient.status(), result.registeredAt().toString(),
                result.registeredAt().toString(), patient.version());
    }
}
