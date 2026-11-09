package co.edu.corhuila.dlc.patients.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** ADR-020: with a secret file, outbound service tokens are issued by IAM instead of read from token files. */
@ConfigurationProperties("patients.service-client")
public record PatientsServiceClientSettings(String id, String secretFile) {
    boolean issuesTokens() {
        return secretFile != null && !secretFile.isBlank();
    }
}
