package com.gaur.friendstogether.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gaur.friendstogether.dto.request.CreateCoinOrderRequest;
import com.gaur.friendstogether.dto.response.AdminCoinOrderResponse;
import com.gaur.friendstogether.dto.response.CoinOrderResponse;
import com.gaur.friendstogether.entity.*;
import com.gaur.friendstogether.exception.BusinessLogicException;
import com.gaur.friendstogether.exception.DuplicateOrderException;
import com.gaur.friendstogether.exception.OrderNotCancellableException;
import com.gaur.friendstogether.exception.ResourceNotFoundException;
import com.gaur.friendstogether.repository.CoinOrderRepository;
import com.gaur.friendstogether.repository.CoinPlanRepository;
import com.gaur.friendstogether.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Year;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * =============================================================================
 * CoinOrderService
 * =============================================================================
 *
 * Complete business logic for Coin Order management.
 *
 * ──────────────────────────────────────────────────────────────────────────────
 * RESPONSIBILITY BOUNDARIES
 * ──────────────────────────────────────────────────────────────────────────────
 *
 * CoinOrderService:
 *   ✔ Creates and manages CoinOrder lifecycle
 *   ✔ Validates CoinPlan
 *   ✔ Enforces status transition rules
 *   ✔ Coordinates with IdempotencyService for deduplication
 *
 * CoinOrderService does NOT:
 *   ❌ Create Payment records (PaymentService, future)
 *   ❌ Call Razorpay API (PaymentService, future)
 *   ❌ Credit wallet (WalletService, called after payment)
 *   ❌ Send notifications (NotificationService, future)
 *
 * ──────────────────────────────────────────────────────────────────────────────
 * STATUS TRANSITION RULES
 * ──────────────────────────────────────────────────────────────────────────────
 *
 * CREATED → PAYMENT_PENDING    (PaymentService, future)
 * CREATED → CANCELLED          (user or admin)
 * CREATED → EXPIRED            (scheduler or admin)
 *
 * PAYMENT_PENDING → PAYMENT_SUCCESS  (PaymentService, future)
 * PAYMENT_PENDING → PAYMENT_FAILED   (PaymentService, future)
 * PAYMENT_PENDING → CANCELLED        (admin only)
 * PAYMENT_PENDING → EXPIRED          (scheduler or admin)
 *
 * Terminal: PAYMENT_SUCCESS, PAYMENT_FAILED, EXPIRED, CANCELLED
 *
 * =============================================================================
 */
@Service
@RequiredArgsConstructor
public class CoinOrderService {

    private static final Logger logger = LoggerFactory.getLogger(CoinOrderService.class);

    /**
     * Order expiry time in minutes.
     * After this duration, CREATED orders are auto-expired by the scheduler.
     */
    private static final long ORDER_EXPIRY_MINUTES = 30;

    /**
     * Terminal statuses — no further transitions allowed.
     */
    private static final Set<CoinOrderStatus> TERMINAL_STATUSES = Set.of(
            CoinOrderStatus.PAYMENT_SUCCESS,
            CoinOrderStatus.PAYMENT_FAILED,
            CoinOrderStatus.EXPIRED,
            CoinOrderStatus.CANCELLED
    );

    /**
     * Active statuses — indicate an order is in progress.
     * Used for duplicate order check.
     */
    private static final List<CoinOrderStatus> ACTIVE_STATUSES = List.of(
            CoinOrderStatus.CREATED,
            CoinOrderStatus.PAYMENT_PENDING
    );

    private final CoinOrderRepository coinOrderRepository;
    private final CoinPlanRepository coinPlanRepository;
    private final UserRepository userRepository;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // =========================================================================
    // CREATE ORDER
    // =========================================================================

    /**
     * Create a new Coin Order.
     *
     * Flow:
     * 1. Idempotency check (Layer 1 + 2)
     * 2. Validate plan
     * 3. Pending order check (Layer 3)
     * 4. Create order
     * 5. Store idempotency record
     * 6. Return response
     *
     * @param userId  The authenticated user's ID
     * @param request Contains planId and idempotencyKey
     * @return CoinOrderResponse with order details
     */
    @Transactional
    public CoinOrderResponse createOrder(Long userId, CreateCoinOrderRequest request) {

        logger.info("[ORDER] Creating order: userId={}, planId={}, idempotencyKey={}",
                userId, request.getPlanId(), request.getIdempotencyKey());

        // =================================================================
        // LAYER 1: Idempotency Key Check
        // =================================================================
        String httpMethod = "POST";
        String requestPath = "/api/orders";

        Optional<IdempotencyRecord> existingRecord = idempotencyService
                .checkAndReturn(request.getIdempotencyKey(), httpMethod, requestPath);

        if (existingRecord.isPresent()) {

            IdempotencyRecord record = existingRecord.get();

            // LAYER 2: Request Hash Validation
            String currentHash = idempotencyService.hashRequest(serializeRequest(request));

            if (record.getRequestHash() != null && !currentHash.equals(record.getRequestHash())) {
                logger.warn("[ORDER] Idempotency key reused with different params: key={}",
                        request.getIdempotencyKey());
                throw new DuplicateOrderException(
                        "Idempotency key already used with different request parameters");
            }

            // Return cached response
            logger.info("[ORDER] Duplicate request detected. Returning cached response: key={}",
                    request.getIdempotencyKey());

            return deserializeResponse(record.getResponseBody());
        }

        // =================================================================
        // VALIDATE PLAN
        // =================================================================
        CoinPlan plan = coinPlanRepository.findById(request.getPlanId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Coin Plan not found with id: " + request.getPlanId()));

        if (plan.getStatus() != CoinPlanStatus.ACTIVE) {
            throw new BusinessLogicException(
                    "Coin Plan '" + plan.getName() + "' is not active for purchase",
                    HttpStatus.BAD_REQUEST);
        }

        // =================================================================
        // LAYER 3: Pending Order Check
        // =================================================================
        Optional<CoinOrder> pendingOrder = coinOrderRepository
                .findByUser_IdAndPlan_IdAndStatusIn(userId, plan.getId(), ACTIVE_STATUSES);

        if (pendingOrder.isPresent()) {
            logger.info("[ORDER] Active order exists for userId={}, planId={}. Returning existing.",
                    userId, plan.getId());
            return mapToResponse(pendingOrder.get());
        }

        // =================================================================
        // GET USER REFERENCE
        // =================================================================
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with id: " + userId));

        // =================================================================
        // CREATE NEW ORDER
        // =================================================================
        CoinOrder order = CoinOrder.builder()
                .orderNumber(generateOrderNumber())
                .user(user)
                .plan(plan)
                .planCode(plan.getCode())
                .planName(plan.getName())
                .baseCoins(plan.getCoins())
                .bonusCoins(plan.getBonusCoins())
                .totalCoins(plan.getCoins() + plan.getBonusCoins())
                .orderAmount(plan.getPrice())
                .currency(plan.getCurrency())
                .status(CoinOrderStatus.CREATED)
                .expiresAt(LocalDateTime.now().plusMinutes(ORDER_EXPIRY_MINUTES))
                .build();

        CoinOrder savedOrder = coinOrderRepository.save(order);

        logger.info("[ORDER] Order created: orderNumber={}, userId={}, planCode={}, amount={}",
                savedOrder.getOrderNumber(), userId, plan.getCode(), plan.getPrice());

        // =================================================================
        // STORE IDEMPOTENCY RECORD
        // =================================================================
        CoinOrderResponse response = mapToResponse(savedOrder);
        String responseJson = serializeResponse(response);
        String requestHash = idempotencyService.hashRequest(serializeRequest(request));

        idempotencyService.createOrderRecord(
                request.getIdempotencyKey(),
                httpMethod,
                requestPath,
                savedOrder,
                user,
                requestHash,
                responseJson,
                201
        );

        return response;
    }

    // =========================================================================
    // USER: FIND ORDERS
    // =========================================================================

    /**
     * Get paginated list of user's orders.
     * Most recent first.
     */
    @Transactional(readOnly = true)
    public Page<CoinOrderResponse> findUserOrders(Long userId, Pageable pageable) {

        logger.info("[ORDER] Fetching orders for userId={}, page={}", userId, pageable.getPageNumber());

        return coinOrderRepository
                .findByUser_IdOrderByCreatedAtDesc(userId, pageable)
                .map(this::mapToResponse);
    }

    /**
     * Get specific order belonging to a user.
     * Validates ownership — if order belongs to different user, throws 404.
     */
    @Transactional(readOnly = true)
    public CoinOrderResponse findUserOrderByOrderNumber(Long userId, String orderNumber) {

        logger.info("[ORDER] Fetching order: userId={}, orderNumber={}", userId, orderNumber);

        CoinOrder order = coinOrderRepository.findByUser_IdAndOrderNumber(userId, orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Order not found: " + orderNumber));

        return mapToResponse(order);
    }

    // =========================================================================
    // USER: CANCEL ORDER
    // =========================================================================

    /**
     * User cancels their own order.
     *
     * Allowed only when status = CREATED (before payment starts).
     * Once PAYMENT_PENDING, user cannot cancel — must contact admin.
     */
    @Transactional
    public CoinOrderResponse cancelOrder(Long userId, String orderNumber) {

        logger.info("[ORDER] User cancel: userId={}, orderNumber={}", userId, orderNumber);

        CoinOrder order = coinOrderRepository.findByUser_IdAndOrderNumber(userId, orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Order not found: " + orderNumber));

        if (order.getStatus() != CoinOrderStatus.CREATED) {
            throw new OrderNotCancellableException(
                    "Order can only be cancelled when status is CREATED. Current status: "
                            + order.getStatus());
        }

        order.setStatus(CoinOrderStatus.CANCELLED);
        order.setRemarks("Cancelled by user");
        CoinOrder saved = coinOrderRepository.save(order);

        logger.info("[ORDER] Order cancelled by user: orderNumber={}", orderNumber);

        return mapToResponse(saved);
    }

    // =========================================================================
    // ADMIN: FIND ORDERS
    // =========================================================================

    /**
     * Admin: get paginated list of ALL orders.
     */
    @Transactional(readOnly = true)
    public Page<AdminCoinOrderResponse> findAllOrders(Pageable pageable) {

        logger.info("[ORDER-ADMIN] Fetching all orders, page={}", pageable.getPageNumber());

        return coinOrderRepository.findAll(pageable)
                .map(this::mapToAdminResponse);
    }

    /**
     * Admin: get orders filtered by status.
     */
    @Transactional(readOnly = true)
    public Page<AdminCoinOrderResponse> findOrdersByStatus(CoinOrderStatus status, Pageable pageable) {

        logger.info("[ORDER-ADMIN] Fetching orders by status={}, page={}", status, pageable.getPageNumber());

        return coinOrderRepository
                .findByStatusOrderByCreatedAtDesc(status, pageable)
                .map(this::mapToAdminResponse);
    }

    /**
     * Admin: get all orders of a specific user.
     */
    @Transactional(readOnly = true)
    public Page<AdminCoinOrderResponse> findOrdersByUserId(Long userId, Pageable pageable) {

        logger.info("[ORDER-ADMIN] Fetching orders for userId={}, page={}", userId, pageable.getPageNumber());

        return coinOrderRepository
                .findByUser_Id(userId, pageable)
                .map(this::mapToAdminResponse);
    }

    /**
     * Admin: get any order by order number.
     */
    @Transactional(readOnly = true)
    public AdminCoinOrderResponse findOrderByOrderNumber(String orderNumber) {

        logger.info("[ORDER-ADMIN] Fetching order: orderNumber={}", orderNumber);

        CoinOrder order = coinOrderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Order not found: " + orderNumber));

        return mapToAdminResponse(order);
    }

    // =========================================================================
    // ADMIN: STATUS CHANGE OPERATIONS
    // =========================================================================

    /**
     * Admin: cancel an order.
     *
     * Allowed for: CREATED, PAYMENT_PENDING
     */
    @Transactional
    public AdminCoinOrderResponse adminCancelOrder(String orderNumber, String remarks) {

        logger.info("[ORDER-ADMIN] Cancel order: orderNumber={}", orderNumber);

        CoinOrder order = getOrderForStatusChange(orderNumber);

        validateNotTerminal(order, "cancel");

        order.setStatus(CoinOrderStatus.CANCELLED);
        order.setRemarks(buildRemarks("Admin cancelled", remarks));
        CoinOrder saved = coinOrderRepository.save(order);

        logger.info("[ORDER-ADMIN] Order cancelled: orderNumber={}", orderNumber);

        return mapToAdminResponse(saved);
    }

    /**
     * Admin: force-expire an order.
     *
     * Allowed for: CREATED, PAYMENT_PENDING
     */
    @Transactional
    public AdminCoinOrderResponse adminExpireOrder(String orderNumber, String remarks) {

        logger.info("[ORDER-ADMIN] Expire order: orderNumber={}", orderNumber);

        CoinOrder order = getOrderForStatusChange(orderNumber);

        validateNotTerminal(order, "expire");

        order.setStatus(CoinOrderStatus.EXPIRED);
        order.setRemarks(buildRemarks("Admin expired", remarks));
        CoinOrder saved = coinOrderRepository.save(order);

        logger.info("[ORDER-ADMIN] Order expired: orderNumber={}", orderNumber);

        return mapToAdminResponse(saved);
    }

    /**
     * Admin: mark an order as failed.
     *
     * Allowed for: CREATED, PAYMENT_PENDING
     */
    @Transactional
    public AdminCoinOrderResponse adminFailOrder(String orderNumber, String remarks) {

        logger.info("[ORDER-ADMIN] Fail order: orderNumber={}", orderNumber);

        CoinOrder order = getOrderForStatusChange(orderNumber);

        validateNotTerminal(order, "mark as failed");

        order.setStatus(CoinOrderStatus.PAYMENT_FAILED);
        order.setRemarks(buildRemarks("Admin marked failed", remarks));
        CoinOrder saved = coinOrderRepository.save(order);

        logger.info("[ORDER-ADMIN] Order marked failed: orderNumber={}", orderNumber);

        return mapToAdminResponse(saved);
    }

    // =========================================================================
    // SCHEDULER: EXPIRE STALE ORDERS
    // =========================================================================

    /**
     * Find and expire all CREATED orders past their expiresAt time.
     *
     * Called by CoinOrderScheduler every 5 minutes.
     *
     * @return Number of orders expired
     */
    @Transactional
    public int expireStaleOrders() {

        logger.info("[ORDER-SCHEDULER] Checking for stale CREATED orders...");

        List<CoinOrder> staleOrders = coinOrderRepository
                .findExpiredOrders(CoinOrderStatus.CREATED, LocalDateTime.now());

        for (CoinOrder order : staleOrders) {
            order.setStatus(CoinOrderStatus.EXPIRED);
            order.setRemarks("Auto-expired by system scheduler");
        }

        if (!staleOrders.isEmpty()) {
            coinOrderRepository.saveAll(staleOrders);
            logger.info("[ORDER-SCHEDULER] Expired {} stale orders", staleOrders.size());
        } else {
            logger.debug("[ORDER-SCHEDULER] No stale orders found");
        }

        return staleOrders.size();
    }

    // =========================================================================
    // FUTURE INTEGRATION METHODS (called by PaymentService)
    // =========================================================================

    /**
     * Transition order status to PAYMENT_PENDING.
     *
     * Called by PaymentService when payment is initiated with gateway.
     * Not exposed via REST — internal service method only.
     */
    @Transactional
    public CoinOrder markPaymentPending(String orderNumber) {

        CoinOrder order = coinOrderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderNumber));

        if (order.getStatus() != CoinOrderStatus.CREATED) {
            throw new BusinessLogicException(
                    "Order must be in CREATED status to start payment. Current: " + order.getStatus(),
                    HttpStatus.BAD_REQUEST);
        }

        order.setStatus(CoinOrderStatus.PAYMENT_PENDING);
        return coinOrderRepository.save(order);
    }

    /**
     * Transition order status to PAYMENT_SUCCESS.
     *
     * Called by PaymentService after payment verification succeeds.
     * Not exposed via REST — internal service method only.
     */
    @Transactional
    public CoinOrder markPaymentSuccess(String orderNumber) {

        CoinOrder order = coinOrderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderNumber));

        if (order.getStatus() != CoinOrderStatus.PAYMENT_PENDING) {
            throw new BusinessLogicException(
                    "Order must be in PAYMENT_PENDING status for success. Current: " + order.getStatus(),
                    HttpStatus.BAD_REQUEST);
        }

        order.setStatus(CoinOrderStatus.PAYMENT_SUCCESS);
        return coinOrderRepository.save(order);
    }

    /**
     * Transition order status to PAYMENT_FAILED.
     *
     * Called by PaymentService when payment fails.
     * Not exposed via REST — internal service method only.
     */
    @Transactional
    public CoinOrder markPaymentFailed(String orderNumber, String reason) {

        CoinOrder order = coinOrderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderNumber));

        if (order.getStatus() != CoinOrderStatus.PAYMENT_PENDING) {
            throw new BusinessLogicException(
                    "Order must be in PAYMENT_PENDING status for failure. Current: " + order.getStatus(),
                    HttpStatus.BAD_REQUEST);
        }

        order.setStatus(CoinOrderStatus.PAYMENT_FAILED);
        order.setRemarks(reason);
        return coinOrderRepository.save(order);
    }

    // =========================================================================
    // INTERNAL HELPERS
    // =========================================================================

    /**
     * Fetch order and validate it exists.
     */
    private CoinOrder getOrderForStatusChange(String orderNumber) {

        return coinOrderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Order not found: " + orderNumber));
    }

    /**
     * Validate order is NOT in a terminal status.
     */
    private void validateNotTerminal(CoinOrder order, String action) {

        if (TERMINAL_STATUSES.contains(order.getStatus())) {
            throw new OrderNotCancellableException(
                    "Cannot " + action + " order in status: " + order.getStatus());
        }
    }

    /**
     * Build admin remarks combining action and optional notes.
     */
    private String buildRemarks(String action, String remarks) {

        if (remarks != null && !remarks.isBlank()) {
            return action + " — " + remarks;
        }
        return action;
    }

    /**
     * Generate human-readable order number.
     *
     * Format: ORD-{YEAR}-{NANOS}
     * Example: ORD-2026-17205123456789
     *
     * NOTE: In a multi-instance deployment, replace with
     * Snowflake ID or distributed sequence.
     */
    private String generateOrderNumber() {
        return "ORD-" + Year.now().getValue() + "-" + System.nanoTime();
    }

    /**
     * Check if a status is terminal (no further transitions allowed).
     */
    public boolean isTerminalStatus(CoinOrderStatus status) {
        return TERMINAL_STATUSES.contains(status);
    }

    // =========================================================================
    // SERIALIZATION HELPERS (for idempotency cache)
    // =========================================================================

    private String serializeRequest(CreateCoinOrderRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            logger.error("[ORDER] Failed to serialize request", e);
            return request.getPlanId() + ":" + request.getIdempotencyKey();
        }
    }

    private String serializeResponse(CoinOrderResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            logger.error("[ORDER] Failed to serialize response", e);
            return "{}";
        }
    }

    private CoinOrderResponse deserializeResponse(String json) {
        try {
            return objectMapper.readValue(json, CoinOrderResponse.class);
        } catch (JsonProcessingException e) {
            logger.error("[ORDER] Failed to deserialize cached response", e);
            throw new RuntimeException("Failed to read cached order response", e);
        }
    }

    // =========================================================================
    // MAPPER METHODS
    // =========================================================================

    /**
     * Entity → User-facing Response DTO
     */
    private CoinOrderResponse mapToResponse(CoinOrder order) {

        return CoinOrderResponse.builder()
                .orderNumber(order.getOrderNumber())
                .planCode(order.getPlanCode())
                .planName(order.getPlanName())
                .baseCoins(order.getBaseCoins())
                .bonusCoins(order.getBonusCoins())
                .totalCoins(order.getTotalCoins())
                .orderAmount(order.getOrderAmount())
                .currency(order.getCurrency().name())
                .status(order.getStatus().name())
                .expiresAt(order.getExpiresAt())
                .createdAt(order.getCreatedAt())
                .build();
    }

    /**
     * Entity → Admin-facing Response DTO
     *
     * Includes internal fields like database ID, user info,
     * idempotency key, and remarks.
     */
    private AdminCoinOrderResponse mapToAdminResponse(CoinOrder order) {

        // Fetch idempotency key from IdempotencyRecord (reverse lookup by orderId)
        String idempotencyKey = idempotencyService
                .findByOrderId(order.getId())
                .map(IdempotencyRecord::getIdempotencyKey)
                .orElse(null);

        return AdminCoinOrderResponse.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .userId(order.getUser().getId())
                .userEmail(order.getUser().getEmail())
                .username(order.getUser().getUsername())
                .planId(order.getPlan().getId())
                .planCode(order.getPlanCode())
                .planName(order.getPlanName())
                .baseCoins(order.getBaseCoins())
                .bonusCoins(order.getBonusCoins())
                .totalCoins(order.getTotalCoins())
                .orderAmount(order.getOrderAmount())
                .currency(order.getCurrency().name())
                .status(order.getStatus().name())
                .remarks(order.getRemarks())
                .idempotencyKey(idempotencyKey)
                .expiresAt(order.getExpiresAt())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}
