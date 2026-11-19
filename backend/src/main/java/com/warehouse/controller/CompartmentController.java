package com.warehouse.controller;

import com.warehouse.dto.response.CompartmentAvailabilityResponse;
import com.warehouse.service.CompartmentService;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/compartimentos")
public class CompartmentController {

    private final CompartmentService compartmentService;

    public CompartmentController(CompartmentService compartmentService) {
        this.compartmentService = compartmentService;
    }

    @GetMapping("/disponiveis")
    public List<CompartmentAvailabilityResponse> findAvailable(
            @RequestParam @NotBlank String tipo,
            @RequestParam @DecimalMin("0.001") @Digits(integer = 9, fraction = 3) BigDecimal quantidade
    ) {
        return compartmentService.findAvailable(tipo, quantidade);
    }

    @GetMapping("/disponiveis-para-venda")
    public List<CompartmentAvailabilityResponse> findAvailableForSale(@RequestParam @NotBlank String tipo) {
        return compartmentService.findAvailableForSale(tipo);
    }
}
