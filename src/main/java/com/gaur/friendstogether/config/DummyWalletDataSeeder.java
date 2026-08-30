package com.gaur.friendstogether.config;

import com.gaur.friendstogether.entity.*;
import com.gaur.friendstogether.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * =============================================================================
 * DummyWalletDataSeeder
 * =============================================================================
 *
 * Seeds realistic production-like data for testing.
 *
 * Uses the real user (mohd.shahvez.dev@gmail.com) and creates:
 * 1. Coin Plans (realistic Indian pricing)
 * 2. Coin Orders (completed purchases)
 * 3. Payments (Razorpay-style gateway records)
 * 4. Wallet Transactions (CREDIT/DEBIT mix with realistic reasons)
 *
 * IDEMPOTENT — only seeds if tables are empty.
 * =============================================================================
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(3) // Run after WalletSeeder
public class DummyWalletDataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final UserWalletRepository userWalletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final CoinPlanRepository coinPlanRepository;
    private final CoinOrderRepository coinOrderRepository;
    private final PaymentRepository paymentRepository;

    private int txnCounter = 1;
    private int payCounter = 1;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        log.info("[DUMMY-DATA-SEEDER] Checking if dummy data is needed...");

        // Find the real user
        User primaryUser = userRepository.findByEmail("mohd.shahvez.dev@gmail.com").orElse(null);
        if (primaryUser == null) {
            log.warn("[DUMMY-DATA-SEEDER] User mohd.shahvez.dev@gmail.com not found. Skipping.");
            return;
        }

        UserWallet wallet = userWalletRepository.findByUser_Id(primaryUser.getId()).orElse(null);
        if (wallet == null) {
            log.warn("[DUMMY-DATA-SEEDER] Wallet not found for primary user. Skipping.");
            return;
        }

        // 1. Seed Coin Plans
        List<CoinPlan> plans = seedCoinPlans();

        // 2. Seed full purchase flow (CoinOrder + Payment + WalletTransaction)
        boolean hasTransactions = !walletTransactionRepository
                .findTop5ByWallet_IdOrderByCreatedAtDesc(wallet.getId()).isEmpty();

        if (!hasTransactions) {
            // log.info("[DUMMY-DATA-SEEDER] Seeding realistic transaction data for walletId={}...", wallet.getId());
            // seedPurchaseFlows(primaryUser, wallet, plans);
            // seedGiftTransactions(primaryUser, wallet);
            // seedMiscTransactions(primaryUser, wallet);

            // Update final wallet balance
            userWalletRepository.save(wallet);
            log.info("[DUMMY-DATA-SEEDER] Final wallet balance: {} coins", wallet.getCurrentBalance());
        } else {
            log.info("[DUMMY-DATA-SEEDER] Wallet {} already has transactions. Skipping.", wallet.getId());
        }

        log.info("[DUMMY-DATA-SEEDER] Seeding completed.");
    }

    // =========================================================================
    // 1. COIN PLANS — Realistic Indian pricing
    // =========================================================================
    private List<CoinPlan> seedCoinPlans() {
        if (coinPlanRepository.count() > 0) {
            return coinPlanRepository.findAll();
        }

        log.info("[DUMMY-DATA-SEEDER] Seeding Coin Plans...");
        List<CoinPlan> plans = new ArrayList<>();

        plans.add(buildPlan("STARTER_49", "Starter Pack", "Perfect for beginners",
                50, 0, new BigDecimal("49.00"), CoinPlanBadge.NONE, 1));

        plans.add(buildPlan("POPULAR_99", "Popular Pack", "Most purchased pack!",
                120, 10, new BigDecimal("99.00"), CoinPlanBadge.POPULAR, 2));

        plans.add(buildPlan("VALUE_199", "Value Pack", "Great value for regular users",
                260, 30, new BigDecimal("199.00"), CoinPlanBadge.NONE, 3));

        plans.add(buildPlan("SUPER_499", "Super Pack", "Best deal — 40% more coins!",
                700, 100, new BigDecimal("499.00"), CoinPlanBadge.HOT, 4));

        plans.add(buildPlan("MEGA_999", "Mega Pack", "For power users — maximum coins",
                1500, 300, new BigDecimal("999.00"), CoinPlanBadge.BEST_VALUE, 5));

        plans.add(buildPlan("ULTRA_1999", "Ultra Pack", "Premium experience — huge bonus",
                3200, 800, new BigDecimal("1999.00"), CoinPlanBadge.NONE, 6));

        plans.add(buildPlan("DIAMOND_4999", "Diamond Pack", "Top spender exclusive pack",
                8500, 2500, new BigDecimal("4999.00"), CoinPlanBadge.HOT, 7));

        plans.add(buildPlan("ROYAL_9999", "Royal Pack", "The ultimate coin package",
                18000, 5000, new BigDecimal("9999.00"), CoinPlanBadge.BEST_VALUE, 8));

        plans.add(buildPlan("MINI_29", "Mini Pack", "Quick top-up for small gifts",
                25, 0, new BigDecimal("29.00"), CoinPlanBadge.NONE, 9));

        plans.add(buildPlan("FESTIVAL_149", "Festival Special", "Limited time festive offer!",
                200, 50, new BigDecimal("149.00"), CoinPlanBadge.NEW, 10));

        return coinPlanRepository.saveAll(plans);
    }

    private CoinPlan buildPlan(String code, String name, String desc,
            int coins, int bonus, BigDecimal price,
            CoinPlanBadge badge, int order) {
        return CoinPlan.builder()
                .code(code)
                .name(name)
                .description(desc)
                .coins(coins)
                .bonusCoins(bonus)
                .price(price)
                .currency(CurrencyType.INR)
                .badge(badge)
                .status(CoinPlanStatus.ACTIVE)
                .displayOrder(order)
                .version(1)
                .build();
    }

    // =========================================================================
    // 2. PURCHASE FLOWS — CoinOrder + Payment + WalletTransaction (CREDIT)
    // =========================================================================
    private void seedPurchaseFlows(User user, UserWallet wallet, List<CoinPlan> plans) {
        if (plans.isEmpty())
            return;
        LocalDateTime baseTime = LocalDateTime.now().minusDays(15);
        int s = plans.size();

        // Purchase 1: Starter Pack via UPI
        createCompletePurchase(user, wallet, plans.get(0 % s), baseTime.plusDays(0),
                PaymentMethod.UPI, "order_RzpStarter001", "pay_RzpStarter001");

        // Purchase 2: Popular Pack via Card
        createCompletePurchase(user, wallet, plans.get(1 % s), baseTime.plusDays(3),
                PaymentMethod.CARD, "order_RzpPopular002", "pay_RzpPopular002");

        // Purchase 3: Super Pack via UPI
        createCompletePurchase(user, wallet, plans.get(3 % s), baseTime.plusDays(7),
                PaymentMethod.UPI, "order_RzpSuper003", "pay_RzpSuper003");

        // Purchase 4: Value Pack via Net Banking
        createCompletePurchase(user, wallet, plans.get(2 % s), baseTime.plusDays(10),
                PaymentMethod.NET_BANKING, "order_RzpValue004", "pay_RzpValue004");

        log.info("[DUMMY-DATA-SEEDER] 4 purchase flows created.");
    }

    private void createCompletePurchase(User user, UserWallet wallet, CoinPlan plan,
            LocalDateTime orderTime, PaymentMethod method,
            String gatewayOrderId, String gatewayPaymentId) {
        int totalCoins = plan.getCoins() + plan.getBonusCoins();

        // 1. CoinOrder
        String orderNumber = "ORD-2026-" + String.format("%06d", txnCounter);
        CoinOrder order = CoinOrder.builder()
                .orderNumber(orderNumber)
                .user(user)
                .plan(plan)
                .planCode(plan.getCode())
                .planName(plan.getName())
                .baseCoins(plan.getCoins())
                .bonusCoins(plan.getBonusCoins())
                .totalCoins(totalCoins)
                .orderAmount(plan.getPrice())
                .currency(CurrencyType.INR)
                .status(CoinOrderStatus.PAYMENT_SUCCESS)
                .remarks("Completed purchase")
                .build();
        coinOrderRepository.save(order);

        // 2. Payment
        String payRef = "PAY-2026-" + String.format("%08d", payCounter++);
        Payment payment = Payment.builder()
                .paymentReference(payRef)
                .order(order)
                .provider(PaymentProvider.RAZORPAY)
                .paymentMethod(method)
                .gatewayOrderId(gatewayOrderId)
                .gatewayPaymentId(gatewayPaymentId)
                .gatewaySignature("sig_" + gatewayPaymentId + "_verified")
                .amount(plan.getPrice())
                .currency(CurrencyType.INR)
                .status(PaymentStatus.CAPTURED)
                .verificationStatus(PaymentVerificationStatus.VERIFIED)
                .walletCredited(true)
                .retryCount(0)
                .completedAt(orderTime.plusMinutes(2))
                .build();
        paymentRepository.save(payment);

        // 3. WalletTransaction — CREDIT for purchase
        wallet.setCurrentBalance(wallet.getCurrentBalance() + totalCoins);
        wallet.setLifetimeRechargedCoins(wallet.getLifetimeRechargedCoins() + totalCoins);
        wallet.setLastTransactionAt(orderTime.plusMinutes(2));

        String txnNum = "TXN-2026-" + String.format("%08d", txnCounter++);
        WalletTransaction txn = WalletTransaction.builder()
                .transactionNumber(txnNum)
                .wallet(wallet)
                .amount((long) totalCoins)
                .type(TransactionType.CREDIT)
                .reason(TransactionReason.PURCHASE)
                .referenceType(ReferenceType.PAYMENT)
                .referenceId(payment.getId())
                .description("Purchased " + plan.getName() + " — " + totalCoins + " coins credited")
                .status(TransactionStatus.SUCCESS)
                .balanceAfterTransaction(wallet.getCurrentBalance())
                .build();
        walletTransactionRepository.save(txn);
    }

    // =========================================================================
    // 3. GIFT TRANSACTIONS — DEBIT (sent) and CREDIT (received)
    // =========================================================================
    private void seedGiftTransactions(User user, UserWallet wallet) {
        LocalDateTime baseTime = LocalDateTime.now().minusDays(5);

        // Gift Sent 1: Sent a ROSE (20 coins)
        debitWallet(wallet, 20L, TransactionReason.GIFT_SENT, ReferenceType.GIFT,
                "Sent ROSE gift in Voice Room", baseTime.plusHours(2));

        // Gift Sent 2: Sent a CHOCOLATE (100 coins)
        debitWallet(wallet, 100L, TransactionReason.GIFT_SENT, ReferenceType.GIFT,
                "Sent CHOCOLATE gift in Voice Room", baseTime.plusHours(5));

        // Gift Received 1: Received HEART (10 coins cashback)
        creditWallet(wallet, 10L, TransactionReason.GIFT_RECEIVED, ReferenceType.GIFT,
                "Received HEART gift cashback reward", baseTime.plusDays(1));

        // Gift Sent 3: Sent CAKE (500 coins)
        debitWallet(wallet, 500L, TransactionReason.GIFT_SENT, ReferenceType.GIFT,
                "Sent CAKE gift in Voice Room", baseTime.plusDays(2));

        // Gift Received 2: Received FIREWORKS (50 coins cashback)
        creditWallet(wallet, 50L, TransactionReason.GIFT_RECEIVED, ReferenceType.GIFT,
                "Received FIREWORKS gift cashback reward", baseTime.plusDays(3));

        log.info("[DUMMY-DATA-SEEDER] 5 gift transactions created.");
    }

    // =========================================================================
    // 4. MISC TRANSACTIONS — Daily Reward, Referral, etc.
    // =========================================================================
    private void seedMiscTransactions(User user, UserWallet wallet) {
        LocalDateTime baseTime = LocalDateTime.now().minusDays(10);

        // Daily Reward
        creditWallet(wallet, 15L, TransactionReason.DAILY_REWARD, ReferenceType.DAILY_REWARD,
                "Daily login reward — Day 1", baseTime.plusDays(1));

        log.info("[DUMMY-DATA-SEEDER] 1 misc transaction created.");
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================
    private void creditWallet(UserWallet wallet, long amount, TransactionReason reason,
            ReferenceType refType, String description, LocalDateTime time) {
        wallet.setCurrentBalance(wallet.getCurrentBalance() + amount);
        wallet.setLastTransactionAt(time);

        String txnNum = "TXN-2026-" + String.format("%08d", txnCounter++);
        WalletTransaction txn = WalletTransaction.builder()
                .transactionNumber(txnNum)
                .wallet(wallet)
                .amount(amount)
                .type(TransactionType.CREDIT)
                .reason(reason)
                .referenceType(refType)
                .referenceId(0L)
                .description(description)
                .status(TransactionStatus.SUCCESS)
                .balanceAfterTransaction(wallet.getCurrentBalance())
                .build();
        walletTransactionRepository.save(txn);
    }

    private void debitWallet(UserWallet wallet, long amount, TransactionReason reason,
            ReferenceType refType, String description, LocalDateTime time) {
        wallet.setCurrentBalance(wallet.getCurrentBalance() - amount);
        wallet.setLastTransactionAt(time);

        String txnNum = "TXN-2026-" + String.format("%08d", txnCounter++);
        WalletTransaction txn = WalletTransaction.builder()
                .transactionNumber(txnNum)
                .wallet(wallet)
                .amount(amount)
                .type(TransactionType.DEBIT)
                .reason(reason)
                .referenceType(refType)
                .referenceId(0L)
                .description(description)
                .status(TransactionStatus.SUCCESS)
                .balanceAfterTransaction(wallet.getCurrentBalance())
                .build();
        walletTransactionRepository.save(txn);
    }
}
