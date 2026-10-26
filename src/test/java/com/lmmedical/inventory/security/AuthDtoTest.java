package com.lmmedical.inventory.security;

import com.lmmedical.inventory.dto.auth.*;
import com.lmmedical.inventory.dto.usuario.CrearUsuarioRequest;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class AuthDtoTest {
    @Test void toStringNoMuestraContrasenasNiTokens() {
        String secret = UUID.randomUUID().toString();
        String access = UUID.randomUUID().toString();
        String refresh = UUID.randomUUID().toString();
        assertFalse(new LoginRequest("persona@example.test", secret).toString().contains(secret));
        assertFalse(new CrearUsuarioRequest("Ana", "Pérez", "persona@example.test", secret).toString().contains(secret));
        assertFalse(new LoginResponse(access, "Bearer", 900).toString().contains(access));
        assertFalse(new RefreshTokenRequest(refresh).toString().contains(refresh));
        var response = new AuthTokenResponse(access, refresh, "Bearer", 900);
        assertFalse(response.toString().contains(access));
        assertFalse(response.toString().contains(refresh));
    }

    @Test void laRespuestaJsonUsaTokenTypeYConservaLosTokensParaElCliente() {
        var json = JsonMapper.builder().build();
        String access = UUID.randomUUID().toString();
        String refresh = UUID.randomUUID().toString();
        var login = json.valueToTree(new LoginResponse(access, "Bearer", 900));
        assertEquals("Bearer", login.get("tokenType").asText());
        assertFalse(login.has("tokenTypen"));
        var session = json.valueToTree(new AuthTokenResponse(access, refresh, "Bearer", 900));
        assertEquals(access, session.get("accessToken").asText());
        assertEquals(refresh, session.get("refreshToken").asText());
    }
}
