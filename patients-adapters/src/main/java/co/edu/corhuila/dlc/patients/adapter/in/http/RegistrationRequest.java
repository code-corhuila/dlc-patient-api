package co.edu.corhuila.dlc.patients.adapter.in.http;

import co.edu.corhuila.dlc.patients.application.port.in.RegisterPatientUseCase.Command;
import co.edu.corhuila.dlc.patients.domain.model.Document;
import co.edu.corhuila.dlc.patients.domain.model.EmergencyContact;
import co.edu.corhuila.dlc.patients.domain.model.InvalidPatientDataException;
import co.edu.corhuila.dlc.patients.domain.model.PatientProfile;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

final class RegistrationRequest {
    private static final Set<String> FIELDS = Set.of("documentType", "documentNumber", "name", "birthDate",
            "phone", "email", "address", "emergencyContacts");
    private static final Set<String> CONTACT = Set.of("name", "relationship", "phone");

    private RegistrationRequest() { }

    static Command command(JsonNode body, String key, UUID correlationId, UUID causationId) {
        closedObject(body, FIELDS);
        String birthDate = text(body, "birthDate", true);
        if (!birthDate.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw invalid("birthDate");
        var contacts = new ArrayList<EmergencyContact>();
        if (body.has("emergencyContacts")) {
            var array = body.get("emergencyContacts");
            if (!array.isArray()) throw invalid("emergencyContacts");
            for (var contact : array) {
                closedObject(contact, CONTACT);
                contacts.add(new EmergencyContact(text(contact, "name", true), text(contact, "relationship", true),
                        text(contact, "phone", true)));
            }
        }
        return new Command(new Document(text(body, "documentType", true), text(body, "documentNumber", true)),
                new PatientProfile(text(body, "name", true), LocalDate.parse(birthDate), text(body, "phone", true),
                        text(body, "email", true), text(body, "address", false), contacts), key, correlationId, causationId);
    }

    private static void closedObject(JsonNode node, Set<String> allowed) {
        if (node == null || !node.isObject()) throw invalid("body");
        node.fieldNames().forEachRemaining(field -> {
            if (!allowed.contains(field)) throw invalid("body");
        });
    }

    private static String text(JsonNode node, String field, boolean required) {
        if (!required && !node.has(field)) return null;
        var value = node.get(field);
        if (value == null || !value.isTextual()) throw invalid(field);
        return value.textValue();
    }

    private static InvalidPatientDataException invalid(String field) {
        return new InvalidPatientDataException(field, "Invalid input data");
    }
}
