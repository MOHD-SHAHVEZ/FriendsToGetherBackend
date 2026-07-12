package com.gaur.friendstogether.dto.response;

import com.gaur.friendstogether.entity.CoinPlanBadge;
import com.gaur.friendstogether.entity.CoinPlanStatus;
import com.gaur.friendstogether.entity.CurrencyType;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * CoinPlanResponse
 *
 * Clean response DTO — Admin aur User dono ke liye.
 * Internal/sensitive fields expose nahi hote.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoinPlanResponse {

    private Long id;
    private String code;
    private String name;
    private String description;
    private String imageUrl;

    /** Base coins */
    private Integer coins;

    /** Bonus coins */
    private Integer bonusCoins;

    /** Total coins (base + bonus) — calculated field */
    private Integer totalCoins;

    private BigDecimal price;
    private CurrencyType currency;
    private CoinPlanBadge badge;
    private CoinPlanStatus status;
    private Integer displayOrder;
    private LocalDateTime offerStartAt;
    private LocalDateTime offerEndAt;
    private Integer purchaseLimit;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
