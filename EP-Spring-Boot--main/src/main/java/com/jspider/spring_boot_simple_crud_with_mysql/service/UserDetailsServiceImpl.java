package com.jspider.spring_boot_simple_crud_with_mysql.service;

import java.util.List;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.jspider.spring_boot_simple_crud_with_mysql.entity.User;
import com.jspider.spring_boot_simple_crud_with_mysql.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Spring Security principal-lookup adapter for the JWT authentication feature.
 *
 * <p>This {@link Service} is the single implementation of Spring Security's
 * {@link UserDetailsService} contract that the rest of the security layer is
 * compiled against. Its sole responsibility is to translate a username string
 * into a fully-populated {@link UserDetails} principal by:</p>
 *
 * <ol>
 *   <li>Delegating the database lookup to
 *       {@link UserRepository#findByUsername(String)}.</li>
 *   <li>Adapting the resulting {@code com.jspider.spring_boot_simple_crud_with_mysql.entity.User}
 *       JPA entity to Spring Security's built-in
 *       {@code org.springframework.security.core.userdetails.User} adapter
 *       (referenced by its fully-qualified name to avoid the import-level
 *       naming collision with the JPA entity).</li>
 *   <li>Mapping the entity's single {@code role} column to exactly one
 *       {@link SimpleGrantedAuthority} prefixed with {@code "ROLE_"} and
 *       normalised to upper-case so that
 *       {@code SecurityExpressions#hasRole(String)} matches without the
 *       application having to re-issue tokens when fine-grained
 *       authorisation rules are added later.</li>
 * </ol>
 *
 * <p>The class is consumed by three collaborators wired through Spring's
 * component scan:</p>
 * <ul>
 *   <li>{@code DaoAuthenticationProvider} (declared in
 *       {@code config/SecurityConfig.java}) &mdash; resolves the principal
 *       during {@code AuthenticationManager.authenticate(...)} on
 *       {@code POST /auth/login}.</li>
 *   <li>{@code JwtAuthenticationFilter} (declared in
 *       {@code security/JwtAuthenticationFilter.java}) &mdash; resolves the
 *       principal once per protected request after the bearer token's subject
 *       claim is extracted.</li>
 *   <li>{@code AuthService.login(...)} (in this same package) &mdash; reloads
 *       the principal for JWT minting once
 *       {@code AuthenticationManager.authenticate(...)} succeeds.</li>
 * </ul>
 *
 * <h2>Why a single role mapped to a single {@code SimpleGrantedAuthority}?</h2>
 * <p>The {@code User} entity carries exactly one role string (e.g.
 * {@code "USER"} or {@code "ADMIN"}); wrapping it in an immutable
 * single-element {@link List} via {@link List#of(Object)} matches the entity's
 * cardinality without introducing a custom multi-role schema. Should the AAP
 * later require multi-role support, only this adapter (and the {@code role}
 * column) need change &mdash; the rest of the security layer is already
 * authorities-collection based.</p>
 *
 * <h2>Why throw {@link UsernameNotFoundException} and not a generic exception?</h2>
 * <p>Spring Security's {@code DaoAuthenticationProvider} explicitly catches
 * {@link UsernameNotFoundException} and translates it into a
 * {@code BadCredentialsException} so the API caller cannot distinguish
 * "unknown user" from "wrong password" &mdash; this is the canonical defence
 * against account-enumeration attacks. Throwing any other exception would
 * bypass that protection.</p>
 *
 * <h2>Why no {@code @Transactional}?</h2>
 * <p>{@link UserRepository#findByUsername(String)} is a single read; Spring
 * Data JPA opens its own implicit transaction (or session) per call. Wrapping
 * this thin adapter in {@code @Transactional} would buy nothing but extra
 * proxying cost.</p>
 */
@Service
@RequiredArgsConstructor
// Rule Applied
public class UserDetailsServiceImpl implements UserDetailsService {

    /**
     * Spring Data JPA repository used to resolve the {@link User} entity by
     * its unique {@code username}. Constructor-injected by Lombok's
     * {@link RequiredArgsConstructor} (immutable final field, no
     * {@code @Autowired} field injection).
     */
    private final UserRepository userRepository;

    /**
     * Loads a Spring Security principal by username and adapts the persisted
     * {@link User} entity to a {@link UserDetails} instance.
     *
     * <p>The method performs a single repository lookup. If the username is
     * not present in the database, a {@link UsernameNotFoundException} is
     * raised &mdash; {@code DaoAuthenticationProvider} catches that exception
     * and translates it into {@code BadCredentialsException} so the
     * application never reveals whether a username exists.</p>
     *
     * <p>When the entity is found, it is wrapped in the built-in
     * {@code org.springframework.security.core.userdetails.User} adapter,
     * referenced by its fully-qualified class name to avoid the import-level
     * naming collision with the JPA {@code User} entity. The 3-argument
     * constructor used here defaults {@code enabled},
     * {@code accountNonExpired}, {@code credentialsNonExpired}, and
     * {@code accountNonLocked} to {@code true} &mdash; which is correct for
     * this AAP scope (the feature does not model deactivated, expired, or
     * locked accounts).</p>
     *
     * <p>The role is mapped to a single {@link SimpleGrantedAuthority}
     * prefixed with {@code "ROLE_"} and upper-cased so that Spring Security's
     * {@code hasRole(...)} expressions match the canonical form. The
     * authorities list is built via {@link List#of(Object)} which returns an
     * immutable single-element list &mdash; the modern Java 17 idiom for
     * {@code Collections.singletonList(...)}.</p>
     *
     * @param username the canonical username extracted from either the
     *                 {@code LoginRequestDto} during {@code AuthService.login(...)}
     *                 or the JWT subject claim during
     *                 {@code JwtAuthenticationFilter.doFilterInternal(...)};
     *                 must not be {@code null} (the upstream callers
     *                 guarantee this)
     * @return a fully-populated {@link UserDetails} adapter ready for
     *         consumption by {@code DaoAuthenticationProvider},
     *         {@code JwtAuthenticationFilter}, or the
     *         {@code SecurityContextHolder}
     * @throws UsernameNotFoundException if no row exists in the
     *                                   {@code app_user} table whose
     *                                   {@code username} column matches the
     *                                   supplied value
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        System.out.println("[UserDetailsServiceImpl] loadUserByUsername invoked for username=" + username);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().toUpperCase()))
        );
    }
}
