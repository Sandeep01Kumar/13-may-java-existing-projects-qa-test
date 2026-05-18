package com.jspider.spring_boot_simple_crud_with_mysql;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Smoke test that boots the full Spring application context.
 *
 * <p>This class is annotated with {@link TestPropertySource} per AAP &sect;0.6.1
 * so the context-load succeeds without a live MySQL instance. The overrides
 * substitute an in-memory H2 database for the production MySQL JDBC URL,
 * disable SSL on the test datasource, switch Hibernate's schema mode to
 * {@code create-drop} for the test lifecycle, and pin a deterministic Spring
 * Security default user/password so the test fixture is reproducible.
 *
 * <p>The AAP explicitly endorses either {@code @ActiveProfiles("test")} with a
 * companion {@code application-test.properties} resource OR this inline
 * {@code @TestPropertySource} form &mdash; both are acceptable and equivalent
 * for the purpose of keeping {@link #contextLoads()} green after the security
 * starters and JDBC-TLS hardening are introduced by the surrounding fix set.
 */
@SpringBootTest
@TestPropertySource(properties = {
		// Use H2 in-memory DB to bypass the production MySQL JDBC URL + TLS flags
		// that application.properties may carry in production profiles. The
		// MODE=MySQL flag tells H2 to accept MySQL-flavored DDL/DML emitted by
		// the JPA layer in this codebase.
		"spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=MySQL",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		// Hibernate dialect is auto-detected from the JDBC URL, but pin it
		// explicitly so the boot does not need a live JDBC handshake.
		"spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
		// Recreate the schema for the test lifecycle (production uses 'validate').
		"spring.jpa.hibernate.ddl-auto=create-drop",
		// Deterministic Spring Security default user; with no SecurityConfig
		// bean Spring Boot otherwise generates a random password at startup,
		// making test fixtures non-reproducible.
		"spring.security.user.name=test",
		"spring.security.user.password=test"
})
class SpringBootSimpleCrudWithMysqlApplicationTests {

	@Test
	void contextLoads() {
	}

}
