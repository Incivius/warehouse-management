
package com.warehouse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testes de criptografia de senha")
class PasswordEncoderTest {

    private final BCryptPasswordEncoder passwordEncoder =
            new BCryptPasswordEncoder();

    @Test
    @DisplayName("Deve gerar hash BCrypt e validar a senha original")
    void shouldEncodeAndValidatePassword() {
        // Arrange
        String password = "admin123";

        // Act
        String encodedPassword = passwordEncoder.encode(password);

        // Assert
        assertNotNull(encodedPassword);
        assertNotEquals(password, encodedPassword);
        assertTrue(passwordEncoder.matches(password, encodedPassword));

        System.out.println("Hash BCrypt gerado: " + encodedPassword);
    }

    @Test
    @DisplayName("Não deve validar uma senha incorreta")
    void shouldRejectInvalidPassword() {
        // Arrange
        String password = "admin123";
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
    @DisplayName("Deve gerar hashes BCrypt para os usuários iniciais")
    void shouldGenerateInitialUserPasswordHashes() {
        // Arrange
        String[][] users = {
                {"admin", "admin123"},
                {"operador1", "op123456"},
                {"operador2", "op654321"}
        };

        // Act & Assert
        for (String[] user : users) {
            String login = user[0];
            String password = user[1];

            String hash = passwordEncoder.encode(password);

            assertNotNull(hash);
            assertTrue(passwordEncoder.matches(password, hash));

            System.out.println("Login: " + login);
            System.out.println("Hash: " + hash);
            System.out.println("----------------------");
        }
    }
}
