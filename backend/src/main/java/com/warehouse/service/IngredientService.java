
package com.warehouse.service;

import com.warehouse.dto.request.IngredientCreateRequest;
import com.warehouse.dto.response.IngredientCreateResponse;
import com.warehouse.dto.response.IngredientResponse;
import com.warehouse.dto.response.IngredientVolumeResponse;
import com.warehouse.entity.Ingredient;
import com.warehouse.entity.IngredientType;
import com.warehouse.entity.StockMovement;
import com.warehouse.exception.BusinessException;
import com.warehouse.exception.ErrorCode;
import com.warehouse.repository.CompartmentStockRepository;
import com.warehouse.repository.IngredientRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class IngredientService {

    private final IngredientRepository ingredientRepository;
    private final IngredientTypeService ingredientTypeService;
    private final StockService stockService;
    private final CompartmentStockRepository compartmentStockRepository;

    public IngredientService(
            IngredientRepository ingredientRepository,
            IngredientTypeService ingredientTypeService,
            StockService stockService,
            CompartmentStockRepository compartmentStockRepository
    ) {
        this.ingredientRepository = ingredientRepository;
        this.ingredientTypeService = ingredientTypeService;
        this.stockService = stockService;
        this.compartmentStockRepository = compartmentStockRepository;
    }

    @Transactional
    public IngredientCreateResponse create(IngredientCreateRequest request) {

        validateInitialStock(request);

        IngredientType ingredientType =
                ingredientTypeService.findByName(request.tipo());

        Ingredient ingredient = new Ingredient();
        ingredient.setName(request.nome());
        ingredient.setIngredientType(ingredientType);

        ingredient = ingredientRepository.save(ingredient);

        // Cadastro simples, sem estoque inicial.
        if (request.quantidade() == null) {
            return new IngredientCreateResponse(
                    ingredient.getId(),
                    ingredient.getName(),
                    ingredientType.getName(),
                    null,
                    null,
                    null
            );
        }

        // Cadastro acompanhado de entrada inicial no estoque.
        StockMovement movement = stockService.addInitialStock(
                ingredient,
                request.compartimentoId(),
                request.quantidade()
        );

        return new IngredientCreateResponse(
                ingredient.getId(),
                ingredient.getName(),
                ingredientType.getName(),
                movement.getQuantity(),
                new IngredientCreateResponse.CompartmentInfo(
                        movement.getCompartment().getId(),
                        movement.getCompartment().getCode()
                ),
                new IngredientCreateResponse.MovementInfo(
                        movement.getId(),
                        movement.getMovementType().name(),
                        movement.getQuantity(),
                        movement.getUser().getLogin(),
                        movement.getCreatedAt()
                )
        );
    }

    @Transactional(readOnly = true)
    public List<IngredientResponse> findAll() {
        return ingredientRepository.findAllResponses();
    }

    @Transactional(readOnly = true)
    public IngredientResponse findById(Long id) {
        return ingredientRepository.findResponseById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.INGREDIENT_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public List<IngredientVolumeResponse> findVolumeByType() {
        return compartmentStockRepository.sumQuantityByIngredientType();
    }

    private void validateInitialStock(IngredientCreateRequest request) {

        boolean hasQuantity = request.quantidade() != null;
        boolean hasCompartment = request.compartimentoId() != null;

        if (hasQuantity != hasCompartment) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }
}
