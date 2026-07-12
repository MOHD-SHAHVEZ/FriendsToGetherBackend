package com.gaur.friendstogether.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * =============================================================================
 * WalletTransaction
 * =============================================================================
 *
 * This table is the Financial Ledger of the application.
 * Every coin movement MUST create exactly one WalletTransaction.
 *
 * THIS TABLE IS IMMUTABLE. Never UPDATE or DELETE. Only INSERT new records.
 * Current balance is stored in UserWallet, not calculated from here.
 *
 * Purpose: Audit, History, Reports, Financial Tracking, Fraud Investigation.
 */
@Entity
@Table(
        name = "wallet_transactions",
        indexes = {
                @Index(name = "idx_wallet_transaction_wallet", columnList = "wallet_id"),
                @Index(name = "idx_wallet_transaction_type", columnList = "type"),
                @Index(name = "idx_wallet_transaction_reason", columnList = "reason"),
                @Index(name = "idx_wallet_transaction_reference", columnList = "reference_type,reference_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WalletTransaction {

    /** Internal Database ID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Human readable transaction number (e.g. TXN-2026-00000001) */
    @Column(nullable = false, unique = true, updatable = false, length = 50)
    private String transactionNumber;

    /** Every transaction belongs to exactly one wallet. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id", nullable = false)
    private UserWallet wallet;

    /** Number of coins involved. */
    @Column(nullable = false)
    private Long amount;

    /** Type of transaction: CREDIT or DEBIT */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    /** Business reason for this transaction (e.g. PURCHASE, GIFT_SENT) */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionReason reason;

    /** Generic reference to source module (e.g. PAYMENT, GIFT) */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReferenceType referenceType;

    /** Stores ID of source record (e.g. Payment ID or Gift ID) */
    @Column(nullable = false)
    private Long referenceId;

    /** Human readable transaction description */
    @Column(length = 500)
    private String description;

    /** Represents current processing status: SUCCESS, FAILED, PENDING */
    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false)
    private TransactionStatus status = TransactionStatus.SUCCESS;

    /** Snapshot of wallet balance immediately after this transaction */
    @Column(nullable = false)
    private Long balanceAfterTransaction;

    /** Time when transaction was permanently recorded */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Executed automatically before INSERT */
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}