package com.warehouse.dto.response;

import com.warehouse.enums.UnitOfMeasure;

import java.math.BigDecimal;

public record CompartmentAvailabilityResponse(
        Long id,
        String codigo,
        BigDecimal quantidadeDisponivel,
        UnitOfMeasure unidadeMedida
) {}
