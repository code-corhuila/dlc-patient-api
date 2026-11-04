package co.edu.corhuila.dlc.patients.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("patients.appointments")
public record PatientsAppointmentsSettings(String baseUrl, String serviceTokenFile) { }
