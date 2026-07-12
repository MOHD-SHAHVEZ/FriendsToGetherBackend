package com.gaur.friendstogether.exception;

/**
 * InsufficientBalanceException
 *
 * Jab user ke paas utne coins nahi hain jitne debit karne hain.
 *
 * HTTP Status: 400 Bad Request
 *
 * Example:
 * User ke paas 50 coins hain aur wo 100 coins ka gift bhejne ki koshish kar raha hai.
 *
 * Response:
 * "Insufficient balance. Required: 100 coins, Available: 50 coins"
 */
public class InsufficientBalanceException extends RuntimeException {

    public InsufficientBalanceException(String message) {
        super(message);
    }
}
