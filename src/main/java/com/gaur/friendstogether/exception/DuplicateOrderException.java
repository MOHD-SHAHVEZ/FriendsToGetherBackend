package com.gaur.friendstogether.exception;

import org.springframework.http.HttpStatus;

/**
 * =============================================================================
 * DuplicateOrderException
 * =============================================================================
 *
 * Thrown when an idempotency key is reused with different
 * request parameters.
 *
 * Same key + Same body   → Return cached response (not an error)
 * Same key + Different body → DuplicateOrderException (422)
 *
 * HTTP Status: 422 Unprocessable Entity
 * =============================================================================
 */
public class DuplicateOrderException extends BusinessLogicException {

    public DuplicateOrderException(String message) {
        super(message, HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
