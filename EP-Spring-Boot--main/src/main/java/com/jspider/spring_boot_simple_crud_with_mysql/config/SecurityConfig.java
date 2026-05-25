package com.jspider.spring_boot_simple_crud_with_mysql.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.jspider.spring_boot_simple_crud_with_mysql.filter.JwtAuthenticationFilter;
import com.jspider.spring_boot_simple_crud_with_mysql.service.CustomUserDetailsService;

// Rule Applied
@Configuration
@EnableWebSecurity
public class SecurityConfig {

	@Autowired
	JwtAuthenticationFilter jwtAuthenticationFilter;

	@Autowired
	JwtAuthEntryPoint jwtAuthEntryPoint;

	@Autowired
	CustomUserDetailsService customUserDetailsService;

	@Bean
	public PasswordEncoder passwordEncoder() {
		System.out.println("SecurityConfig.passwordEncoder: creating BCryptPasswordEncoder bean");
		return new BCryptPasswordEncoder();
	}

	@Bean
	public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
		System.out.println("SecurityConfig.authenticationManager: exposing AuthenticationManager bean");
		return config.getAuthenticationManager();
	}

	@Bean
	public DaoAuthenticationProvider daoAuthenticationProvider() {
		System.out.println("SecurityConfig.daoAuthenticationProvider: wiring CustomUserDetailsService + BCryptPasswordEncoder");
		DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
		provider.setUserDetailsService(customUserDetailsService);
		provider.setPasswordEncoder(passwordEncoder());
		return provider;
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		System.out.println("SecurityConfig.securityFilterChain: configuring stateless JWT security");
		http
			// CSRF protection is disabled because this application is a
			// stateless JWT REST API. It does not issue session cookies, so
			// there is no session-bound state for an attacker to forge a
			// request against; the JWT in the Authorization header is the
			// sole credential and is not automatically attached by browsers
			// to cross-site requests.
			.csrf(csrf -> csrf.disable())
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/auth/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
				.anyRequest().authenticated()
			)
			// STATELESS session creation policy ensures Spring Security never
			// creates or consults an HttpSession. Every request must carry its
			// own JWT in the Authorization header, which is consistent with
			// the JWT-based identity model and prevents session fixation /
			// session hijacking on this API surface.
			.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.exceptionHandling(eh -> eh.authenticationEntryPoint(jwtAuthEntryPoint))
			.authenticationProvider(daoAuthenticationProvider())
			// JwtAuthenticationFilter is registered BEFORE the default
			// UsernamePasswordAuthenticationFilter so that the JWT is parsed,
			// validated, and a fully-populated Authentication is placed in
			// the SecurityContext before any form-login machinery runs.
			// This makes the filter ordering the explicit entry point of
			// stateless authentication for every protected request.
			.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
		return http.build();
	}
}
