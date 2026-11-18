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
        Compartment compartment = compartmentService.validateAndLock(
                compartmentId,
                ingredient.getIngredientType(),
                quantity
        );

        AppUser user = getAuthenticatedUser();

        updateCompartmentStock(compartment, ingredient, quantity);

        compartment.setStatus(CompartmentStatus.OCCUPIED);
        compartment.setCurrentType(ingredient.getIngredientType());

        StockMovement movement = new StockMovement();
        movement.setUser(user);
        movement.setCompartment(compartment);
        movement.setIngredient(ingredient);
        movement.setMovementType(MovementType.INBOUND);
        movement.setQuantity(quantity);

        StockMovement savedMovement = stockMovementRepository.save(movement);
        compartment.setLastMovementAt(savedMovement.getCreatedAt());
        return savedMovement;
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
