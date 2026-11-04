package co.edu.corhuila.dlc.patients.adapter.out.persistence;

import co.edu.corhuila.dlc.patients.application.model.RegistrationResult;
import co.edu.corhuila.dlc.patients.application.port.out.RegistrationTransaction.Write;
import co.edu.corhuila.dlc.patients.domain.model.Document;
import co.edu.corhuila.dlc.patients.domain.model.EmergencyContact;
import co.edu.corhuila.dlc.patients.domain.model.Patient;
import co.edu.corhuila.dlc.patients.domain.model.PatientProfile;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

record RegistrationSnapshot(UUID id, String documentType, String documentNumber, String name,
        LocalDate birthDate, String phone, String email, String address, List<EmergencyContact> emergencyContacts,
        String status, int version, Instant createdAt, Instant updatedAt) {
    private static final JsonMapper JSON = JsonMapper.builder().addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .serializationInclusion(JsonInclude.Include.NON_NULL).build();

    static String encode(Write write) {
        var patient = write.patient();
        var profile = patient.profile();
        var snapshot = new RegistrationSnapshot(patient.id(), patient.document().type(), patient.document().number(),
                profile.name(), profile.birthDate(), profile.phone(), profile.email(), profile.address(),
                profile.emergencyContacts(), patient.status().name(), patient.version(), write.registeredAt(), write.registeredAt());
        try {
            return JSON.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Registration response could not be encoded");
        }
    }

    static RegistrationResult replay(String value) {
        try {
            var snapshot = JSON.readValue(value, RegistrationSnapshot.class);
            if (!"ACTIVE".equals(snapshot.status()) || snapshot.version() != 1) {
                throw new IllegalStateException("Stored registration has invalid initial state");
            }
            var profile = new PatientProfile(snapshot.name(), snapshot.birthDate(), snapshot.phone(),
                    snapshot.email(), snapshot.address(), snapshot.emergencyContacts());
            var patient = Patient.restoreRegistration(snapshot.id(),
                    new Document(snapshot.documentType(), snapshot.documentNumber()), profile);
            return new RegistrationResult(patient, snapshot.createdAt(), true);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored registration could not be decoded");
        }
    }
}
