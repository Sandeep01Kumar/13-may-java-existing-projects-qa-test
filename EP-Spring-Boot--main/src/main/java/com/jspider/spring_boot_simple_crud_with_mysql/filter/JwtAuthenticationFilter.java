package com.jspider.spring_boot_simple_crud_with_mysql.filter;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.jspider.spring_boot_simple_crud_with_mysql.service.CustomUserDetailsService;
import com.jspider.spring_boot_simple_crud_with_mysql.service.JwtService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// Rule Applied
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	@Autowired
	JwtService jwtService;

	@Autowired
	CustomUserDetailsService customUserDetailsService;

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
			FilterChain chain) throws ServletException, IOException {
		System.out.println("JwtAuthenticationFilter.doFilterInternal: " + request.getMethod() + " " + request.getRequestURI());

		String authHeader = request.getHeader("Authorization");
		if (authHeader == null || !authHeader.startsWith("Bearer ")) {
			chain.doFilter(request, response);
			return;
		}

		String token = authHeader.substring(7);
		String username = null;
		try {
			username = jwtService.extractUsername(token);
		} catch (Exception e) {
			System.out.println("JwtAuthenticationFilter: token parsing failed: " + e.getMessage());
			chain.doFilter(request, response);
			return;
		}

		if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
			// Wrap user lookup and token validation in a try/catch so that
			// expected authentication failures (e.g. a validly-signed token
			// whose subject no longer exists in the users table) do NOT
			// propagate as runtime exceptions. Without this guard the
			// UsernameNotFoundException thrown by CustomUserDetailsService
			// would bubble out of the filter chain and trigger Spring's
			// /error dispatch with a logged stack trace before the normal
			// 401 response from JwtAuthEntryPoint could be issued.
			try {
				UserDetails userDetails = customUserDetailsService.loadUserByUsername(username);
				if (jwtService.isTokenValid(token, userDetails)) {
					UsernamePasswordAuthenticationToken authToken =
							new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
					authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
					SecurityContextHolder.getContext().setAuthentication(authToken);
				}
			} catch (Exception e) {
				// Log a single, generic message (no stack trace) and leave
				// the SecurityContext unauthenticated. The downstream
				// AuthorizationFilter will then route the unauthenticated
				// request through JwtAuthEntryPoint, producing the standard
				// 401 JSON response for the originally requested path.
				System.out.println("JwtAuthenticationFilter: authentication failed for token subject '"
						+ username + "': " + e.getMessage());
				SecurityContextHolder.clearContext();
			}
		}

		chain.doFilter(request, response);
	}
}
