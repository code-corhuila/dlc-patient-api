package co.edu.corhuila.dlc.patients.app.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

public final class InternalListenerFilter extends OncePerRequestFilter {
    private final int internalPort;
    public InternalListenerFilter(int internalPort) { this.internalPort = internalPort; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getServletPath();
        boolean internal = path.equals("/internal") || path.startsWith("/internal/") || path.startsWith("/internal;");
        if (internal != (internalPort > 0 && request.getLocalPort() == internalPort)) {
            response.sendError(404); return;
        }
        chain.doFilter(request, response);
    }
}
