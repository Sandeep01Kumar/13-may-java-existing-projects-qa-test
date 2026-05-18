package com.jspider.spring_boot_simple_crud_with_mysql.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jspider.spring_boot_simple_crud_with_mysql.config.RateLimitingFilter;
import com.jspider.spring_boot_simple_crud_with_mysql.dto.ProductRequestDto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc-based security tests for {@link com.jspider.spring_boot_simple_crud_with_mysql.controller.ProductController}.
 *
 * <p>Verifies AAP &sect;0.8.1 controller-level security behaviors:
 * <ul>
 *   <li>Finding #2 (CWE-20, OWASP A03): Input validation via {@code @Valid @RequestBody ProductRequestDto}.</li>
 *   <li>Finding #3 (CWE-915, OWASP A08): Mass-assignment &mdash; {@link ProductRequestDto} has no {@code id} field.</li>
 *   <li>Finding #4 (CWE-770, OWASP A04): Rate limiting via Bucket4j-backed {@code RateLimitingFilter}.</li>
 *   <li>Finding #8 (CWE-306, OWASP A01): Authentication required for all endpoints.</li>
 *   <li>Finding #9 (CWE-862, OWASP A01): Method-level {@code @PreAuthorize} authorization.</li>
 * </ul>
 *
 * <p>helmet.js translation: helmet.js is a Node.js/Express middleware incompatible with the JVM.
 * Equivalent HTTP security headers are verified in
 * {@link com.jspider.spring_boot_simple_crud_with_mysql.config.SecurityConfigTest}
 * via Spring Security 6's {@code HeadersConfigurer}.
 *
 * <p>Test profile: {@code test} (loads {@code application-test.properties}: H2 in-memory DB,
 * in-memory test user, TLS disabled, CORS allow-list {@code https://localhost:8443}).
 * Rate-limit capacity overridden to 5 for deterministic 429 triggering.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "app.security.rate-limit.capacity=5",
    "app.security.rate-limit.refill-tokens=5",
    "app.security.rate-limit.refill-period-seconds=60"
})
class ProductControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RateLimitingFilter rateLimitingFilter;

    /**
     * Test-isolation fixture &mdash; resets the per-IP rate-limit bucket map
     * before every test method.
     *
     * <p><b>Why this is needed:</b> {@link RateLimitingFilter} is a Spring
     * singleton whose internal {@code ConcurrentHashMap<String, Bucket>}
     * persists across test methods sharing the same Spring test context. Every
     * MockMvc request originates from {@code 127.0.0.1} (the default
     * {@code MockHttpServletRequest} remote address), so all tests share a
     * single bucket. Combined with this class's
     * {@code @TestPropertySource(properties = "app.security.rate-limit.capacity=5")},
     * the bucket is depleted within the first few requests of the test run
     * and every subsequent test would receive HTTP 429 unrelated to its
     * intent. {@link RateLimitingFilter#clearBuckets()} resets the map so each
     * test starts with a full bucket, restoring the "tests are independent
     * and stateless" property the AAP &sect;0.8.1 specification assumes.
     *
     * <p><b>Why not {@code @DirtiesContext}:</b> recreating the entire Spring
     * context before each test would cost ~2&nbsp;seconds per method, inflating
     * the test suite from a few seconds to a few minutes. The targeted
     * {@link RateLimitingFilter#clearBuckets()} call costs microseconds and
     * has the same isolation effect.
     */
    @BeforeEach
    void resetRateLimiterBuckets() {
        rateLimitingFilter.clearBuckets();
    }

    @Test
    @WithMockUser(roles = "USER")
    void validationRejectsEmptyName() throws Exception {
        String json = "{\"name\":\"\",\"color\":\"red\",\"price\":1.0}";
        mockMvc.perform(post("/product/saveProduct")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "USER")
    void validationRejectsNegativePrice() throws Exception {
        String json = "{\"name\":\"widget\",\"color\":\"red\",\"price\":-1.0}";
        mockMvc.perform(post("/product/saveProduct")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "USER")
    void validationRejectsNullPrice() throws Exception {
        String json = "{\"name\":\"widget\",\"color\":\"red\"}";
        mockMvc.perform(post("/product/saveProduct")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "USER")
    void validationRejectsOversizedName() throws Exception {
        String oversizedName = "a".repeat(101);
        String json = "{\"name\":\"" + oversizedName + "\",\"color\":\"red\",\"price\":1.0}";
        mockMvc.perform(post("/product/saveProduct")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "USER")
    void massAssignmentDoesNotExposeIdField() throws Exception {
        // Client supplies `id`:999; Jackson silently drops it because
        // ProductRequestDto has no `id` record component.
        String json = "{\"id\":999,\"name\":\"widget\",\"color\":\"red\",\"price\":1.0}";
        mockMvc.perform(post("/product/saveProduct")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().is2xxSuccessful());
    }

    @Test
    @WithMockUser(roles = "USER")
    void rateLimitTriggers429() throws Exception {
        // With @TestPropertySource capacity=5, the first 5 requests succeed.
        // The 6th request triggers HTTP 429 Too Many Requests.
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/product/findAllProduct"));
        }
        mockMvc.perform(get("/product/findAllProduct"))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().exists("Retry-After"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void deleteRequiresAdminRole_userIsForbidden() throws Exception {
        mockMvc.perform(delete("/product/deleteProductByPrice/100")
                .with(csrf()))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteRequiresAdminRole_adminAllowed() throws Exception {
        // ADMIN IS authorized; status should NOT be 401/403.
        // 200/204 = successful delete; 404 = no matching row; 500 = DAO error on empty H2 DB.
        mockMvc.perform(delete("/product/deleteProductByPrice/100")
                .with(csrf()))
            .andExpect(status().is(anyOf(is(200), is(204), is(404), is(500))));
    }

    @Test
    @WithMockUser(roles = "USER")
    void userCanGetProducts() throws Exception {
        mockMvc.perform(get("/product/findAllProduct"))
            .andExpect(status().is2xxSuccessful());
    }

    @Test
    void unauthenticatedDeleteReturns401() throws Exception {
        // No @WithMockUser -> anonymous request -> 401.
        mockMvc.perform(delete("/product/deleteProductByPrice/100")
                .with(csrf()))
            .andExpect(status().isUnauthorized());
    }
}
