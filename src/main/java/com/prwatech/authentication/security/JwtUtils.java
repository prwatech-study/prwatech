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
  private static final String ORGANIZATION_ID_CLAIM = "oid";
  private static final String ORG_ROLE_CLAIM = "or";
  private static final String ORG_SLUG_CLAIM = "oslug";

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
    return generateSkillamaToken(usersDetails, tokenVersion, null, null, null);
  }

  public Map<String, String> generateSkillamaToken(
          UserDetails usersDetails,
          Integer tokenVersion,
          String organizationId,
          String orgRole,
          String orgSlug) {
    Map<String, Object> claims = new HashMap<>();
    if (tokenVersion != null) {
      claims.put(TOKEN_VERSION_CLAIM, tokenVersion);
    }
    if (organizationId != null) {
      claims.put(ORGANIZATION_ID_CLAIM, organizationId);
    }
    if (orgRole != null) {
      claims.put(ORG_ROLE_CLAIM, orgRole);
    }
    if (orgSlug != null) {
      claims.put(ORG_SLUG_CLAIM, orgSlug);
    }
    return createToken(claims, usersDetails.getUsername());
  }

  public String extractOrganizationId(String token) {
    return extractClaim(token, claims -> claims.get(ORGANIZATION_ID_CLAIM, String.class));
  }

  public String extractOrgRole(String token) {
    return extractClaim(token, claims -> claims.get(ORG_ROLE_CLAIM, String.class));
  }

  public String extractOrgSlug(String token) {
    return extractClaim(token, claims -> claims.get(ORG_SLUG_CLAIM, String.class));
  }

  /** @deprecated use {@link #generateSkillamaToken} */
  @Deprecated
  private Map<String, String> generateTokenLegacy(UserDetails usersDetails, Integer tokenVersion) {
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
