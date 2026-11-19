package com.warehouse.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record IngredientCreateResponse(

    Long id,
    String nome,
    String tipo,
    BigDecimal quantidade,
    CompartmentInfo compartimento,
    MovementInfo movimentacao

) {

    public record CompartmentInfo(
        Long id,
        String codigo
    ) {}

    public record MovementInfo(
        Long id,
        String tipo,
        BigDecimal quantidade,
        String responsavel,
        LocalDateTime dataHora
    ) {}
}