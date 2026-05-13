package com.jspider.spring_boot_simple_crud_with_mysql.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT bearer-token authentication filter for the Product CRUD application's
 * security layer.
 *
 * <p>This Spring-managed {@link Component} extends
 * {@link OncePerRequestFilter} to guarantee a single execution per request
 * dispatch (the canonical Spring base class for security filters). Its single
 * responsibility is to:</p>
 *
 * <ol>
 *   <li>Inspect the inbound {@code Authorization} HTTP header for a bearer
 *       token of the form {@code "Bearer &lt;jws&gt;"}.</li>
 *   <li>Delegate parsing/validation of the token to {@link JwtUtil}.</li>
 *   <li>Load the corresponding principal via the configured
 *       {@link UserDetailsService} bean (an {@code UserDetailsServiceImpl}
 *       wired by component scan).</li>
 *   <li>Populate the per-thread {@link SecurityContextHolder} with an
 *       authenticated {@link UsernamePasswordAuthenticationToken} when, and
 *       only when, every validation step succeeds.</li>
 * </ol>
 *
 * <p>The filter is deliberately NON short-circuiting on every code path:
 * regardless of whether the header is missing, malformed, or carries an
 * invalid/expired token, {@link FilterChain#doFilter(jakarta.servlet.ServletRequest,
 * jakarta.servlet.ServletResponse)} is always invoked so that the downstream
 * Spring Security exception-translation filter can route to
 * {@code JwtAuthenticationEntryPoint} and emit a clean
 * {@code 401 Unauthorized} JSON response for protected resources. Throwing
 * directly from this filter would bypass that entry point and surface a
 * generic {@code 500} error page, breaking the documented
 * {@code ResponseStructure}-shaped error contract.</p>
 *
 * <p>This filter is wired by {@code SecurityConfig} via
 * {@code HttpSecurity.addFilterBefore(jwtAuthenticationFilter,
 * UsernamePasswordAuthenticationFilter.class)} so that bearer-token
 * authentication is attempted before Spring Security's standard
 * username/password form-login filter.</p>
 *
 * <h2>Authorization-header state machine</h2>
 * <ul>
 *   <li><strong>Header absent</strong> &rarr; chain proceeds with no
 *       authentication set; the chain's authorisation rules decide whether
 *       to deny.</li>
 *   <li><strong>Header present but not prefixed with {@code "Bearer "}</strong>
 *       &rarr; chain proceeds with no authentication set.</li>
 *   <li><strong>Header present, token malformed / signature invalid /
 *       expired</strong> &rarr; the JJWT exception is caught and logged; the
 *       chain proceeds with no authentication set. The entry point emits
 *       {@code 401} for any protected target.</li>
 *   <li><strong>Header present, token cryptographically valid, principal
 *       resolvable</strong> &rarr; {@link SecurityContextHolder} is
 *       populated with a 3-arg
 *       {@link UsernamePasswordAuthenticationToken} (authenticated form).</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
// Rule Applied
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /**
     * JWT minting / parsing utility used to extract the subject (username)
     * claim from the bearer token and to assert the token is structurally
     * valid, cryptographically verifiable, and not expired before the
     * {@link SecurityContextHolder} is populated.
     *
     * <p>Constructor-injected by Lombok's {@code @RequiredArgsConstructor}
     * (preferred over field injection for testability and immutability).</p>
     */
    private final JwtUtil jwtUtil;

    /**
     * Spring Security's contract for loading a {@link UserDetails} principal
     * by username. Resolved at runtime to the {@code UserDetailsServiceImpl}
     * bean declared under the application's {@code service/} package, which
     * adapts the {@code User} JPA entity to Spring Security's principal
     * abstraction.
     *
     * <p>Constructor-injected by Lombok's {@code @RequiredArgsConstructor}.</p>
     */
    private final UserDetailsService userDetailsService;

    /**
     * Intercepts every incoming HTTP request exactly once per dispatch and
     * attempts to upgrade the security context to an authenticated
     * principal when a valid JWT bearer token is presented.
     *
     * <p>The method NEVER throws on token-validation failures &mdash; every
     * {@link JwtException} (and the related {@link IllegalArgumentException}
     * thrown for null/blank tokens) is swallowed and logged. The downstream
     * filter chain is always invoked so that
     * {@code JwtAuthenticationEntryPoint} can produce the documented
     * {@code 401 Unauthorized} response body for protected resources.</p>
     *
     * @param request     the inbound HTTP request whose {@code Authorization}
     *                    header is inspected for a bearer token; never
     *                    {@code null}
     * @param response    the HTTP response forwarded unchanged by this
     *                    filter; never {@code null}
     * @param filterChain the Spring Security filter chain to which the
     *                    request is unconditionally forwarded; never
     *                    {@code null}
     * @throws ServletException if a downstream filter throws a servlet
     *                          exception
     * @throws IOException      if a downstream filter encounters an I/O
     *                          failure
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        System.out.println("[JwtAuthenticationFilter] doFilterInternal invoked for URI=" + request.getRequestURI());

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        try {
            String username = jwtUtil.extractUsername(token);
            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                if (jwtUtil.isTokenValid(token, userDetails)) {
                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }
        } catch (JwtException | IllegalArgumentException ex) {
            System.out.println("[JwtAuthenticationFilter] token validation failed: " + ex.getMessage());
            // Do NOT rethrow — let the entry point emit 401 when no auth is set on a protected resource.
        }

        filterChain.doFilter(request, response);
    }
}
