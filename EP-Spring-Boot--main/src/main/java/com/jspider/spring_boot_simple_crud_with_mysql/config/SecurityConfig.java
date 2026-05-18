package com.jspider.spring_boot_simple_crud_with_mysql.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Central Spring Security 6 configuration for the
 * {@code spring-boot-simple-crud-with-mysql} application.
 *
 * <p>This configuration implements the security middleware requested by the
 * user via the prompt "Implement security headers, input validation, rate
 * limiting, and HTTPS support" plus "add helmet.js for security middleware".
 * Because helmet.js is a Node.js/Express middleware incompatible with the
 * JVM, the equivalent HTTP security headers are produced here via Spring
 * Security 6's {@code HeadersConfigurer} lambda DSL (see the helmet.js &rarr;
 * Spring Security translation matrix in AAP &sect;0.1.3).
 *
 * <h2>helmet.js &rarr; Spring Security Header Mapping</h2>
 * <ul>
 *   <li>{@code helmet.contentSecurityPolicy()} &rarr;
 *       {@code Content-Security-Policy} via
 *       {@code headers.contentSecurityPolicy(csp -> csp.policyDirectives(...))}.</li>
 *   <li>{@code helmet.frameguard()} &rarr;
 *       {@code X-Frame-Options: DENY} via
 *       {@code headers.frameOptions(f -> f.deny())}.</li>
 *   <li>{@code helmet.hsts()} &rarr;
 *       {@code Strict-Transport-Security} via
 *       {@code headers.httpStrictTransportSecurity(...)}.</li>
 *   <li>{@code helmet.noSniff()} &rarr;
 *       {@code X-Content-Type-Options: nosniff} &mdash; emitted BY DEFAULT
 *       by Spring Security's {@code XContentTypeOptionsHeaderWriter}; no
 *       explicit code required.</li>
 *   <li>{@code helmet.referrerPolicy()} &rarr;
 *       {@code Referrer-Policy} via
 *       {@code headers.referrerPolicy(r -> r.policy(STRICT_ORIGIN_WHEN_CROSS_ORIGIN))}.</li>
 *   <li>{@code helmet.permittedCrossDomainPolicies()} &rarr;
 *       {@code X-Permitted-Cross-Domain-Policies: none} via
 *       {@code headers.addHeaderWriter(new StaticHeadersWriter(...))}.</li>
 *   <li>Permissions-Policy (newer helmet) &rarr;
 *       {@code Permissions-Policy} via
 *       {@code headers.permissionsPolicyHeader(p -> p.policy(...))}.</li>
 * </ul>
 *
 * <h2>OWASP Top 10 (2021) Coverage</h2>
 * <ul>
 *   <li><b>A01 Broken Access Control</b> &mdash;
 *       {@code authorizeHttpRequests(...).anyRequest().authenticated()} plus
 *       method-level {@code @PreAuthorize} (enabled by
 *       {@link EnableMethodSecurity}) on controllers. Closes AAP findings
 *       #8 (CWE-306 Missing Authentication) and #9 (CWE-862 Missing
 *       Authorization).</li>
 *   <li><b>A04 Insecure Design</b> &mdash; {@link RateLimitingFilter}
 *       (Bucket4j per-IP token bucket) is registered before the
 *       authentication filter via
 *       {@code addFilterBefore(rateLimitingFilter, UsernamePasswordAuthenticationFilter.class)}.
 *       Flooding clients are rejected with HTTP 429 before they consume
 *       authentication resources. Closes AAP finding #4 (CWE-770 Allocation
 *       of Resources Without Limits or Throttling).</li>
 *   <li><b>A05 Security Misconfiguration</b> &mdash; full HTTP security
 *       header set (HSTS, CSP, X-Frame-Options, X-Content-Type-Options,
 *       Referrer-Policy, Permissions-Policy, X-Permitted-Cross-Domain-Policies)
 *       plus global CORS allow-list replacing the previous ambiguous
 *       per-controller {@code @CrossOrigin(value = "")} annotation. Closes
 *       AAP findings #1 (CWE-693 Protection Mechanism Failure) and #10
 *       (CWE-942 Permissive Cross-domain Policy with Untrusted Domains).</li>
 * </ul>
 *
 * <h2>Stateless REST Posture</h2>
 * <ul>
 *   <li>CSRF disabled &mdash; appropriate for stateless REST authenticated
 *       via HTTP Basic per request; CSRF protection is required only for
 *       session-cookie-bearing authentication.</li>
 *   <li>{@link SessionCreationPolicy#STATELESS} &mdash; no {@code JSESSIONID}
 *       cookies are issued; each request re-authenticates via the
 *       {@code Authorization: Basic} header.</li>
 *   <li>HTTP Basic authentication &mdash; didactic default per AAP
 *       &sect;0.10.4. Production deployments should migrate to JWT/OAuth2
 *       resource-server authentication (flagged future work,
 *       out of scope).</li>
 * </ul>
 *
 * <h2>Public Allow-list</h2>
 * <p>The following request paths bypass authentication:
 * <ul>
 *   <li>{@code /actuator/health} &mdash; operational health endpoint
 *       (no-op today; if Spring Boot Actuator is added later this path
 *       supports load-balancer readiness probes).</li>
 *   <li>{@code /v3/api-docs/**} &mdash; springdoc OpenAPI JSON specification
 *       (closed in production via {@code springdoc.api-docs.enabled=false}
 *       in {@code application-prod.properties}).</li>
 *   <li>{@code /swagger-ui/**} and {@code /swagger-ui.html} &mdash; Swagger
 *       UI WebJar paths (similarly closed in production).</li>
 * </ul>
 * Every other request is gated by {@code anyRequest().authenticated()}.
 *
 * <h2>References</h2>
 * <ul>
 *   <li>Spring Security Reference, "Headers" chapter &mdash;
 *       https://docs.spring.io/spring-security/reference/servlet/exploits/headers.html</li>
 *   <li>Spring Security Reference, "CORS" &mdash;
 *       https://docs.spring.io/spring-security/reference/servlet/integrations/cors.html</li>
 *   <li>OWASP Top 10 (2021) &mdash; https://owasp.org/Top10/</li>
 *   <li>OWASP REST Security Cheat Sheet &mdash;
 *       https://cheatsheetseries.owasp.org/cheatsheets/REST_Security_Cheat_Sheet.html</li>
 * </ul>
 *
 * @see RateLimitingFilter
 * @see RateLimitingProperties
 * @see SecurityFilterChain
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@EnableConfigurationProperties(RateLimitingProperties.class)
public class SecurityConfig {

    /**
     * Defines the application's central servlet filter chain.
     *
     * <p>Configuration sequence (order matters):
     * <ol>
     *   <li><b>CSRF disabled</b> &mdash; stateless REST authenticated via
     *       HTTP Basic does not require CSRF tokens.</li>
     *   <li><b>Session management</b> set to
     *       {@link SessionCreationPolicy#STATELESS} &mdash; no session is
     *       created, no {@code JSESSIONID} cookie is issued.</li>
     *   <li><b>CORS</b> wired to the {@link #corsConfigurationSource(String)}
     *       bean via {@code Customizer.withDefaults()}. Spring Security's
     *       {@code CorsConfigurer} looks up the bean by name
     *       {@code corsConfigurationSource} and applies its configuration
     *       on every request (including OPTIONS preflights).</li>
     *   <li><b>Authorization rules</b>:
     *       <ul>
     *         <li>{@code /actuator/health}, {@code /v3/api-docs/**},
     *             {@code /swagger-ui/**}, {@code /swagger-ui.html} &mdash;
     *             {@code permitAll()}.</li>
     *         <li>Everything else &mdash; {@code authenticated()}.</li>
     *       </ul>
     *       Matcher ordering is significant: permit-all matchers come first;
     *       the catch-all {@code anyRequest().authenticated()} is LAST.</li>
     *   <li><b>HTTP Basic</b> authentication via
     *       {@code Customizer.withDefaults()} &mdash; didactic default;
     *       production must migrate to JWT/OAuth2 (AAP &sect;0.10.4).</li>
     *   <li><b>Security headers</b> &mdash; the helmet.js-equivalent set
     *       (see class-level Javadoc for the mapping table). All six
     *       configured headers are emitted on every response;
     *       {@code X-Content-Type-Options: nosniff} is emitted by default
     *       via Spring Security's {@code XContentTypeOptionsHeaderWriter}
     *       without explicit configuration here.</li>
     *   <li><b>Rate-limit filter</b> inserted via
     *       {@code addFilterBefore(rateLimitingFilter, UsernamePasswordAuthenticationFilter.class)}.
     *       {@link UsernamePasswordAuthenticationFilter} is used as the
     *       positional reference even though HTTP Basic actually uses
     *       {@code BasicAuthenticationFilter} &mdash;
     *       {@code UsernamePasswordAuthenticationFilter} is earlier in the
     *       chain and produces the correct ordering for our intent: throttle
     *       BEFORE authentication so flooding clients do not consume auth
     *       resources.</li>
     * </ol>
     *
     * @param http               the Spring Security {@link HttpSecurity}
     *                           builder, autowired by Spring
     * @param rateLimitingFilter the per-IP rate-limit filter, autowired by
     *                           Spring (it is a {@code @Component})
     * @return the fully configured {@link SecurityFilterChain}
     * @throws Exception if {@link HttpSecurity#build()} fails (build-time
     *                   configuration error)
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   RateLimitingFilter rateLimitingFilter) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .cors(Customizer.withDefaults())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .anyRequest().authenticated()
            )
            .httpBasic(Customizer.withDefaults())
            .headers(headers -> headers
                .frameOptions(frame -> frame.deny())
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .maxAgeInSeconds(31_536_000)
                )
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                    "default-src 'self'; frame-ancestors 'none'; object-src 'none'; base-uri 'self'"
                ))
                .referrerPolicy(referrer -> referrer.policy(
                    ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN
                ))
                .permissionsPolicyHeader(permissions -> permissions.policy(
                    "geolocation=(), microphone=(), camera=()"
                ))
                .addHeaderWriter(new StaticHeadersWriter(
                    "X-Permitted-Cross-Domain-Policies", "none"
                ))
            )
            .addFilterBefore(rateLimitingFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * Defines the application-wide CORS allow-list.
     *
     * <p>Spring Security's {@code cors(Customizer.withDefaults())} call in
     * {@link #securityFilterChain(HttpSecurity, RateLimitingFilter)} discovers
     * this bean by name ({@code corsConfigurationSource}) and applies its
     * configuration on every request, including OPTIONS preflights.
     *
     * <p><b>Allow-list, NOT wildcard.</b> The
     * {@code app.cors.allowed-origins} property is comma-delimited so callers
     * can supply multiple origins (e.g.
     * {@code https://app.example.com,https://admin.example.com}). Spring
     * resolves the placeholder from {@code application.properties} where the
     * value is {@code ${CORS_ALLOWED_ORIGINS:https://localhost:8443}}.
     *
     * <p><b>Why NOT {@code setAllowedOriginPatterns("*")} or
     * {@code setAllowedOrigins(List.of("*"))}:</b> wildcard origins re-create
     * AAP finding #10 (CWE-942 permissive CORS). The previous codebase
     * carried {@code @CrossOrigin(value = "")} on
     * {@code ProductController} which Spring resolves to "any origin"
     * &mdash; that annotation has been removed by the controller-update
     * agent and this bean replaces it with an explicit allow-list.
     *
     * <p><b>{@code setAllowCredentials(true)}</b> &mdash; valid only because
     * the allow-list is explicit. The CORS spec forbids
     * {@code Access-Control-Allow-Credentials: true} when
     * {@code Access-Control-Allow-Origin: *}, so wildcard origins are
     * doubly unsuitable here.
     *
     * <p><b>{@code setMaxAge(3600L)}</b> &mdash; caches the preflight
     * response for one hour, reducing the OPTIONS round-trip burden on the
     * server without sacrificing security (the cache lives in the browser
     * only).
     *
     * <p><b>{@code registerCorsConfiguration("/**", configuration)}</b>
     * &mdash; applies the same allow-list to every URL path. If the
     * application later needs different origins per controller, this
     * registration can be split into multiple
     * {@code registerCorsConfiguration} calls keyed by path pattern.
     *
     * @param allowedOrigins comma-delimited list of allowed origins,
     *                       sourced from {@code app.cors.allowed-origins}
     *                       in {@code application.properties}
     * @return a {@link CorsConfigurationSource} that Spring Security wires
     *         into the filter chain
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins}") String allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setExposedHeaders(List.of("Location"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * Provides the application's password hashing strategy.
     *
     * <p>{@link BCryptPasswordEncoder} is the standard Spring Security
     * {@link PasswordEncoder} for new applications. Default strength
     * (10 rounds, log2 of 1024 iterations) is appropriate for a didactic
     * project; production deployments handling sensitive data should raise
     * the strength parameter via the
     * {@link BCryptPasswordEncoder#BCryptPasswordEncoder(int)} overload
     * (typical production values: 12&ndash;14).
     *
     * <p>This bean is consumed by
     * {@link #userDetailsService(PasswordEncoder)} to BCrypt-encode the
     * seeded in-memory user passwords. It is also available for any future
     * password-handling code (e.g. a JPA-backed
     * {@code UserDetailsService} replacement) via standard Spring
     * autowiring.
     *
     * <p><b>Explicitly avoided alternatives:</b>
     * <ul>
     *   <li>{@code User.withDefaultPasswordEncoder()} &mdash; deprecated;
     *       emits a runtime warning; intended only for prototyping.</li>
     *   <li>{@code NoOpPasswordEncoder.getInstance()} &mdash; plaintext
     *       storage; strictly worse than the pre-fix state and unsuitable
     *       for any environment.</li>
     *   <li>{@code Pbkdf2PasswordEncoder} / {@code SCryptPasswordEncoder}
     *       / {@code Argon2PasswordEncoder} &mdash; valid alternatives;
     *       BCrypt is chosen for ubiquity and didactic clarity.</li>
     * </ul>
     *
     * @return a {@link BCryptPasswordEncoder} at default strength
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Didactic default user provider &mdash; DO NOT use in production.
     *
     * <p>This in-memory user store is appropriate ONLY for the didactic
     * Spring Boot project this configuration ships with. Production
     * deployments must replace this bean with a JPA-backed
     * {@link UserDetailsService} (or migrate to JWT/OAuth2
     * resource-server authentication entirely) per AAP &sect;0.10.4
     * "future security work flagged but explicitly out of scope".
     *
     * <p>The two seeded users are:
     * <ul>
     *   <li>{@code user} / {@code user} &mdash; role {@code USER}
     *       (read-only and standard writes).</li>
     *   <li>{@code admin} / {@code admin} &mdash; roles {@code USER} +
     *       {@code ADMIN} (required for DELETE endpoints per
     *       {@code @PreAuthorize("hasRole('ADMIN')")}).</li>
     * </ul>
     *
     * <p>Passwords are BCrypt-encoded via the
     * {@link #passwordEncoder()} bean. Plaintext password literals
     * (e.g. {@code .password("user")}) are wrapped in
     * {@code passwordEncoder.encode(...)} BEFORE being passed to
     * {@link User#builder()}, so the {@link InMemoryUserDetailsManager}
     * stores only the BCrypt hash &mdash; never the plaintext.
     *
     * <p><b>{@code roles(...)} vs {@code authorities(...)}:</b>
     * {@code User.builder().roles("USER")} automatically prefixes the
     * role name with {@code ROLE_} (so the granted authority becomes
     * {@code ROLE_USER}). This is the authority pattern that
     * {@code hasRole("USER")} expressions check against in
     * {@code @PreAuthorize} annotations on the controllers.
     *
     * <p><b>Future work (AAP &sect;0.10.4):</b>
     * <ul>
     *   <li>Replace this in-memory store with a JPA-backed
     *       {@link UserDetailsService} that loads
     *       {@link UserDetails} from a relational {@code users} +
     *       {@code roles} schema.</li>
     *   <li>Migrate authentication from HTTP Basic to JWT bearer tokens
     *       or OAuth2 resource server semantics.</li>
     *   <li>Externalize the seeded credentials via environment variables
     *       if this bean must remain for any transitional period.</li>
     * </ul>
     *
     * @param passwordEncoder the {@link PasswordEncoder} bean (BCrypt at
     *                        default strength) used to encode the seeded
     *                        plaintext passwords
     * @return an {@link InMemoryUserDetailsManager} containing the two
     *         didactic users described above
     */
    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
        UserDetails user = User.builder()
            .username("user")
            .password(passwordEncoder.encode("user"))
            .roles("USER")
            .build();
        UserDetails admin = User.builder()
            .username("admin")
            .password(passwordEncoder.encode("admin"))
            .roles("USER", "ADMIN")
            .build();
        return new InMemoryUserDetailsManager(user, admin);
    }
}
