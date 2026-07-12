package com.gaur.friendstogether.service;

import com.gaur.friendstogether.entity.*;
import com.gaur.friendstogether.repository.IdempotencyRecordRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * =============================================================================
 * IdempotencyService
 * =============================================================================
 *
 * Dedicated service for idempotency operations.
 *
 * Uses httpMethod + requestPath to identify which endpoint
 * the idempotency key belongs to (instead of a ResourceType enum).
 *
 * ──────────────────────────────────────────────────────────────────────────────
 * CALLERS
 * ──────────────────────────────────────────────────────────────────────────────
 *
 * CoinOrderService → POST /api/orders
 *   - Before creating order, check idempotency key
 *   - After creating order, store record with orderId FK
 *
 * PaymentService → POST /api/payments/initiate (future)
 *   - Before calling Razorpay, check idempotency key
 *   - After getting checkout session, store with paymentId + checkoutUrl
 *
 * Scheduler → Cleanup
 *   - Mark expired records
 *
 * =============================================================================
 */
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private static final Logger logger = LoggerFactory.getLogger(IdempotencyService.class);

    /**
     * Default TTL for idempotency records: 24 hours.
     */
    private static final long IDEMPOTENCY_TTL_HOURS = 24;

    private final IdempotencyRecordRepository idempotencyRecordRepository;

    // =========================================================================
    // CHECK OPERATIONS
    // =========================================================================

    /**
     * Check if an idempotency key already exists for a given endpoint.
     *
     * Uses httpMethod + requestPath instead of ResourceType enum.
     *
     * Example:
     *   checkAndReturn("uuid-abc", "POST", "/api/orders")
     *   checkAndReturn("pay-uuid", "POST", "/api/payments/initiate")
     *
     * @param idempotencyKey  The key sent by the frontend
     * @param httpMethod      HTTP method (POST, PUT, etc.)
     * @param requestPath     API path (/api/orders, /api/payments/initiate)
     * @return Optional containing the record if found
     */
    @Transactional(readOnly = true)
    public Optional<IdempotencyRecord> checkAndReturn(
            String idempotencyKey,
            String httpMethod,
            String requestPath) {

        logger.debug("[IDEMPOTENCY] Checking key={}, method={}, path={}",
                idempotencyKey, httpMethod, requestPath);

        return idempotencyRecordRepository
                .findByIdempotencyKeyAndHttpMethodAndRequestPath(
                        idempotencyKey, httpMethod, requestPath);
    }

    // =========================================================================
    // CREATE OPERATIONS
    // =========================================================================

    /**
     * Create a new idempotency record for an ORDER creation.
     *
     * Called AFTER the CoinOrder is persisted.
     * Must be in the SAME transaction as the order creation.
     *
     * @param idempotencyKey   Frontend UUID
     * @param httpMethod       HTTP method (POST)
     * @param requestPath      API path (/api/orders)
     * @param order            The created CoinOrder entity (direct FK)
     * @param user             The user who created the order
     * @param requestHash      SHA-256 hash of request body
     * @param responseBody     JSON response to cache
     * @param statusCode       HTTP status code (201)
     * @return The created IdempotencyRecord
     */
    @Transactional
    public IdempotencyRecord createOrderRecord(
            String idempotencyKey,
            String httpMethod,
            String requestPath,
            CoinOrder order,
            User user,
            String requestHash,
            String responseBody,
            Integer statusCode) {

        logger.info("[IDEMPOTENCY] Creating order record: key={}, method={}, path={}, orderId={}",
                idempotencyKey, httpMethod, requestPath, order.getId());

        IdempotencyRecord record = IdempotencyRecord.builder()
                .idempotencyKey(idempotencyKey)
                .httpMethod(httpMethod)
                .requestPath(requestPath)
                .order(order)
                .user(user)
                .status(IdempotencyStatus.IN_PROGRESS)
                .requestHash(requestHash)
                .responseBody(responseBody)
                .responseStatusCode(statusCode)
                .expiresAt(LocalDateTime.now().plusHours(IDEMPOTENCY_TTL_HOURS))
                .build();

        IdempotencyRecord saved = idempotencyRecordRepository.save(record);

        logger.info("[IDEMPOTENCY] Record created: id={}, key={}", saved.getId(), idempotencyKey);

        return saved;
    }

    /**
     * Create a new idempotency record for a PAYMENT initiation (future).
     *
     * Called AFTER the Payment is created and Razorpay session is obtained.
     *
     * @param idempotencyKey     Frontend UUID
     * @param httpMethod         HTTP method (POST)
     * @param requestPath        API path (/api/payments/initiate)
     * @param paymentId          The created Payment ID
     * @param user               The user
     * @param requestHash        SHA-256 hash of request body
     * @param responseBody       JSON response to cache
     * @param statusCode         HTTP status code (201)
     * @param checkoutSessionId  Razorpay order_id / Stripe session_id
     * @param checkoutSessionUrl Complete checkout URL for hosted pages
     * @return The created IdempotencyRecord
     */
    @Transactional
    public IdempotencyRecord createPaymentRecord(
            String idempotencyKey,
            String httpMethod,
            String requestPath,
            Long paymentId,
            User user,
            String requestHash,
            String responseBody,
            Integer statusCode,
            String checkoutSessionId,
            String checkoutSessionUrl) {

        logger.info("[IDEMPOTENCY] Creating payment record: key={}, paymentId={}, sessionId={}",
                idempotencyKey, paymentId, checkoutSessionId);

        IdempotencyRecord record = IdempotencyRecord.builder()
                .idempotencyKey(idempotencyKey)
                .httpMethod(httpMethod)
                .requestPath(requestPath)
                .paymentId(paymentId)
                .user(user)
                .status(IdempotencyStatus.IN_PROGRESS)
                .requestHash(requestHash)
                .responseBody(responseBody)
                .responseStatusCode(statusCode)
                .checkoutSessionId(checkoutSessionId)
                .checkoutSessionUrl(checkoutSessionUrl)
                .expiresAt(LocalDateTime.now().plusHours(IDEMPOTENCY_TTL_HOURS))
                .build();

        IdempotencyRecord saved = idempotencyRecordRepository.save(record);

        logger.info("[IDEMPOTENCY] Payment record created: id={}, key={}", saved.getId(), idempotencyKey);

        return saved;
    }

    // =========================================================================
    // UPDATE OPERATIONS
    // =========================================================================

    /**
     * Store the checkout session ID and URL from payment gateway.
     *
     * Called when checkout session is created separately
     * from the idempotency record.
     */
    @Transactional
    public void updateCheckoutSession(
            String idempotencyKey,
            String checkoutSessionId,
            String checkoutSessionUrl) {

        logger.info("[IDEMPOTENCY] Updating checkout session: key={}, sessionId={}",
                idempotencyKey, checkoutSessionId);

        IdempotencyRecord record = idempotencyRecordRepository
                .findByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> new RuntimeException(
                        "IdempotencyRecord not found for key: " + idempotencyKey));

        record.setCheckoutSessionId(checkoutSessionId);
        record.setCheckoutSessionUrl(checkoutSessionUrl);

        idempotencyRecordRepository.save(record);

        logger.info("[IDEMPOTENCY] Checkout session stored: key={}", idempotencyKey);
    }

    /**
     * Mark record as COMPLETED.
     * Called when the full flow succeeds.
     */
    @Transactional
    public void markCompleted(String idempotencyKey) {

        logger.info("[IDEMPOTENCY] Marking COMPLETED: key={}", idempotencyKey);

        idempotencyRecordRepository.findByIdempotencyKey(idempotencyKey)
                .ifPresent(record -> {
                    record.setStatus(IdempotencyStatus.COMPLETED);
                    idempotencyRecordRepository.save(record);
                });
    }

    /**
     * Mark record as FAILED.
     * Client should generate a new key to retry.
     */
    @Transactional
    public void markFailed(String idempotencyKey) {

        logger.info("[IDEMPOTENCY] Marking FAILED: key={}", idempotencyKey);

        idempotencyRecordRepository.findByIdempotencyKey(idempotencyKey)
                .ifPresent(record -> {
                    record.setStatus(IdempotencyStatus.FAILED);
                    idempotencyRecordRepository.save(record);
                });
    }

    /**
     * Mark record as CANCELLED.
     * Called when user or admin cancels the operation.
     */
    @Transactional
    public void markCancelled(String idempotencyKey) {

        logger.info("[IDEMPOTENCY] Marking CANCELLED: key={}", idempotencyKey);

        idempotencyRecordRepository.findByIdempotencyKey(idempotencyKey)
                .ifPresent(record -> {
                    record.setStatus(IdempotencyStatus.CANCELLED);
                    idempotencyRecordRepository.save(record);
                });
    }

    // =========================================================================
    // LOOKUP OPERATIONS
    // =========================================================================

    /**
     * Find idempotency record by checkout session ID.
     * Used during webhook processing.
     */
    @Transactional(readOnly = true)
    public Optional<IdempotencyRecord> findByCheckoutSessionId(String checkoutSessionId) {

        return idempotencyRecordRepository.findByCheckoutSessionId(checkoutSessionId);
    }

    /**
     * Find idempotency record by CoinOrder ID.
     * Used for admin views — show the idempotency key used for an order.
     */
    @Transactional(readOnly = true)
    public Optional<IdempotencyRecord> findByOrderId(Long orderId) {

        return idempotencyRecordRepository.findByOrder_Id(orderId);
    }

    /**
     * Find idempotency record by Payment ID.
     * Used for admin views.
     */
    @Transactional(readOnly = true)
    public Optional<IdempotencyRecord> findByPaymentId(Long paymentId) {

        return idempotencyRecordRepository.findByPaymentId(paymentId);
    }

    // =========================================================================
    // SCHEDULER OPERATIONS
    // =========================================================================

    /**
     * Mark expired IN_PROGRESS records as EXPIRED.
     *
     * Called by IdempotencyCleanupScheduler every hour.
     *
     * @return Number of records expired
     */
    @Transactional
    public int cleanupExpiredRecords() {

        logger.info("[IDEMPOTENCY] Running cleanup for expired records...");

        List<IdempotencyRecord> expiredRecords = idempotencyRecordRepository
                .findExpiredRecords(IdempotencyStatus.IN_PROGRESS, LocalDateTime.now());

        for (IdempotencyRecord record : expiredRecords) {
            record.setStatus(IdempotencyStatus.EXPIRED);
        }

        if (!expiredRecords.isEmpty()) {
            idempotencyRecordRepository.saveAll(expiredRecords);
            logger.info("[IDEMPOTENCY] Expired {} records", expiredRecords.size());
        }

        return expiredRecords.size();
    }

    // =========================================================================
    // UTILITY METHODS
    // =========================================================================

    /**
     * Generate SHA-256 hash of a string.
     *
     * Used to hash the request body for integrity validation.
     *
     * On duplicate request:
     *   Same key + Same hash   → return cached response
     *   Same key + Different hash → reject with 422
     *
     * @param input The string to hash (usually JSON request body)
     * @return 64-character hex SHA-256 hash
     */
    public String hashRequest(String input) {

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to be available in all JVMs
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
