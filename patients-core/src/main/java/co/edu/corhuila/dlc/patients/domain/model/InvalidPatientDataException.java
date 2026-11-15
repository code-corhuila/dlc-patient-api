package co.edu.corhuila.dlc.patients.domain.model;

public final class InvalidPatientDataException extends IllegalArgumentException {
    private final String field;

    public InvalidPatientDataException(String field, String rule) {
        super(field + ": " + rule);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
