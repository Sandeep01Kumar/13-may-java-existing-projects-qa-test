package com.jspider.spring_boot_simple_crud_with_mysql.config;

import org.hamcrest.Matchers;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc-based tests for {@link com.jspider.spring_boot_simple_crud_with_mysql.config.SecurityConfig}.
 *
 * <p>Verifies AAP &sect;0.8.1 security configuration behaviors:
 * <ul>
 *   <li>Finding #1 (CWE-693, OWASP A05): HTTP security response headers &mdash; the helmet.js-equivalent set.</li>
 *   <li>Finding #8 (CWE-306, OWASP A01): Authentication required for all endpoints (401 on anonymous).</li>
 *   <li>Finding #10 (CWE-942, OWASP A05): Permissive CORS replaced by allow-list.</li>
 * </ul>
 *
 * <p>helmet.js translation: helmet.js is a Node.js/Express middleware INCOMPATIBLE with the JVM.
 * Per AAP &sect;0.1.3, equivalent HTTP security headers are emitted by Spring Security 6's
 * {@code HeadersConfigurer}: {@code Content-Security-Policy}, {@code X-Frame-Options},
 * {@code Strict-Transport-Security}, {@code X-Content-Type-Options}, {@code Referrer-Policy},
 * {@code Permissions-Policy}, and {@code X-Permitted-Cross-Domain-Policies}.
 *
 * <p>Test profile: {@code test} (loads {@code application-test.properties}: H2 in-memory DB,
 * in-memory test user, TLS disabled, CORS allow-list {@code https://localhost:8443}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * Negative authentication test &mdash; closes AAP finding #8 (CWE-306, OWASP A01).
     *
     * <p>Anonymous (no {@code @WithMockUser}) {@code GET /product/findAllProduct} MUST be
     * rejected with HTTP 401 because {@code SecurityConfig} declares
     * {@code authorizeHttpRequests(...).anyRequest().authenticated()}. Spring Security's
     * {@code BasicAuthenticationEntryPoint} (or {@code Http403ForbiddenEntryPoint}, depending
     * on the configured entry point) produces the 401 response.
     */
    @Test
    void allEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/product/findAllProduct"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Positive security-headers test &mdash; closes AAP finding #1 (CWE-693, OWASP A05) and
     * validates the helmet.js &rarr; Spring Security {@code HeadersConfigurer} translation
     * matrix from AAP &sect;0.1.3.
     *
     * <p>The {@code @WithMockUser} annotation injects a mock authenticated principal so the
     * request bypasses the {@code anyRequest().authenticated()} gate and the response is
     * 2xx. {@code HeadersConfigurer} writes the seven helmet.js-equivalent headers on every
     * response regardless of body content.
     *
     * <p>Hamcrest {@code containsString} matchers are used for {@code Strict-Transport-Security}
     * and {@code Content-Security-Policy} to avoid coupling tests to exact configured values
     * (e.g. {@code max-age=31536000; includeSubDomains}). {@code exists()} is used for
     * {@code Referrer-Policy} and {@code Permissions-Policy} because exact spellings may
     * vary across Spring Security 6.x patch versions.
     */
    @Test
    @WithMockUser
    void securityHeadersArePresent() throws Exception {
        mockMvc.perform(get("/product/findAllProduct"))
                .andExpect(status().is2xxSuccessful())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Strict-Transport-Security",
                        Matchers.containsString("max-age=")))
                .andExpect(header().string("Content-Security-Policy",
                        Matchers.containsString("default-src 'self'")))
                .andExpect(header().exists("Referrer-Policy"))
                .andExpect(header().exists("Permissions-Policy"))
                .andExpect(header().string("X-Permitted-Cross-Domain-Policies", "none"));
    }

    /**
     * Negative CORS test &mdash; closes AAP finding #10 (CWE-942, OWASP A05).
     *
     * <p>An OPTIONS preflight request from a disallowed origin
     * ({@code https://evil.example}) MUST NOT receive an {@code Access-Control-Allow-Origin}
     * response header. The {@code Origin} value is deliberately not in the test profile's
     * {@code app.cors.allowed-origins=https://localhost:8443} allow-list, so Spring's
     * {@code CorsProcessor} suppresses the CORS response headers entirely.
     *
     * <p>No {@code @WithMockUser} annotation: CORS preflights are sent by browsers without
     * credentials per the Fetch spec, and Spring Security's {@code CorsFilter} runs before
     * the authentication filter.
     */
    @Test
    void corsRejectsDisallowedOrigin() throws Exception {
        mockMvc.perform(options("/product/saveProduct")
                        .header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    /**
     * Positive CORS test &mdash; complements {@link #corsRejectsDisallowedOrigin()} by
     * verifying the allow-listed origin receives the expected CORS response headers.
     *
     * <p>An OPTIONS preflight request from {@code https://localhost:8443} (the value of
     * {@code app.cors.allowed-origins} in {@code application-test.properties}) MUST receive
     * {@code Access-Control-Allow-Origin: https://localhost:8443} and a non-empty
     * {@code Access-Control-Allow-Methods} header (populated by the
     * {@code CorsConfigurationSource} bean's {@code setAllowedMethods(...)} call).
     */
    @Test
    void corsAllowsConfiguredOrigin() throws Exception {
        mockMvc.perform(options("/product/saveProduct")
                        .header("Origin", "https://localhost:8443")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(header().string("Access-Control-Allow-Origin", "https://localhost:8443"))
                .andExpect(header().exists("Access-Control-Allow-Methods"));
    }

    /**
     * Positive authentication test &mdash; complements
     * {@link #allEndpointsRequireAuthentication()} (negative case).
     *
     * <p>{@code @WithMockUser} supplies a valid authenticated principal so the request passes
     * {@code anyRequest().authenticated()} and the controller produces a 2xx response. This
     * sanity check ensures that adding security does NOT break the read path for authorised
     * callers; combined with the 401 assertion above it proves the gate is correctly
     * configured (not always-deny, not always-allow).
     */
    @Test
    @WithMockUser
    void authenticatedGetReturns200() throws Exception {
        mockMvc.perform(get("/product/findAllProduct"))
                .andExpect(status().is2xxSuccessful());
    }
}
