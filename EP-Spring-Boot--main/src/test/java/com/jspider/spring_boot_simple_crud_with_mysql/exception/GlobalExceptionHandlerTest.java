package com.jspider.spring_boot_simple_crud_with_mysql.exception;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc-based regression tests for
 * {@link com.jspider.spring_boot_simple_crud_with_mysql.exception.GlobalExceptionHandler}.
 *
 * <p>Closes the QA Checkpoint 5 Issue #1 (MAJOR &mdash; HTTP-semantic exception
 * handling): without the dedicated {@code @ExceptionHandler} methods added in
 * the Checkpoint 5 fix, Spring MVC's HTTP-semantic exceptions
 * ({@code HttpRequestMethodNotSupportedException},
 * {@code HttpMessageNotReadableException},
 * {@code HttpMediaTypeNotSupportedException},
 * {@code HttpMediaTypeNotAcceptableException},
 * {@code NoResourceFoundException}) were swallowed by the broader
 * {@code @ExceptionHandler(Exception.class)} catch-all and returned as
 * HTTP&nbsp;500 instead of their canonical RFC&nbsp;9110 status codes.
 *
 * <p>Each test below corresponds to one of the QA finding's explicit
 * reproduction steps and asserts:
 * <ul>
 *   <li>The correct HTTP status code is returned (405, 400, 415, 404).</li>
 *   <li>The response body keeps the structured minimal-disclosure shape
 *       used by the rest of the advice ({@code timestamp}, {@code status},
 *       {@code error}) and never echoes the underlying exception message,
 *       FQCN, or stack frames (CWE-209 mitigation).</li>
 *   <li>Where RFC&nbsp;9110 mandates a header on the response (the
 *       {@code Allow} header on 405 and the {@code Accept} header on 415),
 *       that header is present.</li>
 * </ul>
 *
 * <p>Test profile: {@code test} (loads {@code application-test.properties}:
 * H2 in-memory DB, in-memory test user, TLS disabled, CORS allow-list
 * {@code https://localhost:8443}). The {@code @WithMockUser} annotation
 * injects a mock authenticated principal so that requests pass
 * {@code SecurityFilterChain}'s {@code anyRequest().authenticated()} gate and
 * reach the controller, where the relevant {@code DispatcherServlet} /
 * argument-resolution layer raises the HTTP-semantic exception under test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * Verifies that a GET request on a POST-only endpoint returns
     * HTTP&nbsp;405 (Method Not Allowed), not HTTP&nbsp;500 &mdash;
     * the core QA Issue #1 failure mode.
     *
     * <p>{@code /student/addition/{a1}/{b1}} is mapped only to
     * {@code RequestMethod.POST} on {@link
     * com.jspider.spring_boot_simple_crud_with_mysql.controller.StudentController}.
     * Issuing a GET against the same path causes Spring MVC to raise
     * {@code HttpRequestMethodNotSupportedException}; the
     * {@link GlobalExceptionHandler#handleMethodNotSupported} method must
     * claim it and return 405.
     *
     * <p>RFC&nbsp;9110 &sect;15.5.6 requires a 405 response to include an
     * {@code Allow} header listing the supported methods. The header is
     * asserted to contain {@code POST}.
     */
    @Test
    @WithMockUser(roles = "USER")
    void getOnPostEndpointReturns405WithAllowHeader() throws Exception {
        mockMvc.perform(get("/student/addition/5/3"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", containsString("POST")))
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.error").value("Method Not Allowed"));
    }

    /**
     * Verifies that the structured response body for a 405 response keeps
     * the minimal-disclosure shape used by the rest of the advice and
     * never echoes the underlying exception message.
     */
    @Test
    @WithMockUser(roles = "USER")
    void getOnPostEndpointResponseBodyKeepsStructuredShape() throws Exception {
        mockMvc.perform(get("/student/addition/5/3"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.error").value("Method Not Allowed"))
                // CWE-209 mitigation: NO exception message echo, NO FQCN,
                // NO stack frames in the body.
                .andExpect(content().string(not(containsString("HttpRequestMethodNotSupportedException"))))
                .andExpect(content().string(not(containsString("org.springframework"))))
                .andExpect(content().string(not(containsString("\tat "))));
    }

    /**
     * Verifies that posting malformed JSON to a JSON-accepting endpoint
     * returns HTTP&nbsp;400, not HTTP&nbsp;500. Spring MVC's
     * {@code MappingJackson2HttpMessageConverter} raises
     * {@code HttpMessageNotReadableException} on the parse failure; the
     * {@link GlobalExceptionHandler#handleMessageNotReadable} method must
     * claim it.
     */
    @Test
    @WithMockUser(roles = "USER")
    void malformedJsonReturns400() throws Exception {
        // Trailing comma + missing closing brace renders the body unparseable
        // by Jackson; HttpMessageConverter raises HttpMessageNotReadableException.
        String malformedJson = "{ \"name\": \"x\", \"color\": \"y\", ";
        mockMvc.perform(post("/product/saveProduct")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(malformedJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Malformed request body"))
                // CWE-209 mitigation: NO Jackson parse offset, NO FQCN.
                .andExpect(content().string(not(containsString("JsonParseException"))))
                .andExpect(content().string(not(containsString("com.fasterxml.jackson"))));
    }

    /**
     * Verifies that posting with a Content-Type the controller does not
     * support returns HTTP&nbsp;415, not HTTP&nbsp;500. Spring MVC raises
     * {@code HttpMediaTypeNotSupportedException} when no
     * {@code HttpMessageConverter} can deserialize the supplied
     * Content-Type onto the handler parameter; the
     * {@link GlobalExceptionHandler#handleMediaTypeNotSupported} method
     * must claim it.
     *
     * <p>RFC&nbsp;9110 &sect;15.5.16 recommends an {@code Accept} response
     * header on 415; the header is asserted to be present and to contain
     * a JSON media type.
     */
    @Test
    @WithMockUser(roles = "USER")
    void unsupportedContentTypeReturns415WithAcceptHeader() throws Exception {
        mockMvc.perform(post("/product/saveProduct")
                        .with(csrf())
                        .contentType(MediaType.TEXT_HTML)
                        .content("<html></html>"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(header().exists("Accept"))
                .andExpect(header().string("Accept", containsString("application/json")))
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.error").value("Unsupported Media Type"));
    }

    /**
     * Verifies that hitting an unmapped path (no controller handler bound
     * to it) returns HTTP&nbsp;404, not HTTP&nbsp;500. Spring MVC raises
     * {@code NoResourceFoundException} when the dispatcher cannot match
     * the URI to any handler or static resource; the
     * {@link GlobalExceptionHandler#handleNoResourceFound} method must
     * claim it.
     *
     * <p>This test closes the QA Issue #3 (MINOR) follow-on: a request to
     * {@code /actuator/health} on this application returns 404 because
     * Spring Boot Actuator is intentionally not on the classpath (AAP
     * &sect;0.9.2 de-scope), and the {@code SecurityConfig} permit-all
     * matcher for that path is a future-looking placeholder. Without the
     * dedicated 404 handler, the catch-all would return 500 here.
     */
    @Test
    void unmappedPathReturns404() throws Exception {
        // No @WithMockUser: /actuator/health is on the permit-all list in
        // SecurityConfig, so anonymous traffic reaches the dispatcher and
        // the lack of a handler produces NoResourceFoundException.
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    /**
     * Verifies that the catch-all {@code @ExceptionHandler(Exception.class)}
     * is no longer over-broad &mdash; the {@code Internal Server Error}
     * literal it produces does NOT appear on the responses we just
     * asserted are 405 / 400 / 415 / 404. This is the regression check for
     * QA Issue #1.
     */
    @Test
    @WithMockUser(roles = "USER")
    void httpSemanticExceptionsDoNotFallThroughToCatchAll() throws Exception {
        // 405 path
        mockMvc.perform(get("/student/addition/5/3"))
                .andExpect(jsonPath("$.error", not(equalTo("Internal Server Error"))));
        // 400 path (malformed JSON)
        mockMvc.perform(post("/product/saveProduct")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ not-json"))
                .andExpect(jsonPath("$.error", not(equalTo("Internal Server Error"))));
        // 415 path
        mockMvc.perform(post("/product/saveProduct")
                        .with(csrf())
                        .contentType(MediaType.TEXT_HTML)
                        .content("<x/>"))
                .andExpect(jsonPath("$.error", not(equalTo("Internal Server Error"))));
    }
}
