package com.orange.logistics.auth.service;

import io.jsonwebtoken.Claims;
import java.util.Map;

public interface JwtService {
    String generateAccessToken(Long userId, String username, Map<String, Object> claims);
    String generateRefreshToken(Long userId, String username);
    Claims parseToken(String token);
    boolean validateToken(String token);
    Long getUserIdFromToken(String token);
    String getUsernameFromToken(String token);
}
