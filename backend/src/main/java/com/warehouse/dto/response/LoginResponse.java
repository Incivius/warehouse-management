
package com.warehouse.dto.response;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        UserInfo usuario
) {

    public record UserInfo(
            Long id,
            String nome,
            String login
    ) {}
}
