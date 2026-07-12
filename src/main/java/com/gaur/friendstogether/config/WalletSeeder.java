package com.gaur.friendstogether.config;

import com.gaur.friendstogether.entity.User;
import com.gaur.friendstogether.repository.UserRepository;
import com.gaur.friendstogether.service.WalletCreationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * =============================================================================
 * WalletSeeder
 * =============================================================================
 *
 * Application startup pe har existing user ke liye wallet seed karta hai.
 *
 * FLOW:
 * 1. Fetch all users from users table
 * 2. Check each user — agar wallet already exist karta hai to SKIP
 * 3. Agar wallet nahi hai to WalletCreationService se create karo
 *
 * IMPORTANT:
 * - Idempotent hai — baar baar chala sakte ho, duplicate nahi banega.
 * - WalletCreationService ke through create hota hai — consistent logic.
 * - Production safe — existing wallets ko touch nahi karta.
 *
 * =============================================================================
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(2)  // DatabaseMigrationRunner ke BAAD chale
public class WalletSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final WalletCreationService walletCreationService;

    @Override
    public void run(String... args) throws Exception {
        log.info("[WALLET-SEEDER] Starting wallet seeding for existing users...");
        seedWalletsForAllUsers();
    }

    private void seedWalletsForAllUsers() {
        try {

            // 1. Fetch all users via JPA
            List<User> users = userRepository.findAll();

            if (users.isEmpty()) {
                log.info("[WALLET-SEEDER] No users found. Skipping.");
                return;
            }

            int created = 0;
            int skipped = 0;

            for (User user : users) {

                try {
                    // ensureWalletExists is idempotent — skips if wallet already exists
                    boolean hadWallet = user.getWallet() != null;

                    walletCreationService.ensureWalletExists(user);

                    if (hadWallet) {
                        skipped++;
                    } else {
                        created++;
                        log.info("[WALLET-SEEDER] Wallet created: userId={}, email={}",
                                user.getId(), user.getEmail());
                    }
                } catch (Exception e) {
                    log.error("[WALLET-SEEDER] Failed to seed wallet for userId={}: {}",
                            user.getId(), e.getMessage());
                    // Non-fatal — continue with next user
                }
            }

            log.info("[WALLET-SEEDER] Seeding complete. Created: {}, Skipped (already existed): {}",
                    created, skipped);

        } catch (Exception e) {
            log.error("[WALLET-SEEDER] Failed to seed wallets: {}", e.getMessage());
            // Non-fatal — don't prevent application startup
        }
    }
}
