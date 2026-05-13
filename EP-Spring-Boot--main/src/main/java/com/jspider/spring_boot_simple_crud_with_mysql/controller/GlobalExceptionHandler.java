package com.jspider.spring_boot_simple_crud_with_mysql.controller;

import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.jspider.spring_boot_simple_crud_with_mysql.responses.ResponseStructure;

/**
 * Application-wide REST exception translator that maps client-input
 * exceptions raised in {@code @RestController}s (specifically
 * {@code AuthController}) onto the project's standard
 * {@link ResponseStructure} envelope with a precise HTTP {@code 4xx}
 * status code.
 *
 * <p><strong>Why this class exists (root cause of QA CP3 Issues #1, #2, #3):
 * </strong> Prior to introducing this advice, exceptions thrown from the
 * authentication endpoints (notably {@link IllegalArgumentException} for
 * duplicate-username,
 * {@link org.springframework.dao.DataIntegrityViolationException} for
 * constraint violations,
 * {@link org.springframework.http.converter.HttpMessageNotReadableException}
 * for malformed JSON, and Bean Validation's
 * {@link MethodArgumentNotValidException}) had no project-level
 * {@link ExceptionHandler}. Spring's default fall-back was to forward the
 * request internally to the {@code /error} servlet. That forward re-entered
 * the security filter chain; {@code /error} is NOT listed in
 * {@code SecurityConfig.permitAll(...)}, so the JWT filter found no bearer
 * token and {@code JwtAuthenticationEntryPoint} emitted HTTP 401 &mdash;
 * even though the original failure was a client-input problem. The QA agent
 * reported this as "all registration validation failures incorrectly return
 * HTTP 401". This advice short-circuits that path by translating each
 * exception type into a meaningful {@code 4xx} response before Spring's
 * default {@code /error} forward can fire.</p>
 *
 * <p><strong>Why it lives in the controller package:</strong> the existing
 * project follows a thin-controller layout in which controllers, DAOs,
 * entities, repositories, DTOs, and security primitives each have their
 * own package. An {@code @RestControllerAdvice} is fundamentally part of
 * the controller surface (it intercepts controller exceptions and writes
 * controller responses) so it is co-located here rather than in
 * {@code config/} or {@code service/}. This matches the QA report's
 * preferred fix anchored on AAP &sect;0.5.2 "Each new class has a single
 * concern".</p>
 *
 * <p><strong>What it does NOT handle:</strong> authentication failures (e.g.
 * {@code BadCredentialsException},
 * {@code InsufficientAuthenticationException}) raised by the Spring
 * Security filter chain are intentionally NOT intercepted here &mdash;
 * those are routed through {@code JwtAuthenticationEntryPoint} which
 * remains the canonical 401 emitter for genuine auth failures. The
 * separation keeps 400 semantics (bad input) cleanly distinct from 401
 * semantics (bad credentials / missing token).</p>
 *
 * <p>Rule compliance (AAP &sect;0.7.1):</p>
 * <ul>
 *   <li>Rule 1 (camelCase) &mdash; all fields, parameters, and locals use
 *       camelCase; class and method identifiers follow standard Java
 *       PascalCase / camelCase respectively, which AAP &sect;0.7.3
 *       explicitly endorses.</li>
 *   <li>Rule 2 ({@code // Rule Applied}) &mdash; placed immediately above
 *       the class declaration after the class-level annotation.</li>
 *   <li>Rule 3 ({@code System.out.println} per new method) &mdash; every
 *       handler method emits an opening log line describing the inbound
 *       exception type so the runtime trace shows which handler fired.</li>
 * </ul>
 */
@RestControllerAdvice
// Rule Applied
public class GlobalExceptionHandler {

    /**
     * Maps Bean Validation failures raised by {@code @Valid}-annotated
     * controller parameters (e.g. {@code @NotBlank} on a DTO field) onto
     * HTTP {@code 400 Bad Request}.
     *
     * <p>Triggered when {@code POST /auth/register} or
     * {@code POST /auth/login} receives a body whose JSON deserialises
     * successfully but violates one or more constraints declared on
     * {@code RegisterRequestDto} or {@code LoginRequestDto} (missing
     * fields, empty strings, whitespace-only strings, or
     * over-length values).</p>
     *
     * <p>The response body's {@code data} payload is a single string that
     * concatenates each violation in {@code "field: message"} form so a
     * client can present every failing field in one round trip without
     * parsing nested arrays.</p>
     *
     * @param ex the validation exception carrying the field-by-field
     *           {@code BindingResult}; supplied by Spring
     * @return a {@code ResponseEntity} wrapping a
     *         {@link ResponseStructure} envelope with
     *         {@code statusCode = 400},
     *         {@code apiDescription = "Validation failed"}, and
     *         {@code data} listing each violating field
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ResponseStructure<String>> handleValidation(MethodArgumentNotValidException ex) {
        System.out.println("[GlobalExceptionHandler] handleValidation invoked: " + ex.getMessage());

        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));

        ResponseStructure<String> body = new ResponseStructure<>();
        body.setStatusCode(HttpStatus.BAD_REQUEST.value());
        body.setApiDescription("Validation failed");
        body.setData(details);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * Maps the {@link IllegalArgumentException} thrown by
     * {@code AuthService.register(...)} (e.g. when
     * {@code UserRepository.existsByUsername(...)} returns {@code true}, or
     * when a defensive blank-input guard fires) onto HTTP
     * {@code 400 Bad Request}.
     *
     * <p>The exception's message is propagated into the
     * {@code apiDescription} field of the envelope (deliberately generic
     * for the duplicate-username case to preserve anti-enumeration
     * posture). This is the canonical resolution for QA CP3 Issue #1 in
     * which the duplicate-registration path was emitting an unrelated
     * HTTP 401.</p>
     *
     * @param ex the {@link IllegalArgumentException} carrying the
     *           service-layer rejection reason
     * @return a {@code ResponseEntity} wrapping a
     *         {@link ResponseStructure} envelope with
     *         {@code statusCode = 400} and
     *         {@code apiDescription = ex.getMessage()}
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ResponseStructure<String>> handleIllegalArgument(IllegalArgumentException ex) {
        System.out.println("[GlobalExceptionHandler] handleIllegalArgument invoked: " + ex.getMessage());

        ResponseStructure<String> body = new ResponseStructure<>();
        body.setStatusCode(HttpStatus.BAD_REQUEST.value());
        body.setApiDescription(ex.getMessage() != null ? ex.getMessage() : "Bad request");
        body.setData(null);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * Maps Jackson deserialisation failures (malformed JSON, unparseable
     * tokens, type mismatches) onto HTTP {@code 400 Bad Request}.
     *
     * <p>Triggered when a client submits an empty body, syntactically
     * invalid JSON, or JSON whose structure does not match the target
     * DTO (e.g. an object where a string is expected). Without this
     * handler such requests would propagate to the default {@code /error}
     * forward and be falsely reported as HTTP 401 by the JWT entry point.</p>
     *
     * @param ex the {@link HttpMessageNotReadableException} carrying the
     *           Jackson cause
     * @return a {@code ResponseEntity} wrapping a
     *         {@link ResponseStructure} envelope with
     *         {@code statusCode = 400} and a fixed apiDescription
     *         "Malformed request body"
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ResponseStructure<String>> handleMessageNotReadable(HttpMessageNotReadableException ex) {
        System.out.println("[GlobalExceptionHandler] handleMessageNotReadable invoked: " + ex.getMessage());

        ResponseStructure<String> body = new ResponseStructure<>();
        body.setStatusCode(HttpStatus.BAD_REQUEST.value());
        body.setApiDescription("Malformed request body");
        body.setData(null);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * Maps database constraint violations (UNIQUE collisions, NOT NULL
     * violations, data truncation) onto HTTP {@code 400 Bad Request}.
     *
     * <p>Although the {@code @NotBlank} and {@code @Size} validations on
     * {@code RegisterRequestDto} should normally short-circuit these
     * exceptions, a race condition between two concurrent
     * {@code register} calls can still trigger a database-level
     * {@code UNIQUE} constraint violation on {@code app_user.username}.
     * This handler ensures the loser of the race receives a meaningful
     * 400 response rather than an unrelated 401.</p>
     *
     * @param ex the {@link DataIntegrityViolationException} carrying the
     *           underlying SQL cause
     * @return a {@code ResponseEntity} wrapping a
     *         {@link ResponseStructure} envelope with
     *         {@code statusCode = 400} and a generic apiDescription
     *         "Data integrity violation"
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ResponseStructure<String>> handleDataIntegrity(DataIntegrityViolationException ex) {
        System.out.println("[GlobalExceptionHandler] handleDataIntegrity invoked: " + ex.getMessage());

        ResponseStructure<String> body = new ResponseStructure<>();
        body.setStatusCode(HttpStatus.BAD_REQUEST.value());
        body.setApiDescription("Data integrity violation");
        body.setData(null);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }
}
