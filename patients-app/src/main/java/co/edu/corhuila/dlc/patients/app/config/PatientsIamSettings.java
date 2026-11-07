package co.edu.corhuila.dlc.patients.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("patients.iam")
public record PatientsIamSettings(String baseUrl, String environment, String issuer, String audience,
        String registrationPermission, String searchPermission, String updatePermission, String serviceTokenFile) {
}
