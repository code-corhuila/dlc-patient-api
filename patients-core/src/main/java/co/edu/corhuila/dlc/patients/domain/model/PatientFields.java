package co.edu.corhuila.dlc.patients.domain.model;

final class PatientFields {
    private PatientFields() {
    }

    static void required(String field, Object value) {
        if (value == null) {
            throw new InvalidPatientDataException(field, "is required");
        }
    }

    static void text(String field, String value, int maximum) {
        required(field, value);
        if (value.isBlank()) {
            throw new InvalidPatientDataException(field, "must not be blank");
        }
        // OpenAPI lengths and PostgreSQL char_length count Unicode code points.
        if (value.codePointCount(0, value.length()) > maximum) {
            throw new InvalidPatientDataException(field, "must have at most " + maximum + " characters");
        }
    }
}
