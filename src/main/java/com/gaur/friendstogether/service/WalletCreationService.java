package com.gaur.friendstogether.service;

import com.gaur.friendstogether.entity.*;
import com.gaur.friendstogether.repository.UserWalletRepository;
import com.gaur.friendstogether.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;

/**
 * =============================================================================
 * WalletCreationService
 * =============================================================================
 *
 * Centralized wallet creation logic.
 *
 * Har jagah se wallet create karna ho — registration, seeder, safety net —
 * sab isi service ke through hoga. Duplicate logic nahi hogi.
 *
 * FLOW:
 * 1. UserWallet create karo (ACTIVE, welcome bonus coins)
 * 2. WalletTransaction audit entry create karo (WELCOME_BONUS)
 * 3. User entity pe wallet set karo (bidirectional relationship)
 *
 * =============================================================================
 */
@Service
@RequiredArgsConstructor
public class WalletCreationService {

    private static final Logger logger = LoggerFactory.getLogger(WalletCreationService.class);

    /**
     * Welcome bonus amount — har naye user ko itne coins milte hain.
     */
    private static final long WELCOME_BONUS_COINS = 50L;

    private final UserWalletRepository userWalletRepository;
    private final WalletTransactionRepository walletTransactionRepository;

    /**
     * Naye user ke liye wallet create karo with welcome bonus.
     *
     * IMPORTANT:
     * - Ye method caller ke @Transactional me participate karta hai.
     * - Wallet + Transaction dono ek hi transaction me save hote hain.
     * - User entity pe wallet set karta hai (bidirectional sync).
     *
     * @param user The user entity (must be managed / about to be persisted)
     * @return The created UserWallet
     */
    public UserWallet createWalletForUser(User user) {

        logger.info("[WALLET-CREATE] Creating wallet for user: email={}", user.getEmail());

        // 1. Build wallet with welcome bonus
        UserWallet wallet = UserWallet.builder()
                .user(user)
                .currentBalance(WELCOME_BONUS_COINS)
                .lifetimeRechargedCoins(0L)
                .status(WalletStatus.ACTIVE)
                .build();

        // 2. Set bidirectional relationship
        user.setWallet(wallet);

        logger.info("[WALLET-CREATE] Wallet built: email={}, coins={}", user.getEmail(), WELCOME_BONUS_COINS);

        return wallet;
    }

    /**
     * Wallet save hone ke baad welcome bonus transaction record create karo.
     *
     * IMPORTANT:
     * - Ye method tab call karo jab wallet already persisted ho (ID assigned ho).
     * - Typically: userRepository.save() ke baad call karo.
     *
     * @param wallet The persisted wallet (must have an ID)
     */
    @Transactional
    public void createWelcomeBonusTransaction(UserWallet wallet) {

        logger.info("[WALLET-CREATE] Creating welcome bonus transaction: walletId={}, coins={}",
                wallet.getId(), WELCOME_BONUS_COINS);

        WalletTransaction txn = WalletTransaction.builder()
                .transactionNumber(generateTransactionNumber())
                .wallet(wallet)
                .amount(WELCOME_BONUS_COINS)
                .type(TransactionType.CREDIT)
                .reason(TransactionReason.WELCOME_BONUS)
                .referenceType(ReferenceType.WELCOME_BONUS)
                .referenceId(0L)
                .description("Welcome bonus - " + WELCOME_BONUS_COINS + " coins credited")
                .status(TransactionStatus.SUCCESS)
                .balanceAfterTransaction(WELCOME_BONUS_COINS)
                .build();

        walletTransactionRepository.save(txn);

        logger.info("[WALLET-CREATE] Welcome bonus transaction created: txn={}",
                txn.getTransactionNumber());
    }

    /**
     * Existing user ke liye wallet ensure karo — agar nahi hai to create karo.
     *
     * Idempotent — agar wallet already exist karta hai to skip karta hai.
     * Used by: WalletSeeder (backfill) and WalletService (safety net).
     *
     * @param user The user entity
     * @return The existing or newly created wallet
     */
    @Transactional
    public UserWallet ensureWalletExists(User user) {

        // Check if wallet already exists
        return userWalletRepository.findByUser_Id(user.getId())
                .orElseGet(() -> {
                    logger.info("[WALLET-CREATE] No wallet found for userId={}. Creating...", user.getId());

                    UserWallet wallet = UserWallet.builder()
                            .user(user)
                            .currentBalance(WELCOME_BONUS_COINS)
                            .lifetimeRechargedCoins(0L)
                            .status(WalletStatus.ACTIVE)
                            .build();

                    user.setWallet(wallet);
                    UserWallet savedWallet = userWalletRepository.save(wallet);

                    // Create audit trail
                    createWelcomeBonusTransaction(savedWallet);

                    logger.info("[WALLET-CREATE] Wallet backfilled: userId={}, walletId={}, coins={}",
                            user.getId(), savedWallet.getId(), WELCOME_BONUS_COINS);

                    return savedWallet;
                });
    }

    /**
     * Transaction number generate karo.
     *
     * Format: TXN-{YEAR}-{TIMESTAMP_NANOS}
     */
    private String generateTransactionNumber() {
        return "TXN-" + Year.now().getValue() + "-" + System.nanoTime();
    }
}
