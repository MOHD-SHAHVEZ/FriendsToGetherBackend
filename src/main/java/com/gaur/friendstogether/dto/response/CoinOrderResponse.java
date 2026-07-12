package com.gaur.friendstogether.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * =============================================================================
 * CoinOrderResponse
 * =============================================================================
 *
 * User-facing response DTO.
 *
 * Contains ONLY information the user should see.
 * Does NOT expose:
 *   - Internal database ID
 *   - Idempotency key
 *   - Admin remarks
 *   - Other users' data
 *
 * Used by:
 *   POST /api/orders              → newly created order
 *   GET  /api/orders/my-orders    → order list items
 *   GET  /api/orders/my-orders/{} → single order detail
 * =============================================================================
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoinOrderResponse {

    /**
     * Business order number.
     * Example: "ORD-2026-17205123456789"
     */
    private String orderNumber;

    // =========================================================================
    // PLAN SNAPSHOT (captured at order time)
    // =========================================================================

    /**
     * Plan business code at time of purchase.
     * Example: "STARTER_99"
     */
    private String planCode;

    /**
     * Plan display name at time of purchase.
     * Example: "Starter Pack"
     */
    private String planName;

    /**
     * Base coins at time of purchase.
     */
    private Integer baseCoins;

    /**
     * Bonus coins at time of purchase.
     */
    private Integer bonusCoins;

    /**
     * Total coins (base + bonus) at time of purchase.
     */
    private Integer totalCoins;

    /**
     * Purchase amount at time of order.
     * Example: 99.00
     */
    private BigDecimal orderAmount;

    /**
     * Currency at time of order.
     * Example: "INR"
     */
    private String currency;

    // =========================================================================
    // ORDER STATE
    // =========================================================================

    /**
     * Current order status.
     * Example: "CREATED", "PAYMENT_PENDING", "PAYMENT_SUCCESS"
     */
    private String status;

    /**
     * When this order will auto-expire.
     * Null if already in a terminal status.
     */
    private LocalDateTime expiresAt;

    /**
     * When the order was placed.
     */
    private LocalDateTime createdAt;
}
