
package com.warehouse.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    // Ingredientes
    INGREDIENT_NOT_FOUND(
            "Ingrediente não encontrado.",
            HttpStatus.NOT_FOUND
    ),

    INGREDIENT_ALREADY_EXISTS(
            "Ingrediente já cadastrado.",
            HttpStatus.CONFLICT
    ),

    // Compartimentos
    COMPARTMENT_NOT_FOUND(
            "Compartimento não encontrado.",
            HttpStatus.NOT_FOUND
    ),

    INSUFFICIENT_CAPACITY(
            "Capacidade insuficiente no compartimento.",
            HttpStatus.BAD_REQUEST
    ),

    INSUFFICIENT_STOCK(
            "Quantidade em estoque insuficiente.",
            HttpStatus.BAD_REQUEST
    ),

    INCOMPATIBLE_INGREDIENT_TYPE(
            "Tipo de ingrediente incompatível com o compartimento.",
            HttpStatus.BAD_REQUEST
    ),

    COMPARTMENT_TEMPORARILY_BLOCKED(
            "Compartimento temporariamente bloqueado para troca de tipo.",
            HttpStatus.BAD_REQUEST
    ),

    // Autenticação
    INVALID_CREDENTIALS(
            "Login ou senha inválidos.",
            HttpStatus.UNAUTHORIZED
    ),

    UNAUTHORIZED(
            "Autenticação necessária para acessar este recurso.",
            HttpStatus.UNAUTHORIZED
    ),

    // Requisição
    INVALID_REQUEST(
            "Os dados enviados são inválidos.",
            HttpStatus.BAD_REQUEST
    ),

    // Sistema
    INTERNAL_ERROR(
            "Ocorreu um erro interno inesperado.",
            HttpStatus.INTERNAL_SERVER_ERROR
    );

    private final String message;
    private final HttpStatus status;

    ErrorCode(String message, HttpStatus status) {
        this.message = message;
        this.status = status;
    }

    public String getCode() {
        return name();
    }

    public String getMessage() {
        return message;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
