package co.edu.corhuila.dlc.patients.domain.model;

import java.util.Objects;

/** Local eligibility only: no decision here authorizes deactivation without a durable guard. */
public final class PatientDeactivation {
    public enum ReasonCode { ADMINISTRATIVE, DUPLICATE_RECORD, PATIENT_REQUEST, OTHER }
    private PatientDeactivation() { }

    public record Request(ReasonCode reasonCode, String reason, int expectedVersion) {
        public Request {
            PatientFields.required("reasonCode", reasonCode);
            PatientFields.text("reason", reason, 1000);
            if (expectedVersion < 1) throw new InvalidPatientDataException("expectedVersion", "must be positive");
        }
        @Override public String toString() { return "Request[reasonCode=" + reasonCode + ", reason=[REDACTED], expectedVersion=" + expectedVersion + "]"; }
    }

    public static void requireEligible(Request request, PatientStatus currentStatus, int currentVersion) {
        Objects.requireNonNull(request); Objects.requireNonNull(currentStatus);
        if (currentVersion < 1) throw new IllegalArgumentException("Invalid stored version");
        if (currentStatus != PatientStatus.ACTIVE) throw new PatientDeactivationException(PatientDeactivationException.Reason.INVALID_STATUS_TRANSITION);
        if (request.expectedVersion() != currentVersion) throw new PatientDeactivationException(PatientDeactivationException.Reason.STALE_VERSION);
    }
}
