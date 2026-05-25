package com.jspider.spring_boot_simple_crud_with_mysql.service;

import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import jakarta.annotation.PostConstruct;

// Rule Applied
@Service
public class JwtService {

	@Value("${jwt.secret}")
	private String secret;

	@Value("${jwt.expiration-ms}")
	private long expirationMs;

	// Cached HMAC-SHA signing key derived from the externalized Base64-encoded
	// jwt.secret. Initialized once at startup via @PostConstruct so that the
	// Base64 decode and key construction do not run on every sign/parse call.
	private SecretKey signingKey;

	@PostConstruct
	void initSigningKey() {
		System.out.println("JwtService.initSigningKey: decoding configured jwt.secret and caching SecretKey");
		this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
	}

	public String generateToken(UserDetails userDetails) {
		System.out.println("JwtService.generateToken called for user: " + userDetails.getUsername());
		String role = userDetails.getAuthorities().stream()
				.findFirst()
				.map(GrantedAuthority::getAuthority)
				.orElse("");
		Date now = new Date();
		Date expiry = new Date(now.getTime() + expirationMs);
		// Explicitly sign with HS256 to satisfy AAP requirement #3. Without the
		// explicit algorithm, jjwt 0.13.0 auto-selects the strongest HMAC variant
		// supported by the key length (HS512 for a >=64-byte secret), which
		// would emit alg=HS512 instead of the required alg=HS256.
		return Jwts.builder()
				.subject(userDetails.getUsername())
				.claim("role", role)
				.issuedAt(now)
				.expiration(expiry)
				.signWith(getSigningKey(), Jwts.SIG.HS256)
				.compact();
	}

	public String extractUsername(String token) {
		System.out.println("JwtService.extractUsername called");
		return parseClaims(token).getSubject();
	}

	public boolean isTokenValid(String token, UserDetails userDetails) {
		System.out.println("JwtService.isTokenValid called for user: " + userDetails.getUsername());
		String username = extractUsername(token);
		return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
	}

	private boolean isTokenExpired(String token) {
		System.out.println("JwtService.isTokenExpired called");
		Date expiration = parseClaims(token).getExpiration();
		return expiration.before(new Date());
	}

	private Claims parseClaims(String token) {
		System.out.println("JwtService.parseClaims called");
		return Jwts.parser()
				.verifyWith(getSigningKey())
				.build()
				.parseSignedClaims(token)
				.getPayload();
	}

	private SecretKey getSigningKey() {
		System.out.println("JwtService.getSigningKey called");
		// Return the cached SecretKey populated by @PostConstruct so each
		// sign/parse path avoids re-running Base64 decode and HMAC key
		// construction on every authenticated request.
		return signingKey;
	}
}
