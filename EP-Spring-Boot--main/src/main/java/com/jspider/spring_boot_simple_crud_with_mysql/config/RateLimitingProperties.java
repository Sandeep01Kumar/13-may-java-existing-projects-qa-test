package com.jspider.spring_boot_simple_crud_with_mysql.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tunable parameters for the per-IP rate-limiting filter
 * ({@code RateLimitingFilter}).
 *
 * <p>Bound from {@code application.properties} via Spring Boot 3.x
 * {@link ConfigurationProperties} relaxed binding and implicit record
 * constructor binding:
 * <ul>
 *   <li>{@code app.security.rate-limit.capacity} &rarr; {@link #capacity()}</li>
 *   <li>{@code app.security.rate-limit.refill-tokens} &rarr; {@link #refillTokens()}</li>
 *   <li>{@code app.security.rate-limit.refill-period-seconds} &rarr; {@link #refillPeriodSeconds()}</li>
 * </ul>
 *
 * <p><b>Registration:</b> this record is registered as a Spring bean via
 * {@code @EnableConfigurationProperties(RateLimitingProperties.class)} on the
 * {@code SecurityConfig} class. <b>DO NOT</b> add {@code @Component} or
 * {@code @ConstructorBinding} to this record &mdash; Spring Boot 3.x auto-detects
 * record constructor binding, and adding {@code @Component} would cause
 * double registration (once via {@code @ComponentScan} and again via
 * {@code @EnableConfigurationProperties}).
 *
 * <p><b>Defaults (in application.properties):</b>
 * <ul>
 *   <li>{@code capacity = 100} &mdash; max tokens per IP</li>
 *   <li>{@code refillTokens = 100} &mdash; tokens added per refill window</li>
 *   <li>{@code refillPeriodSeconds = 60} &mdash; refill window length</li>
 * </ul>
 *
 * <p>The default 100 req / 60 s per IP is generous enough for legitimate
 * burst traffic on a didactic CRUD service yet tight enough to stop a
 * naive flooder. Adjust per deployment via the corresponding property keys.
 *
 * <p><b>Why primitive {@code int} and not {@code Integer}?</b> Primitives
 * signal "must be present, cannot be null". A missing property fails fast
 * at startup with a binding error &mdash; exactly the desired behaviour, since
 * {@code RateLimitingFilter} would throw {@code NullPointerException} if
 * an accessor returned {@code null}. Defense in depth is cheap.
 *
 * <p>This file supports AAP finding #4 closure (CWE-770 Allocation of
 * Resources Without Limits or Throttling, OWASP A04 Insecure Design).
 *
 * @param capacity            Maximum number of tokens the bucket holds (must be {@code > 0}).
 * @param refillTokens        Number of tokens added per refill window (must be {@code > 0}).
 * @param refillPeriodSeconds Length of the refill window in seconds (must be {@code > 0}).
 */
@ConfigurationProperties(prefix = "app.security.rate-limit")
public record RateLimitingProperties(int capacity, int refillTokens, int refillPeriodSeconds) {
}
