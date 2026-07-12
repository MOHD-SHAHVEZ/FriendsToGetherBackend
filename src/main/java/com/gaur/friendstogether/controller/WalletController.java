package com.gaur.friendstogether.controller;

import com.gaur.friendstogether.dto.response.ApiResponse;
import com.gaur.friendstogether.dto.response.WalletBalanceResponse;
import com.gaur.friendstogether.dto.response.WalletSummaryResponse;
import com.gaur.friendstogether.dto.response.WalletTransactionResponse;
import com.gaur.friendstogether.entity.TransactionType;
import com.gaur.friendstogether.entity.User;
import com.gaur.friendstogether.exception.ResourceNotFoundException;
import com.gaur.friendstogether.repository.UserRepository;
import com.gaur.friendstogether.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * =============================================================================
 * WalletController
 * =============================================================================
 *
 * User Wallet Dashboard ke REST endpoints.
 *
 * IMPORTANT:
 * ---------------------------------------------------------------------------
 * Ye controller SIRF READ operations expose karta hai.
 *
 * credit() aur debit() KABHI REST endpoint se expose NAHI hone chahiye.
 * Wo sirf internal service methods hain jo:
 * - PaymentService (purchase ke baad)
 * - GiftService (gift bhejte waqt)
 * - AdminService (manual bonus/deduction)
 * call karein.
 *
 * Agar credit/debit REST pe expose karein to koi bhi user
 * apne aap ko unlimited coins de sakta hai — CRITICAL SECURITY RISK.
 *
 * ENDPOINTS:
 * ---------------------------------------------------------------------------
 * GET /api/wallet/balance       → Current coin balance
 * GET /api/wallet/transactions  → Paginated transaction history
 * GET /api/wallet/summary       → Dashboard composite (balance + recent txns)
 *
 * =============================================================================
 */
@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
public class WalletController {

    private static final Logger logger = LoggerFactory.getLogger(WalletController.class);

    private final WalletService walletService;
    private final UserRepository userRepository;

    // =========================================================================
    // GET /api/wallet/balance
    // =========================================================================

    /**
     * Current coin balance return karo.
     *
     * Source: user_wallets table (1 row, O(1) lookup).
     *
     * Response Example:
     * {
     *   "success": true,
     *   "message": "Wallet balance fetched successfully",
     *   "data": {
     *     "coins": 2500,
     *     "status": "ACTIVE",
     *     "lifetimeRechargedCoins": 12500,
     *     "lastTransactionAt": "2026-07-09T14:00:00"
     *   }
     * }
     */
    @GetMapping("/balance")
    public ResponseEntity<ApiResponse<WalletBalanceResponse>> getBalance(
            Authentication authentication) {

        Long userId = extractUserId(authentication);
        logger.info("[WALLET-API] GET /balance → userId={}", userId);

        WalletBalanceResponse balance = walletService.getBalance(userId);

        ApiResponse<WalletBalanceResponse> response = ApiResponse.<WalletBalanceResponse>builder()
                .success(true)
                .message("Wallet balance fetched successfully")
                .data(balance)
                .build();

        return ResponseEntity.ok(response);
    }

    // =========================================================================
    // GET /api/wallet/transactions
    // =========================================================================

    /**
     * Paginated transaction history.
     *
     * Query Params:
     * - page (default: 0)
     * - size (default: 20, max: 50)
     * - type (optional: CREDIT / DEBIT)
     *
     * Response Example:
     * {
     *   "success": true,
     *   "data": {
     *     "content": [ ... transactions ... ],
     *     "totalElements": 150,
     *     "totalPages": 8,
     *     "number": 0
     *   }
     * }
     */
    @GetMapping("/transactions")
    public ResponseEntity<ApiResponse<Page<WalletTransactionResponse>>> getTransactionHistory(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) TransactionType type) {

        // Cap page size at 50 to prevent abuse
        if (size > 50) {
            size = 50;
        }

        Long userId = extractUserId(authentication);
        logger.info("[WALLET-API] GET /transactions → userId={}, page={}, size={}, type={}",
                userId, page, size, type);

        Pageable pageable = PageRequest.of(page, size);
        Page<WalletTransactionResponse> transactions =
                walletService.getTransactionHistory(userId, type, pageable);

        ApiResponse<Page<WalletTransactionResponse>> response =
                ApiResponse.<Page<WalletTransactionResponse>>builder()
                        .success(true)
                        .message("Transaction history fetched successfully")
                        .data(transactions)
                        .build();

        return ResponseEntity.ok(response);
    }

    // =========================================================================
    // GET /api/wallet/summary
    // =========================================================================

    /**
     * Dashboard composite — balance + last 5 recent transactions.
     *
     * Ek API call me poora dashboard data.
     * Frontend ko 2 alag calls nahi karni padtee.
     *
     * Response Example:
     * {
     *   "success": true,
     *   "data": {
     *     "balance": { "coins": 2500, "status": "ACTIVE" },
     *     "recentTransactions": [ ... last 5 ... ]
     *   }
     * }
     */
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<WalletSummaryResponse>> getDashboardSummary(
            Authentication authentication) {

        Long userId = extractUserId(authentication);
        logger.info("[WALLET-API] GET /summary → userId={}", userId);

        WalletSummaryResponse summary = walletService.getDashboardSummary(userId);

        ApiResponse<WalletSummaryResponse> response = ApiResponse.<WalletSummaryResponse>builder()
                .success(true)
                .message("Wallet summary fetched successfully")
                .data(summary)
                .build();

        return ResponseEntity.ok(response);
    }

    // =========================================================================
    // HELPER
    // =========================================================================

    /**
     * JWT Authentication se user ka database ID extract karo.
     *
     * Flow:
     * 1. Authentication.getName() → user email (from JWT)
     * 2. UserRepository.findByEmail() → User entity
     * 3. User.getId() → database ID (Long)
     */
    private Long extractUserId(Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResourceNotFoundException("User not authenticated");
        }

        String email = authentication.getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found: " + email));

        return user.getId();
    }
}
