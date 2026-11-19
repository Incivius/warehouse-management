package com.warehouse.service;

import com.warehouse.dto.request.StockMovementRequest;
import com.warehouse.dto.response.StockMovementResponse;
import com.warehouse.dto.response.StockSnapshotResponse;
import com.warehouse.dto.response.StockSnapshotResponse.CompartmentDetails;
import com.warehouse.dto.response.StockSnapshotResponse.IngredientStock;
import com.warehouse.entity.AppUser;
import com.warehouse.entity.Compartment;
import com.warehouse.entity.CompartmentStock;
import com.warehouse.entity.Ingredient;
import com.warehouse.entity.IngredientType;
import com.warehouse.entity.StockMovement;
import com.warehouse.enums.CompartmentStatus;
import com.warehouse.enums.MovementType;
import com.warehouse.exception.BusinessException;
import com.warehouse.exception.ErrorCode;
import com.warehouse.repository.AppUserRepository;
import com.warehouse.repository.CompartmentRepository;
import com.warehouse.repository.CompartmentRepository.StockSnapshotRow;
import com.warehouse.repository.CompartmentStockRepository;
import com.warehouse.repository.IngredientRepository;
import com.warehouse.repository.StockMovementRepository;
import com.warehouse.security.CustomUserDetails;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class StockService {

    private final CompartmentService compartmentService;
    private final CompartmentRepository compartmentRepository;
    private final CompartmentStockRepository compartmentStockRepository;
    private final StockMovementRepository stockMovementRepository;
    private final AppUserRepository appUserRepository;
    private final IngredientRepository ingredientRepository;

    public StockService(
            CompartmentService compartmentService,
            CompartmentRepository compartmentRepository,
            CompartmentStockRepository compartmentStockRepository,
            StockMovementRepository stockMovementRepository,
            AppUserRepository appUserRepository,
            IngredientRepository ingredientRepository
    ) {
        this.compartmentService = compartmentService;
        this.compartmentRepository = compartmentRepository;
        this.compartmentStockRepository = compartmentStockRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.appUserRepository = appUserRepository;
        this.ingredientRepository = ingredientRepository;
    }

    @Transactional
    public StockMovementResponse addStock(StockMovementRequest request) {
        Ingredient ingredient = ingredientRepository.findById(request.ingredienteId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INGREDIENT_NOT_FOUND));
        return toResponse(addStock(ingredient, request.compartimentoId(), request.quantidade()));
    }

    @Transactional
    public StockMovementResponse removeStock(StockMovementRequest request) {
        return toResponse(removeStock(request.compartimentoId(), request.ingredienteId(), request.quantidade()));
    }

    @Transactional(readOnly = true)
    public StockSnapshotResponse findCurrentStock() {
        var rowsByCompartment = compartmentRepository.findStockSnapshot().stream()
                .collect(Collectors.groupingBy(StockSnapshotRow::getCompartmentId,
                        LinkedHashMap::new, Collectors.toList()));
        return new StockSnapshotResponse(rowsByCompartment.values().stream()
                .map(this::toCompartmentDetails)
                .toList());
    }

    private CompartmentDetails toCompartmentDetails(List<StockSnapshotRow> rows) {
        StockSnapshotRow compartment = rows.getFirst();
        List<IngredientStock> ingredients = rows.stream()
                .filter(row -> row.getStockId() != null)
                .map(row -> new IngredientStock(row.getStockId(), row.getIngredientId(),
                        row.getIngredientName(), row.getIngredientType(), row.getQuantity(), row.getUnit()))
                .toList();
        BigDecimal occupied = ingredients.stream().map(IngredientStock::quantidade)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal capacity = compartment.getCapacity();
        BigDecimal percentage = capacity == null || capacity.signum() == 0
                ? BigDecimal.ZERO : occupied.movePointRight(2).divide(capacity, 2, RoundingMode.HALF_UP);
        BigDecimal available = capacity == null ? null : capacity.subtract(occupied).max(BigDecimal.ZERO);
        return new CompartmentDetails(compartment.getCompartmentId(), compartment.getCompartmentCode(),
                compartment.getStatus(), compartment.getIngredientType(), capacity, compartment.getUnit(),
                occupied, available, percentage, compartment.getLastMovement(), ingredients);
    }

    @Transactional(readOnly = true)
    public Page<StockMovementResponse> findHistory(String sortBy, String order, int page, int size) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        Sort.Direction direction = switch (order) {
            case "asc" -> Sort.Direction.ASC;
            case "desc" -> Sort.Direction.DESC;
            default -> throw new BusinessException(ErrorCode.INVALID_REQUEST);
        };
        Sort sort = switch (sortBy) {
            case "date" -> Sort.by(new Sort.Order(direction, "createdAt"), Sort.Order.asc("compartment.code"));
            case "compartment" -> Sort.by(new Sort.Order(direction, "compartment.code"), Sort.Order.desc("createdAt"));
            default -> throw new BusinessException(ErrorCode.INVALID_REQUEST);
        };
        PageRequest pageRequest = PageRequest.of(page, size, sort.and(Sort.by("id")));
        return stockMovementRepository.findAll(pageRequest).map(this::toResponse);
    }

    @Transactional
    public StockMovement addStock(Ingredient ingredient, Long compartmentId, BigDecimal quantity) {
        validateQuantity(quantity);
        IngredientType type = ingredient.getIngredientType();
        Compartment compartment = compartmentId == null
                ? compartmentService.selectAndLock(type, quantity)
                : compartmentService.validateAndLock(compartmentId, type, quantity);

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
                .orElseThrow(() -> new BusinessException(ingredientRepository.existsById(ingredientId)
                        ? ErrorCode.INSUFFICIENT_STOCK
                        : ErrorCode.INGREDIENT_NOT_FOUND));

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

    private StockMovementResponse toResponse(StockMovement movement) {
        Ingredient ingredient = movement.getIngredient();
        Compartment compartment = movement.getCompartment();
        return new StockMovementResponse(
                movement.getId(),
                movement.getMovementType(),
                ingredient.getId(),
                ingredient.getName(),
                ingredient.getIngredientType().getName(),
                movement.getQuantity(),
                compartment.getId(),
                compartment.getCode(),
                movement.getUser().getLogin(),
                movement.getCreatedAt()
        );
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
