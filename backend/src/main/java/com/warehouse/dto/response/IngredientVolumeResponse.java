package com.warehouse.dto.response;

import java.math.BigDecimal;

public record IngredientVolumeResponse(
    String tipo,
    BigDecimal quantidadeTotal,
    String unidadeMedida
) {}