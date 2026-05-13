package com.jspider.spring_boot_simple_crud_with_mysql.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jspider.spring_boot_simple_crud_with_mysql.responses.ResponseStructure;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Spring Security {@link AuthenticationEntryPoint} implementation that produces a uniform
 * HTTP {@code 401 Unauthorized} JSON response whenever an unauthenticated request reaches
 * a protected resource within this application.
 *
 * <p>The Spring Security filter chain invokes {@link #commence(HttpServletRequest,
 * HttpServletResponse, AuthenticationException)} whenever any of the following occur:</p>
 * <ul>
 *   <li>A request to a protected URL arrives without an {@code Authorization} header.</li>
 *   <li>The {@code Authorization} header is present but malformed (e.g. missing the
 *       {@code Bearer } prefix).</li>
 *   <li>The supplied JWT is invalid, expired, signed with the wrong key, or tampered with;
 *       in this case the sibling {@code JwtAuthenticationFilter} leaves the
 *       {@code SecurityContextHolder} empty and the security filter chain then routes the
 *       request through this entry point.</li>
 * </ul>
 *
 * <p>The emitted response body matches the project-wide {@code ResponseStructure} envelope
 * so that clients can parse authentication failures using the same JSON shape they already
 * use for the existing CRUD endpoints.</p>
 */
@Component
// Rule Applied
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    /**
     * Emits a {@code 401 Unauthorized} JSON response describing the authentication failure.
     *
     * <p>This implementation deliberately writes the body itself (rather than calling
     * {@link HttpServletResponse#sendError(int)}) so that the JSON shape exactly matches the
     * {@code ResponseStructure} envelope used by every other endpoint in the application.</p>
     *
     * @param request       the inbound HTTP request that triggered the authentication failure
     * @param response      the outbound HTTP response onto which the 401 JSON body is written
     * @param authException the exception explaining why the request was rejected by Spring Security
     * @throws IOException      if the response body cannot be serialised or flushed
     * @throws ServletException declared to match the {@link AuthenticationEntryPoint} contract
     */
    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException)
            throws IOException, ServletException {
        System.out.println("[JwtAuthenticationEntryPoint] commence invoked for URI=" + request.getRequestURI()
                + ", reason=" + authException.getMessage());

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");

        ResponseStructure<String> body = new ResponseStructure<>();
        body.setStatusCode(401);
        body.setApiDescription("Unauthorized - invalid or missing JWT token");
        body.setData(authException.getMessage());

        new ObjectMapper().writeValue(response.getOutputStream(), body);
    }
}
