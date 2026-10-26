package com.lmmedical.inventory.dto.auth;

public record AuthTokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn
) {
    @Override public String toString() { return "AuthTokenResponse[tokenType=" + tokenType + ", expiresIn=" + expiresIn + ", tokens=<ocultos>]"; }
}
