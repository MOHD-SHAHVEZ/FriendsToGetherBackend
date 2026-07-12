package com.gaur.friendstogether.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * =============================================================================
 * IdempotencyRecord
 * =============================================================================
 *
 * Dedicated idempotency table — universal deduplication layer.
 *
 * DESIGN INSPIRATION:
 * Production payment microservice idempotency pattern.
 *
 * KEY DESIGN DECISIONS:
 *
 * 1. method + path (instead of ResourceType enum)
 *    ─────────────────────────────────────────────
 *    Instead of maintaining an enum like ORDER_CREATION, PAYMENT_INITIATION,
 *    we store the actual HTTP method + path.
 *
 *    Example:
 *      POST /api/orders              → order creation
 *      POST /api/payments/initiate   → payment initiation
 *
 *    Benefits:
 *      ✔ No need to add new enum values for every new endpoint
 *      ✔ Any endpoint automatically gets idempotency support
 *      ✔ Self-documenting — you can see exactly which API was called
 *
 * 2. Direct orderId + paymentId (instead of generic resourceType + resourceId)
 *    ─────────────────────────────────────────────────────────────────────────
 *    Explicit nullable FK fields make queries cleaner:
 *      findByOrderId(42) → direct lookup
 *    Instead of:
 *      findByResourceTypeAndResourceId(ORDER, 42) → indirect
 *
 * 3. Composite Unique Key: (user_id, idempotency_key, http_method, request_path)
 *    ──────────────────────────────────────────────────────────────────────────────
 *    Same idempotency key can safely exist for different endpoints.
 *    Example: key "uuid-abc" on POST /api/orders is DIFFERENT from
 *             key "uuid-abc" on POST /api/payments/initiate.
 *
 * ──────────────────────────────────────────────────────────────────────────────
 * FLOW: Order Creation
 * ──────────────────────────────────────────────────────────────────────────────
 *
 * 1st Click:
 *   key="uuid-abc", method=POST, path=/api/orders
 *     → No record found
 *     → Create CoinOrder
 *     → Create IdempotencyRecord (orderId=42)
 *     → Return response
 *
 * 2nd Click (duplicate):
 *   key="uuid-abc", method=POST, path=/api/orders
 *     → Record found! Hash check passes.
 *     → Return cached response
 *     → No new order created
 *
 * ──────────────────────────────────────────────────────────────────────────────
 * FLOW: Payment Initiation (Future)
 * ──────────────────────────────────────────────────────────────────────────────
 *
 * 1st Click:
 *   key="pay-uuid", method=POST, path=/api/payments/initiate
 *     → No record found
 *     → Create Payment + Call Razorpay
 *     → Create IdempotencyRecord (paymentId=99, checkoutSessionId, checkoutSessionUrl)
 *     → Return checkout session
 *
 * Network failure / Retry:
 *   key="pay-uuid", method=POST, path=/api/payments/initiate
 *     → Record found with checkoutSessionId + checkoutSessionUrl!
 *     → Return SAME checkout session
 *     → No duplicate Razorpay order, no duplicate charge
 *
 * =============================================================================
 */
@Entity
@Table(
        name = "idempotency_records",
        uniqueConstraints = {

                /**
                 * Composite unique: same key can exist on different endpoints.
                 *
                 * key="uuid-abc" on POST /api/orders       → Record #1
                 * key="uuid-abc" on POST /api/payments      → Record #2 (different!)
                 *
                 * This is the ultimate DB-level safety net.
                 */
                @UniqueConstraint(
                        name = "uk_idempotency_key_method_path",
                        columnNames = {"user_id", "idempotency_key", "http_method", "request_path"}
                )
        },
        indexes = {

                /**
                 * Fast lookup by key alone.
                 */
                @Index(
                        name = "idx_idempotency_key",
                        columnList = "idempotency_key"
                ),

                /**
                 * Find all records for a specific user.
                 */
                @Index(
                        name = "idx_idempotency_user",
                        columnList = "user_id"
                ),

                /**
                 * Reverse lookup: find idempotency record by order.
                 */
                @Index(
                        name = "idx_idempotency_order",
                        columnList = "order_id"
                ),

                /**
                 * Reverse lookup: find idempotency record by payment.
                 */
                @Index(
                        name = "idx_idempotency_payment",
                        columnList = "payment_id"
                ),

                /**
                 * Scheduler: find expired records for cleanup.
                 */
                @Index(
                        name = "idx_idempotency_status_expires",
                        columnList = "status, expires_at"
                ),

                /**
                 * Find record by checkout session ID.
                 * Used by webhook processing.
                 */
                @Index(
                        name = "idx_idempotency_checkout",
                        columnList = "checkout_session_id"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdempotencyRecord {

    /**
     * Internal Database ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // =========================================================================
    // IDEMPOTENCY KEY + ENDPOINT IDENTIFICATION
    // =========================================================================

    /**
     * The idempotency key sent by the frontend.
     *
     * Format: UUID v4 generated by the client.
     * Example: "f47ac10b-58cc-4372-a567-0e02b2c3d479"
     *
     * Combined with method + path to form a composite unique key.
     */
    @Column(
            name = "idempotency_key",
            nullable = false,
            updatable = false,
            length = 100
    )
    private String idempotencyKey;

    /**
     * HTTP method of the original request.
     *
     * Example: "POST", "PUT", "PATCH"
     *
     * Allows the same idempotency key to be used
     * on different endpoints without collision.
     */
    @Column(
            name = "http_method",
            nullable = false,
            updatable = false,
            length = 10
    )
    private String httpMethod;

    /**
     * API path of the original request.
     *
     * Example:
     *   "/api/orders"
     *   "/api/payments/initiate"
     *
     * Self-documenting: you can see exactly which
     * API was called just by looking at this field.
     */
    @Column(
            name = "request_path",
            nullable = false,
            updatable = false,
            length = 255
    )
    private String requestPath;

    // =========================================================================
    // USER
    // =========================================================================

    /**
     * Which user owns this idempotency record.
     *
     * Part of the composite unique constraint.
     * Prevents cross-user key collisions.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // =========================================================================
    // LINKED RESOURCES (Direct FK instead of generic resourceType+resourceId)
    // =========================================================================

    /**
     * The CoinOrder created by this idempotent operation.
     *
     * Populated when method=POST, path=/api/orders
     *
     * Direct FK makes reverse lookup simple:
     *   findByOrder_Id(42) → clean and fast
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private CoinOrder order;

    /**
     * The Payment ID created by this idempotent operation.
     *
     * Populated when method=POST, path=/api/payments/initiate
     *
     * Stored as Long (not FK) because Payment module
     * is not yet built. Will be linked later.
     *
     * Null for order creation requests.
     */
    @Column(name = "payment_id")
    private Long paymentId;

    // =========================================================================
    // CHECKOUT SESSION (for Payment deduplication)
    // =========================================================================

    /**
     * Gateway checkout session ID.
     *
     * Null for order creation requests.
     * Populated when payment gateway creates a session.
     *
     * Example (Razorpay): "order_QA84DBITDS1ENp"
     * Example (Stripe):   "cs_test_a1b2c3..."
     *
     * On retry → return this same session → no duplicate gateway order.
     */
    @Column(name = "checkout_session_id", length = 200)
    private String checkoutSessionId;

    /**
     * Complete checkout session URL.
     *
     * For hosted checkout pages (Razorpay Hosted, Stripe Checkout),
     * the full URL is stored here.
     *
     * On duplicate payment request → return this URL directly.
     * Frontend opens the same checkout page — no new session created.
     *
     * Example (Razorpay): "https://api.razorpay.com/v1/checkout/..."
     * Example (Stripe):   "https://checkout.stripe.com/c/pay/cs_test_..."
     */
    @Column(name = "checkout_session_url", length = 1000)
    private String checkoutSessionUrl;

    // =========================================================================
    // REQUEST INTEGRITY
    // =========================================================================

    /**
     * SHA-256 hash of the original request body.
     *
     * On duplicate request, we compare hashes.
     * If different (same key, different body) → reject with 422.
     *
     * Prevents key reuse with different parameters.
     */
    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    // =========================================================================
    // RESPONSE CACHE
    // =========================================================================

    /**
     * Current lifecycle status.
     *
     * IN_PROGRESS → Resource created, flow in progress
     * COMPLETED   → Full flow succeeded
     * FAILED      → Operation failed
     * CANCELLED   → User or admin cancelled
     * EXPIRED     → Key timed out
     */
    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false)
    private IdempotencyStatus status = IdempotencyStatus.IN_PROGRESS;

    /**
     * HTTP status code of the original response.
     *
     * Stored so that duplicate requests return the
     * exact same HTTP status.
     *
     * Example: 201 (Created), 200 (OK)
     */
    @Column(name = "response_status_code")
    private Integer responseStatusCode;

    /**
     * JSON snapshot of the original API response body.
     *
     * On duplicate request, we deserialize and return
     * this directly — no service layer hit needed.
     *
     * Same input → Same output, always.
     */
    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "response_body")
    private String responseBody;

    // =========================================================================
    // LIFECYCLE
    // =========================================================================

    /**
     * When this idempotency key expires.
     *
     * Default: 24 hours from creation.
     * After expiry, scheduler marks it EXPIRED.
     */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /**
     * Record Creation Time
     */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Last Update Time
     */
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {

        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {

        updatedAt = LocalDateTime.now();
    }
}
