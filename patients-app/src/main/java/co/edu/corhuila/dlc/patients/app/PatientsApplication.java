package co.edu.corhuila.dlc.patients.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Composition root (Norma 5.3.2): the only place that knows every concrete type. */
@SpringBootApplication(scanBasePackages = "co.edu.corhuila.dlc.patients")
public class PatientsApplication {

    public static void main(String[] args) {
        SpringApplication.run(PatientsApplication.class, args);
    }
}
