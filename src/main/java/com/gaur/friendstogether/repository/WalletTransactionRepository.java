package com.gaur.friendstogether.repository;

import com.gaur.friendstogether.entity.TransactionType;
import com.gaur.friendstogether.entity.WalletTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * WalletTransactionRepository
 *
 * wallet_transactions table ke liye database operations.
 *
 * IMPORTANT:
 * - Ye table IMMUTABLE hai — sirf INSERT hota hai.
 * - Kabhi UPDATE ya DELETE mat karo.
 * - Current balance isse calculate NAHI karna — UserWallet se read karo.
 */
@Repository
public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {

    /**
     * Paginated transaction history.
     *
     * GET /api/wallet/transactions ke liye.
     * Latest transactions pehle dikhte hain.
     */
    Page<WalletTransaction> findByWallet_IdOrderByCreatedAtDesc(Long walletId, Pageable pageable);

    /**
     * Filtered paginated history — sirf CREDIT ya sirf DEBIT.
     *
     * GET /api/wallet/transactions?type=CREDIT ke liye.
     */
    Page<WalletTransaction> findByWallet_IdAndTypeOrderByCreatedAtDesc(
            Long walletId, TransactionType type, Pageable pageable);

    /**
     * Dashboard Summary ke liye top 5 recent transactions.
     *
     * GET /api/wallet/summary ke liye.
     */
    List<WalletTransaction> findTop5ByWallet_IdOrderByCreatedAtDesc(Long walletId);
}
