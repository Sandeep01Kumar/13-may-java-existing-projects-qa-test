package com.jspider.spring_boot_simple_crud_with_mysql.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jspider.spring_boot_simple_crud_with_mysql.dto.AuthResponseDto;
import com.jspider.spring_boot_simple_crud_with_mysql.dto.LoginRequestDto;
import com.jspider.spring_boot_simple_crud_with_mysql.dto.RegisterRequestDto;
import com.jspider.spring_boot_simple_crud_with_mysql.responses.ResponseStructure;
import com.jspider.spring_boot_simple_crud_with_mysql.security.JwtAuthenticationEntryPoint;
import com.jspider.spring_boot_simple_crud_with_mysql.security.JwtAuthenticationFilter;
import com.jspider.spring_boot_simple_crud_with_mysql.service.AuthService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice-test for {@link AuthController} that verifies the HTTP / JSON contract
 * of {@code POST /auth/register} and {@code POST /auth/login} in isolation,
 * without booting the full Spring application context, database, or Spring
 * Security filter chain.
 *
 * <h2>Slice configuration</h2>
 * <ul>
 *   <li>{@code @WebMvcTest(controllers = AuthController.class)} &mdash;
 *       narrows the Spring context to ONLY {@link AuthController}, the MVC
 *       infrastructure (Jackson, validator), and the {@code @RestControllerAdvice}
 *       beans. {@code ProductController}, {@code StudentController}, JPA, MySQL,
 *       and JWT minting are all excluded.</li>
 *   <li>{@code @AutoConfigureMockMvc(addFilters = false)} &mdash; disables the
 *       Spring Security filter chain in the slice so MockMvc requests are
 *       NOT pre-empted with 401/403 before reaching the controller method.</li>
 *   <li>{@code @MockBean private AuthService authService} &mdash; provides a
 *       Mockito mock for the controller's sole collaborator so that
 *       {@code register(...)} and {@code login(...)} return deterministic
 *       stubbed envelopes without invoking any real business logic, password
 *       hashing, persistence, or JWT minting.</li>
 * </ul>
 *
 * <h2>Test-data note (production-validation alignment)</h2>
 * <p>{@link RegisterRequestDto} carries Jakarta Bean Validation constraints
 * ({@code @NotBlank} and {@code @Size(min = 8, max = 100)} on the password
 * field) which are evaluated by Spring MVC at the controller boundary because
 * the {@code spring-boot-starter-validation} dependency is on the classpath
 * and the controller method declares {@code @Valid @RequestBody}. The
 * register-test payload therefore uses an 8-character password that satisfies
 * the production validation rules so the request reaches the (mocked)
 * service layer and the controller emits the expected HTTP 201 envelope;
 * a shorter password would be rejected with HTTP 400 by
 * {@code GlobalExceptionHandler} before the controller method body runs.
 * {@link LoginRequestDto} carries only {@code @NotBlank} (no length
 * constraint) so the login test can use a shorter password without any
 * validation interference.</p>
 *
 * <h2>Rule compliance (AAP &sect;0.7.1)</h2>
 * <ul>
 *   <li>Rule 1 (camelCase): all field, local-variable, and parameter
 *       identifiers are camelCase. Class and method identifiers follow
 *       standard Java / JUnit conventions (PascalCase / snake_case test
 *       names) which AAP &sect;0.7.3 explicitly endorses.</li>
 *   <li>Rule 2 ({@code // Rule Applied}): placed immediately above the
 *       class declaration, below the last class-level annotation.</li>
 *   <li>Rule 3 ({@code System.out.println} per new method): each
 *       {@code @Test} method emits two progress log lines &mdash; one
 *       after the Mockito stub is configured and one after the
 *       MockMvc assertions complete.</li>
 * </ul>
 */
@WebMvcTest(controllers = AuthController.class,
        excludeAutoConfiguration = { SecurityAutoConfiguration.class })
@AutoConfigureMockMvc(addFilters = false)
// Rule Applied
class AuthControllerTest {

    /**
     * Spring's HTTP simulation harness. Auto-configured by
     * {@code @WebMvcTest} + {@code @AutoConfigureMockMvc} and injected here
     * via {@link Autowired}. Used to {@code perform(...)} simulated POST
     * requests against {@code /auth/register} and {@code /auth/login}
     * without binding to a real TCP port.
     */
    @Autowired
    private MockMvc mockMvc;

    /**
     * Jackson serializer auto-configured by Spring Boot's default web setup
     * (the {@code spring-boot-starter-web} starter brings in
     * {@code jackson-databind}; Spring Boot's {@code JacksonAutoConfiguration}
     * exposes a singleton {@link ObjectMapper} bean). Used here to convert
     * the {@link RegisterRequestDto} / {@link LoginRequestDto} test
     * fixtures into JSON strings for the {@code MockMvc} request body.
     */
    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Mockito mock registered into the Spring test context via
     * {@link MockBean}. The {@link AuthController} declares an
     * {@code @RequiredArgsConstructor}-generated dependency on
     * {@link AuthService}, so without this mock the slice context would
     * fail with {@code NoSuchBeanDefinitionException}. Each test stubs
     * the {@code register(...)} or {@code login(...)} method with a
     * pre-populated {@link ResponseStructure} envelope so the controller
     * has a deterministic value to wrap and return.
     */
    @MockBean
    private AuthService authService;

    /**
     * Mocked {@link JwtAuthenticationFilter}. The production filter is a
     * {@code @Component} extending {@code OncePerRequestFilter} (a {@code Filter}),
     * so Spring Boot's {@code @WebMvcTest} type-include filter pulls it into
     * the slice context automatically. Without this mock, the slice would
     * attempt to instantiate the real filter and fail with
     * {@code NoSuchBeanDefinitionException} for its {@code JwtUtil} /
     * {@code UserDetailsService} constructor dependencies (which are NOT
     * in {@code @WebMvcTest}'s allow-list because they are plain
     * {@code @Component} / {@code @Service} beans). Mocking the filter
     * itself short-circuits the dependency chain entirely. The mock is
     * never invoked at request time because
     * {@code @AutoConfigureMockMvc(addFilters = false)} disables servlet
     * filter application for MockMvc requests.
     */
    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    /**
     * Mocked {@link JwtAuthenticationEntryPoint}. The production entry point
     * is a {@code @Component} that Spring's component scan would normally
     * load; mocking it here defends against the slice attempting to
     * instantiate the real bean alongside {@link JwtAuthenticationFilter}.
     * Like the filter mock above, this mock is never invoked at request
     * time because the security filter chain is disabled by
     * {@code @AutoConfigureMockMvc(addFilters = false)}.
     */
    @MockBean
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    /**
     * Verifies that a syntactically valid {@code POST /auth/register} request
     * is forwarded to {@link AuthService#register(RegisterRequestDto)} and
     * that the stubbed envelope is wrapped in a {@code 201 Created} response
     * with the expected JSON shape.
     *
     * <p>Test sequence:</p>
     * <ol>
     *   <li>Build a {@link RegisterRequestDto} whose field values satisfy
     *       the production Jakarta Bean Validation constraints (username
     *       length in [3, 50]; password length in [8, 100]; role length
     *       in [1, 20]).</li>
     *   <li>Build a stub {@link ResponseStructure} envelope that mirrors the
     *       shape the real {@link AuthService#register(RegisterRequestDto)}
     *       would produce on success.</li>
     *   <li>Stub the mocked {@link AuthService} so it returns the prepared
     *       envelope for any {@link RegisterRequestDto} (necessary because
     *       Jackson deserialises a NEW DTO instance that won't
     *       {@code equals()} our local one).</li>
     *   <li>Perform the {@code MockMvc} POST and assert HTTP 201 plus each
     *       field of the JSON envelope.</li>
     * </ol>
     *
     * @throws Exception {@code MockMvc.perform(...)} and
     *                   {@code ObjectMapper.writeValueAsString(...)} declare
     *                   checked exceptions; we propagate them so JUnit can
     *                   report the failure with the original cause attached.
     */
    @Test
    void register_returns201_onSuccess() throws Exception {
        // The password "password" (length 8) satisfies the production
        // @Size(min = 8, max = 100) constraint declared on
        // RegisterRequestDto.password. Using a shorter literal would be
        // rejected by Spring MVC's Bean Validation step BEFORE the controller
        // method runs, surfacing as HTTP 400 (via GlobalExceptionHandler) and
        // failing this test.
        RegisterRequestDto dto = new RegisterRequestDto("alice", "password", "USER");

        ResponseStructure<String> stubResponse = new ResponseStructure<>();
        stubResponse.setStatusCode(201);
        stubResponse.setApiDescription("User registered successfully");
        stubResponse.setData("alice");

        when(authService.register(any(RegisterRequestDto.class))).thenReturn(stubResponse);
        System.out.println("[AuthControllerTest] register stub configured; invoking POST /auth/register");

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.statusCode").value(201))
                .andExpect(jsonPath("$.apiDescription").value("User registered successfully"))
                .andExpect(jsonPath("$.data").value("alice"));

        System.out.println("[AuthControllerTest] register endpoint verified.");
    }

    /**
     * Verifies that a syntactically valid {@code POST /auth/login} request
     * is forwarded to {@link AuthService#login(LoginRequestDto)} and that
     * the stubbed envelope (with a nested {@link AuthResponseDto}) is
     * wrapped in a {@code 200 OK} response with the expected JSON shape.
     *
     * <p>The {@link LoginRequestDto} carries only {@code @NotBlank}
     * constraints (no length bounds), so any non-blank password value
     * satisfies the validation step. The test uses a deliberately short
     * placeholder password to make clear that the controller never inspects
     * the password contents &mdash; that responsibility is delegated entirely
     * to the (mocked) {@link AuthService}.</p>
     *
     * <p>The nested {@link AuthResponseDto} assertions verify that the JSON
     * serialization preserves all four fields (token, username, role,
     * expiresInMs). The {@code expiresInMs} field is asserted as the integer
     * literal {@code 3600000}; Spring's JSONPath comparison is type-permissive
     * for numeric values, so the stored {@code long} {@code 3600000L}
     * matches the integer assertion.</p>
     *
     * @throws Exception {@code MockMvc.perform(...)} and
     *                   {@code ObjectMapper.writeValueAsString(...)} declare
     *                   checked exceptions; we propagate them so JUnit can
     *                   report the failure with the original cause attached.
     */
    @Test
    void login_returns200_onSuccess() throws Exception {
        LoginRequestDto dto = new LoginRequestDto("alice", "pwd");

        AuthResponseDto authResponseDto = new AuthResponseDto("dummy.jwt.token", "alice", "USER", 3600000L);

        ResponseStructure<AuthResponseDto> stubResponse = new ResponseStructure<>();
        stubResponse.setStatusCode(200);
        stubResponse.setApiDescription("Login successful");
        stubResponse.setData(authResponseDto);

        when(authService.login(any(LoginRequestDto.class))).thenReturn(stubResponse);
        System.out.println("[AuthControllerTest] login stub configured; invoking POST /auth/login");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.apiDescription").value("Login successful"))
                .andExpect(jsonPath("$.data.token").value("dummy.jwt.token"))
                .andExpect(jsonPath("$.data.username").value("alice"))
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.expiresInMs").value(3600000));

        System.out.println("[AuthControllerTest] login endpoint verified.");
    }
}
