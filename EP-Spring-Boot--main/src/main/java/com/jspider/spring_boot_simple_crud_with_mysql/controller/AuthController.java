package com.jspider.spring_boot_simple_crud_with_mysql.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jspider.spring_boot_simple_crud_with_mysql.dto.AuthResponse;
import com.jspider.spring_boot_simple_crud_with_mysql.dto.LoginRequest;
import com.jspider.spring_boot_simple_crud_with_mysql.dto.RegisterRequest;
import com.jspider.spring_boot_simple_crud_with_mysql.service.AuthService;

// Rule Applied
@RestController
@RequestMapping("/auth")
public class AuthController {

	@Autowired
	AuthService authService;

	@PostMapping("/register")
	public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request) {
		System.out.println("AuthController.register called for username: " + request.getUsername());
		AuthResponse response = authService.register(request);
		return ResponseEntity.ok(response);
	}

	@PostMapping("/login")
	public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
		System.out.println("AuthController.login called for username: " + request.getUsername());
		AuthResponse response = authService.login(request);
		return ResponseEntity.ok(response);
	}
}
