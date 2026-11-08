package co.edu.corhuila.dlc.patients.application.port.in;

import java.util.Objects;
import java.util.UUID;

/** Minimum invoice recipient for authorized Billing and notification services (HU-BIL-002/003). */
public interface ReadBillingContactUseCase {
    Contact read(UUID patientId);

    record Contact(UUID patientId, String email, String displayName) {
        public Contact {
            Objects.requireNonNull(patientId);
            if (email == null || email.isBlank() || displayName == null || displayName.isBlank()) {
                throw new IllegalArgumentException("Incomplete billing contact");
            }
        }
    }
}
