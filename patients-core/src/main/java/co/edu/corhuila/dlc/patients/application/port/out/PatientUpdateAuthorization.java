package co.edu.corhuila.dlc.patients.application.port.out;

import java.util.UUID;

/** Require a current staff session and update permission; deny Dentist. */
@FunctionalInterface
public interface PatientUpdateAuthorization { UUID requireAuthorizedActor(); }
