package co.edu.corhuila.dlc.patients.application.port.out;

import java.util.UUID;

@FunctionalInterface
public interface RegistrationAuthorization {
    /** Verify current identity/session and Administrator/Assistant permission; fail closed otherwise. */
    UUID requireAuthorizedActor();
}
