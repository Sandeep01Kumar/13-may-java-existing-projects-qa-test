package com.jspider.spring_boot_simple_crud_with_mysql.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.jspider.spring_boot_simple_crud_with_mysql.security.JwtAuthenticationEntryPoint;
import com.jspider.spring_boot_simple_crud_with_mysql.security.JwtAuthenticationFilter;

import lombok.RequiredArgsConstructor;

/**
 * Spring Security 6.x bean-based configuration for the JWT authentication and
 * authorisation feature layered onto the existing Product CRUD application.
 *
 * <p>This {@link Configuration} class is the single wiring point for the entire
 * security feature. It exposes four beans &mdash; {@code PasswordEncoder},
 * {@code AuthenticationProvider}, {@code AuthenticationManager}, and
 * {@code SecurityFilterChain} &mdash; and configures the filter chain so that:
 * </p>
 *
 * <ul>
 *   <li>{@code /auth/**} (registration and login) and Swagger UI paths are
 *       reachable anonymously.</li>
 *   <li>Every other endpoint (including {@code /product/**} and
 *       {@code /student/**}) requires a valid JWT bearer token.</li>
 *   <li>Authentication is stateless &mdash; no server-side
 *       {@code HttpSession} is ever created.</li>
 *   <li>CSRF protection is disabled because the API is token-based and
 *       stateless.</li>
 *   <li>Unauthenticated requests to protected resources receive a clean HTTP
 *       {@code 401 Unauthorized} JSON response shaped like the project's
 *       existing {@code ResponseStructure} envelope.</li>
 * </ul>
 *
 * <h2>Spring Security 6.x bean-based configuration</h2>
 * <p>Spring Security 6.0 removed the legacy {@code WebSecurityConfigurerAdapter}
 * base class. The current canonical pattern is to publish a
 * {@link SecurityFilterChain} {@link Bean} that fluently builds the filter
 * chain from an injected {@link HttpSecurity}. This class follows that
 * idiom exclusively.</p>
 *
 * <h2>Dependency wiring</h2>
 * <p>Three collaborators are injected via Lombok-generated constructor
 * injection:</p>
 * <ul>
 *   <li>{@link JwtAuthenticationFilter} &mdash; registered before
 *       {@link UsernamePasswordAuthenticationFilter} so bearer tokens are
 *       evaluated first.</li>
 *   <li>{@link JwtAuthenticationEntryPoint} &mdash; wired into the chain's
 *       exception-handling step to emit the JSON 401 response body.</li>
 *   <li>{@link UserDetailsService} &mdash; resolved at runtime to the
 *       {@code @Service}-annotated {@code UserDetailsServiceImpl} bean (the
 *       sole implementation in the project) which loads principals from the
 *       {@code app_user} table.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
// Rule Applied
public class SecurityConfig {

    /**
     * JWT bearer-token authentication filter that intercepts every secured
     * request, extracts and validates the {@code Authorization: Bearer ...}
     * header, and populates the {@code SecurityContextHolder} with an
     * authenticated principal when the token is valid.
     *
     * <p>Constructor-injected by Lombok's {@link RequiredArgsConstructor};
     * registered in the filter chain via
     * {@code http.addFilterBefore(jwtAuthenticationFilter,
     * UsernamePasswordAuthenticationFilter.class)}.</p>
     */
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /**
     * Custom {@code AuthenticationEntryPoint} that emits an HTTP
     * {@code 401 Unauthorized} JSON response (shaped like the project's
     * {@code ResponseStructure} envelope) for any unauthenticated request that
     * reaches a protected resource.
     *
     * <p>Constructor-injected by Lombok's {@link RequiredArgsConstructor};
     * wired via {@code http.exceptionHandling(eh ->
     * eh.authenticationEntryPoint(jwtAuthenticationEntryPoint))}.</p>
     */
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    /**
     * Spring Security's contract for loading a {@code UserDetails} principal
     * by username. Typed as the INTERFACE so this configuration class remains
     * loosely coupled to the concrete {@code UserDetailsServiceImpl}
     * implementation in the {@code service/} package.
     *
     * <p>Constructor-injected by Lombok's {@link RequiredArgsConstructor};
     * wired into the {@link DaoAuthenticationProvider} via
     * {@link DaoAuthenticationProvider#setUserDetailsService(UserDetailsService)}.</p>
     */
    private final UserDetailsService userDetailsService;

    /**
     * Exposes a {@link BCryptPasswordEncoder} as the application's
     * {@link PasswordEncoder} bean.
     *
     * <p>This bean is consumed by two collaborators:</p>
     * <ul>
     *   <li>{@code AuthService.register(...)} &mdash; hashes the plaintext
     *       password before persisting the {@code User} entity.</li>
     *   <li>{@link DaoAuthenticationProvider} &mdash; verifies a submitted
     *       plaintext password against the persisted BCrypt hash during
     *       {@code AuthenticationManager.authenticate(...)}.</li>
     * </ul>
     *
     * <p>The return type is the {@link PasswordEncoder} INTERFACE (not the
     * concrete class) so callers depend on the abstraction and the encoder
     * can be swapped (e.g. to Argon2) without modifying callers.</p>
     *
     * @return a singleton {@link BCryptPasswordEncoder} instance using the
     *         default strength (10 rounds), which is appropriate for the
     *         scope of this feature
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        System.out.println("[SecurityConfig] passwordEncoder bean created");
        return new BCryptPasswordEncoder();
    }

    /**
     * Exposes a {@link DaoAuthenticationProvider} configured with the
     * application's {@link UserDetailsService} and {@link PasswordEncoder}.
     *
     * <p>Spring's {@code @Configuration} CGLib proxy ensures that the
     * self-call to {@link #passwordEncoder()} returns the cached singleton
     * bean rather than instantiating a new {@link BCryptPasswordEncoder}.
     * This is essential &mdash; if a fresh encoder were used here, the
     * BCrypt salt comparison would still succeed (BCrypt is salt-stable
     * across instances), but the singleton property keeps memory usage low
     * and is the canonical Spring idiom.</p>
     *
     * <p>The provider is registered with the security filter chain via
     * {@code http.authenticationProvider(authenticationProvider())} so that
     * {@code AuthenticationManager.authenticate(...)} delegates credential
     * verification to it during {@code POST /auth/login}.</p>
     *
     * @return a configured {@link AuthenticationProvider} backed by
     *         {@link DaoAuthenticationProvider}
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        System.out.println("[SecurityConfig] authenticationProvider bean created");
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    /**
     * Exposes Spring Security's pre-built {@link AuthenticationManager} as a
     * bean so that {@code AuthService.login(...)} can call
     * {@link AuthenticationManager#authenticate(org.springframework.security.core.Authentication)}.
     *
     * <p>The {@link AuthenticationConfiguration} parameter is injected by
     * Spring &mdash; it is the framework-managed bean that aggregates the
     * application's authentication providers (including our
     * {@link DaoAuthenticationProvider}) into a single
     * {@link AuthenticationManager} instance.</p>
     *
     * <p>The method declares {@code throws Exception} because
     * {@link AuthenticationConfiguration#getAuthenticationManager()} itself
     * declares this checked exception.</p>
     *
     * @param cfg Spring's authentication configuration aggregator (auto-injected)
     * @return the application-scoped {@link AuthenticationManager} bean
     * @throws Exception if Spring's authentication configuration cannot be
     *                   resolved
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration cfg) throws Exception {
        System.out.println("[SecurityConfig] authenticationManager bean created");
        return cfg.getAuthenticationManager();
    }

    /**
     * Defines the security filter chain for the entire application.
     *
     * <p>Configuration steps (in declaration order):</p>
     * <ol>
     *   <li><strong>CORS integration</strong> &mdash; delegates CORS pre-flight
     *       (HTTP {@code OPTIONS}) handling to Spring MVC's
     *       {@code CrossOriginHandler} (driven by the existing
     *       {@code @CrossOrigin} annotations on the controllers, e.g.
     *       {@code @CrossOrigin(value = "")} on {@code ProductController}).
     *       Without this call, Spring Security 6.x rejects pre-flight requests
     *       with HTTP {@code 401} before they reach the MVC layer, which is an
     *       observable regression versus the pre-feature behaviour
     *       (AAP &sect;0.7.5: "Existing CORS configuration is preserved").</li>
     *   <li><strong>CSRF disabled</strong> &mdash; JWT-based stateless REST
     *       APIs do not use server-side sessions or cookie-bearing auth, so
     *       CSRF tokens have no role.</li>
     *   <li><strong>Authorisation rules</strong> &mdash; {@code /auth/**} (the
     *       new public registration and login endpoints), {@code /swagger-ui/**},
     *       {@code /v3/api-docs/**}, {@code /swagger-ui.html}, and
     *       {@code /error} are anonymous-accessible. The {@code /error}
     *       endpoint is included because Spring Boot internally forwards
     *       unmapped URLs (e.g. {@code GET /totally/random/path}) and
     *       unhandled exceptions to {@code /error} via the
     *       {@code BasicErrorController}; without permitting that path, the
     *       security filter chain re-evaluates the internal forward and emits
     *       {@code 401 Unauthorized} instead of the expected {@code 404 Not
     *       Found}. Every other request (including {@code /product/**} and
     *       {@code /student/**}) requires authentication.</li>
     *   <li><strong>Stateless session policy</strong> &mdash; mandatory for
     *       the JWT model so Spring Security never creates an
     *       {@code HttpSession}.</li>
     *   <li><strong>Custom 401 entry point</strong> &mdash; routes
     *       unauthenticated access to protected resources through
     *       {@link JwtAuthenticationEntryPoint} which emits a JSON
     *       {@code ResponseStructure}-shaped 401 body.</li>
     *   <li><strong>Authentication provider</strong> &mdash; registers the
     *       {@link DaoAuthenticationProvider} from
     *       {@link #authenticationProvider()} with the filter chain.</li>
     *   <li><strong>JWT filter placement</strong> &mdash; inserts
     *       {@link JwtAuthenticationFilter} BEFORE Spring's default
     *       {@link UsernamePasswordAuthenticationFilter} so bearer tokens are
     *       evaluated first and the {@code SecurityContextHolder} is
     *       populated before any downstream filter runs.</li>
     * </ol>
     *
     * <p>The method declares {@code throws Exception} because Spring
     * Security's fluent DSL methods are themselves declared as throwing
     * {@code Exception}.</p>
     *
     * @param http the Spring Security fluent builder, injected by Spring
     * @return the fully configured {@link SecurityFilterChain} bean
     * @throws Exception if the filter-chain DSL methods raise a checked
     *                   exception
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        System.out.println("[SecurityConfig] securityFilterChain bean created");
        http
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/auth/**", "/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html", "/error").permitAll()
                .anyRequest().authenticated())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(eh -> eh.authenticationEntryPoint(jwtAuthenticationEntryPoint))
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
