
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
    public Registration create(IngredientCreateRequest request) {
        if (request.quantidade() == null && request.compartimentoId() != null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "Envie quantidade junto com compartimentoId para registrar uma entrada, "
                            + "ou omita compartimentoId para cadastrar sem estoque.");
        }

        IngredientType ingredientType = ingredientTypeService.findByName(request.tipo());
        // A consulta precisa ocorrer depois que o bloqueio de identidade for adquirido.
        ingredientRepository.lockIdentity(ingredientType.getId(), request.nome());
        var existing = ingredientRepository.findByIdentity(ingredientType.getId(), request.nome());
        boolean created = existing.isEmpty();
        if (created && request.quantidade() != null && request.compartimentoId() == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "Para cadastrar um ingrediente novo com estoque inicial, envie compartimentoId junto com quantidade. "
                            + "Para cadastrar sem estoque, omita quantidade.");
        }

        Ingredient ingredient = existing.orElseGet(() -> {
            Ingredient newIngredient = new Ingredient();
            newIngredient.setName(request.nome());
            newIngredient.setIngredientType(ingredientType);
            return ingredientRepository.save(newIngredient);
        });

        if (request.quantidade() == null) {
            return new Registration(new IngredientCreateResponse(
                    ingredient.getId(), ingredient.getName(), ingredientType.getName(),
                    null, null, null
            ), created);
        }

        StockMovement movement = stockService.addStock(ingredient, request.compartimentoId(), request.quantidade());
        return new Registration(new IngredientCreateResponse(
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
        ), created);
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

    public record Registration(IngredientCreateResponse response, boolean created) {}
}
