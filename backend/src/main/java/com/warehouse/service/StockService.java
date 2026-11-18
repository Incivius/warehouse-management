package com.warehouse.service;

import com.warehouse.entity.AppUser;
import com.warehouse.entity.Compartment;
import com.warehouse.entity.CompartmentStock;
import com.warehouse.entity.Ingredient;
import com.warehouse.entity.StockMovement;
import com.warehouse.enums.CompartmentStatus;
import com.warehouse.enums.MovementType;
import com.warehouse.exception.BusinessException;
import com.warehouse.exception.ErrorCode;
import com.warehouse.repository.AppUserRepository;
import com.warehouse.repository.CompartmentStockRepository;
import com.warehouse.repository.StockMovementRepository;
import com.warehouse.security.CustomUserDetails;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class StockService {

    private final CompartmentService compartmentService;
    private final CompartmentStockRepository compartmentStockRepository;
    private final StockMovementRepository stockMovementRepository;
    private final AppUserRepository appUserRepository;

    public StockService(
            CompartmentService compartmentService,
            CompartmentStockRepository compartmentStockRepository,
            StockMovementRepository stockMovementRepository,
            AppUserRepository appUserRepository
    ) {
        this.compartmentService = compartmentService;
        this.compartmentStockRepository = compartmentStockRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.appUserRepository = appUserRepository;
    }

    @Transactional
    public StockMovement addInitialStock(
            Ingredient ingredient,
            Long compartmentId,
            BigDecimal quantity
    ) {
        validateQuantity(quantity);
        Compartment compartment = compartmentService.validateAndLock(
                compartmentId,
                ingredient.getIngredientType(),
                quantity
        );

        AppUser user = getAuthenticatedUser();

        updateCompartmentStock(compartment, ingredient, quantity);

        compartment.setStatus(CompartmentStatus.OCCUPIED);
        compartment.setCurrentType(ingredient.getIngredientType());

        return saveMovement(compartment, ingredient, user, MovementType.INBOUND, quantity);
    }

    @Transactional
    public StockMovement removeStock(Long compartmentId, Long ingredientId, BigDecimal quantity) {
        validateQuantity(quantity);
        Compartment compartment = compartmentService.lockById(compartmentId);
        AppUser user = getAuthenticatedUser();
        CompartmentStock stock = compartmentStockRepository
                .findByCompartmentIdAndIngredientId(compartmentId, ingredientId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INSUFFICIENT_STOCK));

        BigDecimal remaining = stock.getQuantity().subtract(quantity);
        if (remaining.signum() < 0) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK);
        }

        Ingredient ingredient = stock.getIngredient();
        StockMovement movement = saveMovement(compartment, ingredient, user, MovementType.OUTBOUND, quantity);

        if (remaining.signum() > 0) {
            stock.setQuantity(remaining);
        } else {
            compartmentStockRepository.delete(stock);
            if (!compartmentStockRepository.existsByCompartmentId(compartmentId)) {
                compartment.setStatus(CompartmentStatus.EMPTY);
                compartment.setLastMovementAt(movement.getCreatedAt());
            }
        }

        return movement;
    }

    private void validateQuantity(BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0 || quantity.scale() > 3
                || quantity.precision() - (long) quantity.scale() > 9) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }

    private StockMovement saveMovement(
            Compartment compartment,
            Ingredient ingredient,
            AppUser user,
            MovementType movementType,
            BigDecimal quantity
    ) {
        StockMovement movement = new StockMovement();
        movement.setUser(user);
        movement.setCompartment(compartment);
        movement.setIngredient(ingredient);
        movement.setMovementType(movementType);
        movement.setQuantity(quantity);

        return stockMovementRepository.save(movement);
    }

    private void updateCompartmentStock(
            Compartment compartment,
            Ingredient ingredient,
            BigDecimal quantity
    ) {
        CompartmentStock stock = compartmentStockRepository
                .findByCompartmentIdAndIngredientId(
                        compartment.getId(),
                        ingredient.getId()
                )
                .orElseGet(() -> {
                    CompartmentStock newStock = new CompartmentStock();
                    newStock.setCompartment(compartment);
                    newStock.setIngredient(ingredient);
                    newStock.setQuantity(BigDecimal.ZERO);
                    return newStock;
                });

        stock.setQuantity(stock.getQuantity().add(quantity));

        compartmentStockRepository.save(stock);
    }

    private AppUser getAuthenticatedUser() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !(authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        return appUserRepository.getReferenceById(userDetails.getId());
    }
}
