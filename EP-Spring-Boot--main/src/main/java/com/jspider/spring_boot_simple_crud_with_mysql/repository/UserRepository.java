package com.jspider.spring_boot_simple_crud_with_mysql.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.jspider.spring_boot_simple_crud_with_mysql.entity.User;

/**
 * Spring Data JPA repository contract for the {@link User} JPA entity.
 *
 * <p>This is the persistence-layer entry point introduced by the JWT
 * authentication feature. It is consumed by:</p>
 * <ul>
 *   <li>{@code UserDetailsServiceImpl#loadUserByUsername(String)} — to resolve
 *       the principal during JWT authentication and during the
 *       {@code POST /auth/login} flow. The service chains
 *       {@code .orElseThrow(UsernameNotFoundException::new)} on the result of
 *       {@link #findByUsername(String)}, which is exactly why the return type
 *       is {@link Optional} rather than the raw entity.</li>
 *   <li>{@code AuthService#register(...)} — to guard against duplicate
 *       registrations via {@link #existsByUsername(String)} before calling
 *       {@link JpaRepository#save(Object)}.</li>
 * </ul>
 *
 * <p>The interface mirrors the existing {@code ProductRepository.java}
 * convention exactly. Notable differences:</p>
 * <ul>
 *   <li>The aggregate-id wrapper type is {@link Long} (not {@code Integer}),
 *       matching {@code User#id} which is declared as
 *       {@code @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id}.
 *       Using {@code Integer} here would cause Spring Data to coerce the
 *       generated {@code BIGINT} key into a 32-bit wrapper at runtime and
 *       trigger {@code ClassCastException} or {@code IllegalArgumentException}
 *       on repository operations.</li>
 *   <li>The derived queries operate on the unique {@code username} property,
 *       so {@link #findByUsername(String)} returns {@code Optional<User>}
 *       (single-result semantics) rather than the {@code List<Product>}
 *       used by {@code ProductRepository#findByName(String)}.</li>
 * </ul>
 *
 * <p>No {@code @Repository} stereotype annotation is required: Spring Data's
 * repository scanner detects every interface that extends {@link JpaRepository}
 * (or any other {@code Repository} sub-interface) and auto-registers it as a
 * bean — the existing {@code ProductRepository} relies on the same convention.</p>
 *
 * <p>Note: the {@code User} entity is mapped to the physical table
 * {@code app_user} via {@code @Table(name = "app_user")} to avoid the
 * MySQL 8.x reserved-word collision with {@code USER}. The derived queries
 * declared here operate on entity properties only ({@code username}); the
 * entity-to-table translation is handled transparently by Hibernate.</p>
 */
// Rule Applied
public interface UserRepository extends JpaRepository<User, Long> {

	/**
	 * Look up a user by their unique username.
	 *
	 * <p>Spring Data parses the method name at startup and synthesizes a
	 * proxy implementation equivalent to
	 * {@code SELECT * FROM app_user WHERE username = ?} — no
	 * {@code @Query} annotation is required.</p>
	 *
	 * <p>Because the {@code username} column carries a database-level
	 * {@code UNIQUE} constraint, this query returns at most one row; the
	 * Spring Data idiom for "may not exist" semantics on a unique-result
	 * derived query is {@link Optional}.</p>
	 *
	 * @param username the username to search for (must be non-null; the
	 *                 caller is responsible for upstream validation)
	 * @return an {@link Optional} containing the matching {@link User}, or
	 *         {@link Optional#empty()} if no row matches
	 */
	Optional<User> findByUsername(String username);

	/**
	 * Cheap existence check by unique username.
	 *
	 * <p>Spring Data synthesizes this into a {@code SELECT COUNT(*) > 0}
	 * (or dialect-equivalent {@code EXISTS(...)}) SQL form, which is more
	 * efficient than fetching the full entity just to call
	 * {@code Optional#isPresent()}.</p>
	 *
	 * <p>Used by {@code AuthService#register(...)} to short-circuit
	 * duplicate registrations before delegating to
	 * {@link JpaRepository#save(Object)}; the database-level
	 * {@code UNIQUE} constraint remains the ultimate safety net in case
	 * of a race between two concurrent {@code register} calls.</p>
	 *
	 * @param username the username to test for
	 * @return {@code true} if a row with the given username already exists,
	 *         {@code false} otherwise
	 */
	boolean existsByUsername(String username);
}
