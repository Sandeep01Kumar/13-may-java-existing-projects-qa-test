package com.jspider.spring_boot_simple_crud_with_mysql.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.jspider.spring_boot_simple_crud_with_mysql.dto.AuthResponse;
import com.jspider.spring_boot_simple_crud_with_mysql.dto.LoginRequest;
import com.jspider.spring_boot_simple_crud_with_mysql.dto.RegisterRequest;
import com.jspider.spring_boot_simple_crud_with_mysql.entity.User;
import com.jspider.spring_boot_simple_crud_with_mysql.repository.UserRepository;

// Rule Applied
@Service
public class AuthService {

	@Autowired
	UserRepository userRepository;

	@Autowired
	PasswordEncoder passwordEncoder;

	@Autowired
	AuthenticationManager authenticationManager;

	@Autowired
	JwtService jwtService;

	public AuthResponse register(RegisterRequest request) {
		System.out.println("AuthService.register called for username: " + request.getUsername());
		if (userRepository.existsByUsername(request.getUsername())) {
			throw new RuntimeException("Username already exists: " + request.getUsername());
		}
		User user = new User();
		user.setUsername(request.getUsername());
		user.setPassword(passwordEncoder.encode(request.getPassword()));
		String role = (request.getRole() == null || request.getRole().isBlank()) ? "USER" : request.getRole();
		user.setRole(role);
		userRepository.save(user);
		return new AuthResponse(null, user.getUsername(), user.getRole(), "Bearer");
	}

	public AuthResponse login(LoginRequest request) {
		System.out.println("AuthService.login called for username: " + request.getUsername());
		Authentication authentication = authenticationManager.authenticate(
				new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
		);
		UserDetails userDetails = (UserDetails) authentication.getPrincipal();
		User user = userRepository.findByUsername(request.getUsername())
				.orElseThrow(() -> new RuntimeException("User not found after authentication: " + request.getUsername()));
		String token = jwtService.generateToken(userDetails);
		return new AuthResponse(token, user.getUsername(), user.getRole(), "Bearer");
	}
}
