package com.gaur.friendstogether.dto.response;

import lombok.*;

import java.time.LocalDateTime;

/**
 * WalletTransactionResponse
 *
 * Single wallet transaction ka response DTO.
 *
 * Source: wallet_transactions table.
 *
 * IMPORTANT:
 * - Internal wallet_id, database id expose NAHI karna.
 * - transactionNumber hi public identifier hai (e.g., TXN-2026-00000051).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletTransactionResponse {

    /**
     * Human readable transaction number.
     *
     * Example: "TXN-2026-00000051"
     */
    private String transactionNumber;

    /**
     * Coins involved in this transaction.
     *
     * Example: 100
     */
    private Long amount;

    /**
     * CREDIT or DEBIT.
     */
    private String type;

    /**
     * Business reason.
     *
     * Example: "PURCHASE", "GIFT_SENT", "GIFT_RECEIVED", "ADMIN_CREDIT"
     */
    private String reason;

    /**
     * Human readable description.
     *
     * Example: "Rose sent to @priya"
     */
    private String description;

    /**
     * Wallet balance snapshot after this transaction.
     *
     * Example: 2400
     */
    private Long balanceAfter;

    /**
     * Transaction timestamp.
     */
    private LocalDateTime createdAt;
}
