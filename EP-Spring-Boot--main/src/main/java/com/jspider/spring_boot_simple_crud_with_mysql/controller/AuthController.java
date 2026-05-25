package com.jspider.spring_boot_simple_crud_with_mysql.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jspider.spring_boot_simple_crud_with_mysql.dto.AuthResponse;
import com.jspider.spring_boot_simple_crud_with_mysql.dto.LoginRequest;
import com.jspider.spring_boot_simple_crud_with_mysql.dto.RegisterRequest;
import com.jspider.spring_boot_simple_crud_with_mysql.service.AuthService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

// Rule Applied
@RestController
@RequestMapping(value = "/auth")
@CrossOrigin(value = "")
@Tag(name = "authcontroller", description = "JWT authentication endpoints")
public class AuthController {

	@Autowired
	AuthService authService;

	/**
	 * Registers a new user account.
	 *
	 * <p>The {@code @Valid} annotation triggers Jakarta Bean Validation on
	 * the inbound {@link RegisterRequest}, enforcing the
	 * {@code @NotBlank}/{@code @Size} constraints declared on its fields.
	 * Any constraint violation throws a
	 * {@code MethodArgumentNotValidException} that is translated by
	 * {@code GlobalExceptionHandler} into a <em>400 Bad Request</em>
	 * response with per-field error messages — closing QA finding F-2
	 * (empty/whitespace usernames previously accepted as HTTP 200).
	 *
	 * <p>{@code LoginRequest} is intentionally <strong>not</strong>
	 * {@code @Valid}-annotated so that authentication failures (including
	 * null/blank usernames or passwords) continue to surface as a uniform
	 * <em>401 "Bad credentials"</em> from Spring Security's
	 * {@code AuthenticationManager} — the behavior the QA report verified
	 * as correct and that prevents username-enumeration attacks via the
	 * login endpoint.
	 */
	@PostMapping(value = "/register")
	public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
		System.out.println("AuthController.register called for username: " + request.getUsername());
		AuthResponse response = authService.register(request);
		return ResponseEntity.ok(response);
	}

	@PostMapping(value = "/login")
	public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
		System.out.println("AuthController.login called for username: " + request.getUsername());
		AuthResponse response = authService.login(request);
		return ResponseEntity.ok(response);
	}
}
