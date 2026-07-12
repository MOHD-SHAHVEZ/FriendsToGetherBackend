package com.gaur.friendstogether.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * =============================================================================
 * CreateCoinOrderRequest
 * =============================================================================
 *
 * Request body for POST /api/orders (Buy Now).
 *
 * Frontend sends:
 * {
 *   "planId": 5,
 *   "idempotencyKey": "f47ac10b-58cc-4372-a567-0e02b2c3d479"
 * }
 *
 * ──────────────────────────────────────────────────────────────────────────────
 * WHY idempotencyKey IS MANDATORY
 * ──────────────────────────────────────────────────────────────────────────────
 *
 * If frontend sends Buy → Buy → Buy (triple click),
 * all three requests will carry the same idempotencyKey.
 *
 * Backend checks IdempotencyRecord table:
 *   1st request → no record → create order → store key
 *   2nd request → record found → return cached response
 *   3rd request → record found → return cached response
 *
 * Result: Only ONE order is created.
 *
 * ──────────────────────────────────────────────────────────────────────────────
 * FRONTEND IMPLEMENTATION
 * ──────────────────────────────────────────────────────────────────────────────
 *
 * Generate UUID once when user opens the "Buy" screen:
 *
 * const idempotencyKey = crypto.randomUUID();
 *
 * Send this key with EVERY click of the "Buy Now" button.
 * Generate a NEW key only when user navigates to a
 * different plan or refreshes the page.
 * =============================================================================
 */
@Data
public class CreateCoinOrderRequest {

    /**
     * Which coin plan the user wants to purchase.
     *
     * Must be a valid, ACTIVE CoinPlan ID.
     */
    @NotNull(message = "Plan ID is required")
    private Long planId;

    /**
     * Frontend-generated UUID v4 for duplicate prevention.
     *
     * Must be unique per purchase intent.
     * Reuse the same key for retries of the same purchase.
     * Generate a new key for a new purchase intent.
     */
    @NotBlank(message = "Idempotency key is required")
    @Size(max = 100, message = "Idempotency key must not exceed 100 characters")
    private String idempotencyKey;
}
