package org.voror.auth.casdoor;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;

import java.io.IOException;
import java.security.Principal;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * TEST STAND-IN for the real Casdoor valve (same class name, so the webapp's real META-INF/context.xml
 * loads it in Tomcat11SmokeTest). Instead of validating a Casdoor token it takes the user from the
 * {@code X-Test-User} header and the roles from {@code X-Test-Roles}.
 *
 * <p>Like the real valve it reads {@code protected_paths} and {@code required_roles} from the webapp's
 * context-params (web.xml): no user on a protected path is a 401, a user with none of the required roles a 403.
 * Otherwise it wraps the request the way the real valve does.</p>
 */
public class CasdoorAuthenticatorValve extends ValveBase {

    @Override
    public void invoke(Request request, Response response) throws IOException, ServletException {
        var servletContext = request.getContext().getServletContext();
        Set<String> protectedPaths = split(servletContext.getInitParameter("protected_paths"));
        Set<String> requiredRoles = split(servletContext.getInitParameter("required_roles"));

        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (protectedPaths.stream().noneMatch(path::startsWith)) {
            getNext().invoke(request, response);
            return;
        }
        String user = request.getHeader("X-Test-User");
        if (user == null) {
            response.sendError(401, "Missing or invalid Authorization header");
            return;
        }
        Set<String> roles = split(request.getHeader("X-Test-Roles"));
        if (!requiredRoles.isEmpty() && roles.stream().noneMatch(requiredRoles::contains)) {
            response.sendError(403, "Access denied: insufficient roles");
            return;
        }
        Principal principal = () -> user;

        request.setRequest(new HttpServletRequestWrapper(request.getRequest()) {
            @Override
            public Principal getUserPrincipal() {
                return principal;
            }

            @Override
            public String getRemoteUser() {
                return user;
            }

            @Override
            public boolean isUserInRole(String role) {
                return roles.contains(role);
            }
        });
        getNext().invoke(request, response);
    }

    private static Set<String> split(String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
    }
}
