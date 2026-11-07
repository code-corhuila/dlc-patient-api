package co.edu.corhuila.dlc.patients.app.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;

/** The private listener stays on loopback by default and can bind a private network address when configured. */
class PatientsInternalListenerAddressTest {
    private final PatientsInternalConfiguration configuration = new PatientsInternalConfiguration();

    @Test
    void bindsLoopbackByDefault() {
        assertEquals("127.0.0.1", boundAddress("127.0.0.1"));
    }

    @Test
    void bindsTheConfiguredPrivateAddress() {
        assertEquals("0.0.0.0", boundAddress("0.0.0.0"));
    }

    @Test
    void rejectsABlankAddress() {
        assertThrows(IllegalStateException.class, () -> configuration.privateConnector(9091, 8080, " "));
    }

    private String boundAddress(String address) {
        var factory = new TomcatServletWebServerFactory();
        configuration.privateConnector(9091, 8080, address).customize(factory);
        var connector = factory.getAdditionalTomcatConnectors().get(0);
        assertEquals(9091, connector.getPort());
        return ((java.net.InetAddress) connector.getProperty("address")).getHostAddress();
    }
}
