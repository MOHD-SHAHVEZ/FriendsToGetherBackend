package com.gaur.friendstogether.dto.request;

import com.gaur.friendstogether.entity.CoinPlanBadge;
import com.gaur.friendstogether.entity.CoinPlanStatus;
import com.gaur.friendstogether.entity.CurrencyType;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * UpdateCoinPlanRequest
 *
 * Admin existing Coin Plan update karne ke liye ye request body bhejega.
 */
@Data
public class UpdateCoinPlanRequest {

    /** Unique business code */
    @NotBlank(message = "Plan code is required")
    @Size(max = 50, message = "Code max 50 characters")
    private String code;

    /** Display name */
    @NotBlank(message = "Plan name is required")
    private String name;

    /** Short description */
    @Size(max = 500, message = "Description max 500 characters")
    private String description;

    /** Image/icon URL */
    private String imageUrl;

    /** Base coins */
    @NotNull(message = "Coins is required")
    @Min(value = 1, message = "Coins must be at least 1")
    private Integer coins;

    /** Bonus coins */
    @Min(value = 0, message = "Bonus coins cannot be negative")
    private Integer bonusCoins = 0;

    /** Price */
    @NotNull(message = "Price is required")
    @DecimalMin(value = "1.00", message = "Price must be at least 1.00")
    private BigDecimal price;

    /** Currency */
    private CurrencyType currency = CurrencyType.INR;

    /** Badge */
    private CoinPlanBadge badge = CoinPlanBadge.NONE;

    /** Status */
    private CoinPlanStatus status = CoinPlanStatus.ACTIVE;

    /** Display order */
    @NotNull(message = "Display order is required")
    private Integer displayOrder;

    /** Offer start time */
    private LocalDateTime offerStartAt;

    /** Offer end time */
    private LocalDateTime offerEndAt;

    /** Max purchase limit */
    private Integer purchaseLimit;
}
