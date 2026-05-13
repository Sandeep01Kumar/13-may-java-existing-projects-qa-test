package com.jspider.spring_boot_simple_crud_with_mysql.security;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

/**
 * JWT minting, parsing and validation utility for the Product CRUD application's
 * authentication layer.
 *
 * <p>This Spring-managed {@link Component} is the single, focused owner of the JSON Web
 * Token lifecycle: it builds HMAC-SHA256-signed compact JWS strings on successful login
 * ({@link #generateToken(UserDetails)}), exposes the encoded subject for downstream
 * principal lookup ({@link #extractUsername(String)}), and answers a strict
 * boolean validity question for the JWT authentication filter
 * ({@link #isTokenValid(String, UserDetails)}). It deliberately knows nothing about
 * HTTP, persistence, or authentication credential matching — those concerns live in
 * {@code JwtAuthenticationFilter} and {@code AuthService} respectively.</p>
 *
 * <p>The signing key, the token lifetime, and the issuer claim are all externalised
 * to {@code application.properties} under the {@code app.jwt.*} namespace so that
 * secrets and rotation policies never leak into source control:</p>
 * <ul>
 *   <li>{@code app.jwt.secret} — Base64-encoded secret used to construct the HMAC
 *       signing key. The decoded byte length MUST be at least 32 bytes (256 bits)
 *       to satisfy JJWT's HS256 weak-key guard.</li>
 *   <li>{@code app.jwt.expiration-ms} — token lifetime in milliseconds.</li>
 *   <li>{@code app.jwt.issuer} — value written into the JWT {@code iss} claim.</li>
 * </ul>
 *
 * <p>The implementation targets the JJWT 0.11.5 API ({@code Jwts.builder()},
 * {@code Jwts.parserBuilder()}, {@link SignatureAlgorithm#HS256}); it must not be
 * upgraded to the JJWT 0.12.x API without coordinated changes in {@code pom.xml}.</p>
 */
@Component
// Rule Applied
public class JwtUtil {

    /**
     * Base64-encoded HMAC secret used to sign and verify all JWTs issued by the
     * application. Decoded inside {@link #getSigningKey()} via
     * {@link Decoders#BASE64}. Stored as the raw Base64 string here so the JVM
     * never holds the decoded bytes in a long-lived field.
     */
    @Value("${app.jwt.secret}")
    private String jwtSecret;

    /**
     * Token lifetime in milliseconds. Declared as a {@code long} primitive so
     * Spring Boot's relaxed binding can convert the configured value directly
     * without an autoboxing detour.
     */
    @Value("${app.jwt.expiration-ms}")
    private long jwtExpirationMs;

    /**
     * Value written into the JWT {@code iss} (issuer) standard claim.
     */
    @Value("${app.jwt.issuer}")
    private String jwtIssuer;

    /**
     * Mints a signed, compact JWS string for the supplied authenticated principal.
     *
     * <p>The resulting token carries the following claims:</p>
     * <ul>
     *   <li>{@code iss} — set to the configured {@link #jwtIssuer}.</li>
     *   <li>{@code sub} — set to {@link UserDetails#getUsername()}.</li>
     *   <li>{@code role} — custom claim populated from the principal's first
     *       {@link GrantedAuthority#getAuthority()} (e.g. {@code "ROLE_USER"});
     *       falls back to {@code "ROLE_USER"} when the principal carries no
     *       authorities.</li>
     *   <li>{@code iat} — current instant.</li>
     *   <li>{@code exp} — current instant plus {@link #jwtExpirationMs}.</li>
     * </ul>
     *
     * <p>The token is signed with HMAC-SHA256 using the key produced by
     * {@link #getSigningKey()}.</p>
     *
     * @param userDetails the authenticated principal whose username and role
     *                    are encoded into the resulting JWT; must not be {@code null}
     * @return a non-empty compact JWS string consisting of three Base64URL
     *         segments separated by {@code '.'}
     */
    public String generateToken(UserDetails userDetails) {
        System.out.println("[JwtUtil] generateToken invoked for subject=" + userDetails.getUsername());

        String role = userDetails.getAuthorities().stream()
                .findFirst()
                .map(GrantedAuthority::getAuthority)
                .orElse("ROLE_USER");

        return Jwts.builder()
                .setIssuer(jwtIssuer)
                .setSubject(userDetails.getUsername())
                .claim("role", role)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + jwtExpirationMs))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Parses the supplied compact JWS string and returns the {@code sub}
     * (subject) claim — the username originally encoded by
     * {@link #generateToken(UserDetails)}.
     *
     * <p>Any failure in parsing or signature verification (malformed token,
     * tampered signature, expired token, etc.) results in a
     * {@link JwtException} subtype being thrown to the caller. The JWT
     * authentication filter is expected to translate those into an HTTP
     * {@code 401 Unauthorized}.</p>
     *
     * @param token the compact JWS string to parse; must not be {@code null} or empty
     * @return the {@code sub} claim — the username that issued the token
     * @throws JwtException             if the token is malformed, tampered, expired, or
     *                                  otherwise fails verification
     * @throws IllegalArgumentException if {@code token} is {@code null} or empty
     */
    public String extractUsername(String token) {
        System.out.println("[JwtUtil] extractUsername invoked");

        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }

    /**
     * Reports whether the supplied token is structurally valid, cryptographically
     * verifiable with the configured signing key, not expired, AND issued to the
     * exact same username as the supplied principal.
     *
     * <p>This method is the single source of truth that the JWT authentication
     * filter consults before populating the {@code SecurityContextHolder}. It
     * never throws — every JJWT runtime exception ({@code JwtException} and its
     * subtypes, plus {@link IllegalArgumentException} for null/blank tokens) is
     * caught and translated into a {@code false} return value so the filter can
     * route the request through the {@code JwtAuthenticationEntryPoint} cleanly.</p>
     *
     * @param token       the compact JWS string under test (may be {@code null} or empty)
     * @param userDetails the principal the token is claimed to belong to;
     *                    must not be {@code null}
     * @return {@code true} only when the token parses successfully, its signature
     *         verifies, its subject equals the supplied username, and its
     *         {@code exp} claim is strictly in the future; {@code false} otherwise
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        System.out.println("[JwtUtil] isTokenValid invoked for subject=" + userDetails.getUsername());

        try {
            String username = extractUsername(token);
            Date expiration = Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody()
                    .getExpiration();
            return username.equals(userDetails.getUsername()) && expiration.after(new Date());
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    /**
     * Exposes the configured JWT lifetime in milliseconds. Consumed by
     * {@code AuthService} to populate the {@code expiresInMs} field of the
     * login response without forcing a second read of {@code application.properties}.
     *
     * @return the value of {@code app.jwt.expiration-ms} as a {@code long}
     */
    public long getExpirationMs() {
        System.out.println("[JwtUtil] getExpirationMs invoked");
        return this.jwtExpirationMs;
    }

    /**
     * Builds a fresh HMAC signing key from the Base64-encoded
     * {@link #jwtSecret}.
     *
     * <p>The key is rebuilt on every call rather than cached so the class has
     * no initialisation-order dependency on Spring's property injection. The
     * cost is negligible (a Base64 decode plus a {@code SecretKeySpec}
     * allocation) and the simplicity is worth it.</p>
     *
     * <p>{@link Keys#hmacShaKeyFor(byte[])} validates the key strength and
     * throws a {@code WeakKeyException} if the decoded byte array is shorter
     * than 32 bytes (256 bits). The configured {@code app.jwt.secret} value
     * decodes to at least 32 bytes, so this is safe at runtime.</p>
     *
     * @return a {@link Key} suitable for HS256 signing and verification
     */
    private Key getSigningKey() {
        System.out.println("[JwtUtil] getSigningKey invoked");
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
    }
}
