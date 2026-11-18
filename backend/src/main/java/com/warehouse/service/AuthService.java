
package com.warehouse.service;

import com.warehouse.dto.request.LoginRequest;
import com.warehouse.dto.response.LoginResponse;
import com.warehouse.security.CustomUserDetails;
import com.warehouse.security.JwtService;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(
            AuthenticationManager authenticationManager,
            JwtService jwtService
    ) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    public LoginResponse login(LoginRequest request) {

        try {
            Authentication authentication =
                    authenticationManager.authenticate(
                            new UsernamePasswordAuthenticationToken(
                                    request.login(),
                                    request.senha()
                            )
                    );

            CustomUserDetails userDetails =
                    (CustomUserDetails) authentication.getPrincipal();

            String token = jwtService.generateToken(userDetails);

            return new LoginResponse(
                    token,
                    "Bearer",
                    3600,
                    new LoginResponse.UserInfo(
                            userDetails.getId(),
                            userDetails.getName(),
                            userDetails.getUsername()
                    )
            );

        } catch (AuthenticationException exception) {
            throw new com.warehouse.exception.BusinessException(
                    com.warehouse.exception.ErrorCode.INVALID_CREDENTIALS
            );
        }
    }
}
