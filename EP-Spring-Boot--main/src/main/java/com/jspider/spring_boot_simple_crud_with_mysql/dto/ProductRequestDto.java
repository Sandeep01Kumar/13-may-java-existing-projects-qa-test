package com.jspider.spring_boot_simple_crud_with_mysql.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Request-side Data Transfer Object for product create and update operations.
 *
 * <p><strong>Security purpose &mdash; Mass-Assignment Defense (CWE-915, OWASP A08:2021).</strong>
 * This record deliberately <em>omits</em> any {@code id} component so that a client-supplied
 * {@code id} value in the JSON request body (e.g.
 * {@code {"id":999,"name":"x","color":"y","price":1.0}}) cannot be bound to a server-side
 * field. Jackson's default {@code FAIL_ON_UNKNOWN_PROPERTIES=false} silently drops the
 * unknown {@code id} property because no record component declares it &mdash; there is no
 * setter, no field, and no accessor to receive it. The persistence-side {@code id} on the
 * {@code Product} entity is generated exclusively by the database, never influenced by
 * client input. This pattern is the canonical mass-assignment defense documented in the
 * OWASP Mass Assignment Cheat Sheet.
 *
 * <p><strong>Security purpose &mdash; Input Validation Boundary (CWE-20, OWASP A03:2021).</strong>
 * Each record component carries Jakarta Bean Validation 3.x constraints that Spring/Hibernate
 * Validator evaluates at request-binding time when the controller method parameter is
 * annotated {@code @Valid}. Constraint violations raise
 * {@link org.springframework.web.bind.MethodArgumentNotValidException}, which the
 * application's {@code GlobalExceptionHandler} translates into an HTTP 400 response with a
 * structured error body. The constraints applied here are:
 * <ul>
 *   <li>{@code name}: {@link NotBlank} (rejects null/empty/whitespace-only) and
 *       {@link Size}{@code (max = 100)} (bounds payload length and aligns with the database
 *       column size).</li>
 *   <li>{@code color}: {@link NotBlank} and {@link Size}{@code (max = 50)} (same rationale,
 *       narrower upper bound).</li>
 *   <li>{@code price}: {@link NotNull} (catches JSON omission &mdash; boxed {@link Double}
 *       is required for this to be meaningful, since a primitive {@code double} would
 *       silently default to {@code 0.0}), {@link Positive} (rejects zero and negative
 *       values), and {@link DecimalMax}{@code ("99999.99")} (upper bound; the string
 *       argument is mandated by the Java annotation parameter restriction that disallows
 *       {@link java.math.BigDecimal} literals).</li>
 * </ul>
 *
 * <p><strong>Type-design rationale.</strong> A {@link Record} is used in preference to a
 * mutable bean-style class because records are immutable and tamper-proof after
 * construction &mdash; there is no mutating API, so neither a malicious interceptor nor a
 * downstream aspect can mutate the bound payload before it reaches the controller. The
 * boxed {@link Double} on {@code price} is deliberate (see {@link NotNull} rationale above).
 * The companion entity {@code Product} continues to use primitive {@code double} for
 * persistence; the controller layer maps DTO {@link Double} &rarr; entity {@code double}
 * via auto-unboxing.
 *
 * <p><strong>Record accessors.</strong> Per JLS &sect;8.10, this record auto-generates
 * canonical accessors named {@link #name()}, {@link #color()}, and {@link #price()}
 * (component-name style, not JavaBean {@code getName()} style). Consumers (e.g.
 * {@code ProductController}) MUST use the {@code dto.name()} form when mapping to the
 * persistence entity.
 *
 * @param name  product display name; non-blank, at most 100 characters
 * @param color product color; non-blank, at most 50 characters
 * @param price product price; non-null, strictly positive, at most {@code 99999.99}
 *
 * @see jakarta.validation.Valid
 * @see <a href="https://cheatsheetseries.owasp.org/cheatsheets/Mass_Assignment_Cheat_Sheet.html">OWASP Mass Assignment Cheat Sheet</a>
 * @see <a href="https://cwe.mitre.org/data/definitions/915.html">CWE-915</a>
 * @see <a href="https://beanvalidation.org/3.0/spec/">Jakarta Bean Validation 3.0 Specification</a>
 */
public record ProductRequestDto(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 50) String color,
        @NotNull @Positive @DecimalMax("99999.99") Double price
) {
}
