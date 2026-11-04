package co.edu.corhuila.dlc.patients.adapter.in.http;

import co.edu.corhuila.dlc.patients.application.model.PatientSearchPage;
import co.edu.corhuila.dlc.patients.application.model.PatientSearchPage.Care;
import co.edu.corhuila.dlc.patients.application.model.PatientSearchPage.Item;
import co.edu.corhuila.dlc.patients.application.model.PatientSearchPage.Row;
import co.edu.corhuila.dlc.patients.domain.model.EmergencyContact;
import co.edu.corhuila.dlc.patients.domain.model.PatientStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
record PatientSearchResponse(List<View> data, Meta meta) {
    static PatientSearchResponse from(PatientSearchPage page) {
        return new PatientSearchResponse(page.data().stream().map(View::from).toList(),
                new Meta(page.meta().page(), page.meta().limit(), page.meta().total(), page.meta().totalPages()));
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record View(UUID id, String documentType, String documentNumber, String name, String birthDate, String phone,
            String email, String address, List<EmergencyContact> emergencyContacts, PatientStatus status,
            String createdAt, String updatedAt, Integer version) {
        static View from(Item item) {
            if (item instanceof Care care) return new View(care.id(), null, null, care.name(), null, care.phone(),
                    care.email(), null, null, care.status(), null, null, care.version());
            Row row = (Row) item;
            var profile = row.profile();
            return new View(row.id(), row.document().type(), row.document().number(), profile.name(), profile.birthDate().toString(),
                    profile.phone(), profile.email(), profile.address(), profile.emergencyContacts(), row.status(),
                    row.createdAt().toString(), row.updatedAt().toString(), row.version());
        }
    }

    record Meta(int page, int limit, long total, long totalPages) { }
}
