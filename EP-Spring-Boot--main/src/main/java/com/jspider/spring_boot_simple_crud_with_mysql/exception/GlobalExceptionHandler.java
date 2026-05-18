package com.jspider.spring_boot_simple_crud_with_mysql.exception;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

/**
 * Application-wide REST exception handler that converts framework and runtime
 * exceptions into structured, minimal-disclosure JSON responses.
 *
 * <p><strong>Security purpose &mdash; Information Disclosure Defense (CWE-209,
 * OWASP A05:2021 / A09:2021).</strong> The Spring Boot "whitelabel" error page,
 * which is what Spring MVC renders when no controller advice is registered,
 * exposes stack frames, internal class names, package names, and exception
 * messages to remote callers. Those details give an attacker a free
 * reconnaissance map of the application's internal structure (e.g. the FQCN of
 * the persistence package, the Hibernate dialect in use, or the exact JDBC
 * error returned by the database driver). This advice replaces that default
 * behavior with a structured JSON envelope whose payload is intentionally
 * narrow: {@code timestamp}, {@code status}, {@code error}, and (only for
 * validation handlers) a list of per-field issues. Internal exception messages
 * and stack traces are logged server-side through SLF4J but are never written
 * to the HTTP response body.
 *
 * <p><strong>Wiring.</strong> Spring auto-discovers this class through the
 * {@code @RestControllerAdvice} stereotype during component-scanning of the
 * {@code com.jspider.spring_boot_simple_crud_with_mysql} root package (rooted
 * in the {@code @SpringBootApplication} class). No explicit {@code @Bean}
 * declaration is required anywhere else. The advice applies globally to every
 * {@code @RestController} in the application; no {@code basePackages} narrowing
 * is configured.
 *
 * <p><strong>Handler coverage.</strong> Five {@link ExceptionHandler} methods
 * are declared:
 * <ul>
 *   <li>{@link #handleValidation(MethodArgumentNotValidException)} &rarr;
 *       HTTP&nbsp;400 for {@code @Valid @RequestBody} binding failures.</li>
 *   <li>{@link #handleConstraintViolation(ConstraintViolationException)} &rarr;
 *       HTTP&nbsp;400 for class-level {@code @Validated} + method-parameter
 *       constraint failures (path variables, request parameters).</li>
 *   <li>{@link #handleAccessDenied(AccessDeniedException)} &rarr;
 *       HTTP&nbsp;403 for {@code @PreAuthorize} denials.</li>
 *   <li>{@link #handleAuthentication(AuthenticationException)} &rarr;
 *       HTTP&nbsp;401 for authentication failures (defense-in-depth: Spring
 *       Security's {@code BasicAuthenticationEntryPoint} normally handles this
 *       upstream of the advice chain).</li>
 *   <li>{@link #handleGeneric(Exception)} &rarr;
 *       HTTP&nbsp;500 catch-all for any uncaught {@link Exception} subclass
 *       (including bare {@link RuntimeException}s thrown by the DAO layer).</li>
 * </ul>
 *
 * <p><strong>Thread-safety.</strong> Every handler builds a fresh
 * {@link HashMap} per invocation and never mutates shared state, so concurrent
 * exception flows on different request threads cannot interfere with each
 * other's response bodies. The only instance-level state is the static
 * {@link Logger}, which SLF4J guarantees to be thread-safe.
 *
 * <p><strong>What this handler intentionally does NOT do.</strong>
 * <ul>
 *   <li>It does not write a Basic-authentication challenge response header.
 *       Spring Security's default {@code BasicAuthenticationEntryPoint} adds
 *       that header on the upstream filter-chain path; duplicating it here
 *       would produce conflicting headers in defense-in-depth scenarios.</li>
 *   <li>It does not propagate {@code ex.getMessage()} from
 *       {@link AccessDeniedException} or {@link AuthenticationException} into
 *       the HTTP body. Those messages can encode role names, scheme names, or
 *       realm hints that constitute meaningful reconnaissance data.</li>
 *   <li>It does not include the underlying {@link Exception} class name, FQCN,
 *       or stack frames in any response body.</li>
 * </ul>
 *
 * @see org.springframework.web.bind.annotation.RestControllerAdvice
 * @see org.springframework.web.bind.annotation.ExceptionHandler
 * @see <a href="https://cwe.mitre.org/data/definitions/209.html">CWE-209: Generation of Error Message Containing Sensitive Information</a>
 * @see <a href="https://owasp.org/Top10/A05_2021-Security_Misconfiguration/">OWASP A05:2021 &mdash; Security Misconfiguration</a>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * SLF4J logger for this advice. Used exclusively for server-side logging
     * of exception details &mdash; the resulting log lines never propagate to
     * remote callers (CWE-209 mitigation). Declared {@code static final} so it
     * is initialized once at class-load time and remains thread-safe under
     * concurrent invocation.
     */
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Response key for the ISO-8601 timestamp at which the error was rendered.
     * Centralized as a constant so all five handlers use the same body shape.
     */
    private static final String KEY_TIMESTAMP = "timestamp";

    /**
     * Response key for the numeric HTTP status code (mirrors the response line
     * status). Useful to clients that parse the body but not the status line.
     */
    private static final String KEY_STATUS = "status";

    /**
     * Response key for the short, human-readable error category. The value is
     * always a fixed literal &mdash; never an interpolated exception message
     * &mdash; to prevent CWE-209 information disclosure.
     */
    private static final String KEY_ERROR = "error";

    /**
     * Response key for the long-form generic message used by the catch-all
     * 500 handler. Other handlers omit this key in favor of {@code fieldErrors}
     * or {@code violations} arrays.
     */
    private static final String KEY_MESSAGE = "message";

    /**
     * Response key for the {@link MethodArgumentNotValidException} field-error
     * list emitted by {@link #handleValidation}.
     */
    private static final String KEY_FIELD_ERRORS = "fieldErrors";

    /**
     * Response key for the {@link ConstraintViolationException} violation list
     * emitted by {@link #handleConstraintViolation}.
     */
    private static final String KEY_VIOLATIONS = "violations";

    /**
     * Handles {@link MethodArgumentNotValidException} raised when a
     * {@code @Valid @RequestBody} parameter fails Jakarta Bean Validation
     * constraints (e.g. {@code @NotBlank}, {@code @Size}, {@code @Positive}).
     *
     * <p>Triggered by Spring MVC's argument-resolution layer when binding a
     * JSON payload onto a constrained DTO such as {@code ProductRequestDto}.
     * The returned response is HTTP&nbsp;400 (Bad Request) with a structured
     * body that enumerates each rejected field, the value the client supplied,
     * and the constraint-defined error message. The exception class name and
     * stack frames are intentionally omitted.
     *
     * <p>The {@code fieldErrors} array is built fresh per request from the
     * {@link MethodArgumentNotValidException#getBindingResult() BindingResult}
     * carried by the exception. Each entry is a small {@link HashMap} with
     * three keys: {@code field} (the property path), {@code rejectedValue}
     * (the offending value, which Jackson serializes as-is &mdash; including
     * {@code null}), and {@code message} (the constraint's
     * {@link FieldError#getDefaultMessage() default message}).
     *
     * @param ex the validation exception raised by Spring's argument resolver;
     *           guaranteed non-{@code null} by the framework dispatch
     * @return an HTTP&nbsp;400 response whose body contains the structured
     *         {@code fieldErrors} list
     * @see org.springframework.web.bind.MethodArgumentNotValidException
     * @see org.springframework.validation.FieldError
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put(KEY_TIMESTAMP, Instant.now().toString());
        body.put(KEY_STATUS, HttpStatus.BAD_REQUEST.value());
        body.put(KEY_ERROR, "Validation Failed");

        // Iterate over per-field errors from the BindingResult. The list is
        // fresh per call (not a shared field) so this method remains
        // thread-safe under concurrent validation failures.
        List<Map<String, Object>> fieldErrors = new ArrayList<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            Map<String, Object> fieldError = new HashMap<>();
            fieldError.put("field", fe.getField());
            fieldError.put("rejectedValue", fe.getRejectedValue());
            fieldError.put("message", fe.getDefaultMessage());
            fieldErrors.add(fieldError);
        }
        body.put(KEY_FIELD_ERRORS, fieldErrors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * Handles {@link ConstraintViolationException} raised when class-level
     * {@code @Validated} on a controller combines with method-parameter
     * constraints (e.g. {@code @Positive @PathVariable Integer id}) and the
     * caller supplies a value that violates the constraint.
     *
     * <p>This is distinct from {@link MethodArgumentNotValidException}:
     * {@code MethodArgumentNotValidException} fires on {@code @Valid}-annotated
     * request-body binding, whereas {@code ConstraintViolationException} fires
     * on {@code @Validated}-class method-parameter validation. Both map to
     * HTTP&nbsp;400 (Bad Request) here.
     *
     * <p>The returned body carries a {@code violations} array; each entry is a
     * small {@link HashMap} containing {@code propertyPath} (the qualified
     * path to the offending parameter, e.g.
     * {@code getProductByIdController.id}), {@code invalidValue} (the value
     * the caller supplied), and {@code message} (the constraint's message).
     * The exception class name and stack frames are intentionally omitted.
     *
     * @param ex the constraint-violation exception raised by Hibernate
     *           Validator under the class-level {@code @Validated} aspect;
     *           guaranteed non-{@code null} by the framework dispatch
     * @return an HTTP&nbsp;400 response whose body contains the structured
     *         {@code violations} list
     * @see jakarta.validation.ConstraintViolationException
     * @see jakarta.validation.ConstraintViolation
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(ConstraintViolationException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put(KEY_TIMESTAMP, Instant.now().toString());
        body.put(KEY_STATUS, HttpStatus.BAD_REQUEST.value());
        body.put(KEY_ERROR, "Constraint Violation");

        // Iterate over the set of violations on this exception. As with
        // handleValidation, the list is fresh per call.
        List<Map<String, Object>> violations = new ArrayList<>();
        for (ConstraintViolation<?> cv : ex.getConstraintViolations()) {
            Map<String, Object> violation = new HashMap<>();
            violation.put("propertyPath", cv.getPropertyPath().toString());
            violation.put("invalidValue", cv.getInvalidValue());
            violation.put("message", cv.getMessage());
            violations.add(violation);
        }
        body.put(KEY_VIOLATIONS, violations);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * Handles {@link AccessDeniedException} raised when an authenticated
     * caller invokes an endpoint protected by {@code @PreAuthorize}
     * (or equivalent method-security expression) and the caller's authorities
     * do not satisfy the expression.
     *
     * <p>For example, a user with role {@code USER} invoking
     * {@code DELETE /product/deleteProductByPrice/{price}} (annotated with
     * {@code @PreAuthorize("hasRole('ADMIN')")}) triggers this handler. The
     * response is HTTP&nbsp;403 (Forbidden) with a body that contains only the
     * timestamp, status, and a fixed {@code error} string &mdash; no
     * principal name, role name, permission name, or internal exception
     * message is propagated.
     *
     * <p><strong>Why no exception message in the body?</strong>
     * {@code ex.getMessage()} on a Spring Security {@code AccessDeniedException}
     * commonly encodes the rejected authority (e.g. "Access is denied" plus
     * implementation-specific role hints in subclasses). Exposing that string
     * to a remote caller would leak role-naming conventions and is a
     * CWE-209 anti-pattern.
     *
     * <p><strong>Defense-in-depth note.</strong> Spring Security's
     * {@code AccessDeniedHandlerImpl} (configured by default on the filter
     * chain) intercepts {@code AccessDeniedException} <em>before</em> it
     * reaches {@code @RestControllerAdvice} in most configurations. This
     * handler exists to ensure a consistent JSON response shape when the
     * exception does propagate &mdash; e.g. from a controller method, a
     * post-handler interceptor, or a custom voter.
     *
     * @param ex the access-denied exception raised by Spring Security;
     *           guaranteed non-{@code null} by the framework dispatch
     * @return an HTTP&nbsp;403 response whose body contains only fixed
     *         minimal-disclosure fields
     * @see org.springframework.security.access.AccessDeniedException
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        // Log at DEBUG only. INFO/WARN logs are typically forwarded to log
        // aggregators (e.g. ELK / Splunk), and the exception message itself
        // can encode the rejected authority/role; surfacing it at INFO/WARN
        // would persist that data outside the application boundary.
        log.debug("Access denied: {}", ex.getMessage());

        Map<String, Object> body = new HashMap<>();
        body.put(KEY_TIMESTAMP, Instant.now().toString());
        body.put(KEY_STATUS, HttpStatus.FORBIDDEN.value());
        body.put(KEY_ERROR, "Access Denied");
        // INTENTIONAL: no ex.getMessage(), no principal name, no role name,
        // no permission name. CWE-209 mitigation.
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    /**
     * Handles {@link AuthenticationException} raised when authentication fails
     * at any layer where the exception propagates to the controller-advice
     * stack (e.g. a controller that performs its own authentication, a custom
     * filter that re-throws, or a downstream service that uses the same
     * exception type).
     *
     * <p>The response is HTTP&nbsp;401 (Unauthorized) with a body that
     * contains only the timestamp, status, and a fixed {@code error} string.
     * No realm name, no authentication-scheme name, and no internal exception
     * message is propagated.
     *
     * <p><strong>Why no exception message in the body?</strong> Spring
     * Security's {@code AuthenticationException} subclasses (e.g.
     * {@code BadCredentialsException}, {@code DisabledException},
     * {@code LockedException}) carry messages that distinguish between
     * "user not found" and "wrong password" / "account locked", etc. Such
     * distinctions are a classic
     * <a href="https://owasp.org/www-community/attacks/Credential_stuffing">credential
     * stuffing</a> and user-enumeration aid. The CWE-209-conformant pattern
     * is to return one identical 401 response for every authentication
     * failure mode.
     *
     * <p><strong>Defense-in-depth note.</strong> Spring Security's
     * {@code BasicAuthenticationEntryPoint} (configured by default on the
     * filter chain when HTTP Basic is enabled) intercepts authentication
     * failures <em>before</em> they reach the advice stack and writes its own
     * 401 response with a Basic-authentication challenge header. This handler
     * exists to ensure a consistent JSON shape if the exception does
     * propagate; it deliberately does NOT add any authentication-challenge
     * header here, to avoid conflicting with the upstream entry point.
     *
     * @param ex the authentication exception raised somewhere in the request
     *           dispatch; guaranteed non-{@code null} by the framework
     *           dispatch
     * @return an HTTP&nbsp;401 response whose body contains only fixed
     *         minimal-disclosure fields
     * @see org.springframework.security.core.AuthenticationException
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthentication(AuthenticationException ex) {
        // Log at DEBUG only. Subclass messages (e.g. BadCredentialsException)
        // can distinguish "user not found" from "wrong password", a known
        // user-enumeration aid; we do not want such distinctions in INFO/WARN
        // logs that may be aggregated outside the application boundary.
        log.debug("Authentication failure: {}", ex.getMessage());

        Map<String, Object> body = new HashMap<>();
        body.put(KEY_TIMESTAMP, Instant.now().toString());
        body.put(KEY_STATUS, HttpStatus.UNAUTHORIZED.value());
        body.put(KEY_ERROR, "Authentication Required");
        // INTENTIONAL: no ex.getMessage(), no realm name, no scheme name,
        // no cause text. CWE-209 mitigation.
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    /**
     * Catch-all handler for any {@link Exception} subclass that no more
     * specific handler claims. This is the final safety net that prevents the
     * Spring Boot whitelabel error page from rendering, and that prevents
     * uncaught {@link RuntimeException} messages from leaking to the remote
     * caller.
     *
     * <p>A representative trigger is the bare
     * {@code throw new RuntimeException("Product not found with ID: " + id)}
     * in the DAO layer. That message contains the rejected id, which is
     * useful in server logs but is a CWE-209 leak when echoed to the client.
     * This handler logs the original exception (including stack trace)
     * server-side via the two-argument {@code log.error(String, Throwable)}
     * form, then returns a fixed-shape HTTP&nbsp;500 body whose
     * {@code message} value is the literal {@code "An unexpected error
     * occurred"} &mdash; never an interpolation of {@code ex.getMessage()}.
     *
     * <p>Ordering: Spring resolves {@code @ExceptionHandler} methods by
     * matching the most specific assignable type first, so handlers above
     * (e.g. {@link #handleValidation}) win over this catch-all when their
     * exception type matches. {@link Exception} is the broadest java
     * exception base class for our purposes; {@link Error} subclasses
     * (e.g. {@link OutOfMemoryError}) are not handled here by design &mdash;
     * the JVM should be allowed to fail fast on those.
     *
     * @param ex any otherwise-unhandled exception that propagates to the
     *           dispatcher servlet; guaranteed non-{@code null} by the
     *           framework dispatch
     * @return an HTTP&nbsp;500 response whose body contains only fixed,
     *         generic minimal-disclosure fields
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        // Two-argument log.error logs the full stack trace through the SLF4J
        // appender to the server-side log. This is the ONLY place the
        // original exception is observable; nothing about it reaches the
        // HTTP response body.
        log.error("Unhandled exception", ex);

        Map<String, Object> body = new HashMap<>();
        body.put(KEY_TIMESTAMP, Instant.now().toString());
        body.put(KEY_STATUS, HttpStatus.INTERNAL_SERVER_ERROR.value());
        body.put(KEY_ERROR, "Internal Server Error");
        // INTENTIONAL: hard-coded literal, NOT ex.getMessage(). CWE-209
        // mitigation: the original exception message can encode SQL state,
        // entity ids, file paths, or stack-frame package names.
        body.put(KEY_MESSAGE, "An unexpected error occurred");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
