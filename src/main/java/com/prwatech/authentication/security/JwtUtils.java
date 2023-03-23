package com.prwatech.authentication.security;

import com.prwatech.common.dto.UserDetails;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class JwtUtils {


    private static long serialVersionId=-2550185165626007488L;
    public static final long JWT_TOKEN_VALIDITY = 10000 * 60 * 60 * 10L;
    public static final long JWT_TOKEN_REFRESH_VALIDITY = 10000 * 60 * 60 * 15L;
    private String SECRET_KEY = "secret";
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }
    private Claims extractAllClaims(String token) {
        return Jwts.parser().setSigningKey(SECRET_KEY).parseClaimsJws(token).getBody();
    }

    private Boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    public Map<String, String> generateToken(UserDetails usersDetails) {
        Map<String, Object> claims = new HashMap<>();
        return createToken(claims, usersDetails.getUsername());
    }

    private Map<String, String> createToken(Map<String, Object> claims, String subject) {

        String activeToken= Jwts.builder().setClaims(claims).setSubject(subject).setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + JWT_TOKEN_VALIDITY))
                .signWith(SignatureAlgorithm.HS256, SECRET_KEY).compact();

        String refreshToken=Jwts.builder().setClaims(claims).setSubject(subject).setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + JWT_TOKEN_REFRESH_VALIDITY))
                .signWith(SignatureAlgorithm.HS256, SECRET_KEY).compact();

        Map<String, String> token=new HashMap<>();
        token.put("activeToken", activeToken);
        token.put("refreshToken",refreshToken);
        return token;
    }

    public Boolean validateToken(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }
}
