package com.prwatech.authentication.security;

import com.prwatech.common.dto.UserDetails;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@NoArgsConstructor
public class JwtUtils {

  private static long serialVersionId = -2550185165626007488L;
  public static final long JWT_TOKEN_VALIDITY = 10000 * 180 * 180 * 10L;
  public static final long JWT_TOKEN_REFRESH_VALIDITY = 10000 * 60 * 60 * 15L;
  private static final String TOKEN_VERSION_CLAIM = "tv";

  @Value("${jwt.secret.key}")
  private String secretKey;

  public String extractUsername(String token) {
    return extractClaim(token, Claims::getSubject);
  }

  public Date extractExpiration(String token) {
    return extractClaim(token, Claims::getExpiration);
  }

  /** Missing claim (tokens minted before session-versioning existed) is treated as version 0. */
  public int extractTokenVersion(String token) {
    Integer version = extractClaim(token, claims -> claims.get(TOKEN_VERSION_CLAIM, Integer.class));
    return version != null ? version : 0;
  }

  public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
    final Claims claims = extractAllClaims(token);
    return claimsResolver.apply(claims);
  }

  private Claims extractAllClaims(String token) {
    return Jwts.parser().setSigningKey(secretKey).parseClaimsJws(token).getBody();
  }

  private Boolean isTokenExpired(String token) {
    return extractExpiration(token).before(new Date());
  }

  public Map<String, String> generateToken(UserDetails usersDetails) {
    return generateToken(usersDetails, null);
  }

  /**
   * @param tokenVersion embedded as the {@code tv} claim so a later logout (which bumps the
   *     user's stored token version) invalidates this token; null omits the claim (legacy callers
   *     outside skillama that don't participate in session invalidation).
   */
  public Map<String, String> generateToken(UserDetails usersDetails, Integer tokenVersion) {
    Map<String, Object> claims = new HashMap<>();
    if (tokenVersion != null) {
      claims.put(TOKEN_VERSION_CLAIM, tokenVersion);
    }
    return createToken(claims, usersDetails.getUsername());
  }

  private Map<String, String> createToken(Map<String, Object> claims, String subject) {

    String activeToken =
        Jwts.builder()
            .setClaims(claims)
            .setSubject(subject)
            .setIssuedAt(new Date(System.currentTimeMillis()))
            .setExpiration(new Date(System.currentTimeMillis() + JWT_TOKEN_VALIDITY))
            .signWith(SignatureAlgorithm.HS256, secretKey)
            .compact();

    String refreshToken =
        Jwts.builder()
            .setClaims(claims)
            .setSubject(subject)
            .setIssuedAt(new Date(System.currentTimeMillis()))
            .setExpiration(new Date(System.currentTimeMillis() + JWT_TOKEN_REFRESH_VALIDITY))
            .signWith(SignatureAlgorithm.HS256, secretKey)
            .compact();

    Map<String, String> token = new HashMap<>();
    token.put("accessToken", activeToken);
    token.put("refreshToken", refreshToken);
    return token;
  }

  public Boolean validateToken(String token, UserDetails userDetails) {
    final String username = extractUsername(token);
    return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
  }
}
