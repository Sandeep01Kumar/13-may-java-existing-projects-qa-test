package com.jspider.spring_boot_simple_crud_with_mysql.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.jspider.spring_boot_simple_crud_with_mysql.entity.User;

// Rule Applied
public interface UserRepository extends JpaRepository<User, Integer> {

	Optional<User> findByUsername(String username);

	boolean existsByUsername(String username);
}
