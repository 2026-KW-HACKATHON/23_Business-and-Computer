package com.gakkum.backend.util;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JWTUtil {

    private static final int MIN_SECRET_KEY_LENGTH = 32;

    private final SecretKey secretKey;
    private final Long ACCESS_TOKEN_EXPIRES_IN;
    private final Long REFRESH_TOKEN_EXPIRES_IN;

    public JWTUtil(
            @Value("${jwt.secret}") String secretKeyString,
            @Value("${jwt.access-token-expiration}") Long ACCESS_TOKEN_EXPIRES_IN,
            @Value("${jwt.refresh-token-expiration}") Long REFRESH_TOKEN_EXPIRES_IN
            ) {
        byte[] secretKeyBytes = secretKeyString.getBytes(StandardCharsets.UTF_8);
        this.ACCESS_TOKEN_EXPIRES_IN = ACCESS_TOKEN_EXPIRES_IN;
        this.REFRESH_TOKEN_EXPIRES_IN = REFRESH_TOKEN_EXPIRES_IN;
        if (secretKeyBytes.length < MIN_SECRET_KEY_LENGTH) {
            throw new IllegalArgumentException("jwt.secret must be at least 32 bytes");
        }
        this.secretKey = new SecretKeySpec(secretKeyBytes, Jwts.SIG.HS256.key().build().getAlgorithm());
    }


    // JWT 클레임 username 파싱
    public String getUsername(String token) {
        return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload().get("sub", String.class);
    }

    // JWT 클레임 role 파싱
    public String getRole(String token) {
        return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload().get("role", String.class);
    }


    // JWT 유효 여부 (위조, 시간, Access/Refresh 여부)
    public Boolean isValid(String token, Boolean isAccess) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String type = claims.get("type", String.class);
            if (type == null) return false;

            if (isAccess && !type.equals("access")) return false;
            if (!isAccess && !type.equals("refresh")) return false;

            return true;

        } catch (
                JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    // JWT(Access/Refresh) 생성
    public String createJWT(String username, String role, Boolean isAccess) {

        long now = System.currentTimeMillis();
        long expiry = isAccess ? ACCESS_TOKEN_EXPIRES_IN : REFRESH_TOKEN_EXPIRES_IN;
        String type = isAccess ? "access" : "refresh";

        return Jwts.builder()
                .claim("sub", username)
                .claim("role", role)
                .claim("type", type)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expiry))
                .signWith(secretKey)
                .compact();
    }
}

