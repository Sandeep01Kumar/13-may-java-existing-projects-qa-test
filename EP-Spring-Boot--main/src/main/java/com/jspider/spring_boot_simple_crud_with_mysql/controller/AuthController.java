package com.jspider.spring_boot_simple_crud_with_mysql.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jspider.spring_boot_simple_crud_with_mysql.dto.AuthResponseDto;
import com.jspider.spring_boot_simple_crud_with_mysql.dto.LoginRequestDto;
import com.jspider.spring_boot_simple_crud_with_mysql.dto.RegisterRequestDto;
import com.jspider.spring_boot_simple_crud_with_mysql.responses.ResponseStructure;
import com.jspider.spring_boot_simple_crud_with_mysql.service.AuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

/**
 * REST controller exposing the public-facing JWT authentication endpoints
 * {@code POST /auth/register} and {@code POST /auth/login}.
 *
 * <p>This is a deliberately thin HTTP adapter: it accepts JSON request
 * bodies, delegates every authentication concern to {@link AuthService},
 * wraps the returned {@link ResponseStructure} envelope in a
 * {@link ResponseEntity} carrying the appropriate HTTP status code, and
 * does no business logic of its own. Specifically:</p>
 * <ul>
 *   <li>No JPA access &mdash; persistence is owned by
 *       {@code UserRepository} via {@link AuthService}.</li>
 *   <li>No Spring Security primitives &mdash; password hashing,
 *       {@code AuthenticationManager.authenticate(...)}, and JWT minting
 *       are all encapsulated inside {@link AuthService}.</li>
 *   <li>No inline exception handling &mdash; client-input exceptions
 *       (Bean Validation failures, duplicate-username
 *       {@code IllegalArgumentException}, Jackson
 *       {@code HttpMessageNotReadableException}, JPA
 *       {@code DataIntegrityViolationException}) are translated to
 *       {@code 400 Bad Request} by {@code GlobalExceptionHandler}
 *       ({@code @RestControllerAdvice}); authentication failures
 *       (e.g. {@code BadCredentialsException}) continue to bubble up to
 *       {@code JwtAuthenticationEntryPoint} (HTTP {@code 401}).</li>
 *   <li>Inbound DTOs are {@code @Valid}-annotated so Bean Validation
 *       constraints declared on {@code RegisterRequestDto} /
 *       {@code LoginRequestDto} are enforced before the method body
 *       runs.</li>
 * </ul>
 *
 * <p>The class-level {@code @RequestMapping("/auth")} is paired with the
 * security configuration {@code requestMatchers("/auth/**").permitAll()},
 * which makes both endpoints reachable anonymously while every other
 * endpoint in the application requires a valid JWT bearer token.</p>
 *
 * <p>Springdoc-openapi auto-discovers the {@link RestController} and the
 * {@link Tag}/{@link Operation} annotations, so the two endpoints
 * automatically appear under the &quot;Authentication&quot; section of the
 * Swagger UI without any further configuration.</p>
 *
 * <p>Dependencies are injected via Lombok's
 * {@link RequiredArgsConstructor}-generated constructor &mdash; field-level
 * {@code @Autowired} is intentionally avoided to align with current
 * Spring-recommended constructor-injection best practice for new code.</p>
 */
@RestController
@RequestMapping("/auth")
@Tag(name = "Authentication", description = "Endpoints for user registration and login")
@RequiredArgsConstructor
// Rule Applied
public class AuthController {

    /**
     * Service-layer collaborator that owns the entire authentication
     * business-logic surface: duplicate-username guard, BCrypt password
     * hashing, JPA persistence, {@code AuthenticationManager.authenticate(...)}
     * invocation, and JWT minting via {@code JwtUtil}.
     *
     * <p>Constructor-injected via Lombok's {@link RequiredArgsConstructor};
     * Spring's component scan resolves the bean automatically because
     * {@code AuthService} is annotated with {@code @Service} and lives
     * below the application's base package.</p>
     */
    private final AuthService authService;

    /**
     * Handles {@code POST /auth/register} &mdash; creates a brand-new
     * application user from the supplied {@link RegisterRequestDto}.
     *
     * <p>The actual work (duplicate-username guard, BCrypt hashing,
     * persistence) is delegated to
     * {@link AuthService#register(RegisterRequestDto)}; this method only
     * shapes the HTTP response.</p>
     *
     * <p>The opening log line (Rule 3 compliance) records ONLY the
     * username supplied in the request &mdash; never {@code dto.toString()}
     * (which Lombok's {@code @Data} would expand to include the plaintext
     * password) and never {@code dto.getPassword()} directly. This
     * preserves the security invariant defined in AAP &sect;0.7.5:
     * &quot;Plaintext passwords never reach the database and are never
     * written to any log line.&quot;</p>
     *
     * @param dto the JSON request body Jackson-deserialised into the
     *            {@link RegisterRequestDto} carrying the desired username,
     *            raw plaintext password, and role
     * @return an HTTP {@code 201 Created} response whose body is the
     *         pre-populated {@link ResponseStructure} envelope returned by
     *         {@link AuthService#register(RegisterRequestDto)}; the
     *         envelope's {@code data} field is the saved username
     */
    @PostMapping("/register")
    @Operation(summary = "Register a new user")
    public ResponseEntity<ResponseStructure<String>> register(@Valid @RequestBody RegisterRequestDto dto) {
        System.out.println("[AuthController] register invoked for username=" + dto.getUsername());
        ResponseStructure<String> body = authService.register(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    /**
     * Handles {@code POST /auth/login} &mdash; authenticates an existing
     * application user against their stored BCrypt password and returns
     * a freshly-minted JWT bearer token.
     *
     * <p>The actual work (credential verification through
     * {@code AuthenticationManager.authenticate(...)}, principal reload,
     * JWT minting) is delegated to
     * {@link AuthService#login(LoginRequestDto)}; this method only shapes
     * the HTTP response. On a credential mismatch,
     * {@code AuthenticationManager} throws {@code BadCredentialsException}
     * which propagates up to {@code JwtAuthenticationEntryPoint} and is
     * converted into an HTTP {@code 401 Unauthorized} response.</p>
     *
     * <p>The opening log line (Rule 3 compliance) follows the same
     * security discipline as {@link #register(RegisterRequestDto)}: only
     * the username is logged, never {@code dto.toString()} or
     * {@code dto.getPassword()}.</p>
     *
     * @param dto the JSON request body Jackson-deserialised into the
     *            {@link LoginRequestDto} carrying the username and raw
     *            plaintext password
     * @return an HTTP {@code 200 OK} response whose body is the
     *         pre-populated {@link ResponseStructure} envelope returned by
     *         {@link AuthService#login(LoginRequestDto)}; the envelope's
     *         {@code data} field is an {@link AuthResponseDto} carrying
     *         the JWT, the username, the role, and the configured token
     *         lifetime in milliseconds
     */
    @PostMapping("/login")
    @Operation(summary = "Authenticate user and return JWT")
    public ResponseEntity<ResponseStructure<AuthResponseDto>> login(@Valid @RequestBody LoginRequestDto dto) {
        System.out.println("[AuthController] login invoked for username=" + dto.getUsername());
        ResponseStructure<AuthResponseDto> body = authService.login(dto);
        return ResponseEntity.ok(body);
    }
}
