package com.jspider.spring_boot_simple_crud_with_mysql.service;

import java.util.Collections;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.jspider.spring_boot_simple_crud_with_mysql.repository.UserRepository;

// Rule Applied
@Service
public class CustomUserDetailsService implements UserDetailsService {

	@Autowired
	UserRepository userRepository;

	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		System.out.println("CustomUserDetailsService.loadUserByUsername called for: " + username);
		com.jspider.spring_boot_simple_crud_with_mysql.entity.User user =
				userRepository.findByUsername(username)
						.orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
		return User.builder()
				.username(user.getUsername())
				.password(user.getPassword())
				.authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + user.getRole())))
				.build();
	}
}
