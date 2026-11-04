package co.edu.corhuila.dlc.patients.application.port.out;

import java.util.Objects;
import java.util.UUID;

@FunctionalInterface
public interface PatientSearchAuthorization {
    /** Verify current JWT/session, eligible staff role AND operation permission; never infer permission from a role. */
    Access requireAuthorizedAccess();
    /** ADMINISTRATIVE is for Administrator/Assistant; CARE is the Dentist's restricted projection. */
    enum View { ADMINISTRATIVE, CARE }
    record Access(UUID actorId, View view) {
        public Access {
            Objects.requireNonNull(actorId);
            Objects.requireNonNull(view);
        }
    }
}
