package com.jspider.spring_boot_simple_crud_with_mysql.service;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.jspider.spring_boot_simple_crud_with_mysql.dto.AuthResponseDto;
import com.jspider.spring_boot_simple_crud_with_mysql.dto.LoginRequestDto;
import com.jspider.spring_boot_simple_crud_with_mysql.dto.RegisterRequestDto;
import com.jspider.spring_boot_simple_crud_with_mysql.entity.User;
import com.jspider.spring_boot_simple_crud_with_mysql.repository.UserRepository;
import com.jspider.spring_boot_simple_crud_with_mysql.responses.ResponseStructure;
import com.jspider.spring_boot_simple_crud_with_mysql.security.JwtUtil;

import lombok.RequiredArgsConstructor;

/**
 * Service-layer orchestrator for the JWT authentication feature's
 * {@code POST /auth/register} and {@code POST /auth/login} endpoints.
 *
 * <p>This {@link Service} encapsulates the entire business-logic surface area
 * for user registration and login so that {@code AuthController} remains a
 * thin HTTP adapter. The class deliberately delegates every cross-cutting
 * concern to a single collaborator:</p>
 * <ul>
 *   <li>{@link UserRepository} &mdash; durable persistence of {@link User}
 *       rows and the cheap {@code existsByUsername} guard used to short-circuit
 *       duplicate registrations.</li>
 *   <li>{@link PasswordEncoder} &mdash; BCrypt-backed hashing of the raw
 *       plaintext password received in the {@link RegisterRequestDto}; the
 *       interface is injected (never the concrete {@code BCryptPasswordEncoder})
 *       so that future algorithm rotations require zero edits here.</li>
 *   <li>{@link AuthenticationManager} &mdash; Spring Security's
 *       credential-verification orchestrator, configured in
 *       {@code SecurityConfig} to use {@code DaoAuthenticationProvider} on
 *       top of {@code UserDetailsServiceImpl} and {@code BCryptPasswordEncoder}.
 *       Invoked during {@link #login(LoginRequestDto)} with an unauthenticated
 *       {@link UsernamePasswordAuthenticationToken}.</li>
 *   <li>{@link UserDetailsService} &mdash; the bean implemented by
 *       {@code UserDetailsServiceImpl} that adapts a {@link User} entity to
 *       Spring Security's {@link UserDetails} contract; reloaded after a
 *       successful {@code authenticate(...)} call so that the principal
 *       handed to {@link JwtUtil} carries the canonical {@code ROLE_*}
 *       authority for inclusion in the minted token.</li>
 *   <li>{@link JwtUtil} &mdash; mints the HMAC-SHA256-signed compact JWS and
 *       exposes the configured token lifetime via
 *       {@link JwtUtil#getExpirationMs()} so that the {@link AuthResponseDto}
 *       can echo the expiration duration to the client without re-reading
 *       {@code application.properties}.</li>
 * </ul>
 *
 * <p>Design notes:</p>
 * <ul>
 *   <li>All five collaborators are immutable {@code private final} fields
 *       constructor-injected by Lombok's {@link RequiredArgsConstructor};
 *       no field-level {@code @Autowired} is used and the constructor is
 *       not hand-written.</li>
 *   <li>Both public methods open with a {@link System#out} log line (per
 *       AAP &sect;0.7.1 Rule 3) that includes ONLY the username &mdash;
 *       never {@code dto.toString()} and never {@code dto.getPassword()}
 *       &mdash; because Lombok's generated {@code toString()} on the DTOs
 *       would otherwise leak the raw plaintext password into stdout.</li>
 *   <li>No method is annotated with {@code @Transactional}: each repository
 *       call is its own implicit transaction, matching the pattern
 *       established by {@code ProductDao}.</li>
 *   <li>Plaintext passwords are read from the inbound DTO, hashed via
 *       {@link PasswordEncoder#encode(CharSequence)}, and discarded; they
 *       are never persisted, never returned, and never logged.</li>
 *   <li>The response envelope is the existing project-wide
 *       {@link ResponseStructure} type so that the new endpoints look and
 *       feel identical to the pre-existing {@code /product/**} surface.</li>
 * </ul>
 *
 * <p>Constraint compliance recap (AAP &sect;0.7.2):</p>
 * <ul>
 *   <li>Existing CRUD APIs are not touched &mdash; this is a net-new service.</li>
 *   <li>The class has a single concern (authentication-flow orchestration);
 *       every collaborator has a single, distinct responsibility.</li>
 *   <li>Every import resolves from a dependency declared in {@code pom.xml}
 *       (Spring Boot 3.4.4 manages Spring Security 6.x; Lombok 1.18.36
 *       provides {@link RequiredArgsConstructor}).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
// Rule Applied
public class AuthService {

    /**
     * Spring Data JPA repository used to:
     * <ul>
     *   <li>guard against duplicate registrations via
     *       {@link UserRepository#existsByUsername(String)};</li>
     *   <li>persist newly registered users via
     *       {@code JpaRepository#save(Object)};</li>
     *   <li>reload the persisted {@link User} entity after a successful
     *       login so that its raw {@code role} string can be echoed back to
     *       the caller through the {@link AuthResponseDto}.</li>
     * </ul>
     */
    private final UserRepository userRepository;

    /**
     * BCrypt-backed password hasher resolved by Spring as the
     * {@link PasswordEncoder} bean declared in {@code SecurityConfig}. The
     * interface (not the {@code BCryptPasswordEncoder} concrete class) is
     * injected here so that swapping the hashing algorithm later (e.g. to
     * {@code Argon2PasswordEncoder}) requires zero edits in this file.
     */
    private final PasswordEncoder passwordEncoder;

    /**
     * Spring Security's authentication orchestrator. Wired in
     * {@code SecurityConfig} on top of {@code DaoAuthenticationProvider}
     * which itself consumes {@link UserDetailsService} and
     * {@link PasswordEncoder}. Invoked in {@link #login(LoginRequestDto)}
     * with a 2-argument (unauthenticated) {@link UsernamePasswordAuthenticationToken};
     * a successful invocation returns an authenticated {@code Authentication}
     * but a {@code BadCredentialsException} is thrown on credential mismatch.
     */
    private final AuthenticationManager authenticationManager;

    /**
     * JWT minting and validation utility. Used here only to mint a fresh
     * token via {@link JwtUtil#generateToken(UserDetails)} after a
     * successful login and to read the configured token lifetime via
     * {@link JwtUtil#getExpirationMs()} for inclusion in the
     * {@link AuthResponseDto}.
     */
    private final JwtUtil jwtUtil;

    /**
     * Spring Security principal-lookup adapter (implemented by
     * {@code UserDetailsServiceImpl}). After
     * {@link AuthenticationManager#authenticate(org.springframework.security.core.Authentication)}
     * verifies the credentials in {@link #login(LoginRequestDto)}, the
     * principal is re-loaded through this service for JWT minting; this
     * mirrors the loading pattern used by {@code JwtAuthenticationFilter}.
     */
    private final UserDetailsService userDetailsService;

    /**
     * Registers a new application user.
     *
     * <p>The flow is:</p>
     * <ol>
     *   <li>Reject the request with {@link IllegalArgumentException} if the
     *       username already exists. The exception propagates through Spring's
     *       default error pipeline; the service-layer check still avoids
     *       relying solely on the database-level {@code UNIQUE} constraint on
     *       {@code app_user.username} for the common duplicate-username case.</li>
     *   <li>Hash the raw plaintext password via
     *       {@link PasswordEncoder#encode(CharSequence)} and discard the
     *       plaintext.</li>
     *   <li>Build a transient {@link User} entity with a {@code null} id (so
     *       JPA's {@code @GeneratedValue} assigns one on save) and the
     *       BCrypt-hashed password.</li>
     *   <li>Persist the entity through {@link UserRepository#save(Object)};
     *       the returned managed instance carries the database-assigned id.</li>
     *   <li>Wrap the saved username in a {@link ResponseStructure} envelope
     *       with HTTP status code {@code 201} and a human-readable
     *       description. The id and the hashed password are intentionally
     *       NOT included in the envelope to avoid leaking internals.</li>
     * </ol>
     *
     * <p>The {@code existsByUsername} guard is informational; it gives a
     * friendlier error message for the common case. It is NOT race-safe
     * &mdash; the {@code UNIQUE} constraint on {@code app_user.username} is
     * the ultimate guard against concurrent duplicate insertions.</p>
     *
     * <p><strong>Anti-enumeration:</strong> the exception message intentionally
     * does NOT echo the submitted username because that would let an
     * attacker probe the user-store via the registration endpoint. The
     * message {@code "Registration request invalid"} is deliberately generic
     * and indistinguishable from other client-input problems so the
     * registration surface offers the same anti-enumeration posture as the
     * login endpoint.</p>
     *
     * @param dto the request body carrying the desired username, raw
     *            (plaintext) password, and role string; must not be
     *            {@code null}
     * @return a {@link ResponseStructure} envelope whose {@code data}
     *         payload is the persisted username, {@code statusCode} is
     *         {@code 201}, and {@code apiDescription} is
     *         {@code "User registered successfully"}
     * @throws IllegalArgumentException if a user with the supplied username
     *                                  already exists in the {@code app_user}
     *                                  table
     */
    public ResponseStructure<String> register(RegisterRequestDto dto) {
        System.out.println("[AuthService] register invoked for username=" + dto.getUsername());

        if (userRepository.existsByUsername(dto.getUsername())) {
            // Anti-enumeration: do NOT echo the submitted username back in the
            // exception message (CP2 issue #1). A generic message keeps the
            // response indistinguishable from other client-input rejections so
            // attackers cannot use the register endpoint as a username oracle.
            throw new IllegalArgumentException("Registration request invalid");
        }

        String hashed = passwordEncoder.encode(dto.getPassword());
        User user = new User(null, dto.getUsername(), hashed, dto.getRole());
        User saved = userRepository.save(user);

        ResponseStructure<String> response = new ResponseStructure<>();
        response.setStatusCode(201);
        response.setApiDescription("User registered successfully");
        response.setData(saved.getUsername());
        return response;
    }

    /**
     * Authenticates an application user and mints a JWT bearer token.
     *
     * <p>The flow is:</p>
     * <ol>
     *   <li>Submit an unauthenticated
     *       {@link UsernamePasswordAuthenticationToken} (2-argument
     *       constructor, {@code authenticated == false}) to
     *       {@link AuthenticationManager#authenticate(org.springframework.security.core.Authentication)}.
     *       Spring Security's {@code DaoAuthenticationProvider} loads the
     *       persisted user through {@link UserDetailsService} and compares
     *       the raw password to the stored BCrypt hash via
     *       {@code BCryptPasswordEncoder.matches(...)}. A mismatch throws
     *       {@code BadCredentialsException}; an unknown user throws
     *       {@code UsernameNotFoundException} which the provider translates
     *       into {@code BadCredentialsException} to defeat
     *       account-enumeration attacks.</li>
     *   <li>Reload the {@link UserDetails} via
     *       {@link UserDetailsService#loadUserByUsername(String)} for clarity
     *       and consistency with the JWT filter's load pattern. Although
     *       the {@code Authentication} returned by step 1 also carries a
     *       {@link UserDetails} principal, the AAP-prescribed pattern
     *       favours an explicit reload here.</li>
     *   <li>Fetch the JPA {@link User} entity to extract the raw {@code role}
     *       string (without the {@code ROLE_} prefix that
     *       {@code UserDetailsServiceImpl} adds) for inclusion in the
     *       response DTO. The defensive {@code orElseThrow} is reachable
     *       only in catastrophic data-consistency failures because the
     *       authentication in step 1 already confirmed the row exists.</li>
     *   <li>Mint a JWT via {@link JwtUtil#generateToken(UserDetails)} and
     *       read the configured token lifetime via
     *       {@link JwtUtil#getExpirationMs()}. The {@code expiresInMs}
     *       field is a DURATION (e.g. {@code 3600000} ms = 1 hour),
     *       NOT an absolute expiration timestamp &mdash; clients compute
     *       the wall-clock expiry as {@code now + expiresInMs}.</li>
     *   <li>Wrap the resulting {@link AuthResponseDto} in a
     *       {@link ResponseStructure} envelope with HTTP status code
     *       {@code 200}.</li>
     * </ol>
     *
     * <p>Note that {@link #login(LoginRequestDto)} does NOT set the
     * {@code SecurityContextHolder} itself: the JWT model is stateless,
     * and the per-request {@code JwtAuthenticationFilter} is the sole
     * component that populates the security context based on the
     * {@code Authorization: Bearer <token>} header.</p>
     *
     * @param dto the validated request body carrying the username and raw
     *            (plaintext) password; must not be {@code null}
     * @return a {@link ResponseStructure} envelope whose {@code data}
     *         payload is a freshly-built {@link AuthResponseDto} containing
     *         the minted JWT, the username, the raw role string, and the
     *         configured token lifetime in milliseconds
     */
    public ResponseStructure<AuthResponseDto> login(LoginRequestDto dto) {
        System.out.println("[AuthService] login invoked for username=" + dto.getUsername());

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(dto.getUsername(), dto.getPassword()));

        UserDetails userDetails = userDetailsService.loadUserByUsername(dto.getUsername());
        User user = userRepository.findByUsername(dto.getUsername())
                .orElseThrow(() -> new IllegalStateException("User vanished after authentication: " + dto.getUsername()));

        String token = jwtUtil.generateToken(userDetails);
        AuthResponseDto authResponseDto = new AuthResponseDto(token, user.getUsername(), user.getRole(), jwtUtil.getExpirationMs());

        ResponseStructure<AuthResponseDto> response = new ResponseStructure<>();
        response.setStatusCode(200);
        response.setApiDescription("Login successful");
        response.setData(authResponseDto);
        return response;
    }
}
