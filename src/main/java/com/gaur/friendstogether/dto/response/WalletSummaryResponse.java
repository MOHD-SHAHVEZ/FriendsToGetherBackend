package com.gaur.friendstogether.dto.response;

import lombok.*;

import java.util.List;

/**
 * WalletSummaryResponse
 *
 * Dashboard composite DTO — balance + recent transactions ek hi API call me.
 *
 * GET /api/wallet/summary
 *
 * Isse frontend ko 2 alag API calls nahi karne padte:
 * - /wallet/balance
 * - /wallet/transactions
 *
 * Ek call me poora dashboard data mil jaata hai.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletSummaryResponse {

    /**
     * Current wallet balance info.
     */
    private WalletBalanceResponse balance;

    /**
     * Last 5 recent transactions.
     *
     * Dashboard pe "Recent Activity" section ke liye.
     */
    private List<WalletTransactionResponse> recentTransactions;
}
