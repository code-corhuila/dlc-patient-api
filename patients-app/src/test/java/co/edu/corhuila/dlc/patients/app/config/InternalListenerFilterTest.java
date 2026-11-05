package co.edu.corhuila.dlc.patients.app.config;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class InternalListenerFilterTest {
    @Test void separatesPublicAndPrivateRoutes() throws Exception {
        var filter = new InternalListenerFilter(9091);
        var publicRequest = new MockHttpServletRequest("GET", "/internal/v1/patients/id/validation");
        publicRequest.setLocalPort(8080); publicRequest.setServletPath("/internal/v1/patients/id/validation");
        var response = new MockHttpServletResponse();
        filter.doFilter(publicRequest, response, new MockFilterChain());
        assertEquals(404, response.getStatus());
        var internal = new MockHttpServletRequest("GET", "/api/v1/patients");
        internal.setLocalPort(9091); internal.setServletPath("/api/v1/patients");
        response = new MockHttpServletResponse();
        filter.doFilter(internal, response, new MockFilterChain());
        assertEquals(404, response.getStatus());
        var allowed = new MockHttpServletRequest("GET", "/internal/v1/patients/id/validation");
        allowed.setLocalPort(9091); allowed.setServletPath("/internal/v1/patients/id/validation");
        var chain = new MockFilterChain();
        filter.doFilter(allowed, new MockHttpServletResponse(), chain);
        assertNotNull(chain.getRequest());
    }
}
