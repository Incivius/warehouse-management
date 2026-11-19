
package com.warehouse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testes de criptografia de senha")
class PasswordEncoderTest {

    private final BCryptPasswordEncoder passwordEncoder =
            new BCryptPasswordEncoder();

    @Test
    @DisplayName("Deve gerar hash BCrypt e validar a senha original")
    void shouldEncodeAndValidatePassword() {
        // Arrange
        String password = UUID.randomUUID().toString();

        // Act
        String encodedPassword = passwordEncoder.encode(password);

        // Assert
        assertNotNull(encodedPassword);
        assertNotEquals(password, encodedPassword);
        assertTrue(passwordEncoder.matches(password, encodedPassword));
    }

    @Test
    @DisplayName("Não deve validar uma senha incorreta")
    void shouldRejectInvalidPassword() {
        // Arrange
        String password = UUID.randomUUID().toString();
        String encodedPassword = passwordEncoder.encode(password);

        // Act
        boolean result = passwordEncoder.matches(
                "senha-incorreta",
                encodedPassword
        );

        // Assert
        assertFalse(result);
    }

    @Test
    @DisplayName("Deve gerar hashes BCrypt para diferentes senhas")
    void shouldGenerateHashesForDifferentPasswords() {
        for (int index = 0; index < 3; index++) {
            String password = UUID.randomUUID().toString();
            String hash = passwordEncoder.encode(password);

            assertNotNull(hash);
            assertTrue(passwordEncoder.matches(password, hash));
        }
    }
}
