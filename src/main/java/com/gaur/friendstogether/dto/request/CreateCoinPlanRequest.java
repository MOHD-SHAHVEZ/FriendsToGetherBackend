package com.gaur.friendstogether.dto.request;

import com.gaur.friendstogether.entity.CoinPlanBadge;
import com.gaur.friendstogether.entity.CoinPlanStatus;
import com.gaur.friendstogether.entity.CurrencyType;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * CreateCoinPlanRequest
 *
 * Admin new Coin Plan banane ke liye ye request body bhejega.
 */
@Data
public class CreateCoinPlanRequest {

    /** Unique business code (e.g. STARTER_99) */
    @NotBlank(message = "Plan code is required")
    @Size(max = 50, message = "Code max 50 characters")
    private String code;

    /** Display name (e.g. Starter Pack) */
    @NotBlank(message = "Plan name is required")
    private String name;

    /** Short description */
    @Size(max = 500, message = "Description max 500 characters")
    private String description;

    /** Image/icon URL */
    private String imageUrl;

    /** Base coins user ko milenge */
    @NotNull(message = "Coins is required")
    @Min(value = 1, message = "Coins must be at least 1")
    private Integer coins;

    /** Bonus promotional coins (default 0) */
    @Min(value = 0, message = "Bonus coins cannot be negative")
    private Integer bonusCoins = 0;

    /** Price (e.g. 99.00) */
    @NotNull(message = "Price is required")
    @DecimalMin(value = "1.00", message = "Price must be at least 1.00")
    private BigDecimal price;

    /** Currency (default INR) */
    private CurrencyType currency = CurrencyType.INR;

    /** Badge (e.g. POPULAR, HOT) */
    private CoinPlanBadge badge = CoinPlanBadge.NONE;

    /** Status (default ACTIVE) */
    private CoinPlanStatus status = CoinPlanStatus.ACTIVE;

    /** Display order (small = pehle dikhega) */
    @NotNull(message = "Display order is required")
    private Integer displayOrder;

    /** Offer start time (null = always available) */
    private LocalDateTime offerStartAt;

    /** Offer end time (null = no expiry) */
    private LocalDateTime offerEndAt;

    /** Max purchase limit (null = unlimited) */
    private Integer purchaseLimit;
}
