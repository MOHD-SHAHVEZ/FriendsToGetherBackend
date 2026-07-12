package com.gaur.friendstogether.repository;

import com.gaur.friendstogether.entity.UserWallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * UserWalletRepository
 *
 * user_wallets table ke liye database operations.
 *
 * IMPORTANT:
 * - Read operations ke liye findByUser_Id() use karo (no lock).
 * - Write operations (credit/debit) ke liye findByUserIdWithLock() use karo
 *   taaki concurrent updates se data corruption na ho.
 */
@Repository
public interface UserWalletRepository extends JpaRepository<UserWallet, Long> {

    /**
     * Dashboard / Balance check ke liye.
     *
     * No lock — sirf READ operation hai.
     * Unique index (idx_wallet_user) pe hit hota hai — O(1).
     */
    Optional<UserWallet> findByUser_Id(Long userId);

    /**
     * Credit / Debit operations ke liye.
     *
     * PESSIMISTIC_WRITE lock lagata hai (SELECT ... FOR UPDATE).
     * Jab tak ye transaction commit/rollback nahi hota,
     * koi doosra thread isi wallet row ko modify nahi kar sakta.
     *
     * USE CASE:
     * - Payment credit
     * - Gift debit
     * - Admin bonus
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM UserWallet w WHERE w.user.id = :userId")
    Optional<UserWallet> findByUserIdWithLock(@Param("userId") Long userId);
}
