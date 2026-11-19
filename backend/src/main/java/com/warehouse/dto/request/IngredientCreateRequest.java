package com.warehouse.dto.request;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record IngredientCreateRequest(

    @NotBlank(message = "O nome do ingrediente é obrigatório.")
    @Size(max = 150, message = "O nome do ingrediente deve ter no máximo 150 caracteres.")
    String nome,

    @NotBlank(message = "O tipo do ingrediente é obrigatório.")
    String tipo,

    @DecimalMin(
        value = "0.001",
        message = "A quantidade deve ser maior que zero."
    )
    @Digits(integer = 9, fraction = 3, message = "A quantidade deve ter até 9 dígitos inteiros e 3 casas decimais.")
    BigDecimal quantidade,

    @Positive(message = "O ID do compartimento deve ser positivo.")
    Long compartimentoId

) {}
