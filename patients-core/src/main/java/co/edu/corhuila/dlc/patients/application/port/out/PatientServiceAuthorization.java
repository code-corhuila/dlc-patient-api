package co.edu.corhuila.dlc.patients.application.port.out;

public interface PatientServiceAuthorization {
    void requireValidationScope();

    /** `patients:billing-contact:read`; denied unless the implementation checks it. */
    default void requireBillingContactScope() {
        throw new co.edu.corhuila.dlc.patients.application.model.AuthorizationFailure(
                co.edu.corhuila.dlc.patients.application.model.AuthorizationFailure.Reason.FORBIDDEN);
    }
}
