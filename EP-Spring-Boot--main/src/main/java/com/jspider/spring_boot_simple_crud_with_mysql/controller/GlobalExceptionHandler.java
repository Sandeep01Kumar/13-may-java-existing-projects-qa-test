package com.jspider.spring_boot_simple_crud_with_mysql.controller;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.servlet.http.HttpServletRequest;

// Rule Applied
/**
 * Centralised exception translator for the application's REST API surface.
 *
 * <p>Before this advice existed, any exception thrown from an
 * {@code /auth/**} endpoint (e.g. duplicate-username {@link RuntimeException}
 * from {@code AuthService.register()}, malformed-JSON
 * {@link HttpMessageNotReadableException}, wrong-method
 * {@link HttpRequestMethodNotSupportedException}, etc.) bubbled up out of
 * the {@code DispatcherServlet} and was forwarded by Spring Boot's
 * {@code ErrorMvcAutoConfiguration} to the internal {@code /error} path.
 * Because {@code /error} was not in {@code SecurityConfig#permitAll}, the
 * filter chain rejected it and {@code JwtAuthEntryPoint} returned a
 * misleading <em>401 Unauthorized</em> with {@code path:"/error"} that lost
 * the original error context — the symptom catalogued as QA finding F-1.
 *
 * <p>This advice intercepts each of those exception types at the
 * {@code ExceptionHandlerExceptionResolver} layer (which runs <em>before</em>
 * the {@code /error} dispatch) and emits a domain-appropriate HTTP status
 * and JSON body. Client-input errors are logged at {@code WARN} level
 * without stack traces, which also addresses QA finding F-3.
 *
 * <p><strong>Important:</strong> Spring Security's
 * {@code AuthenticationException} family (e.g. {@code BadCredentialsException}
 * from {@code /auth/login} with a wrong password) is handled by
 * {@link #handleAuthenticationException} which emits a 401 response with
 * the same JSON shape as {@code JwtAuthEntryPoint}. This preserves the
 * verified-correct contract
 * ({@code {"status":401,"error":"Unauthorized","message":"Bad credentials","path":"/auth/login"}})
 * while preventing the catch-all {@link #handleAnyOtherException} from
 * incorrectly mapping the {@code AuthenticationException} to a 500
 * (since {@code AuthenticationException} extends {@code RuntimeException}
 * which extends {@code Exception}).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	/**
	 * Handles application-thrown {@link RuntimeException}s. The most common
	 * source today is {@code AuthService.register()} throwing
	 * {@code new RuntimeException("Username already exists: ...")} when the
	 * uniqueness check fails — the exact case the QA report flagged as
	 * F-1. The message is inspected for {@code "already exists"} and mapped
	 * to <em>409 Conflict</em>; every other {@code RuntimeException} maps to
	 * <em>500 Internal Server Error</em> so the caller can distinguish a
	 * business-rule conflict from an unexpected server-side failure.
	 */
	@ExceptionHandler(RuntimeException.class)
	public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException ex, HttpServletRequest request) {
		System.out.println("GlobalExceptionHandler.handleRuntimeException: " + ex.getClass().getSimpleName()
				+ " on " + request.getRequestURI() + " — " + ex.getMessage());
		String message = ex.getMessage() == null ? "Unexpected server error" : ex.getMessage();
		if (message.toLowerCase().contains("already exists")) {
			log.warn("Conflict on {}: {}", request.getRequestURI(), message);
			return buildResponse(HttpStatus.CONFLICT, "Conflict", message, request);
		}
		log.error("Unhandled runtime exception on {}: {}", request.getRequestURI(), message, ex);
		return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", message, request);
	}

	/**
	 * Handles {@link IllegalArgumentException}s. The principal source is
	 * {@code BCryptPasswordEncoder.encode(...)} rejecting passwords longer
	 * than 72 bytes ({@code "password cannot be more than 72 bytes"}) and
	 * the {@code "rawPassword cannot be null"} thrown when a {@code null}
	 * password is encoded. Both are client-input violations and map to
	 * <em>400 Bad Request</em>.
	 */
	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
		System.out.println("GlobalExceptionHandler.handleIllegalArgument on " + request.getRequestURI()
				+ ": " + ex.getMessage());
		String message = ex.getMessage() == null ? "Invalid request argument" : ex.getMessage();
		log.warn("IllegalArgument on {}: {}", request.getRequestURI(), message);
		return buildResponse(HttpStatus.BAD_REQUEST, "Bad Request", message, request);
	}

	/**
	 * Handles malformed or empty request bodies surfaced by Spring as
	 * {@link HttpMessageNotReadableException}. Examples are
	 * {@code curl ... -d '{invalid json'} and {@code curl ... -d ''}. Both
	 * represent client-side payload errors and map to <em>400 Bad Request</em>.
	 */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<Map<String, Object>> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
			HttpServletRequest request) {
		System.out.println("GlobalExceptionHandler.handleHttpMessageNotReadable on " + request.getRequestURI()
				+ ": " + ex.getMostSpecificCause().getClass().getSimpleName());
		log.warn("Malformed request body on {}: {}", request.getRequestURI(),
				ex.getMostSpecificCause().getMessage());
		// Do not echo the (potentially verbose / stack-trace-flavoured)
		// Jackson parser exception message; return a stable, terse, but
		// useful client-facing description instead.
		return buildResponse(HttpStatus.BAD_REQUEST, "Bad Request",
				"Malformed JSON request body or unreadable payload", request);
	}

	/**
	 * Handles bean-validation failures triggered by {@code @Valid @RequestBody}
	 * (added on {@code AuthController.register} to enforce F-2's empty
	 * username constraint). Field-level errors are collected into a
	 * {@code fields} map for client debuggability and mapped to
	 * <em>400 Bad Request</em>.
	 */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<Map<String, Object>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpServletRequest request) {
		System.out.println("GlobalExceptionHandler.handleMethodArgumentNotValid on " + request.getRequestURI()
				+ ": " + ex.getBindingResult().getFieldErrorCount() + " field error(s)");
		Map<String, String> fields = ex.getBindingResult().getFieldErrors().stream()
				.collect(Collectors.toMap(
						FieldError::getField,
						fe -> fe.getDefaultMessage() == null ? "invalid value" : fe.getDefaultMessage(),
						(a, b) -> a, // keep first message if duplicates
						LinkedHashMap::new));
		String summary = fields.entrySet().stream()
				.map(entry -> entry.getKey() + " " + entry.getValue())
				.collect(Collectors.joining("; "));
		log.warn("Validation failure on {}: {}", request.getRequestURI(), summary);
		Map<String, Object> body = baseBody(HttpStatus.BAD_REQUEST, "Bad Request",
				"Validation failed: " + summary, request);
		body.put("fields", fields);
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
	}

	/**
	 * Handles unsupported request content types (e.g. {@code text/plain},
	 * {@code application/x-www-form-urlencoded}, missing {@code Content-Type})
	 * surfaced by Spring MVC as {@link HttpMediaTypeNotSupportedException}.
	 * Maps to <em>415 Unsupported Media Type</em>.
	 */
	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	public ResponseEntity<Map<String, Object>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex,
			HttpServletRequest request) {
		System.out.println("GlobalExceptionHandler.handleMediaTypeNotSupported on " + request.getRequestURI()
				+ ": contentType=" + ex.getContentType());
		String contentType = ex.getContentType() == null ? "(none)" : ex.getContentType().toString();
		log.warn("Unsupported media type on {}: {}", request.getRequestURI(), contentType);
		return buildResponse(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported Media Type",
				"Content-Type '" + contentType + "' is not supported. Use 'application/json'.", request);
	}

	/**
	 * Handles wrong-method invocations (e.g. {@code GET /auth/login} when only
	 * {@code POST} is mapped) surfaced as
	 * {@link HttpRequestMethodNotSupportedException}. Maps to
	 * <em>405 Method Not Allowed</em>.
	 */
	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<Map<String, Object>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
			HttpServletRequest request) {
		System.out.println("GlobalExceptionHandler.handleMethodNotSupported on " + request.getRequestURI()
				+ ": method=" + ex.getMethod());
		log.warn("Method not allowed on {}: {} (supported: {})",
				request.getRequestURI(), ex.getMethod(),
				ex.getSupportedHttpMethods() == null ? "[]" : ex.getSupportedHttpMethods());
		return buildResponse(HttpStatus.METHOD_NOT_ALLOWED, "Method Not Allowed",
				"HTTP method '" + ex.getMethod() + "' is not supported on this endpoint.", request);
	}

	/**
	 * Handles {@link NoResourceFoundException} thrown by Spring MVC when an
	 * incoming request matches neither a registered controller nor a static
	 * resource (for example {@code GET /student/findAllStudent} — a path
	 * the QA report's contract tests never hit but a typo'd client request
	 * easily could). Without this handler the catch-all
	 * {@link #handleAnyOtherException} would convert the framework's
	 * native 404 condition into a misleading 500. Maps to
	 * <em>404 Not Found</em>.
	 */
	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<Map<String, Object>> handleNoResourceFound(NoResourceFoundException ex,
			HttpServletRequest request) {
		System.out.println("GlobalExceptionHandler.handleNoResourceFound on " + request.getRequestURI()
				+ ": " + ex.getMessage());
		log.warn("Not found on {}: {}", request.getRequestURI(), ex.getMessage());
		return buildResponse(HttpStatus.NOT_FOUND, "Not Found",
				"No endpoint matches the requested path.", request);
	}

	/**
	 * Handles persistence-layer constraint violations
	 * ({@link DataIntegrityViolationException}). The most common trigger is
	 * a {@code username} value that exceeds the {@code varchar(255)} column
	 * width or violates the {@code UNIQUE} index. Both are client-input
	 * problems and map to <em>400 Bad Request</em>.
	 */
	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<Map<String, Object>> handleDataIntegrityViolation(DataIntegrityViolationException ex,
			HttpServletRequest request) {
		System.out.println("GlobalExceptionHandler.handleDataIntegrityViolation on " + request.getRequestURI()
				+ ": " + ex.getMostSpecificCause().getClass().getSimpleName());
		log.warn("Data integrity violation on {}: {}", request.getRequestURI(),
				ex.getMostSpecificCause().getMessage());
		return buildResponse(HttpStatus.BAD_REQUEST, "Bad Request",
				"Request violates a database constraint (e.g. value too long, or duplicate key).",
				request);
	}

	/**
	 * Handles {@link AuthenticationException}s thrown from {@code /auth/login}
	 * (typically {@code BadCredentialsException} from
	 * {@code AuthenticationManager.authenticate(...)} when credentials are
	 * wrong, missing, or the user does not exist).
	 *
	 * <p>The response body intentionally mirrors the shape emitted by
	 * {@code JwtAuthEntryPoint} so that API consumers see a uniform 401
	 * payload regardless of whether the failure was detected at the
	 * Spring Security filter chain (no/expired/tampered token on
	 * {@code /product/**}) or at the controller-advice layer (wrong
	 * credentials on {@code /auth/login}). Without this handler, the
	 * catch-all {@link #handleAnyOtherException} would intercept
	 * {@code AuthenticationException} (which extends {@code Exception})
	 * and incorrectly map it to <em>500 Internal Server Error</em>.
	 */
	@ExceptionHandler(AuthenticationException.class)
	public ResponseEntity<Map<String, Object>> handleAuthenticationException(AuthenticationException ex,
			HttpServletRequest request) {
		System.out.println("GlobalExceptionHandler.handleAuthenticationException: "
				+ ex.getClass().getSimpleName() + " on " + request.getRequestURI()
				+ " — " + ex.getMessage());
		String message = ex.getMessage() == null ? "Unauthorized" : ex.getMessage();
		log.warn("Authentication failure on {}: {}", request.getRequestURI(), message);
		return buildResponse(HttpStatus.UNAUTHORIZED, "Unauthorized", message, request);
	}

	/**
	 * Catch-all for any exception not handled by a more specific
	 * {@code @ExceptionHandler} above. This guarantees that no exception
	 * leaks to the {@code /error} dispatcher path and produces a
	 * misleading 401 (the original F-1 symptom). Returns a generic
	 * <em>500 Internal Server Error</em>.
	 */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<Map<String, Object>> handleAnyOtherException(Exception ex, HttpServletRequest request) {
		System.out.println("GlobalExceptionHandler.handleAnyOtherException: " + ex.getClass().getSimpleName()
				+ " on " + request.getRequestURI() + " — " + ex.getMessage());
		log.error("Unhandled exception on {}: {}", request.getRequestURI(), ex.getMessage(), ex);
		String message = ex.getMessage() == null ? "Unexpected server error" : ex.getMessage();
		return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", message, request);
	}

	/**
	 * Assembles the canonical response body and wraps it in a
	 * {@link ResponseEntity}. The body keys ({@code status}, {@code error},
	 * {@code message}, {@code path}) match the shape already emitted by
	 * {@code JwtAuthEntryPoint} so that the API's error contract is
	 * consistent across both authentication-related 401s and
	 * application-layer error statuses.
	 */
	private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String error, String message,
			HttpServletRequest request) {
		System.out.println("GlobalExceptionHandler.buildResponse: status=" + status.value()
				+ " path=" + request.getRequestURI());
		return ResponseEntity.status(status).body(baseBody(status, error, message, request));
	}

	/**
	 * Builds the mutable base map used by {@link #buildResponse} and any
	 * handler that needs to attach additional structured fields (for
	 * example {@link #handleMethodArgumentNotValid} attaches a
	 * {@code fields} map of per-field validation messages).
	 */
	private Map<String, Object> baseBody(HttpStatus status, String error, String message,
			HttpServletRequest request) {
		System.out.println("GlobalExceptionHandler.baseBody: composing body for status=" + status.value());
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("status", status.value());
		body.put("error", error);
		body.put("message", message);
		body.put("path", request.getRequestURI());
		return body;
	}
}
