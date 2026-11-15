package co.edu.corhuila.dlc.patients.application.model;

import co.edu.corhuila.dlc.patients.domain.model.Document;
import co.edu.corhuila.dlc.patients.domain.model.PatientProfile;
import co.edu.corhuila.dlc.patients.domain.model.PatientStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PatientSearchPage(List<Item> data, Meta meta) {
    public PatientSearchPage { data = List.copyOf(data); }
    public sealed interface Item permits Row, Care { }
    public record Row(UUID id, Document document, PatientProfile profile, PatientStatus status,
            Instant createdAt, Instant updatedAt, int version) implements Item { }
    public record Care(UUID id, String name, String phone, String email, PatientStatus status, int version) implements Item { }
    public record Meta(int page, int limit, long total, long totalPages) { }
}
