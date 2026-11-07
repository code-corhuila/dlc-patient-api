package co.edu.corhuila.dlc.patients.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("patients.database")
public record PatientsDatabaseSettings(String environment, String host, int port, String name,
        String passwordFile, String sslMode, String sslRootCert) {
}
