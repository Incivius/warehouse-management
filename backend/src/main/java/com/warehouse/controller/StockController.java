package com.warehouse.controller;

import com.warehouse.dto.request.StockMovementRequest;
import com.warehouse.dto.response.StockMovementResponse;
import com.warehouse.dto.response.StockSnapshotResponse;
import com.warehouse.service.StockService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/estoque")
public class StockController {

    private final StockService stockService;

    public StockController(StockService stockService) {
        this.stockService = stockService;
    }

    @GetMapping
    public StockSnapshotResponse findCurrentStock() {
        return stockService.findCurrentStock();
    }

    @PostMapping("/entrada")
    @ResponseStatus(HttpStatus.CREATED)
    public StockMovementResponse addStock(@Valid @RequestBody StockMovementRequest request) {
        return stockService.addStock(request);
    }

    @PostMapping("/saida")
    @ResponseStatus(HttpStatus.CREATED)
    public StockMovementResponse removeStock(@Valid @RequestBody StockMovementRequest request) {
        return stockService.removeStock(request);
    }
}
