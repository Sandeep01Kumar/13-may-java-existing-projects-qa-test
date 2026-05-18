# Security Policy

`spring-boot-simple-crud-with-mysql` is a Spring Boot 3.4.x / Java 17 didactic CRUD service that persists `Product` and `Student` records through a JPA / MySQL backend. This document describes how to report security vulnerabilities responsibly and summarizes the security controls currently implemented in the codebase. It is intended for end users, integrators, contributors, and security researchers who interact with the project or its deployed instances.

## Supported Versions

The table below lists the versions of `spring-boot-simple-crud-with-mysql` that currently receive security patches. Older versions should be upgraded to the supported line in order to receive fixes.

| Version | Supported |
|---------|-----------|
| `0.0.1-SNAPSHOT` (current) | :white_check_mark: |
| < `0.0.1-SNAPSHOT` | :x: |

"Supported" means the maintainers will produce, review, and release security patches for the indicated version line. Versions marked with :x: are end-of-life from a security-fix perspective; any deployment running an unsupported version should be upgraded to the current `0.0.1-SNAPSHOT` line before any security report can be evaluated against it.

## Reporting a Vulnerability

> **DO NOT open public GitHub issues for security vulnerabilities.** Public issues are visible to anyone on the internet and can be exploited before a fix is available. Use the private GitHub Security Advisories channel described below instead.

**Reporting channel — GitHub Security Advisories (private).** Navigate to the repository's `Security` tab, choose `Report a vulnerability`, and complete the private advisory form. GitHub will route the report directly to the maintainers without disclosing any detail publicly. Detailed guidance for reporters is available at <https://docs.github.com/en/code-security/security-advisories/guidance-on-reporting-and-writing-information-about-vulnerabilities/privately-reporting-a-security-vulnerability>.

GitHub Security Advisories is the **only** currently supported private reporting channel for this repository. Reporters who require an out-of-band channel (for example, encrypted email or PGP-based key exchange) should open a private GitHub Security Advisory first and request the desired channel there; the maintainers will respond with a follow-up channel as part of the initial acknowledgement.

**What to include in a report.** A high-quality report contains: (1) the affected version (e.g., `0.0.1-SNAPSHOT` at commit SHA `abcdef0`); (2) the vulnerable component (e.g., `com.jspider.spring_boot_simple_crud_with_mysql.controller.ProductController`); (3) a minimal reproduction (HTTP request, payload, sequence of calls); (4) the expected security impact (confidentiality, integrity, availability); and (5) any suggested remediation or references to similar prior advisories.

**Service-level commitment.** We acknowledge security reports within **5 business days** of receipt and aim to provide an initial remediation timeline within **10 business days**. Critical vulnerabilities with active exploitation will be prioritized ahead of the standard schedule. Reporters will receive status updates at significant milestones (triage complete, fix in progress, fix released, advisory published).

## Dependency Update Cadence

The maintainers patch Maven dependencies promptly upon publication of new advisories at <https://spring.io/security> and <https://nvd.nist.gov>. Each release of `spring-boot-simple-crud-with-mysql` records the closed advisory identifiers in its commit and release notes so the audit trail is preserved end-to-end.

Dependency hygiene is enforced in continuous integration by running `./mvnw org.owasp:dependency-check-maven:check -DfailBuildOnCVSS=7`. The build fails on any advisory with a CVSS base score of 7.0 or higher, which prevents regressions to a vulnerable transitive component without an explicit, reviewed exception.

The project commits to staying current on the **3.4.x Spring Boot maintenance line** until the upstream maintenance window ends. When the 3.4 line reaches end-of-support, a planned migration to the next supported minor line will be opened as a tracked initiative with its own security-review checkpoint.

Direct Maven coordinates under active monitoring (group:artifact, with version managed either explicitly or by the Spring Boot BOM):

- `org.springframework.boot:spring-boot-starter-parent` — parent BOM, cascades patches across Spring Framework, Spring Security, embedded Tomcat, Jackson, and the JDBC drivers.
- `org.springdoc:springdoc-openapi-starter-webmvc-ui` — explicit version pin in `pom.xml`; bumped per springdoc release notes.
- `com.bucket4j:bucket4j-core` — explicit version pin; rate-limiting library, not part of the Spring Boot BOM.
- `com.mysql:mysql-connector-j` — transitively managed by the parent BOM.
- `com.h2database:h2` — transitively managed by the parent BOM (test/local scope).

## Security Posture

The security controls implemented in the current version are summarized below. Each bullet is a one-line statement of the control; see the codebase for the canonical configuration.

- **(a) Spring Security 6.x** — stateless `SecurityFilterChain` with HTTP Basic authentication (didactic default; production deployments should migrate to JWT/OAuth2).
- **(b) HTTP Security Headers** — `Strict-Transport-Security` (HSTS with `includeSubDomains` and a 1-year `max-age`), `Content-Security-Policy` (`default-src 'self'; frame-ancestors 'none'; object-src 'none'; base-uri 'self'`), `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: strict-origin-when-cross-origin`, `Permissions-Policy: geolocation=(), microphone=(), camera=()`, and `X-Permitted-Cross-Domain-Policies: none`. These are the JVM-native equivalents of the headers commonly set by helmet.js in Node.js applications, and they are implemented here via Spring Security's `HeadersConfigurer` — no Node.js or cross-stack runtime is introduced.
- **(c) Global CORS allow-list** via a `CorsConfigurationSource` bean wired into `SecurityFilterChain.cors(...)`; replaces the previously ambiguous class-level `@CrossOrigin(value = "")` on `ProductController`.
- **(d) Per-IP rate limiting** via Bucket4j 8.10.1, configured by `app.security.rate-limit.capacity`, `app.security.rate-limit.refill-tokens`, and `app.security.rate-limit.refill-period-seconds`. Default: 100 requests per 60-second window per remote IP.
- **(e) TLS termination at embedded Tomcat** on port `8443` (replacing plaintext port `8090`). Keystore: `classpath:keystore.p12` (PKCS12), password externalized via `${KEYSTORE_PASSWORD}`.
- **(f) JDBC TLS** — the MySQL Connector/J URL is augmented with `useSSL=true&requireSSL=true&verifyServerCertificate=true` so the database channel is encrypted and the server certificate is validated against the JVM truststore.
- **(g) Externalized credentials** via environment variables `DB_USERNAME`, `DB_PASSWORD`, `KEYSTORE_PASSWORD`, and `CORS_ALLOWED_ORIGINS`. Plaintext credentials are no longer present in any source-controlled file.
- **(h) Jakarta Bean Validation 3.x** with `@Valid` enforcement on all `@RequestBody` parameters. Constraints are applied at two distinct layers, with deliberately different semantics: the request DTO `ProductRequestDto` carries `@NotBlank` + `@Size` on `name`/`color` and `@NotNull` + `@Positive` + `@DecimalMax` on a boxed `Double price` (so that an omitted or `null` price is rejected at the request boundary), while the persisted `Product` entity carries `@NotBlank` + `@Size` on `name`/`color` and `@Positive` + `@DecimalMax` on a primitive `double price` (no `@NotNull`, because a primitive `double` field is non-nullable by definition and adding the constraint would have no effect).
- **(i) Mass-assignment defense** via `ProductRequestDto` (no `id` field) — client-supplied `id` values are ignored on `POST /product/save`, and persistence-managed identifiers are assigned exclusively by the database.
- **(j) Method-level authorization** via `@PreAuthorize` — `USER` role is required for write operations and `ADMIN` role is required for `DELETE` operations on the `/product/*` surface.
- **(k) Global exception handling** via `@RestControllerAdvice` — validation errors return structured HTTP 400, authorization errors return HTTP 403, authentication errors return HTTP 401, and internal errors return HTTP 500 with **no stack trace** in the response body.
- **(l) JPA hardening** — `spring.jpa.hibernate.ddl-auto=validate` (no silent schema drift on startup) and `spring.jpa.show-sql=false` (no parameter-bearing SQL statements leaking into application logs).

## OWASP Top 10 (2021) Coverage

The table below maps the implemented controls to the OWASP Top 10 (2021) categories they address. It is a self-assessment, not an external certification.

| OWASP Category | How Addressed |
|----------------|---------------|
| A01 Broken Access Control | `SecurityFilterChain.authorizeHttpRequests(...).anyRequest().authenticated()`; `@PreAuthorize` on write and delete methods; explicit allow-list for `/actuator/health`, `/v3/api-docs/**`, `/swagger-ui/**`. |
| A02 Cryptographic Failures | TLS termination on HTTPS port `8443`; JDBC TLS with server-certificate validation; database, keystore, and CORS credentials externalized via environment variables. |
| A03 Injection | Jakarta Bean Validation on the `Product` entity and the request DTO; positional `?` parameters in JPA `@Query` definitions (no string interpolation). |
| A04 Insecure Design | Bucket4j per-IP rate limiting on every endpoint; structured global exception handling that fails closed and does not leak stack traces. |
| A05 Security Misconfiguration | Full security header set via `HeadersConfigurer`; global CORS allow-list; `ddl-auto=validate`; `show-sql=false`; Swagger UI disabled in the `prod` profile. |
| A06 Vulnerable and Outdated Components | Spring Boot parent BOM and springdoc version bumps; OWASP dependency-check Maven plugin enforced in CI with `failBuildOnCVSS=7`. |

## Deployment Requirements

The deploy environment **MUST** provide the following environment variables. Missing values cause the application to fail closed at startup; this is intentional defense-in-depth so a misconfigured deployment cannot accidentally serve traffic with degraded security.

- `DB_USERNAME` — application-scoped database user. **Do not use `root`** in production; create a dedicated user with the minimum privileges required by the `Product` and `Student` tables.
- `DB_PASSWORD` — application-scoped database password. **No default value.** The application fails closed at startup if this variable is not set.
- `KEYSTORE_PASSWORD` — password for the PKCS12 keystore at `classpath:keystore.p12`. Required when `server.ssl.enabled=true`.
- `CORS_ALLOWED_ORIGINS` — comma-separated list of allowed origins, for example `https://app.example.com,https://admin.example.com`. Defaults to `https://localhost:8443` when unset, which is suitable for local development only.

The PKCS12 keystore (`keystore.p12`) must be either bundled in `src/main/resources` at build time (for development) or mounted at deploy time at a path resolvable on the classpath. For local development the keystore can be generated with `openssl pkcs12 -export -in cert.pem -inkey key.pem -name tomcat -out keystore.p12`. For production environments an organizational certificate authority or an ACME provider (e.g., Let's Encrypt) should issue the certificate, and the keystore should be rotated on the same cadence as the certificate.

## Future Security Work

The following items are flagged as future security work. They are intentionally out of scope for the current hardening pass and tracked here so they are visible to contributors and reviewers.

- [ ] Migration from HTTP Basic to JWT or OAuth2 (resource-server) authentication.
- [ ] JPA-backed `UserDetailsService` replacing the current in-memory user.
- [ ] Audit logging via Hibernate Envers or `@EnableJpaAuditing` with an `AuditorAware` implementation.
- [ ] Spring Boot Actuator with secured `/actuator/*` endpoints (health, metrics, info) for operational observability.
- [ ] Flyway or Liquibase for managed database migrations (replacing `spring.jpa.hibernate.ddl-auto=validate` with explicit, version-controlled migration scripts).
- [ ] Distributed rate limiting (Redis-backed Bucket4j) for multi-replica deployments where per-instance buckets would over-allow.
- [ ] GitHub Dependabot configuration and `.github/workflows/dependency-review.yml` for automated future advisory tracking and pull-request gating.
- [ ] Migration to a secrets manager (HashiCorp Vault, AWS Secrets Manager, or Spring Cloud Config Server) for credential management, replacing direct environment-variable resolution.

## References

The implementation and this document are aligned with the following canonical sources. Reviewers and contributors are encouraged to consult them directly when extending or auditing the project's security posture.

- Spring Security Reference, "Headers" chapter — <https://docs.spring.io/spring-security/reference/servlet/exploits/headers.html>
- Spring Security Reference, "CORS" — <https://docs.spring.io/spring-security/reference/servlet/integrations/cors.html>
- Spring Security advisory feed — <https://spring.io/security>
- OWASP Top 10 (2021) — <https://owasp.org/Top10/>
- OWASP Cheat Sheet Series — <https://cheatsheetseries.owasp.org/>
- MySQL Connector/J Security and SSL/TLS — <https://dev.mysql.com/doc/connector-j/en/connector-j-reference-using-ssl.html>
- Bucket4j documentation — <https://bucket4j.com/>
- Jakarta Bean Validation 3.0 specification — <https://beanvalidation.org/3.0/spec/>
- GitHub Security Advisories guidance — <https://docs.github.com/en/code-security/security-advisories>
