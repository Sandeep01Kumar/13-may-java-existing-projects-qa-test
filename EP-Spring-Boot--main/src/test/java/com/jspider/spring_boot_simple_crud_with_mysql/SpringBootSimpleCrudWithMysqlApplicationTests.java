package com.jspider.spring_boot_simple_crud_with_mysql;

import com.jspider.spring_boot_simple_crud_with_mysql.config.RateLimitingFilter;
import com.jspider.spring_boot_simple_crud_with_mysql.exception.GlobalExceptionHandler;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("test")
class SpringBootSimpleCrudWithMysqlApplicationTests {

	@Autowired
	private ApplicationContext applicationContext;

	@Test
	void contextLoads() {
		assertNotNull(applicationContext.getBean(SecurityFilterChain.class),
				"SecurityFilterChain bean should be present after the security retrofit");
		assertNotNull(applicationContext.getBean("corsConfigurationSource", CorsConfigurationSource.class),
				"CorsConfigurationSource bean should be present for global CORS enforcement");
		assertNotNull(applicationContext.getBean(RateLimitingFilter.class),
				"RateLimitingFilter bean should be present for per-client request throttling");
		assertNotNull(applicationContext.getBean(GlobalExceptionHandler.class),
				"GlobalExceptionHandler bean should be present for structured security error responses");
	}

}
