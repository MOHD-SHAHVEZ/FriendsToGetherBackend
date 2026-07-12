package com.gaur.friendstogether.repository;

import com.gaur.friendstogether.entity.CoinOrder;
import com.gaur.friendstogether.entity.CoinOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * =============================================================================
 * CoinOrderRepository
 * =============================================================================
 *
 * Database operations for the coin_orders table.
 *
 * JpaSpecificationExecutor enables dynamic admin filtering
 * (status + user + date range + amount range — all optional combinations).
 * =============================================================================
 */
@Repository
public interface CoinOrderRepository
        extends JpaRepository<CoinOrder, Long>, JpaSpecificationExecutor<CoinOrder> {

    // =========================================================================
    // ORDER LOOKUP
    // =========================================================================

    /**
     * Find order by business order number.
     *
     * Example: findByOrderNumber("ORD-2026-17205123456789")
     *
     * Used by:
     *   - User: view order detail
     *   - Admin: view any order
     *   - Payment Service: link payment to order
     */
    Optional<CoinOrder> findByOrderNumber(String orderNumber);

    // =========================================================================
    // PENDING ORDER CHECK (Duplicate Prevention Layer 3)
    // =========================================================================

    /**
     * Check if user has an active order for the same plan.
     *
     * Statuses checked: CREATED, PAYMENT_PENDING
     *
     * If found → return existing order (no new order created).
     *
     * Index: idx_coin_order_user_status
     */
    Optional<CoinOrder> findByUser_IdAndPlan_IdAndStatusIn(
            Long userId,
            Long planId,
            List<CoinOrderStatus> statuses
    );

    // =========================================================================
    // USER ORDERS
    // =========================================================================

    /**
     * Paginated list of a user's orders.
     * Most recent first.
     *
     * Used by: GET /api/orders/my-orders
     */
    Page<CoinOrder> findByUser_IdOrderByCreatedAtDesc(
            Long userId,
            Pageable pageable
    );

    /**
     * Find specific order belonging to a user.
     *
     * Combines lookup + ownership check in a single query.
     * If order exists but belongs to different user → Optional.empty()
     *   → Controller returns 404 (not 403, to avoid leaking info).
     */
    Optional<CoinOrder> findByUser_IdAndOrderNumber(
            Long userId,
            String orderNumber
    );

    // =========================================================================
    // SCHEDULER: ORDER EXPIRY
    // =========================================================================

    /**
     * Find CREATED orders that have passed their expiresAt time.
     *
     * Called every 5 minutes by CoinOrderScheduler.
     *
     * Index: idx_coin_order_expires (status, expires_at)
     */
    @Query("SELECT o FROM CoinOrder o WHERE o.status = :status AND o.expiresAt <= :now")
    List<CoinOrder> findExpiredOrders(
            @Param("status") CoinOrderStatus status,
            @Param("now") LocalDateTime now
    );

    // =========================================================================
    // ADMIN QUERIES
    // =========================================================================

    /**
     * Admin: all orders by status (paginated).
     */
    Page<CoinOrder> findByStatusOrderByCreatedAtDesc(
            CoinOrderStatus status,
            Pageable pageable
    );

    /**
     * Admin: all orders of a specific user (paginated).
     */
    Page<CoinOrder> findByUser_Id(Long userId, Pageable pageable);
}
