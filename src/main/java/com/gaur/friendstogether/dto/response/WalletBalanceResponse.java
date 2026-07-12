package com.gaur.friendstogether.dto.response;

import lombok.*;

import java.time.LocalDateTime;

/**
 * WalletBalanceResponse
 *
 * Dashboard pe current balance dikhane ke liye.
 *
 * Source: user_wallets table (single row, O(1) lookup).
 *
 * IMPORTANT:
 * Internal fields (id, version, user_id) kabhi expose NAHI karne.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletBalanceResponse {

    /**
     * Current available coins.
     *
     * Example: 2500
     */
    private Long coins;

    /**
     * Wallet status.
     *
     * Example: "ACTIVE"
     */
    private String status;

    /**
     * Lifetime recharge — VIP level, badges, analytics ke liye.
     *
     * Example: 12500
     */
    private Long lifetimeRechargedCoins;

    /**
     * Last transaction time — activity indicator.
     *
     * Example: "2026-07-09T14:00:00"
     */
    private LocalDateTime lastTransactionAt;
}
