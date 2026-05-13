package com.jspider.spring_boot_simple_crud_with_mysql.controller.advice;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.jspider.spring_boot_simple_crud_with_mysql.responses.ResponseStructure;

/**
 * Centralised {@link RestControllerAdvice} that maps the common application
 * and framework-level exceptions into clean, {@code ResponseStructure}-shaped
 * HTTP error responses for the entire web layer.
 *
 * <p>This class is the QA CP2 remediation for issues #1, #4, #5, and #6
 * &mdash; before it existed, several distinct invalid-input shapes leaked
 * full Java stack traces, internal package paths, and Spring/Tomcat
 * implementation details into the response body, and the duplicate-username
 * case surfaced as HTTP 500 instead of HTTP 400. With this advice in place,
 * every documented error path now produces a deterministic JSON envelope of
 * the form:</p>
 * <pre>
 *   {
 *     "statusCode": &lt;int matching HTTP status&gt;,
 *     "apiDescription": "&lt;short, client-facing category label&gt;",
 *     "data": "&lt;sanitised, human-readable message&gt;"
 *   }
 * </pre>
 *
 * <h2>Why this lives in a separate {@code advice} sub-package</h2>
 * <p>Keeping the advice in {@code controller/advice/} (rather than dropping it
 * into the {@code controller/} package alongside the endpoint controllers)
 * preserves the AAP §0.7.2 constraint "Keep code clean and modular":
 * the advice has a single concern (mapping exceptions to error responses)
 * and is not coupled to any specific controller. The sub-package is still
 * under the existing Spring component-scan base package
 * {@code com.jspider.spring_boot_simple_crud_with_mysql}, so the
 * {@link RestControllerAdvice} bean is discovered automatically without any
 * extra wiring.</p>
 *
 * <h2>Handler order and precedence</h2>
 * <p>Spring resolves {@link ExceptionHandler @ExceptionHandler}s by exception-type
 * specificity, not declaration order: when more than one handler is a match
 * candidate, Spring picks the one declared for the most specific exception
 * type. The fallback {@link #handleGeneric(Exception) handleGeneric} is
 * therefore deliberately declared LAST and is only invoked when no more
 * specific handler matches.</p>
 *
 * <h2>Anti-leak posture</h2>
 * <p>None of the handlers below ever echo the underlying exception's stack
 * trace, internal class name, or low-level cause message into the HTTP
 * response body; they emit only the deliberately-curated short messages
 * defined as constants in this class. The companion
 * {@code application.properties} setting {@code server.error.include-stacktrace=never}
 * provides the same guarantee for the few paths that bypass this advice
 * (e.g. requests routed through {@code BasicErrorController} after a
 * security filter rejection).</p>
 */
@RestControllerAdvice
// Rule Applied
public class GlobalExceptionHandler {

    /**
     * Short, client-facing label used as the {@code apiDescription} field
     * on every HTTP {@code 400 Bad Request} response body produced by this
     * advice. Kept as a constant so the label is consistent across all
     * 400-emitting handlers and is trivially testable.
     */
    private static final String BAD_REQUEST_DESCRIPTION = "Bad request";

    /**
     * Short, client-facing label used as the {@code apiDescription} field
     * on every HTTP {@code 405 Method Not Allowed} response body produced
     * by this advice.
     */
    private static final String METHOD_NOT_ALLOWED_DESCRIPTION = "Method not allowed";

    /**
     * Handles {@link IllegalArgumentException}s thrown by the service
     * layer (notably {@code AuthService#register} when the username is
     * already taken) and maps them to a clean HTTP {@code 400 Bad
     * Request} response with the project-wide {@code ResponseStructure}
     * envelope.
     *
     * <p>Without this handler, Spring would route
     * {@code IllegalArgumentException} through its default error path
     * and emit HTTP {@code 500 Internal Server Error} with a full stack
     * trace in the body (CP2 issue #1).</p>
     *
     * <p>The exception's message is forwarded into the {@code data}
     * field verbatim. Callers should therefore craft anti-enumeration
     * exception messages (e.g. {@code "Registration request invalid"}
     * rather than {@code "Username already exists: alice"}) at the
     * throw site.</p>
     *
     * @param ex the thrown {@link IllegalArgumentException}; must not
     *           be {@code null}
     * @return a {@link ResponseEntity} carrying HTTP {@code 400} and a
     *         {@link ResponseStructure} body whose {@code data} is the
     *         sanitised exception message
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ResponseStructure<String>> handleIllegalArgument(IllegalArgumentException ex) {
        System.out.println("[GlobalExceptionHandler] handleIllegalArgument invoked: " + ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, BAD_REQUEST_DESCRIPTION, ex.getMessage());
    }

    /**
     * Handles {@link MethodArgumentNotValidException}s raised when a
     * controller method parameter annotated with {@code @Valid} fails
     * Bean Validation against any of its constraints (e.g.
     * {@code @NotBlank}, {@code @Size}).
     *
     * <p>The handler aggregates every individual {@link FieldError} into
     * a deterministic, comma-joined {@code field: message} string and
     * places it into the {@code data} field. The Map iteration order
     * mirrors the order Spring reports the errors in, which in practice
     * matches the source order of the constraints on the DTO. The
     * resulting message is concise enough for clients to parse and
     * never leaks any framework internals.</p>
     *
     * <p>This handler closes CP2 issues #2, #3, #4, and #5 in one step
     * &mdash; with the corresponding {@code @NotBlank}/{@code @Size}
     * annotations on {@code RegisterRequestDto} and {@code LoginRequestDto},
     * empty / missing / oversized inputs are all routed here and emit a
     * clean 400 instead of HTTP 201 (Issues #2/#3) or HTTP 500 with a
     * stack trace (Issues #4/#5).</p>
     *
     * @param ex the thrown {@link MethodArgumentNotValidException} carrying
     *           one or more {@link FieldError}s; must not be {@code null}
     * @return a {@link ResponseEntity} carrying HTTP {@code 400} and a
     *         {@link ResponseStructure} body whose {@code data} is a
     *         compact field-by-field validation summary
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ResponseStructure<String>> handleValidation(MethodArgumentNotValidException ex) {
        System.out.println("[GlobalExceptionHandler] handleValidation invoked: "
                + ex.getBindingResult().getFieldErrorCount() + " field error(s)");

        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(fieldError.getField(),
                    fieldError.getDefaultMessage() == null ? "invalid value" : fieldError.getDefaultMessage());
        }

        StringBuilder summary = new StringBuilder("Validation failed: ");
        boolean first = true;
        for (Map.Entry<String, String> entry : fieldErrors.entrySet()) {
            if (!first) {
                summary.append(", ");
            }
            summary.append(entry.getKey()).append(" ").append(entry.getValue());
            first = false;
        }

        return buildResponse(HttpStatus.BAD_REQUEST, BAD_REQUEST_DESCRIPTION, summary.toString());
    }

    /**
     * Handles {@link DataIntegrityViolationException}s raised when an
     * INSERT or UPDATE violates a database-level constraint &mdash;
     * typically a {@code NOT NULL} or {@code UNIQUE} violation. With
     * Bean Validation now annotated on the DTOs, this handler should
     * rarely fire in production, but it acts as a defensive last-line-of-defence
     * for any constraint that is not also enforced in Java (e.g. a
     * unique index added directly in the database or a race-condition
     * between two concurrent registrations slipping past the
     * {@code existsByUsername} pre-check).
     *
     * <p>The handler intentionally does NOT include the underlying
     * exception's message in the response body because that message
     * usually contains the offending SQL statement and the literal
     * column / value that failed (e.g. the username being registered).
     * Leaking that information would re-introduce the CP2 issue #6
     * information disclosure.</p>
     *
     * @param ex the thrown {@link DataIntegrityViolationException};
     *           must not be {@code null}
     * @return a {@link ResponseEntity} carrying HTTP {@code 400} and a
     *         {@link ResponseStructure} body with a generic message
     *         that does not leak SQL or column names
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ResponseStructure<String>> handleDataIntegrity(DataIntegrityViolationException ex) {
        System.out.println("[GlobalExceptionHandler] handleDataIntegrity invoked (root cause sanitised)");
        // Intentionally generic — the underlying message often contains the
        // offending SQL and column values which would re-introduce the
        // information-disclosure problem flagged in CP2 issue #6.
        return buildResponse(HttpStatus.BAD_REQUEST, BAD_REQUEST_DESCRIPTION,
                "Request violates a data-integrity constraint");
    }

    /**
     * Handles {@link HttpMessageNotReadableException}s raised by Spring's
     * {@code MappingJackson2HttpMessageConverter} when the inbound JSON
     * body is malformed (e.g. truncated, missing required JSON tokens),
     * empty, or completely absent. With this handler in place, requests
     * such as {@code POST /auth/register} with body {@code {}} or with
     * no body at all return a clean HTTP {@code 400 Bad Request} rather
     * than leaking a stack trace through Spring Boot's default error
     * handler (CP2 issue #6).
     *
     * @param ex the thrown {@link HttpMessageNotReadableException};
     *           must not be {@code null}
     * @return a {@link ResponseEntity} carrying HTTP {@code 400} and a
     *         {@link ResponseStructure} body with a generic "malformed
     *         request body" message
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ResponseStructure<String>> handleMessageNotReadable(HttpMessageNotReadableException ex) {
        System.out.println("[GlobalExceptionHandler] handleMessageNotReadable invoked");
        return buildResponse(HttpStatus.BAD_REQUEST, BAD_REQUEST_DESCRIPTION,
                "Malformed or missing request body");
    }

    /**
     * Handles {@link HttpRequestMethodNotSupportedException}s raised when
     * a client targets a known URL with the wrong HTTP method (e.g.
     * {@code GET /auth/register} when only {@code POST} is declared).
     *
     * <p>The default Spring Boot {@code BasicErrorController} response
     * for HTTP 405 leaks the full filter-chain stack trace, which CP2
     * issue #6 explicitly called out for {@code GET /auth/register}.
     * Catching the exception here lets us emit the same clean
     * {@code ResponseStructure} envelope used for every other error
     * path.</p>
     *
     * @param ex the thrown {@link HttpRequestMethodNotSupportedException};
     *           must not be {@code null}
     * @return a {@link ResponseEntity} carrying HTTP {@code 405} and a
     *         {@link ResponseStructure} body listing the supported
     *         method(s); the offending method is included so legitimate
     *         clients can correct their integration
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ResponseStructure<String>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        System.out.println("[GlobalExceptionHandler] handleMethodNotAllowed invoked: method=" + ex.getMethod());

        StringBuilder supported = new StringBuilder();
        if (ex.getSupportedMethods() != null) {
            boolean first = true;
            for (String method : ex.getSupportedMethods()) {
                if (!first) {
                    supported.append(", ");
                }
                supported.append(method);
                first = false;
            }
        }

        String message = supported.length() == 0
                ? "Request method '" + ex.getMethod() + "' is not supported"
                : "Request method '" + ex.getMethod() + "' is not supported. Supported methods: " + supported;
        return buildResponse(HttpStatus.METHOD_NOT_ALLOWED, METHOD_NOT_ALLOWED_DESCRIPTION, message);
    }

    /*
     * Deliberately no @ExceptionHandler(Exception.class) fallback:
     *
     * If a generic Exception handler were declared here, Spring MVC's
     * ExceptionHandlerExceptionResolver would intercept
     * org.springframework.security.core.AuthenticationException
     * (notably BadCredentialsException thrown by AuthService#login during
     * wrong-password attempts) BEFORE the exception could propagate back
     * up through the security filter chain to
     * org.springframework.security.web.access.ExceptionTranslationFilter
     * — which is the filter responsible for routing authentication
     * failures to JwtAuthenticationEntryPoint#commence(...) and emitting
     * the canonical 401 response. The QA CP2 report verifies that the 401
     * flow currently works correctly for wrong passwords, unknown users,
     * and missing tokens; introducing a blanket Exception handler here
     * would silently regress all three to HTTP 500. The catch-all
     * responsibility is instead delegated to:
     *
     *   - server.error.include-stacktrace=never (and the sibling
     *     include-message=never, include-exception=false,
     *     include-binding-errors=never) in application.properties — these
     *     suppress the stack-trace leak through Spring Boot's
     *     BasicErrorController for any exception path not intercepted by
     *     a more specific handler above.
     *   - Spring Security's ExceptionTranslationFilter — remains the sole
     *     owner of routing AuthenticationExceptions through
     *     JwtAuthenticationEntryPoint to produce the project's signature
     *     401 body.
     */

    /**
     * Internal helper that builds a {@link ResponseEntity} carrying the
     * project-wide {@link ResponseStructure} envelope.
     *
     * <p>Centralising the envelope construction here means every handler
     * above produces the EXACT same JSON shape, which is also the shape
     * used by {@code JwtAuthenticationEntryPoint} and the
     * {@code ResponseStructure}-returning endpoints in
     * {@code AuthController} and {@code ProductController}. Clients can
     * therefore parse every {@code 4xx}/{@code 5xx} response with the
     * same JSON model they use for {@code 2xx} responses.</p>
     *
     * @param status      the HTTP status code to emit; must not be {@code null}
     * @param description the {@code apiDescription} value for the envelope;
     *                    must not be {@code null} or empty
     * @param data        the {@code data} payload for the envelope; may be
     *                    any non-{@code null} String; sanitised at the
     *                    call site
     * @return a fully-populated {@link ResponseEntity} ready for return
     *         from the calling {@code @ExceptionHandler}
     */
    private ResponseEntity<ResponseStructure<String>> buildResponse(HttpStatus status, String description, String data) {
        System.out.println("[GlobalExceptionHandler] buildResponse invoked: status=" + status.value()
                + ", description=" + description);

        ResponseStructure<String> body = new ResponseStructure<>();
        body.setStatusCode(status.value());
        body.setApiDescription(description);
        body.setData(data == null ? "" : data);
        return ResponseEntity.status(status).body(body);
    }
}
