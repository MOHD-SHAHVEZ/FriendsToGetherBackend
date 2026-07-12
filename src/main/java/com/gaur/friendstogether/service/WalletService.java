package com.gaur.friendstogether.service;

import com.gaur.friendstogether.dto.response.WalletBalanceResponse;
import com.gaur.friendstogether.dto.response.WalletSummaryResponse;
import com.gaur.friendstogether.dto.response.WalletTransactionResponse;
import com.gaur.friendstogether.entity.*;
import com.gaur.friendstogether.exception.InsufficientBalanceException;
import com.gaur.friendstogether.exception.WalletInactiveException;
import com.gaur.friendstogether.exception.WalletNotFoundException;
import com.gaur.friendstogether.repository.UserRepository;
import com.gaur.friendstogether.repository.UserWalletRepository;
import com.gaur.friendstogether.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Year;
import java.util.List;
import java.util.stream.Collectors;

/**
 * =============================================================================
 * WalletService
 * =============================================================================
 *
 * Wallet module ka complete business logic.
 *
 * READ operations   → Controller ke through call hoti hain (user-facing)
 * WRITE operations  → Sirf internal services call karein (PaymentService, GiftService)
 *
 * IMPORTANT:
 * credit() aur debit() kabhi REST endpoint se expose NAHI karna.
 * Ye sirf internal service methods hain.
 *
 * Architecture:
 *
 * WalletController (user-facing, READ-only)
 *       │
 *       ▼
 * WalletService
 *       │
 *       ├── UserWalletRepository        → current balance (user_wallets)
 *       └── WalletTransactionRepository → history (wallet_transactions)
 *
 * PaymentService / GiftService (internal, WRITE)
 *       │
 *       ▼
 * WalletService.credit() / debit()
 *       │
 *       ├── user_wallets       → UPDATE currentBalance
 *       └── wallet_transactions → INSERT new record
 *
 * =============================================================================
 */
@Service
@RequiredArgsConstructor
public class WalletService {

    private static final Logger logger = LoggerFactory.getLogger(WalletService.class);

    private final UserWalletRepository userWalletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final UserRepository userRepository;
    private final WalletCreationService walletCreationService;

    // =========================================================================
    // READ OPERATIONS (called by WalletController)
    // =========================================================================

    /**
     * Current balance return karo.
     *
     * Source: user_wallets table (1 row, O(1) lookup).
     *
     * Called by: WalletController → GET /api/wallet/balance
     */
    @Transactional
    public WalletBalanceResponse getBalance(Long userId) {

        logger.info("[WALLET] Fetching balance for userId={}", userId);

        UserWallet wallet = getOrCreateWalletForRead(userId);

        validateWalletActive(wallet);

        return mapToBalanceResponse(wallet);
    }

    /**
     * Dashboard composite — balance + last 5 transactions.
     *
     * Ek API call me poora dashboard data mil jaata hai.
     * Frontend ko 2 alag calls nahi karni padtee.
     *
     * Called by: WalletController → GET /api/wallet/summary
     */
    @Transactional
    public WalletSummaryResponse getDashboardSummary(Long userId) {

        logger.info("[WALLET] Fetching dashboard summary for userId={}", userId);

        UserWallet wallet = getOrCreateWalletForRead(userId);

        validateWalletActive(wallet);

        // Last 5 recent transactions
        List<WalletTransaction> recentTxns =
                walletTransactionRepository.findTop5ByWallet_IdOrderByCreatedAtDesc(wallet.getId());

        List<WalletTransactionResponse> recentTxnResponses = recentTxns.stream()
                .map(this::mapToTransactionResponse)
                .collect(Collectors.toList());

        return WalletSummaryResponse.builder()
                .balance(mapToBalanceResponse(wallet))
                .recentTransactions(recentTxnResponses)
                .build();
    }

    /**
     * Paginated transaction history.
     *
     * Optional filter by type (CREDIT / DEBIT).
     *
     * Called by: WalletController → GET /api/wallet/transactions
     */
    @Transactional
    public Page<WalletTransactionResponse> getTransactionHistory(
            Long userId, TransactionType type, Pageable pageable) {

        logger.info("[WALLET] Fetching transaction history for userId={}, type={}, page={}",
                userId, type, pageable.getPageNumber());

        UserWallet wallet = getOrCreateWalletForRead(userId);

        validateWalletActive(wallet);

        Page<WalletTransaction> transactions;

        if (type != null) {
            // Filtered: sirf CREDIT ya sirf DEBIT
            transactions = walletTransactionRepository
                    .findByWallet_IdAndTypeOrderByCreatedAtDesc(wallet.getId(), type, pageable);
        } else {
            // All transactions
            transactions = walletTransactionRepository
                    .findByWallet_IdOrderByCreatedAtDesc(wallet.getId(), pageable);
        }

        return transactions.map(this::mapToTransactionResponse);
    }

    // =========================================================================
    // WRITE OPERATIONS (called by other Services ONLY — NEVER via REST)
    // =========================================================================

    /**
     * Wallet me coins add karo.
     *
     * CALLER:
     * - PaymentService   → Purchase success ke baad
     * - GiftService      → Receiver ko reward coins
     * - AdminService     → Manual bonus
     *
     * FLOW:
     * 1. Lock wallet row (PESSIMISTIC_WRITE)
     * 2. Add amount to currentBalance
     * 3. Update lifetimeRechargedCoins (agar PURCHASE hai)
     * 4. Create WalletTransaction record
     * 5. Both save in same @Transactional
     *
     * @param userId      Jis user ke wallet me credit karna hai
     * @param amount      Kitne coins add karne hain
     * @param reason      Kyun add ho rahe hain (PURCHASE, GIFT_RECEIVED, etc.)
     * @param refType     Kis module se aaya (PAYMENT, GIFT, ADMIN, etc.)
     * @param refId       Source record ka ID (Payment ID, Gift ID, etc.)
     * @param description Human readable description
     */
    @Transactional
    public void credit(Long userId, Long amount, TransactionReason reason,
                       ReferenceType refType, Long refId, String description) {

        logger.info("[WALLET] CREDIT: userId={}, amount={}, reason={}, refType={}, refId={}",
                userId, amount, reason, refType, refId);

        // 1. Get or create wallet with pessimistic lock
        UserWallet wallet = getOrCreateWalletWithLock(userId);

        // 2. Validate wallet is active
        validateWalletActive(wallet);

        // 3. Update balance
        wallet.setCurrentBalance(wallet.getCurrentBalance() + amount);

        // 4. Update lifetime recharged (only for purchases)
        if (reason == TransactionReason.PURCHASE) {
            wallet.setLifetimeRechargedCoins(wallet.getLifetimeRechargedCoins() + amount);
        }

        // 5. Update last transaction time
        wallet.setLastTransactionAt(LocalDateTime.now());

        // 6. Save wallet (triggers @Version increment for optimistic lock safety)
        userWalletRepository.save(wallet);

        // 7. Create immutable transaction record
        WalletTransaction txn = WalletTransaction.builder()
                .transactionNumber(generateTransactionNumber())
                .wallet(wallet)
                .amount(amount)
                .type(TransactionType.CREDIT)
                .reason(reason)
                .referenceType(refType)
                .referenceId(refId)
                .description(description)
                .status(TransactionStatus.SUCCESS)
                .balanceAfterTransaction(wallet.getCurrentBalance())
                .build();

        walletTransactionRepository.save(txn);

        logger.info("[WALLET] CREDIT SUCCESS: userId={}, newBalance={}, txn={}",
                userId, wallet.getCurrentBalance(), txn.getTransactionNumber());
    }

    /**
     * Wallet se coins deduct karo.
     *
     * CALLER:
     * - GiftService   → Gift bhejte waqt sender se debit
     * - AdminService  → Manual deduction
     *
     * FLOW:
     * 1. Lock wallet row (PESSIMISTIC_WRITE)
     * 2. Validate balance >= amount
     * 3. Subtract amount from currentBalance
     * 4. Create WalletTransaction record
     * 5. Both save in same @Transactional
     *
     * @param userId      Jis user ke wallet se debit karna hai
     * @param amount      Kitne coins deduct karne hain
     * @param reason      Kyun deduct ho rahe hain (GIFT_SENT, ADMIN_DEBIT, etc.)
     * @param refType     Kis module se aaya (GIFT, ADMIN, etc.)
     * @param refId       Source record ka ID (Gift ID, etc.)
     * @param description Human readable description
     */
    @Transactional
    public void debit(Long userId, Long amount, TransactionReason reason,
                      ReferenceType refType, Long refId, String description) {

        logger.info("[WALLET] DEBIT: userId={}, amount={}, reason={}, refType={}, refId={}",
                userId, amount, reason, refType, refId);

        // 1. Get wallet with pessimistic lock
        UserWallet wallet = userWalletRepository.findByUserIdWithLock(userId)
                .orElseThrow(() -> new WalletNotFoundException(
                        "Wallet not found for user id: " + userId));

        // 2. Validate wallet is active
        validateWalletActive(wallet);

        // 3. Validate sufficient balance
        validateSufficientBalance(wallet, amount);

        // 4. Update balance
        wallet.setCurrentBalance(wallet.getCurrentBalance() - amount);

        // 5. Update last transaction time
        wallet.setLastTransactionAt(LocalDateTime.now());

        // 6. Save wallet
        userWalletRepository.save(wallet);

        // 7. Create immutable transaction record
        WalletTransaction txn = WalletTransaction.builder()
                .transactionNumber(generateTransactionNumber())
                .wallet(wallet)
                .amount(amount)
                .type(TransactionType.DEBIT)
                .reason(reason)
                .referenceType(refType)
                .referenceId(refId)
                .description(description)
                .status(TransactionStatus.SUCCESS)
                .balanceAfterTransaction(wallet.getCurrentBalance())
                .build();

        walletTransactionRepository.save(txn);

        logger.info("[WALLET] DEBIT SUCCESS: userId={}, newBalance={}, txn={}",
                userId, wallet.getCurrentBalance(), txn.getTransactionNumber());
    }

    // =========================================================================
    // INTERNAL HELPERS
    // =========================================================================

    /**
     * Wallet fetch karo with pessimistic lock.
     * Agar wallet exist nahi karta to create kar do (lazy creation).
     *
     * Lazy creation pattern:
     * Har user ka wallet tab create hota hai jab pehli baar
     * credit/debit hota hai — signup pe nahi.
     */
    private UserWallet getOrCreateWalletWithLock(Long userId) {

        return userWalletRepository.findByUserIdWithLock(userId)
                .orElseGet(() -> {
                    logger.info("[WALLET] Creating new wallet for userId={}", userId);

                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new WalletNotFoundException(
                                    "User not found with id: " + userId));

                    UserWallet newWallet = UserWallet.builder()
                            .user(user)
                            .currentBalance(0L)
                            .lifetimeRechargedCoins(0L)
                            .status(WalletStatus.ACTIVE)
                            .build();

                    return userWalletRepository.save(newWallet);
                });
    }

    /**
     * Wallet ACTIVE hai ya nahi check karo.
     *
     * FROZEN / BLOCKED / CLOSED wallet pe koi operation allowed nahi hai.
     */
    private void validateWalletActive(UserWallet wallet) {
        if (wallet.getStatus() != WalletStatus.ACTIVE) {
            throw new WalletInactiveException(
                    "Wallet is " + wallet.getStatus()
                            + ". Operations are not allowed on inactive wallets.");
        }
    }

    /**
     * Read operation ke liye wallet fetch karo.
     * Agar wallet exist nahi karta to auto-create kar do (safety net).
     *
     * Ye scenario tab hota hai jab:
     * - Purane users jinke liye seeder chal nahi paya
     * - Edge case: registration ke beech me failure
     */
    private UserWallet getOrCreateWalletForRead(Long userId) {
        return userWalletRepository.findByUser_Id(userId)
                .orElseGet(() -> {
                    logger.warn("[WALLET] Safety net triggered — no wallet for userId={}. Auto-creating...", userId);

                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new WalletNotFoundException(
                                    "User not found with id: " + userId));

                    return walletCreationService.ensureWalletExists(user);
                });
    }

    /**
     * Balance check karo — debit ke pehle.
     *
     * Negative balance KABHI allowed nahi hai.
     */
    private void validateSufficientBalance(UserWallet wallet, Long amount) {
        if (wallet.getCurrentBalance() < amount) {
            throw new InsufficientBalanceException(
                    "Insufficient balance. Required: " + amount
                            + " coins, Available: " + wallet.getCurrentBalance() + " coins");
        }
    }

    /**
     * Human readable transaction number generate karo.
     *
     * Format: TXN-{YEAR}-{TIMESTAMP_NANOS}
     *
     * Example: TXN-2026-1720512345678901234
     *
     * NOTE: Production me isko Snowflake ID ya distributed sequence
     * se replace karna chahiye agar multiple instances hain.
     */
    private String generateTransactionNumber() {
        return "TXN-" + Year.now().getValue() + "-" + System.nanoTime();
    }

    // =========================================================================
    // MAPPER METHODS
    // =========================================================================

    /**
     * UserWallet entity → WalletBalanceResponse DTO
     */
    private WalletBalanceResponse mapToBalanceResponse(UserWallet wallet) {
        return WalletBalanceResponse.builder()
                .coins(wallet.getCurrentBalance())
                .status(wallet.getStatus().name())
                .lifetimeRechargedCoins(wallet.getLifetimeRechargedCoins())
                .lastTransactionAt(wallet.getLastTransactionAt())
                .build();
    }

    /**
     * WalletTransaction entity → WalletTransactionResponse DTO
     */
    private WalletTransactionResponse mapToTransactionResponse(WalletTransaction txn) {
        return WalletTransactionResponse.builder()
                .transactionNumber(txn.getTransactionNumber())
                .amount(txn.getAmount())
                .type(txn.getType().name())
                .reason(txn.getReason().name())
                .description(txn.getDescription())
                .balanceAfter(txn.getBalanceAfterTransaction())
                .createdAt(txn.getCreatedAt())
                .build();
    }
}
