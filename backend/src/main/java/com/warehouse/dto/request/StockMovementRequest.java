package com.warehouse.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record StockMovementRequest(
        @NotNull(message = "O ID do ingrediente é obrigatório.")
        @Positive(message = "O ID do ingrediente deve ser positivo.")
        Long ingredienteId,

        @NotNull(message = "O ID do compartimento é obrigatório.")
        @Positive(message = "O ID do compartimento deve ser positivo.")
        Long compartimentoId,

        @NotNull(message = "A quantidade é obrigatória.")
        @DecimalMin(value = "0.001", message = "A quantidade deve ser maior que zero.")
        @Digits(integer = 9, fraction = 3, message = "A quantidade deve ter até 9 dígitos inteiros e 3 casas decimais.")
        BigDecimal quantidade
) {}
