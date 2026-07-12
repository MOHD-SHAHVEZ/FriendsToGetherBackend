package com.gaur.friendstogether.exception;

import org.springframework.http.HttpStatus;

/**
 * =============================================================================
 * OrderNotCancellableException
 * =============================================================================
 *
 * Thrown when user or admin tries to cancel an order
 * that is in a terminal status.
 *
 * Terminal statuses (cannot be cancelled):
 *   PAYMENT_SUCCESS
 *   PAYMENT_FAILED
 *   EXPIRED
 *   CANCELLED (already cancelled)
 *
 * HTTP Status: 400 Bad Request
 * =============================================================================
 */
public class OrderNotCancellableException extends BusinessLogicException {

    public OrderNotCancellableException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
