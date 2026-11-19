package com.warehouse.controller;

import com.warehouse.dto.response.StockMovementResponse;
import com.warehouse.service.StockService;
import org.springframework.data.web.PagedModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/historico")
public class StockHistoryController {

    private final StockService stockService;

    public StockHistoryController(StockService stockService) {
        this.stockService = stockService;
    }

    @GetMapping
    public PagedModel<StockMovementResponse> findHistory(
            @RequestParam(defaultValue = "date") String sortBy,
            @RequestParam(defaultValue = "desc") String order,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return new PagedModel<>(stockService.findHistory(sortBy, order, page, size));
    }
}
