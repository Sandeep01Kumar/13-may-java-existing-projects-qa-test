package com.jspider.spring_boot_simple_crud_with_mysql.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// Rule Applied
/**
 * Request payload for {@code POST /auth/register}.
 *
 * <p>The {@code @NotBlank} and {@code @Size} constraints below address QA
 * finding F-2 (empty / whitespace-only usernames being accepted into the
 * {@code users} table with HTTP 200). When {@code AuthController.register}
 * is annotated with {@code @Valid}, Spring's
 * {@code RequestMappingHandlerAdapter} runs Jakarta Bean Validation on this
 * DTO and throws {@code MethodArgumentNotValidException} if any constraint
 * fails — which {@code GlobalExceptionHandler} then translates into a
 * <em>400 Bad Request</em> response.
 *
 * <p>The {@code username} size cap of 50 sits well under the
 * {@code varchar(255)} column width (so the DB constraint cannot trip
 * first) and gives a sensible client-side error message. The
 * {@code password} cap of 72 matches the BCrypt 72-byte boundary that
 * Spring Security 6's {@code BCryptPasswordEncoder} enforces — converting
 * the previously misleading 401-via-{@code /error} into a deterministic
 * 400 at the validation layer. The {@code role} field is left
 * unconstrained because {@code AuthService.register} explicitly defaults
 * null/blank values to {@code "USER"}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {

	@NotBlank(message = "must not be blank")
	@Size(min = 3, max = 50, message = "must be between 3 and 50 characters")
	private String username;

	@NotBlank(message = "must not be blank")
	@Size(min = 1, max = 72, message = "must be between 1 and 72 characters")
	private String password;

	private String role;
}
