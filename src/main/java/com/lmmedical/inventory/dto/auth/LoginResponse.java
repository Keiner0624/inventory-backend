package com.lmmedical.inventory.dto.auth;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn
) {
    @Override public String toString() { return "LoginResponse[tokenType=" + tokenType + ", expiresIn=" + expiresIn + ", accessToken=<oculto>]"; }
}
