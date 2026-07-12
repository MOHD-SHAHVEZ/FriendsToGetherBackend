package com.gaur.friendstogether.controller;

import com.gaur.friendstogether.dto.request.AdminUpdateOrderStatusRequest;
import com.gaur.friendstogether.dto.response.AdminCoinOrderResponse;
import com.gaur.friendstogether.dto.response.ApiResponse;
import com.gaur.friendstogether.entity.CoinOrderStatus;
import com.gaur.friendstogether.service.CoinOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * =============================================================================
 * AdminCoinOrderController
 * =============================================================================
 *
 * Admin-only REST endpoints for Coin Order management.
 *
 * All endpoints require ADMIN role.
 *
 * ENDPOINTS:
 * ---------------------------------------------------------------------------
 * GET   /api/admin/orders                         → All orders (paginated)
 * GET   /api/admin/orders/{orderNumber}           → Any order detail
 * GET   /api/admin/orders/user/{userId}           → Orders of specific user
 * GET   /api/admin/orders/status/{status}         → Orders by status
 * PATCH /api/admin/orders/{orderNumber}/cancel    → Cancel order
 * PATCH /api/admin/orders/{orderNumber}/expire    → Force-expire order
 * PATCH /api/admin/orders/{orderNumber}/fail      → Mark order failed
 *
 * ──────────────────────────────────────────────────────────────────────────────
 * DESIGN DECISIONS
 * ──────────────────────────────────────────────────────────────────────────────
 *
 * No DELETE endpoint:
 *   Financial records must NEVER be physically deleted.
 *   Admin can only change status (cancel/expire/fail).
 *   This is regulatory and audit best practice.
 *
 * PATCH (not PUT) for status changes:
 *   We're partially updating the resource (only status + remarks).
 *   PUT implies full resource replacement — PATCH is more accurate.
 *
 * =============================================================================
 */
@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminCoinOrderController {

    private static final Logger logger = LoggerFactory.getLogger(AdminCoinOrderController.class);

    private final CoinOrderService coinOrderService;

    // =========================================================================
    // GET /api/admin/orders — All Orders (Paginated)
    // =========================================================================

    /**
     * Paginated list of ALL orders across all users.
     *
     * Query Params:
     *   page      (default: 0)
     *   size      (default: 20, max: 100)
     *   sortBy    (default: createdAt)
     *   direction (default: DESC)
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<AdminCoinOrderResponse>>> getAllOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String direction) {

        if (size > 100) {
            size = 100;
        }

        logger.info("[ORDER-ADMIN-API] GET /api/admin/orders → page={}, size={}", page, size);

        Sort.Direction sortDirection = "ASC".equalsIgnoreCase(direction)
                ? Sort.Direction.ASC : Sort.Direction.DESC;

        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortBy));
        Page<AdminCoinOrderResponse> orders = coinOrderService.findAllOrders(pageable);

        ApiResponse<Page<AdminCoinOrderResponse>> apiResponse =
                ApiResponse.<Page<AdminCoinOrderResponse>>builder()
                        .success(true)
                        .message("All orders fetched successfully")
                        .data(orders)
                        .build();

        return ResponseEntity.ok(apiResponse);
    }

    // =========================================================================
    // GET /api/admin/orders/{orderNumber} — Order Detail
    // =========================================================================

    /**
     * Get full detail of any order (including internal fields).
     */
    @GetMapping("/{orderNumber}")
    public ResponseEntity<ApiResponse<AdminCoinOrderResponse>> getOrderDetail(
            @PathVariable String orderNumber) {

        logger.info("[ORDER-ADMIN-API] GET /api/admin/orders/{}", orderNumber);

        AdminCoinOrderResponse order = coinOrderService.findOrderByOrderNumber(orderNumber);

        ApiResponse<AdminCoinOrderResponse> apiResponse = ApiResponse.<AdminCoinOrderResponse>builder()
                .success(true)
                .message("Order detail fetched successfully")
                .data(order)
                .build();

        return ResponseEntity.ok(apiResponse);
    }

    // =========================================================================
    // GET /api/admin/orders/user/{userId} — Orders by User
    // =========================================================================

    /**
     * Get all orders of a specific user (paginated).
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<Page<AdminCoinOrderResponse>>> getOrdersByUser(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (size > 100) {
            size = 100;
        }

        logger.info("[ORDER-ADMIN-API] GET /api/admin/orders/user/{} → page={}", userId, page);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AdminCoinOrderResponse> orders = coinOrderService.findOrdersByUserId(userId, pageable);

        ApiResponse<Page<AdminCoinOrderResponse>> apiResponse =
                ApiResponse.<Page<AdminCoinOrderResponse>>builder()
                        .success(true)
                        .message("User orders fetched successfully")
                        .data(orders)
                        .build();

        return ResponseEntity.ok(apiResponse);
    }

    // =========================================================================
    // GET /api/admin/orders/status/{status} — Orders by Status
    // =========================================================================

    /**
     * Get orders filtered by status (paginated).
     */
    @GetMapping("/status/{status}")
    public ResponseEntity<ApiResponse<Page<AdminCoinOrderResponse>>> getOrdersByStatus(
            @PathVariable CoinOrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (size > 100) {
            size = 100;
        }

        logger.info("[ORDER-ADMIN-API] GET /api/admin/orders/status/{} → page={}", status, page);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AdminCoinOrderResponse> orders = coinOrderService.findOrdersByStatus(status, pageable);

        ApiResponse<Page<AdminCoinOrderResponse>> apiResponse =
                ApiResponse.<Page<AdminCoinOrderResponse>>builder()
                        .success(true)
                        .message("Orders by status fetched successfully")
                        .data(orders)
                        .build();

        return ResponseEntity.ok(apiResponse);
    }

    // =========================================================================
    // PATCH /api/admin/orders/{orderNumber}/cancel — Admin Cancel
    // =========================================================================

    /**
     * Admin cancels an order.
     *
     * Allowed for: CREATED, PAYMENT_PENDING
     * Not allowed for terminal statuses.
     */
    @PatchMapping("/{orderNumber}/cancel")
    public ResponseEntity<ApiResponse<AdminCoinOrderResponse>> cancelOrder(
            @PathVariable String orderNumber,
            @Valid @RequestBody(required = false) AdminUpdateOrderStatusRequest request) {

        logger.info("[ORDER-ADMIN-API] PATCH /api/admin/orders/{}/cancel", orderNumber);

        String remarks = (request != null) ? request.getRemarks() : null;
        AdminCoinOrderResponse cancelled = coinOrderService.adminCancelOrder(orderNumber, remarks);

        ApiResponse<AdminCoinOrderResponse> apiResponse = ApiResponse.<AdminCoinOrderResponse>builder()
                .success(true)
                .message("Order cancelled successfully")
                .data(cancelled)
                .build();

        return ResponseEntity.ok(apiResponse);
    }

    // =========================================================================
    // PATCH /api/admin/orders/{orderNumber}/expire — Admin Expire
    // =========================================================================

    /**
     * Admin force-expires an order.
     *
     * Allowed for: CREATED, PAYMENT_PENDING
     */
    @PatchMapping("/{orderNumber}/expire")
    public ResponseEntity<ApiResponse<AdminCoinOrderResponse>> expireOrder(
            @PathVariable String orderNumber,
            @Valid @RequestBody(required = false) AdminUpdateOrderStatusRequest request) {

        logger.info("[ORDER-ADMIN-API] PATCH /api/admin/orders/{}/expire", orderNumber);

        String remarks = (request != null) ? request.getRemarks() : null;
        AdminCoinOrderResponse expired = coinOrderService.adminExpireOrder(orderNumber, remarks);

        ApiResponse<AdminCoinOrderResponse> apiResponse = ApiResponse.<AdminCoinOrderResponse>builder()
                .success(true)
                .message("Order expired successfully")
                .data(expired)
                .build();

        return ResponseEntity.ok(apiResponse);
    }

    // =========================================================================
    // PATCH /api/admin/orders/{orderNumber}/fail — Admin Mark Failed
    // =========================================================================

    /**
     * Admin marks an order as failed.
     *
     * Allowed for: CREATED, PAYMENT_PENDING
     */
    @PatchMapping("/{orderNumber}/fail")
    public ResponseEntity<ApiResponse<AdminCoinOrderResponse>> failOrder(
            @PathVariable String orderNumber,
            @Valid @RequestBody(required = false) AdminUpdateOrderStatusRequest request) {

        logger.info("[ORDER-ADMIN-API] PATCH /api/admin/orders/{}/fail", orderNumber);

        String remarks = (request != null) ? request.getRemarks() : null;
        AdminCoinOrderResponse failed = coinOrderService.adminFailOrder(orderNumber, remarks);

        ApiResponse<AdminCoinOrderResponse> apiResponse = ApiResponse.<AdminCoinOrderResponse>builder()
                .success(true)
                .message("Order marked as failed successfully")
                .data(failed)
                .build();

        return ResponseEntity.ok(apiResponse);
    }
}
