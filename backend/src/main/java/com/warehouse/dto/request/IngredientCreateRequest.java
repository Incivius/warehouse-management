package com.warehouse.dto.request;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record IngredientCreateRequest(

    @NotBlank(message = "O nome do ingrediente é obrigatório.")
    String nome,

    @NotBlank(message = "O tipo do ingrediente é obrigatório.")
    String tipo,

    @DecimalMin(
        value = "0.001",
        message = "A quantidade deve ser maior que zero."
    )
    BigDecimal quantidade,

    @Positive(message = "O ID do compartimento deve ser positivo.")
    Long compartimentoId

) {}