package com.gaur.friendstogether.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * =============================================================================
 * AdminCoinOrderResponse
 * =============================================================================
 *
 * Admin-facing response DTO.
 *
 * Contains ALL information including:
 *   - Internal database ID
 *   - User details
 *   - Idempotency key
 *   - Admin remarks
 *   - Full timestamps
 *
 * Used by:
 *   GET   /api/admin/orders
 *   GET   /api/admin/orders/{orderNumber}
 *   GET   /api/admin/orders/user/{userId}
 *   PATCH /api/admin/orders/{orderNumber}/cancel
 *   PATCH /api/admin/orders/{orderNumber}/expire
 *   PATCH /api/admin/orders/{orderNumber}/fail
 * =============================================================================
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminCoinOrderResponse {

    // =========================================================================
    // INTERNAL IDENTIFIERS
    // =========================================================================

    /**
     * Internal database ID.
     * Only visible to admin.
     */
    private Long id;

    /**
     * Business order number.
     */
    private String orderNumber;

    // =========================================================================
    // USER INFORMATION
    // =========================================================================

    /**
     * User's internal database ID.
     */
    private Long userId;

    /**
     * User's email for identification.
     */
    private String userEmail;

    /**
     * User's username.
     */
    private String username;

    // =========================================================================
    // PLAN SNAPSHOT
    // =========================================================================

    /**
     * Plan database ID.
     */
    private Long planId;

    private String planCode;

    private String planName;

    private Integer baseCoins;

    private Integer bonusCoins;

    private Integer totalCoins;

    private BigDecimal orderAmount;

    private String currency;

    // =========================================================================
    // ORDER STATE
    // =========================================================================

    private String status;

    /**
     * Admin remarks.
     * Example: "Cancelled per support ticket #1234"
     */
    private String remarks;

    /**
     * Idempotency key used for this order.
     * Fetched from IdempotencyRecord table.
     */
    private String idempotencyKey;

    // =========================================================================
    // TIMESTAMPS
    // =========================================================================

    private LocalDateTime expiresAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
