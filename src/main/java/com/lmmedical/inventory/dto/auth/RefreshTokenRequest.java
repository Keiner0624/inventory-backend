package com.lmmedical.inventory.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record RefreshTokenRequest(@NotBlank String refreshToken) {
    @Override public String toString() { return "RefreshTokenRequest[refreshToken=<oculto>]"; }
}
