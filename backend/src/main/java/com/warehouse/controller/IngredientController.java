
package com.warehouse.controller;

import com.warehouse.dto.request.IngredientCreateRequest;
import com.warehouse.dto.response.IngredientCreateResponse;
import com.warehouse.dto.response.IngredientResponse;
import com.warehouse.dto.response.IngredientVolumeResponse;
import com.warehouse.service.IngredientService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/ingredientes")
public class IngredientController {

    private final IngredientService ingredientService;

    public IngredientController(IngredientService ingredientService) {
        this.ingredientService = ingredientService;
    }

    @PostMapping
    public ResponseEntity<IngredientCreateResponse> create(
            @Valid @RequestBody IngredientCreateRequest request
    ) {
        var registration = ingredientService.create(request);
        return ResponseEntity.status(registration.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(registration.response());
    }

    @GetMapping
    public List<IngredientResponse> findAll() {
        return ingredientService.findAll();
    }

    @GetMapping("/{id}")
    public IngredientResponse findById(@PathVariable Long id) {
        return ingredientService.findById(id);
    }

    @GetMapping("/volume")
    public List<IngredientVolumeResponse> findVolumeByType() {
        return ingredientService.findVolumeByType();
    }
}
