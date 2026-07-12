package com.gaur.friendstogether.controller;

import com.gaur.friendstogether.dto.request.CreateCoinOrderRequest;
import com.gaur.friendstogether.dto.response.ApiResponse;
import com.gaur.friendstogether.dto.response.CoinOrderResponse;
import com.gaur.friendstogether.entity.User;
import com.gaur.friendstogether.exception.ResourceNotFoundException;
import com.gaur.friendstogether.repository.UserRepository;
import com.gaur.friendstogether.service.CoinOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * =============================================================================
 * CoinOrderController
 * =============================================================================
 *
 * User-facing REST endpoints for Coin Order management.
 *
 * All endpoints require authenticated user (JWT).
 *
 * ENDPOINTS:
 * ---------------------------------------------------------------------------
 * POST /api/orders                          → Create new order (Buy Now)
 * GET  /api/orders/my-orders                → My orders (paginated)
 * GET  /api/orders/my-orders/{orderNumber}  → My specific order
 * POST /api/orders/{orderNumber}/cancel     → Cancel my CREATED order
 *
 * ──────────────────────────────────────────────────────────────────────────────
 * SECURITY
 * ──────────────────────────────────────────────────────────────────────────────
 *
 * - All endpoints require JWT authentication
 * - Ownership is validated for every request
 * - User can ONLY see and manage their OWN orders
 * - If order belongs to different user → 404 (not 403, to avoid leaking info)
 *
 * ──────────────────────────────────────────────────────────────────────────────
 * IDEMPOTENCY
 * ──────────────────────────────────────────────────────────────────────────────
 *
 * POST /api/orders is idempotent:
 *   Same idempotencyKey + Same planId → Returns same order
 *   Same idempotencyKey + Different planId → Returns 422
 *   Different idempotencyKey → Creates new order (if no pending exists)
 *
 * =============================================================================
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class CoinOrderController {

    private static final Logger logger = LoggerFactory.getLogger(CoinOrderController.class);

    private final CoinOrderService coinOrderService;
    private final UserRepository userRepository;

    // =========================================================================
    // POST /api/orders — Create New Order (Buy Now)
    // =========================================================================

    /**
     * Create a new Coin Order.
     *
     * Frontend sends:
     * {
     *   "planId": 5,
     *   "idempotencyKey": "f47ac10b-58cc-4372-a567-0e02b2c3d479"
     * }
     *
     * Response:
     * 201 Created → new order created
     * 200 OK      → existing order returned (duplicate/pending)
     * 400          → plan not active
     * 404          → plan not found
     * 422          → idempotency key reused with different params
     */
    @PostMapping
    public ResponseEntity<ApiResponse<CoinOrderResponse>> createOrder(
            Authentication authentication,
            @Valid @RequestBody CreateCoinOrderRequest request) {

        Long userId = extractUserId(authentication);

        logger.info("[ORDER-API] POST /api/orders → userId={}, planId={}, key={}",
                userId, request.getPlanId(), request.getIdempotencyKey());

        CoinOrderResponse response = coinOrderService.createOrder(userId, request);

        ApiResponse<CoinOrderResponse> apiResponse = ApiResponse.<CoinOrderResponse>builder()
                .success(true)
                .message("Order processed successfully")
                .data(response)
                .build();

        return new ResponseEntity<>(apiResponse, HttpStatus.CREATED);
    }

    // =========================================================================
    // GET /api/orders/my-orders — My Orders (Paginated)
    // =========================================================================

    /**
     * Paginated list of user's orders.
     * Most recent first.
     *
     * Query Params:
     *   page (default: 0)
     *   size (default: 20, max: 50)
     */
    @GetMapping("/my-orders")
    public ResponseEntity<ApiResponse<Page<CoinOrderResponse>>> getMyOrders(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (size > 50) {
            size = 50;
        }

        Long userId = extractUserId(authentication);

        logger.info("[ORDER-API] GET /api/orders/my-orders → userId={}, page={}, size={}",
                userId, page, size);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<CoinOrderResponse> orders = coinOrderService.findUserOrders(userId, pageable);

        ApiResponse<Page<CoinOrderResponse>> apiResponse = ApiResponse.<Page<CoinOrderResponse>>builder()
                .success(true)
                .message("Orders fetched successfully")
                .data(orders)
                .build();

        return ResponseEntity.ok(apiResponse);
    }

    // =========================================================================
    // GET /api/orders/my-orders/{orderNumber} — My Specific Order
    // =========================================================================

    /**
     * Get a specific order by order number.
     * Validates that the order belongs to the authenticated user.
     */
    @GetMapping("/my-orders/{orderNumber}")
    public ResponseEntity<ApiResponse<CoinOrderResponse>> getMyOrderDetail(
            Authentication authentication,
            @PathVariable String orderNumber) {

        Long userId = extractUserId(authentication);

        logger.info("[ORDER-API] GET /api/orders/my-orders/{} → userId={}", orderNumber, userId);

        CoinOrderResponse order = coinOrderService.findUserOrderByOrderNumber(userId, orderNumber);

        ApiResponse<CoinOrderResponse> apiResponse = ApiResponse.<CoinOrderResponse>builder()
                .success(true)
                .message("Order detail fetched successfully")
                .data(order)
                .build();

        return ResponseEntity.ok(apiResponse);
    }

    // =========================================================================
    // POST /api/orders/{orderNumber}/cancel — Cancel My Order
    // =========================================================================

    /**
     * Cancel a CREATED order.
     *
     * Only allowed when status = CREATED.
     * Once PAYMENT_PENDING, user must contact admin.
     */
    @PostMapping("/{orderNumber}/cancel")
    public ResponseEntity<ApiResponse<CoinOrderResponse>> cancelOrder(
            Authentication authentication,
            @PathVariable String orderNumber) {

        Long userId = extractUserId(authentication);

        logger.info("[ORDER-API] POST /api/orders/{}/cancel → userId={}", orderNumber, userId);

        CoinOrderResponse cancelled = coinOrderService.cancelOrder(userId, orderNumber);

        ApiResponse<CoinOrderResponse> apiResponse = ApiResponse.<CoinOrderResponse>builder()
                .success(true)
                .message("Order cancelled successfully")
                .data(cancelled)
                .build();

        return ResponseEntity.ok(apiResponse);
    }

    // =========================================================================
    // HELPER
    // =========================================================================

    /**
     * Extract user database ID from JWT Authentication.
     *
     * Flow:
     *   Authentication.getName() → user email (from JWT)
     *   UserRepository.findByEmail() → User entity
     *   User.getId() → database ID (Long)
     */
    private Long extractUserId(Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResourceNotFoundException("User not authenticated");
        }

        String email = authentication.getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found: " + email));

        return user.getId();
    }
}
