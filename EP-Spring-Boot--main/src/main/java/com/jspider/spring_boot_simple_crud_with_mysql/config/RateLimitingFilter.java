package com.jspider.spring_boot_simple_crud_with_mysql.config;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Per-IP HTTP rate-limiting filter backed by Bucket4j 8.10.1 token buckets.
 *
 * <p>Closes AAP finding #4 (CWE-770 Allocation of Resources Without Limits or
 * Throttling; OWASP Top 10 2021 A04 Insecure Design). Registered into the
 * Spring Security filter chain by
 * {@code SecurityConfig.securityFilterChain(...)} via
 * {@code addFilterBefore(rateLimitingFilter,
 * UsernamePasswordAuthenticationFilter.class)} so that abusive clients are
 * rejected <em>before</em> they consume authentication resources.
 *
 * <h2>Algorithm</h2>
 * <p>Each unique remote IP address is allocated its own private
 * {@link Bucket} on first sight via
 * {@link ConcurrentHashMap#computeIfAbsent(Object, java.util.function.Function)}.
 * Every request consumes one token via the non-blocking
 * {@link Bucket#tryConsume(long)} call:
 * <ul>
 *   <li>{@code tryConsume(1) == true}  &rarr; the request continues down the
 *       filter chain via {@link FilterChain#doFilter}.</li>
 *   <li>{@code tryConsume(1) == false} &rarr; the filter short-circuits and
 *       writes an HTTP 429 Too Many Requests response with a
 *       {@code Retry-After} header (seconds form) and a minimal JSON body.
 *       The downstream chain is <em>not</em> invoked.</li>
 * </ul>
 *
 * <h2>Tuning</h2>
 * <p>Bucket dimensions are sourced from
 * {@link RateLimitingProperties} (bound from {@code application.properties}):
 * <ul>
 *   <li>{@code app.security.rate-limit.capacity}             &rarr; max tokens per IP</li>
 *   <li>{@code app.security.rate-limit.refill-tokens}        &rarr; tokens added per refill</li>
 *   <li>{@code app.security.rate-limit.refill-period-seconds}&rarr; refill window length</li>
 * </ul>
 * The refill is <em>greedy</em> (uniformly distributed across the window)
 * rather than intervally — this prevents bursty replenishment at the end of
 * the window that would let abusers double-dip on the boundary.
 *
 * <h2>Scope limitations / future work</h2>
 * <ul>
 *   <li><b>In-process storage.</b> The {@link #buckets} map lives in the JVM
 *       heap of a single application instance. A multi-replica deployment
 *       needs a distributed bucket store (Redis-backed Bucket4j is supported
 *       but flagged out of scope per AAP &sect;0.10.4 future work).</li>
 *   <li><b>Unbounded growth.</b> {@link #buckets} grows with every unique
 *       source IP encountered for the lifetime of the JVM. Acceptable for a
 *       didactic CRUD service; production deployments with many short-lived
 *       client IPs should add an eviction policy (e.g. a Caffeine cache with
 *       time-based or LRU eviction). Flagged future work per AAP &sect;0.10.4.</li>
 *   <li><b>Reverse-proxy unaware.</b> {@link #resolveClientKey} reads
 *       {@link HttpServletRequest#getRemoteAddr()} directly. Behind a trusted
 *       reverse proxy, this returns the proxy IP rather than the original
 *       client IP. {@code X-Forwarded-For} parsing is flagged future work
 *       per AAP &sect;0.10.4.</li>
 *   <li><b>Uniform limit.</b> A single bucket configuration applies to every
 *       request regardless of endpoint or authenticated role. Differentiated
 *       limits per role / endpoint are flagged future work per AAP &sect;0.10.4.</li>
 * </ul>
 *
 * <h2>Thread safety</h2>
 * <p>This filter is safe under concurrent inbound traffic without any
 * {@code synchronized} block. The two primitives that carry the burden are:
 * <ol>
 *   <li>{@link ConcurrentHashMap#computeIfAbsent} — atomic lazy creation of
 *       the per-IP {@link Bucket}.</li>
 *   <li>{@link Bucket#tryConsume(long)} — Bucket4j guarantees internal
 *       thread-safe accounting on the local {@code SynchronizedBucket}
 *       implementation returned by {@link Bucket#builder()}.</li>
 * </ol>
 *
 * <h2>Configuration-properties enablement</h2>
 * <p>This class is annotated with
 * {@code @EnableConfigurationProperties(RateLimitingProperties.class)} as a
 * defensive co-enablement of its own dependency. The canonical location for
 * this annotation per AAP &sect;0.6.1 is the {@code SecurityConfig} class,
 * which is created by a separate agent in the security-fix initiative. Placing
 * the annotation here in addition makes this filter self-sufficient: it can be
 * loaded into any Spring context that component-scans this package without
 * requiring {@code SecurityConfig} to already be present, and the bean wiring
 * of {@link RateLimitingProperties} is guaranteed.
 * <p>Spring deduplicates {@code @EnableConfigurationProperties} target classes
 * via {@code EnableConfigurationPropertiesRegistrar.registerBeanDefinition}'s
 * {@code containsBeanDefinition} check, so duplicate declarations across
 * multiple classes (e.g. here and on {@code SecurityConfig}) are idempotent
 * and do NOT trigger a {@code BeanDefinitionOverrideException}. See
 * {@link EnableConfigurationProperties} and the registrar's source in
 * {@code spring-boot-autoconfigure} for the implementation detail.
 *
 * @see RateLimitingProperties
 * @see OncePerRequestFilter
 * @see EnableConfigurationProperties
 */
@Component
@EnableConfigurationProperties(RateLimitingProperties.class)
public class RateLimitingFilter extends OncePerRequestFilter {

    /**
     * Tunable parameters (capacity, refill tokens, refill window). Constructor-injected
     * by Spring because this class declares exactly one constructor.
     */
    private final RateLimitingProperties properties;

    /**
     * Per-IP token-bucket store. Keyed by remote IP address; value is a private
     * {@link Bucket} for that IP.
     *
     * <p><b>Concurrency:</b> {@link ConcurrentHashMap} supports lock-free reads and
     * fine-grained locking on writes. {@link ConcurrentHashMap#computeIfAbsent}
     * is atomic — only one thread creates the bucket for a given key, even under
     * concurrent first-sight requests from the same IP.
     *
     * <p><b>Memory note (future work, AAP &sect;0.10.4):</b> this map grows
     * unboundedly with the number of unique source IPs observed for the lifetime
     * of the JVM. For a didactic service with a small expected IP population
     * this is acceptable; production deployments should replace this with a
     * size- or time-bounded cache (e.g. Caffeine).
     */
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    /**
     * Creates the filter with the supplied {@link RateLimitingProperties}.
     *
     * <p>No {@code @Autowired} is needed: Spring 4.3+ auto-wires the single
     * declared constructor of a stereotype-annotated component. The
     * {@link RateLimitingProperties} bean is registered by the class-level
     * {@link EnableConfigurationProperties} annotation on this filter (and
     * canonically also by {@code SecurityConfig}; see the "Configuration-properties
     * enablement" section of the class-level Javadoc).
     *
     * @param properties the tunable rate-limit parameters; never {@code null}
     *                   (Spring fails the context start if the bean is missing)
     */
    public RateLimitingFilter(RateLimitingProperties properties) {
        this.properties = properties;
    }

    /**
     * Per-request entry point. Looks up (or lazily creates) the bucket for the
     * caller's IP and attempts to consume one token. If a token is available
     * the request continues; otherwise the filter writes HTTP 429 and the chain
     * is short-circuited.
     *
     * @param request     the inbound HTTP request
     * @param response    the outbound HTTP response
     * @param filterChain the remainder of the Spring Security filter chain
     * @throws ServletException if a downstream filter raises {@link ServletException}
     * @throws IOException      if writing the 429 response body or a downstream
     *                          filter raises {@link IOException}
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        String clientKey = resolveClientKey(request);
        Bucket bucket = buckets.computeIfAbsent(clientKey, k -> newBucket());
        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            writeTooManyRequests(response);
        }
    }

    /**
     * Clears every per-IP bucket currently held by this filter.
     *
     * <p><b>Intent (test-isolation hook).</b> The {@link #buckets} map is a
     * Spring-singleton field that persists across test methods within the
     * same Spring test context. Without an explicit reset, the bucket for
     * {@code 127.0.0.1} (the default {@code MockMvc} remote address) accumulates
     * token consumption from every prior test in the same class. With a low
     * test capacity (e.g. {@code @TestPropertySource} capacity=5 on
     * {@code ProductControllerSecurityTest}), this causes later tests to
     * receive HTTP 429 even though they are functionally unrelated to rate
     * limiting. Calling this method from a {@code @BeforeEach} fixture in the
     * test class restores per-test isolation without resorting to expensive
     * Spring-context recreation via {@code @DirtiesContext}.
     *
     * <p><b>Visibility.</b> {@code public} so that test classes in any package
     * (e.g. {@code ProductControllerSecurityTest} in the {@code controller}
     * sub-package) can invoke it via {@code @Autowired}-injected reference.
     * Production code should NEVER call this method — clearing the rate-
     * limit map at runtime defeats the purpose of the filter; this is
     * enforced by code review and documented here.
     *
     * <p><b>Thread safety.</b> {@link ConcurrentHashMap#clear()} is atomic
     * with respect to the map's own internal segments; concurrent calls to
     * {@link #doFilterInternal(HttpServletRequest, HttpServletResponse, FilterChain)}
     * may observe a partial clear (some IPs reset, others not) but no
     * structural corruption. In a test context there is no concurrent
     * inbound traffic, so this is a non-issue.
     */
    public void clearBuckets() {
        buckets.clear();
    }

    /**
     * Derives the per-request bucket key from the inbound request.
     *
     * <p>For a service running on embedded Tomcat with no reverse proxy in
     * front, {@link HttpServletRequest#getRemoteAddr()} returns the direct TCP
     * peer address — which is the caller's IP and the correct bucket key.
     *
     * <p>If the service is later deployed behind a trusted reverse proxy
     * (nginx, AWS ALB, Cloudflare, &hellip;), this method must be adjusted to
     * parse the left-most non-proxy entry of {@code X-Forwarded-For} (or use
     * Spring's {@code ForwardedHeaderFilter}). Flagged future work per AAP
     * &sect;0.10.4.
     *
     * @param request the inbound HTTP request
     * @return a non-null, non-empty string used as the bucket map key
     */
    private String resolveClientKey(HttpServletRequest request) {
        return request.getRemoteAddr();
    }

    /**
     * Constructs a fresh {@link Bucket} sized by the current
     * {@link RateLimitingProperties}.
     *
     * <p><b>Idiomatic Bucket4j 8.x:</b>
     * <ul>
     *   <li>{@link Bandwidth#classic(long, Refill)} — fixed-capacity bucket
     *       with the supplied refill strategy.</li>
     *   <li>{@link Refill#greedy(long, Duration)} — replenishes
     *       {@code refillTokens} uniformly distributed across the duration;
     *       prevents the boundary-burst that {@code Refill.intervally(...)}
     *       would allow.</li>
     *   <li>{@link Bucket#builder()} — Bucket4j 8.x builder entry point
     *       (the legacy {@code Bucket4j.builder()} from 7.x is removed).</li>
     * </ul>
     *
     * <p><b>Deprecation note:</b> {@link Bandwidth#classic(long, Refill)} and
     * {@link Refill#greedy(long, Duration)} are marked {@code @Deprecated} in
     * Bucket4j 8.10.1 in favour of the new {@code Bandwidth.builder()} fluent
     * API. The deprecation is non-functional (the methods continue to work and
     * are not scheduled for removal in 8.x) and the AAP specifies this exact
     * call sequence, so the warning is suppressed locally rather than rewriting
     * to the new builder. Track upstream Bucket4j 9.x migration as separate
     * future work.
     *
     * @return a freshly-built per-IP {@link Bucket}
     */
    @SuppressWarnings("deprecation")
    private Bucket newBucket() {
        Bandwidth limit = Bandwidth.classic(
                properties.capacity(),
                Refill.greedy(
                        properties.refillTokens(),
                        Duration.ofSeconds(properties.refillPeriodSeconds())
                )
        );
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    /**
     * Serialises an HTTP 429 Too Many Requests response with a {@code Retry-After}
     * hint and a minimal JSON body. The body deliberately carries no stack
     * trace, no internal hostname, and no client identity — only the bare
     * minimum the client needs to recognise the condition and back off
     * (information-disclosure safe per OWASP REST Security Cheat Sheet).
     *
     * <p>The status MUST be set before any data is written to the response —
     * Tomcat commits the response headers on first write.
     *
     * @param response the outbound HTTP response (will be mutated and committed)
     * @throws IOException if writing the response body fails
     */
    private void writeTooManyRequests(HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(properties.refillPeriodSeconds()));
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"error\":\"Too Many Requests\",\"status\":429}");
    }
}
