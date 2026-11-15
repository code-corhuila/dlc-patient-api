package co.edu.corhuila.dlc.patients.adapter.in.http;

import co.edu.corhuila.dlc.patients.application.port.in.UpdatePatientUseCase.Command;
import co.edu.corhuila.dlc.patients.domain.model.EmergencyContact;
import co.edu.corhuila.dlc.patients.domain.model.PatientProfilePatch;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

final class PatientUpdateRequest {
    private static final Set<String> FIELDS = Set.of("expectedVersion", "documentType", "documentNumber", "name", "birthDate", "phone", "email", "address", "emergencyContacts");
    private static final Set<String> CONTACT = Set.of("name", "relationship", "phone");
    private PatientUpdateRequest() { }

    static Command command(JsonNode body, UUID id, UUID correlation, UUID causation) {
        closed(body, FIELDS);
        var version = body.get("expectedVersion");
        if (body.size() < 2 || version == null || !version.isIntegralNumber() || !version.canConvertToInt() || version.intValue() < 1) throw new IllegalArgumentException();
        String birth = text(body, "birthDate");
        if (birth != null && !birth.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw new IllegalArgumentException();
        ArrayList<EmergencyContact> contacts = null;
        if (body.has("emergencyContacts")) {
            if (!body.get("emergencyContacts").isArray()) throw new IllegalArgumentException();
            contacts = new ArrayList<>();
            for (var item : body.get("emergencyContacts")) {
                closed(item, CONTACT);
                contacts.add(new EmergencyContact(required(item, "name"), required(item, "relationship"), required(item, "phone")));
            }
        }
        var patch = new PatientProfilePatch(text(body,"documentType"), text(body,"documentNumber"), text(body,"name"),
                birth == null ? null : LocalDate.parse(birth), text(body,"phone"), text(body,"email"), text(body,"address"), contacts);
        return new Command(id, version.intValue(), patch, correlation, causation);
    }
    private static void closed(JsonNode body, Set<String> allowed) {
        if (body == null || !body.isObject()) throw new IllegalArgumentException();
        body.fieldNames().forEachRemaining(name -> { if (!allowed.contains(name)) throw new IllegalArgumentException(); });
    }
    private static String text(JsonNode body, String name) {
        if (!body.has(name)) return null;
        if (!body.get(name).isTextual()) throw new IllegalArgumentException();
        return body.get(name).textValue();
    }
    private static String required(JsonNode body, String name) {
        String value = text(body,name); if (value == null) throw new IllegalArgumentException(); return value;
    }
}
