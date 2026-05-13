package com.jspider.spring_boot_simple_crud_with_mysql.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jspider.spring_boot_simple_crud_with_mysql.responses.ResponseStructure;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
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
 *   <li>Login credentials submitted to {@code POST /auth/login} fail
 *       {@code AuthenticationManager.authenticate(...)}; Spring Security's
 *       {@code ExceptionTranslationFilter} routes the
 *       {@code BadCredentialsException} through this entry point.</li>
 * </ul>
 *
 * <p>The emitted response body matches the project-wide {@code ResponseStructure} envelope
 * so that clients can parse authentication failures using the same JSON shape they already
 * use for the existing CRUD endpoints.</p>
 *
 * <p><strong>Dynamic {@code apiDescription} (QA CP3 Issue #4 resolution):</strong> the
 * description is no longer hardcoded to the JWT-themed string. It is now derived from
 * the {@link AuthenticationException} subclass and the inbound request URI so that:</p>
 * <ul>
 *   <li>{@code POST /auth/login} credential mismatches surface as
 *       {@code "Bad credentials"} (matches the message Spring Security itself
 *       attaches to the exception).</li>
 *   <li>Anonymous access to a protected resource surfaces as
 *       {@code "Authentication required"} (the {@code InsufficientAuthenticationException}
 *       case).</li>
 *   <li>All other JWT validation failures keep the historical
 *       {@code "Unauthorized - invalid or missing JWT token"} description so the
 *       behaviour observed by existing clients is unchanged.</li>
 * </ul>
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
     * <p>The {@code apiDescription} field is derived dynamically &mdash; see
     * {@link #resolveApiDescription(HttpServletRequest, AuthenticationException)} &mdash; so
     * the body accurately reflects whether the failure was a credential mismatch, a missing
     * bearer token, or a generic validation failure. This addresses QA CP3 Issue #4 in
     * which the entry point always emitted the hardcoded JWT-themed string even when the
     * upstream cause was unrelated to JWT parsing.</p>
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
                + ", exceptionType=" + authException.getClass().getSimpleName()
                + ", reason=" + authException.getMessage());

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");

        ResponseStructure<String> body = new ResponseStructure<>();
        body.setStatusCode(401);
        body.setApiDescription(resolveApiDescription(request, authException));
        body.setData(authException.getMessage());

        new ObjectMapper().writeValue(response.getOutputStream(), body);
    }

    /**
     * Selects an appropriate {@code apiDescription} for the outbound 401 envelope based
     * on the {@link AuthenticationException} subclass and the inbound request URI.
     *
     * <p>The mapping rules (in declaration order, first match wins):</p>
     * <ol>
     *   <li>{@link BadCredentialsException} &rarr; {@code "Bad credentials"}. This is the
     *       canonical Spring Security exception for {@code POST /auth/login} where the
     *       submitted password did not match the stored BCrypt hash (or the username was
     *       unknown; Spring Security maps {@code UsernameNotFoundException} to
     *       {@code BadCredentialsException} for anti-enumeration).</li>
     *   <li>{@link InsufficientAuthenticationException} &rarr; {@code "Authentication required"}.
     *       Raised by Spring Security's {@code ExceptionTranslationFilter} when a request
     *       reaches a protected URL with no authenticated principal in the
     *       {@code SecurityContextHolder} (e.g. anonymous access to {@code /product/**}).</li>
     *   <li>Fallback &rarr; the historical
     *       {@code "Unauthorized - invalid or missing JWT token"} string so existing
     *       clients that depend on the previous wording continue to see it for JWT-specific
     *       failures (tampered signature, expired token, malformed header, etc.).</li>
     * </ol>
     *
     * @param request       the inbound HTTP request (used here only for log context but
     *                      retained as a parameter to allow future URI-aware refinements
     *                      without breaking the method signature)
     * @param authException the Spring Security exception explaining the failure
     * @return a short, client-actionable English description suitable for the
     *         {@link ResponseStructure#getApiDescription()} field
     */
    private String resolveApiDescription(HttpServletRequest request, AuthenticationException authException) {
        System.out.println("[JwtAuthenticationEntryPoint] resolveApiDescription invoked for URI=" + request.getRequestURI()
                + ", exceptionType=" + authException.getClass().getSimpleName());

        if (authException instanceof BadCredentialsException) {
            return "Bad credentials";
        }
        if (authException instanceof InsufficientAuthenticationException) {
            return "Authentication required";
        }
        return "Unauthorized - invalid or missing JWT token";
    }
}
