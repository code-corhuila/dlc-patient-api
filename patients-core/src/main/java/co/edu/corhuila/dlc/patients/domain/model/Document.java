package co.edu.corhuila.dlc.patients.domain.model;

/** Display values; normalized uniqueness is enforced by the owning PostgreSQL index. */
public record Document(String type, String number) {
    public Document {
        PatientFields.text("documentType", type, 30);
        PatientFields.text("documentNumber", number, 50);
    }
}
